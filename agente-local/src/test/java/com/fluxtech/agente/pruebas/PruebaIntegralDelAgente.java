package com.fluxtech.agente.pruebas;

import com.fluxtech.agente.registro.RegistroDeEventos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Prueba de punta a punta del agente contra un reloj Hikvision simulado (ISAPI con Digest real)
 * y una nube simulada. Opcionalmente prueba el respaldo automático por SDK.
 *
 * Uso: java -cp clases:clases-prueba:jna.jar com.fluxtech.agente.pruebas.PruebaIntegralDelAgente [carpeta-con-libhcnetsdk.so]
 * Si se indica la carpeta del SDK simulado, también se prueba que, cuando el reloj no permite
 * leer eventos por ISAPI, el agente pase solo al SDK.
 */
public final class PruebaIntegralDelAgente {

    private PruebaIntegralDelAgente() {
        // Punto de entrada de la prueba: no se instancia
    }

    public static void main(String[] argumentos) throws Exception {
        ContextoDePrueba contexto = new ContextoDePrueba();
        contexto.reloj.iniciar(ContextoDePrueba.PUERTO_DEL_RELOJ);
        contexto.nube.iniciar(ContextoDePrueba.PUERTO_DE_LA_NUBE);
        RegistroDeEventos.iniciar(contexto.cargarConfiguracion().obtenerCarpetaDeDatos(), false);

        try {
            EscenariosDeSincronizacion.ejecutar(contexto);
            EscenariosDeSeguridadYReceptor.ejecutar(contexto);
            if (argumentos.length > 0) {
                probarRespaldoAutomaticoPorSdk(contexto, Paths.get(argumentos[0]));
            }
        } finally {
            contexto.reloj.detener();
            contexto.nube.detener();
        }

        System.out.println();
        System.out.println(contexto.obtenerCantidadDeFallas() == 0
                ? "TODAS LAS PRUEBAS OK"
                : contexto.obtenerCantidadDeFallas() + " PRUEBA(S) FALLARON");
        System.exit(contexto.obtenerCantidadDeFallas() == 0 ? 0 : 1);
    }

    /**
     * 8) El reloj responde por HTTP pero no permite buscar eventos por ISAPI (firmware viejo).
     *    En modo automático, con el SDK instalado, el agente tiene que leer por SDK sin intervención.
     */
    private static void probarRespaldoAutomaticoPorSdk(ContextoDePrueba contexto, Path carpetaDelSdkSimulado) throws Exception {
        Path carpetaDelSdk = contexto.carpetaDelAgente.resolve("sdk-hikvision");
        Files.createDirectories(carpetaDelSdk);
        Files.copy(carpetaDelSdkSimulado.resolve("libhcnetsdk.so"), carpetaDelSdk.resolve("libhcnetsdk.so"));
        contexto.reloj.busquedaDeEventosNoSoportada = true;
        // Estado limpio, como un reloj recién instalado: así se leen los últimos 31 días (incluye el 15/10/2026)
        Files.deleteIfExists(contexto.cargarConfiguracion().obtenerCarpetaDeDatos().resolve("estado.json"));

        contexto.ejecutarCicloConAgenteNuevo();
        // El SDK simulado tiene una fichada de 102 a las 08:05 del 15/10/2026 con legajo solo numérico
        contexto.verificar("8. ISAPI sin búsqueda de eventos → el agente pasa solo al SDK",
                contexto.nube.ultimoHeartbeat.contains("\"metodo\":\"SDK\""));
        contexto.verificar("8b. Las fichadas leídas por SDK llegan a la nube (incluido el legajo numérico de firmware viejo)",
                contexto.nube.fichadasGuardadas.contains("102|2026-10-15T08:05:00")
                        && contexto.nube.fichadasGuardadas.contains("101|2026-10-15T17:00:00"));
        contexto.verificar("8c. Por SDK tampoco se suben rechazos (101 a las 08:01)",
                !contexto.nube.fichadasGuardadas.contains("101|2026-10-15T08:01:00"));
    }
}
