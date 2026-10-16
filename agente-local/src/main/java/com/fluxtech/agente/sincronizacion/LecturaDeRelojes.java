package com.fluxtech.agente.sincronizacion;

import com.fluxtech.agente.cola.ColaDeFichadasPendientes;
import com.fluxtech.agente.modelo.ConfiguracionDeReloj;
import com.fluxtech.agente.modelo.EventoDeReloj;
import com.fluxtech.agente.modelo.FichadaLeida;
import com.fluxtech.agente.modelo.InformacionDelEquipo;
import com.fluxtech.agente.registro.RegistroDeEventos;
import com.fluxtech.agente.reloj.CredencialesDelRelojIncorrectasException;
import com.fluxtech.agente.reloj.EquipoDistintoAlDelPanelException;
import com.fluxtech.agente.reloj.LectorDeReloj;
import com.fluxtech.agente.reloj.SeleccionadorDeLectorDeReloj;
import com.fluxtech.agente.reloj.hikvision.CodigosDeEventoHikvision;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee la memoria de un reloj y pasa a la cola las fichadas nuevas.
 *
 * Recorrido del dato (por cada reloj del panel):
 *   1. SeleccionadorDeLectorDeReloj indica qué métodos probar (ISAPI y/o SDK) y en qué orden.
 *   2. Con cada método: se pide el número de serie al equipo y se compara con el del panel.
 *   3. Si coincide, se leen los eventos desde la última fichada confirmada (ReglasDeLectura).
 *   4. CodigosDeEventoHikvision descarta rechazos; lo válido se agrega a ColaDeFichadasPendientes.
 *   5. Se devuelve el estado del reloj (ok o error) para informarlo a la nube en el heartbeat.
 *   Si un método falla se prueba el siguiente, salvo que el error sea usuario/contraseña incorrectos.
 */
final class LecturaDeRelojes {

    private final SeleccionadorDeLectorDeReloj seleccionadorDeLector;
    private final ColaDeFichadasPendientes colaDePendientes;
    private final int diasALeerLaPrimeraVez;

    LecturaDeRelojes(SeleccionadorDeLectorDeReloj seleccionadorDeLector, ColaDeFichadasPendientes colaDePendientes,
                     int diasALeerLaPrimeraVez) {
        this.seleccionadorDeLector = seleccionadorDeLector;
        this.colaDePendientes = colaDePendientes;
        this.diasALeerLaPrimeraVez = diasALeerLaPrimeraVez;
    }

    /** Lee un reloj y devuelve su estado en el formato del heartbeat: {id, ok, error, serialDetectado, metodo}. */
    Map<String, Object> leerReloj(ConfiguracionDeReloj reloj) throws InterruptedException {
        Map<String, Object> estadoDelReloj = new LinkedHashMap<>();
        estadoDelReloj.put("id", reloj.obtenerIdDelReloj());

        // Validaciones previas: marca soportada y datos de conexión completos
        List<LectorDeReloj> lectoresAProbar = seleccionadorDeLector.obtenerLectoresEnOrdenDePrueba(reloj);
        if (lectoresAProbar.isEmpty()) {
            return marcarError(estadoDelReloj, "marca '" + reloj.obtenerMarca() + "' todavía no soportada por el agente");
        }
        if (!reloj.tieneDatosDeConexionCompletos()) {
            return marcarError(estadoDelReloj, "faltan IP, usuario o contraseña del reloj en el panel");
        }

        String ultimoError = "";
        for (LectorDeReloj lector : lectoresAProbar) {
            try {
                leerConUnMetodo(reloj, lector, estadoDelReloj);
                seleccionadorDeLector.recordarMetodoQueFunciono(reloj.obtenerIdDelReloj(), lector.obtenerNombreDelMetodo());
                estadoDelReloj.put("ok", true);
                estadoDelReloj.put("metodo", lector.obtenerNombreDelMetodo());
                return estadoDelReloj;
            } catch (CredencialesDelRelojIncorrectasException | EquipoDistintoAlDelPanelException errorDefinitivo) {
                // Contraseña incorrecta u otro equipo en esa IP: otro método no lo soluciona
                RegistroDeEventos.avisar("Reloj " + reloj.obtenerNombreParaMostrar() + ": " + errorDefinitivo.getMessage());
                return marcarError(estadoDelReloj, errorDefinitivo.getMessage());
            } catch (IOException | RuntimeException excepcion) {
                ultimoError = lector.obtenerNombreDelMetodo() + ": " + RegistroDeEventos.describirFallo(excepcion);
                RegistroDeEventos.avisar("Reloj " + reloj.obtenerNombreParaMostrar() + " no se pudo leer por " + ultimoError);
            } finally {
                lector.cerrarConexion();
            }
        }
        return marcarError(estadoDelReloj, ultimoError);
    }

