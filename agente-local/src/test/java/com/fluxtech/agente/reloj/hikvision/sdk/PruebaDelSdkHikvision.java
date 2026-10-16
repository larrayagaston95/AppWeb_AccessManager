package com.fluxtech.agente.reloj.hikvision.sdk;

import com.fluxtech.agente.modelo.EventoDeReloj;
import com.fluxtech.agente.reloj.CredencialesDelRelojIncorrectasException;
import com.fluxtech.agente.reloj.MetodoDeLecturaNoSoportadoException;
import com.fluxtech.agente.reloj.hikvision.CodigosDeEventoHikvision;
import com.sun.jna.Library;
import com.sun.jna.Native;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Prueba del lector por SDK contra la biblioteca nativa simulada (src/test/c/hcnetsdk_simulado.c).
 *
 * Qué valida:
 *   - Que el tamaño de cada estructura en Java (JNA) sea idéntico al que calcula el compilador de C.
 *     Si no coinciden, el SDK real de Hikvision rechazaría la búsqueda.
 *   - El flujo completo: inicio de sesión, búsqueda, espera, resultados, fin y cierre de sesión.
 *   - Las reglas: legajo de texto o numérico, rechazos excluidos, credenciales incorrectas.
 *
 * Uso: java ... PruebaDelSdkHikvision <carpeta-con-libhcnetsdk.so> [sin-soporte]
 *      ("sin-soporte" se usa con la variable SIMULADOR_SDK_SIN_SOPORTE=1)
 */
public final class PruebaDelSdkHikvision {

    /** Funciones extra que expone solo el simulador, para verificar tamaños y recursos liberados. */
    public interface FuncionesDelSimulador extends Library {
        int SIMULADOR_TamanioCondicion();
        int SIMULADOR_TamanioEvento();
        int SIMULADOR_TamanioInicioDeSesion();
        int SIMULADOR_SesionesAbiertas();
        int SIMULADOR_BusquedasAbiertas();
    }

    private static int cantidadDeFallas = 0;

    private PruebaDelSdkHikvision() {
        // Punto de entrada de la prueba: no se instancia
    }

