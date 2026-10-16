package com.fluxtech.agente.cola;

import com.fluxtech.agente.json.EscritorJson;
import com.fluxtech.agente.json.LectorJson;
import com.fluxtech.agente.modelo.FichadaLeida;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cola de fichadas leídas que todavía no confirmó la nube (archivo datos/pendientes.json).
 *
 * Regla de negocio central: una fichada se borra de la cola SOLO cuando la nube confirma que la
 * recibió. Si no hay internet, si se corta la luz o si se apaga la PC, las fichadas siguen acá.
 *
 * Recorrido del dato:
 *   1. LecturaDeRelojes y ReceptorDeEventosHttp llaman a agregar...() con fichadas nuevas.
 *   2. SubidaDeFichadas pide obtenerLoteEnOrdenCronologico() y lo envía a la nube.
 *   3. Si la nube responde bien, SubidaDeFichadas llama a confirmarSubida() y el lote sale de la cola.
 *   Los métodos son synchronized porque el receptor agrega desde otro hilo.
 */
public final class ColaDeFichadasPendientes {

    private final Path archivoDePendientes;
    private final EstadoDeLecturaDeRelojes estadoDeLectura;
    /** Clave única → fichada, en orden de llegada. */
    private final Map<String, FichadaLeida> fichadasPendientes = new LinkedHashMap<>();

    public ColaDeFichadasPendientes(Path carpetaDeDatos) throws IOException {
        Files.createDirectories(carpetaDeDatos);
        this.archivoDePendientes = carpetaDeDatos.resolve("pendientes.json");
        this.estadoDeLectura = new EstadoDeLecturaDeRelojes(carpetaDeDatos);
        cargarDesdeDisco();
    }

    /** Agrega una fichada si no está pendiente ni fue confirmada. Devuelve true si era nueva. */
    public synchronized boolean agregarSiEsNueva(FichadaLeida fichada) {
        boolean eraNueva = agregarSinGuardar(fichada);
        if (eraNueva) {
            guardarEnDisco();
        }
        return eraNueva;
    }

    /** Agrega varias fichadas y guarda una sola vez. Devuelve cuántas eran nuevas. */
    public synchronized int agregarTodasLasNuevas(List<FichadaLeida> fichadas) {
        int cantidadDeNuevas = 0;
        for (FichadaLeida fichada : fichadas) {
            if (agregarSinGuardar(fichada)) {
                cantidadDeNuevas++;
            }
        }
        if (cantidadDeNuevas > 0) {
            guardarEnDisco();
        }
        return cantidadDeNuevas;
    }

    /** Próximo lote a subir, de la fichada más vieja a la más nueva (la nube empareja entrada/salida en orden). */
    public synchronized List<FichadaLeida> obtenerLoteEnOrdenCronologico(int tamanioMaximoDelLote) {
        List<FichadaLeida> todasLasPendientes = new ArrayList<>(fichadasPendientes.values());
        todasLasPendientes.sort(Comparator.comparing(FichadaLeida::obtenerFechaHoraDeLaFichada)
                .thenComparing(FichadaLeida::obtenerLegajoDelEmpleado));
        return new ArrayList<>(todasLasPendientes.subList(0, Math.min(tamanioMaximoDelLote, todasLasPendientes.size())));
    }

    /** La nube confirmó el lote: se saca de la cola y se avanza el estado de lectura. */
    public synchronized void confirmarSubida(List<FichadaLeida> loteConfirmado) {
        for (FichadaLeida fichada : loteConfirmado) {
            fichadasPendientes.remove(fichada.obtenerClaveUnica());
        }
        guardarEnDisco();
        estadoDeLectura.registrarConfirmadas(loteConfirmado);
    }

    public synchronized int contarPendientes() {
        return fichadasPendientes.size();
    }

    public synchronized LocalDateTime obtenerUltimaFichadaConfirmada(long idDelReloj) {
        return estadoDeLectura.obtenerUltimaFichadaConfirmada(idDelReloj);
    }

    private boolean agregarSinGuardar(FichadaLeida fichada) {
        String claveUnica = fichada.obtenerClaveUnica();
        if (fichadasPendientes.containsKey(claveUnica) || estadoDeLectura.fueConfirmadaRecientemente(claveUnica)) {
            return false;
        }
        fichadasPendientes.put(claveUnica, fichada);
        return true;
    }

    private void guardarEnDisco() {
        List<Object> fichadasComoMapas = new ArrayList<>();
        for (FichadaLeida fichada : fichadasPendientes.values()) {
            fichadasComoMapas.add(fichada.convertirEnMapa());
        }
        ArchivoConEscrituraSegura.escribir(archivoDePendientes, EscritorJson.convertirEnTexto(fichadasComoMapas));
    }

    @SuppressWarnings("unchecked")
    private void cargarDesdeDisco() {
        String contenido = ArchivoConEscrituraSegura.leerSiExiste(archivoDePendientes);
        if (contenido == null) {
            return;
        }
        try {
            Object lista = LectorJson.convertirEnObjeto(contenido);
            if (!(lista instanceof List)) {
                return;
            }
            for (Object elemento : (List<Object>) lista) {
                if (elemento instanceof Map) {
                    FichadaLeida fichada = FichadaLeida.crearDesdeMapa((Map<String, Object>) elemento);
                    if (fichada != null) {
                        fichadasPendientes.put(fichada.obtenerClaveUnica(), fichada);
                    }
                }
            }
        } catch (RuntimeException archivoDaniado) {
            // Se guarda la copia; lo que faltó se vuelve a leer del reloj en el próximo ciclo
            ArchivoConEscrituraSegura.guardarCopiaDeArchivoDaniado(archivoDePendientes);
        }
    }
}
