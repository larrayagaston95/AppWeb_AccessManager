package com.fluxtech.agente.diagnostico;

import com.fluxtech.agente.modelo.EventoDeReloj;
import com.fluxtech.agente.modelo.InformacionDelEquipo;
import com.fluxtech.agente.registro.RegistroDeEventos;
import com.fluxtech.agente.reloj.LectorDeReloj;
import com.fluxtech.agente.reloj.hikvision.CodigosDeEventoHikvision;
import com.fluxtech.agente.reloj.hikvision.LectorHikvisionIsapi;
import com.fluxtech.agente.reloj.hikvision.sdk.UbicacionDelSdkHikvision;
import com.fluxtech.agente.reloj.hikvision.sdk.LectorHikvisionSdk;
import com.fluxtech.agente.sincronizacion.ReglasDeLectura;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Prueba manual de un reloj con cada método de lectura y muestra el resultado en la consola.
 * Es la herramienta para saber, antes de instalar, si un modelo se lee por ISAPI o necesita el SDK.
 *
 * Recorrido del dato:
 *   Main (--probar-reloj / --probar-sdk) o DiagnosticoCompleto llaman a probarTodosLosMetodos().
 *   Por cada método: se pide la información del equipo, se leen las últimas 48 h de eventos y se
 *   muestran los más recientes indicando si cuentan como fichada o como rechazo.
 */
public final class DiagnosticoDeReloj {

    private DiagnosticoDeReloj() {
        // Clase de utilidades: no se instancia
    }

    /**
     * Prueba ISAPI y, si el SDK está instalado (o se pide solo SDK), también el SDK.
     * Devuelve el nombre del primer método que funcionó, o null si ninguno.
     */
    public static String probarTodosLosMetodos(String direccion, String usuario, String contrasenia, Path carpetaDelSdk,
                                              int puertoDelSdk, String numeroDeSerieEsperado, boolean probarSoloSdk)
            throws InterruptedException {
        String metodoQueFunciono = null;
        if (!probarSoloSdk) {
            System.out.println("-- Método ISAPI (red, sin instalar nada) --");
            if (probarUnMetodo(new LectorHikvisionIsapi(direccion, usuario, contrasenia), numeroDeSerieEsperado)) {
                metodoQueFunciono = "ISAPI";
            }
        }
        boolean sdkInstalado = UbicacionDelSdkHikvision.estaInstaladoEn(carpetaDelSdk);
        if (probarSoloSdk || sdkInstalado) {
            System.out.println("-- Método SDK oficial de Hikvision (puerto " + puertoDelSdk + ") --");
            String ipSinPuerto = direccion.replaceFirst("^https?://", "").replaceFirst(":\\d+$", "");
            boolean funcionoElSdk = probarUnMetodo(
                    new LectorHikvisionSdk(ipSinPuerto, puertoDelSdk, usuario, contrasenia, carpetaDelSdk), numeroDeSerieEsperado);
            if (funcionoElSdk && metodoQueFunciono == null) {
                metodoQueFunciono = "SDK";
            }
        } else if (metodoQueFunciono == null) {
            System.out.println("[INFO]  El SDK de Hikvision no está instalado en " + carpetaDelSdk
                    + ". Si ISAPI no funciona, instalarlo (ver README) y volver a probar.");
        }
        System.out.println(metodoQueFunciono != null
                ? "Resultado: este reloj se puede leer por " + metodoQueFunciono
                : "Resultado: no se pudo leer la memoria de este reloj con ningún método");
        return metodoQueFunciono;
    }

    /** Prueba un método: información del equipo + lectura de 48 h. Devuelve true si ambas funcionaron. */
    private static boolean probarUnMetodo(LectorDeReloj lector, String numeroDeSerieEsperado) throws InterruptedException {
        try {
            // Paso 1: el equipo responde y es el esperado
            InformacionDelEquipo equipo = lector.obtenerInformacionDelEquipo();
            System.out.println("[OK]    Responde: modelo " + equipo.obtenerModelo() + ", serie " + equipo.obtenerNumeroDeSerie()
                    + (equipo.obtenerVersionDeFirmware().isEmpty() ? "" : ", firmware " + equipo.obtenerVersionDeFirmware()));
            if (numeroDeSerieEsperado != null && !ReglasDeLectura.coincideElNumeroDeSerie(numeroDeSerieEsperado, equipo.obtenerNumeroDeSerie())) {
                System.out.println("[FALLA] El número de serie no coincide con el del panel (" + numeroDeSerieEsperado + ")");
                return false;
            }
            // Paso 2: leer la memoria
            LocalDateTime ahora = LocalDateTime.now().withNano(0);
            List<EventoDeReloj> eventos = lector.leerEventosEntre(ahora.minusDays(2), ahora.plusMinutes(1));
            mostrarEventos(eventos);
            return true;
        } catch (Exception excepcion) {
            System.out.println("[FALLA] " + RegistroDeEventos.describirFallo(excepcion));
            return false;
        } finally {
            lector.cerrarConexion();
        }
    }

    /** Cantidad de eventos y los 8 más recientes con su código y si cuentan como fichada. */
    private static void mostrarEventos(List<EventoDeReloj> eventos) {
        long cantidadDeFichadas = eventos.stream().filter(CodigosDeEventoHikvision::esFichadaValida).count();
        System.out.println("[OK]    Lectura de memoria: " + eventos.size() + " evento(s) en las últimas 48 h, "
                + cantidadDeFichadas + " cuentan como fichada.");
        int cantidadMostrada = 0;
        for (int indice = eventos.size() - 1; indice >= 0 && cantidadMostrada < 8; indice--, cantidadMostrada++) {
            EventoDeReloj evento = eventos.get(indice);
            String legajo = evento.identificoAUnEmpleado() ? evento.obtenerLegajoDelEmpleado() : "(sin legajo)";
            System.out.println("        legajo " + legajo + "  " + evento.obtenerFechaHoraDelEvento() + "  código "
                    + evento.obtenerCodigoDeTipoDeEvento()
                    + (CodigosDeEventoHikvision.esFichadaValida(evento) ? "  -> fichada" : "  -> no se carga"));
        }
        if (eventos.isEmpty()) {
            System.out.println("        (Fichá una vez en el reloj y volvé a correr la prueba para ver el evento.)");
        }
    }
}