    /** Verifica el equipo, lee los eventos y encola las fichadas nuevas. Lanza IOException si algo falla. */
    private void leerConUnMetodo(ConfiguracionDeReloj reloj, LectorDeReloj lector, Map<String, Object> estadoDelReloj)
            throws IOException, InterruptedException {
        // Paso 1: confirmar que la IP del panel apunta a este reloj y no a otro equipo
        InformacionDelEquipo equipo = lector.obtenerInformacionDelEquipo();
        estadoDelReloj.put("serialDetectado", equipo.obtenerNumeroDeSerie());
        if (!ReglasDeLectura.coincideElNumeroDeSerie(reloj.obtenerNumeroDeSerieEnElPanel(), equipo.obtenerNumeroDeSerie())) {
            throw new EquipoDistintoAlDelPanelException(reloj.obtenerDireccionEnLaRedLocal(), equipo.obtenerNumeroDeSerie(),
                    reloj.obtenerNumeroDeSerieEnElPanel());
        }

        // Paso 2: rango de lectura
        LocalDateTime ahora = LocalDateTime.now().withNano(0);
        LocalDateTime desde = ReglasDeLectura.calcularInicioDeLectura(
                colaDePendientes.obtenerUltimaFichadaConfirmada(reloj.obtenerIdDelReloj()),
                reloj.obtenerUltimaFichadaGuardadaEnLaNube(), ahora, diasALeerLaPrimeraVez);

        // Paso 3: leer, filtrar rechazos y encolar
        List<FichadaLeida> fichadasValidas = new ArrayList<>();
        for (EventoDeReloj evento : lector.leerEventosEntre(desde, ahora.plusMinutes(1))) {
            RegistroDeEventos.detallar("Reloj " + reloj.obtenerNombreParaMostrar() + " evento: legajo=" + evento.obtenerLegajoDelEmpleado()
                    + " hora=" + evento.obtenerFechaHoraDelEvento() + " código=" + evento.obtenerCodigoDeTipoDeEvento());
            if (CodigosDeEventoHikvision.esFichadaValida(evento)) {
                fichadasValidas.add(new FichadaLeida(reloj.obtenerIdDelReloj(), evento.obtenerLegajoDelEmpleado(),
                        evento.obtenerFechaHoraDelEvento(), evento.obtenerModoDeVerificacion()));
            }
        }
        int cantidadDeNuevas = colaDePendientes.agregarTodasLasNuevas(fichadasValidas);
        if (cantidadDeNuevas > 0) {
            RegistroDeEventos.informar("Reloj " + reloj.obtenerNombreParaMostrar() + " (" + lector.obtenerNombreDelMetodo() + "): "
                    + cantidadDeNuevas + " fichada(s) nueva(s)");
        }
    }

    private static Map<String, Object> marcarError(Map<String, Object> estadoDelReloj, String descripcion) {
        estadoDelReloj.put("ok", false);
        estadoDelReloj.put("error", descripcion);
        return estadoDelReloj;
    }

}