    public static void main(String[] argumentos) throws Exception {
        Path carpetaDelSdk = Paths.get(argumentos[0]).toAbsolutePath();
        FuncionesDelSimulador simulador = Native.load(carpetaDelSdk.resolve("libhcnetsdk.so").toString(), FuncionesDelSimulador.class);
        LocalDateTime inicioDelDia = LocalDateTime.of(2026, 10, 15, 0, 0);

        if (argumentos.length > 1 && argumentos[1].equals("sin-soporte")) {
            probarEquipoSinSoporte(carpetaDelSdk, inicioDelDia);
            terminar();
        }

        // 1) Tamaños de estructuras: Java (JNA) contra C (gcc)
        verificar("S1. NET_DVR_ACS_EVENT_COND mide igual en Java y en C (" + simulador.SIMULADOR_TamanioCondicion() + " bytes)",
                new EstructuraCondicionDeBusquedaSdk().size() == simulador.SIMULADOR_TamanioCondicion());
        verificar("S1b. NET_DVR_ACS_EVENT_CFG mide igual en Java y en C (" + simulador.SIMULADOR_TamanioEvento() + " bytes)",
                new EstructurasDelEventoDeAccesoSdk.EventoDeAccesoSdk().size() == simulador.SIMULADOR_TamanioEvento());
        verificar("S1c. NET_DVR_USER_LOGIN_INFO mide igual en Java y en C (" + simulador.SIMULADOR_TamanioInicioDeSesion() + " bytes)",
                new EstructurasDeSesionSdk.DatosDeInicioDeSesion().size() == simulador.SIMULADOR_TamanioInicioDeSesion());

        // 2) Flujo completo
        LectorHikvisionSdk lector = new LectorHikvisionSdk("127.0.0.1", 8000, "admin", "Clave123", carpetaDelSdk);
        verificar("S2. Inicio de sesión y número de serie leído de la memoria del SDK",
                lector.obtenerInformacionDelEquipo().obtenerNumeroDeSerie().equals("DS-K1A802MF20180709AAWR123456789"));
        List<EventoDeReloj> eventos = lector.leerEventosEntre(inicioDelDia, inicioDelDia.plusDays(1));
        List<String> fichadas = eventos.stream().filter(CodigosDeEventoHikvision::esFichadaValida)
                .map(evento -> evento.obtenerLegajoDelEmpleado() + "@" + evento.obtenerFechaHoraDelEvento().toLocalTime())
                .collect(Collectors.toList());
        verificar("S2b. Trae los 6 eventos del día (incluida la espera del SDK)", eventos.size() == 6);
        verificar("S2c. Fichadas válidas: " + fichadas, fichadas.equals(List.of("101@08:00", "102@08:05", "101@17:00", "102@17:10")));
        verificar("S2d. Legajo numérico de firmware viejo leído como \"102\"",
                eventos.stream().anyMatch(evento -> evento.obtenerLegajoDelEmpleado().equals("102") && evento.obtenerModoDeVerificacion().equals("tarjeta")));

        // 3) Rango de fechas respetado
        List<EventoDeReloj> deLaManiana = lector.leerEventosEntre(inicioDelDia.withHour(8), inicioDelDia.withHour(8).withMinute(3));
        verificar("S3. Respeta el rango de fechas (08:00 a 08:03 → 3 eventos)", deLaManiana.size() == 3);

        // 4) Recursos liberados
        lector.cerrarConexion();
        verificar("S4. Cierra la sesión y las búsquedas", simulador.SIMULADOR_SesionesAbiertas() == 0 && simulador.SIMULADOR_BusquedasAbiertas() == 0);

        // 5) Contraseña incorrecta → error definitivo
        LectorHikvisionSdk lectorConClaveMala = new LectorHikvisionSdk("127.0.0.1", 8000, "admin", "mala", carpetaDelSdk);
        boolean reconocioCredenciales = false;
        try {
            lectorConClaveMala.obtenerInformacionDelEquipo();
        } catch (CredencialesDelRelojIncorrectasException esperada) {
            reconocioCredenciales = true;
        }
        verificar("S5. Contraseña incorrecta se informa como credenciales incorrectas", reconocioCredenciales);
        terminar();
    }

    /** 6) El equipo no soporta la búsqueda (código 23) → MetodoDeLecturaNoSoportadoException. */
    private static void probarEquipoSinSoporte(Path carpetaDelSdk, LocalDateTime inicioDelDia) throws Exception {
        LectorHikvisionSdk lector = new LectorHikvisionSdk("127.0.0.1", 8000, "admin", "Clave123", carpetaDelSdk);
        boolean reconocioFaltaDeSoporte = false;
        try {
            lector.leerEventosEntre(inicioDelDia, inicioDelDia.plusDays(1));
        } catch (MetodoDeLecturaNoSoportadoException esperada) {
            reconocioFaltaDeSoporte = true;
        } finally {
            lector.cerrarConexion();
        }
        verificar("S6. Equipo sin búsqueda de eventos → se informa como método no soportado", reconocioFaltaDeSoporte);
    }

    private static void verificar(String descripcion, boolean resultadoCorrecto) {
        System.out.println((resultadoCorrecto ? "  [OK]    " : "  [FALLA] ") + descripcion);
        if (!resultadoCorrecto) {
            cantidadDeFallas++;
        }
    }

    private static void terminar() {
        System.out.println(cantidadDeFallas == 0 ? "PRUEBAS DEL SDK OK" : cantidadDeFallas + " PRUEBA(S) DEL SDK FALLARON");
        System.exit(cantidadDeFallas == 0 ? 0 : 1);
    }
}
