package com.fluxtech.agente.sincronizacion;

import com.fluxtech.agente.cola.ColaDeFichadasPendientes;
import com.fluxtech.agente.configuracion.ConfiguracionLocal;
import com.fluxtech.agente.modelo.ConfiguracionDeReloj;
import com.fluxtech.agente.nube.ServicioNubeFluxTech;
import com.fluxtech.agente.receptor.ReceptorDeEventosHttp;
import com.fluxtech.agente.registro.RegistroDeEventos;
import com.fluxtech.agente.reloj.SeleccionadorDeLectorDeReloj;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinador del agente: repite el ciclo de sincronización cada N segundos (60 por defecto).
 *
 * Recorrido del dato en cada ciclo (ejecutarUnCiclo):
 *   1. ActualizadorDeConfiguracion trae de la nube la lista de relojes (o usa la guardada).
 *   2. LecturaDeRelojes lee cada reloj y deja las fichadas nuevas en ColaDeFichadasPendientes.
 *   3. SubidaDeFichadas sube lo pendiente; solo se borra de la cola lo que la nube confirma.
 *   4. Se envía el heartbeat (estado de cada reloj + pendientes) para que el panel muestre "En línea".
 *   En paralelo, ReceptorDeEventosHttp puede ir agregando a la cola lo que envían los relojes.
 *   Ningún error detiene el agente: se registra y se reintenta en el ciclo siguiente.
 */
public final class CicloDeSincronizacion {

    public static final String VERSION_DEL_AGENTE = "1.1.0";
    private static final String NOMBRE_DEL_ARCHIVO = "CicloDeSincronizacion.java";

    private final ConfiguracionLocal configuracionLocal;
    private final ServicioNubeFluxTech servicioNube;
    private final ColaDeFichadasPendientes colaDePendientes;
    private final ActualizadorDeConfiguracion actualizadorDeConfiguracion;
    private final LecturaDeRelojes lecturaDeRelojes;
    private final SubidaDeFichadas subidaDeFichadas;
    private volatile boolean debeSeguirCorriendo = true;

    public CicloDeSincronizacion(ConfiguracionLocal configuracionLocal) throws IOException {
        this.configuracionLocal = configuracionLocal;
        this.servicioNube = new ServicioNubeFluxTech(configuracionLocal.obtenerDireccionDelServidor(),
                configuracionLocal.obtenerClaveDelAgente());
        this.colaDePendientes = new ColaDeFichadasPendientes(configuracionLocal.obtenerCarpetaDeDatos());
        this.actualizadorDeConfiguracion = new ActualizadorDeConfiguracion(servicioNube,
                configuracionLocal.obtenerCarpetaDeDatos(), configuracionLocal.obtenerSegundosEntreCiclosForzados());
        this.lecturaDeRelojes = new LecturaDeRelojes(new SeleccionadorDeLectorDeReloj(configuracionLocal), colaDePendientes,
                configuracionLocal.obtenerDiasALeerLaPrimeraVez());
        this.subidaDeFichadas = new SubidaDeFichadas(colaDePendientes, servicioNube);
    }

    /** Arranca el receptor (si está habilitado) y repite el ciclo hasta que se cierre el agente. */
    public void ejecutarParaSiempre() throws InterruptedException {
        RegistroDeEventos.informar("Agente FluxTech " + VERSION_DEL_AGENTE + " iniciado. Servidor: "
                + configuracionLocal.obtenerDireccionDelServidor() + " | Pendientes en cola: " + colaDePendientes.contarPendientes());
        ReceptorDeEventosHttp receptor = iniciarReceptorSiCorresponde();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> debeSeguirCorriendo = false));

        while (debeSeguirCorriendo) {
            long momentoDeInicio = System.currentTimeMillis();
            try {
                ejecutarUnCiclo();
            } catch (InterruptedException interrupcion) {
                throw interrupcion;
            } catch (RuntimeException errorInesperado) {
                RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "ejecutarParaSiempre",
                        "error inesperado en el ciclo, se reintenta: " + RegistroDeEventos.describirFallo(errorInesperado));
            }
            long milisegundosRestantes = actualizadorDeConfiguracion.obtenerSegundosEntreCiclos() * 1000L
                    - (System.currentTimeMillis() - momentoDeInicio);
            if (milisegundosRestantes > 0) {
                Thread.sleep(milisegundosRestantes);
            }
        }
        if (receptor != null) {
            receptor.detener();
        }
        RegistroDeEventos.informar("Agente detenido.");
    }

    /** Un ciclo completo: configuración → lectura → subida → heartbeat. Público para las pruebas. */
    public void ejecutarUnCiclo() throws InterruptedException {
        actualizadorDeConfiguracion.actualizarDesdeLaNube();

        List<Map<String, Object>> estadoDeCadaReloj = new ArrayList<>();
        if (configuracionLocal.estaHabilitadaLaLecturaDeMemoria()) {
            for (ConfiguracionDeReloj reloj : actualizadorDeConfiguracion.obtenerRelojesConfigurados()) {
                estadoDeCadaReloj.add(lecturaDeRelojes.leerReloj(reloj));
            }
        }

        subidaDeFichadas.subirTodasLasPendientes();
        enviarHeartbeat(estadoDeCadaReloj);
    }

    public ColaDeFichadasPendientes obtenerColaDePendientes() {
        return colaDePendientes;
    }

    /** Abre el receptor; si el puerto está ocupado sigue funcionando solo con la lectura de memoria. */
    private ReceptorDeEventosHttp iniciarReceptorSiCorresponde() {
        if (!configuracionLocal.estaHabilitadoElReceptor()) {
            return null;
        }
        ReceptorDeEventosHttp receptor = new ReceptorDeEventosHttp(configuracionLocal.obtenerPuertoDelReceptor(),
                colaDePendientes, actualizadorDeConfiguracion::buscarIdDeRelojPorIp);
        try {
            receptor.iniciar();
            return receptor;
        } catch (IOException puertoOcupado) {
            RegistroDeEventos.avisar("Se sigue sin receptor, solo con la lectura de memoria de los relojes");
            return null;
        }
    }

    /** POST /api/agente/heartbeat. Si falla no se reintenta: el próximo ciclo manda uno nuevo. */
    private void enviarHeartbeat(List<Map<String, Object>> estadoDeCadaReloj) throws InterruptedException {
        Map<String, Object> estadoDelAgente = new LinkedHashMap<>();
        estadoDelAgente.put("version", VERSION_DEL_AGENTE);
        estadoDelAgente.put("pendientes", colaDePendientes.contarPendientes());
        estadoDelAgente.put("relojes", estadoDeCadaReloj);
        try {
            servicioNube.informarEstado(estadoDelAgente);
        } catch (IOException | RuntimeException excepcion) {
            // ServicioNubeFluxTech ya registró el error con el formato obligatorio
        }
    }
}
