package com.fluxtech.agente;

import com.fluxtech.agente.configuracion.ConfiguracionLocal;
import com.fluxtech.agente.diagnostico.DiagnosticoCompleto;
import com.fluxtech.agente.diagnostico.DiagnosticoDeReloj;
import com.fluxtech.agente.registro.RegistroDeEventos;
import com.fluxtech.agente.sincronizacion.CicloDeSincronizacion;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Punto de entrada del agente local FluxTech.
 *
 *   java -jar agente-fluxtech.jar                                  → corre el agente
 *   java -jar agente-fluxtech.jar --probar                         → diagnóstico completo
 *   java -jar agente-fluxtech.jar --probar-reloj IP USUARIO CLAVE  → prueba un reloj (ISAPI y SDK si está)
 *   java -jar agente-fluxtech.jar --probar-sdk IP USUARIO CLAVE    → prueba un reloj solo con el SDK
 *   java -jar agente-fluxtech.jar --version
 *
 * La configuración (agente.properties), el SDK (sdk-hikvision/) y los datos (datos/) se buscan junto
 * al .jar, así funciona igual cuando Windows lo arranca desde otra carpeta.
 */
public final class Main {

    private static final int PUERTO_DEL_SDK_POR_DEFECTO = 8000;

    /** Se mantiene referenciado para que el bloqueo dure mientras el agente corre. */
    private static FileLock bloqueoDeInstanciaUnica;

    private Main() {
        // Punto de entrada: no se instancia
    }

    public static void main(String[] argumentos) throws Exception {
        String modo = argumentos.length > 0 ? argumentos[0] : "";
        Path carpetaDelAgente = obtenerCarpetaDelJar();

        if (modo.equals("--version")) {
            System.out.println("Agente FluxTech " + CicloDeSincronizacion.VERSION_DEL_AGENTE);
            return;
        }
        if (modo.equals("--probar-reloj") || modo.equals("--probar-sdk")) {
            System.exit(probarRelojSinConfiguracion(modo, argumentos, carpetaDelAgente));
        }

        ConfiguracionLocal configuracion;
        try {
            configuracion = ConfiguracionLocal.cargarDesdeCarpeta(carpetaDelAgente);
        } catch (IOException errorDeConfiguracion) {
            System.err.println("Error en Main.java -> main: configuración inválida: " + errorDeConfiguracion.getMessage());
            System.exit(2);
            return;
        }

        if (modo.equals("--probar")) {
            System.exit(DiagnosticoCompleto.ejecutar(configuracion));
        }

        // Una sola copia del agente por PC: Windows lo relanza cada pocos minutos por si se cayó;
        // si ya está corriendo, esta copia termina sin hacer nada.
        if (!tomarBloqueoDeInstanciaUnica(configuracion.obtenerCarpetaDeDatos())) {
            System.out.println("El agente ya está corriendo en esta PC.");
            return;
        }
        RegistroDeEventos.iniciar(configuracion.obtenerCarpetaDeDatos(), configuracion.debeRegistrarDetalleDeEventos());
        new CicloDeSincronizacion(configuracion).ejecutarParaSiempre();
    }

    /** --probar-reloj / --probar-sdk IP USUARIO CLAVE: no necesita agente.properties ni la nube. */
    private static int probarRelojSinConfiguracion(String modo, String[] argumentos, Path carpetaDelAgente) throws InterruptedException {
        if (argumentos.length < 4) {
            System.out.println("Uso: java -jar agente-fluxtech.jar " + modo + " IP USUARIO CLAVE");
            return 2;
        }
        System.out.println("== Probando reloj Hikvision en " + argumentos[1] + " ==");
        String metodo = DiagnosticoDeReloj.probarTodosLosMetodos(argumentos[1], argumentos[2], argumentos[3],
                carpetaDelAgente.resolve("sdk-hikvision"), PUERTO_DEL_SDK_POR_DEFECTO, null, modo.equals("--probar-sdk"));
        return metodo != null ? 0 : 1;
    }

    private static boolean tomarBloqueoDeInstanciaUnica(Path carpetaDeDatos) throws IOException {
        Files.createDirectories(carpetaDeDatos);
        FileChannel canal = FileChannel.open(carpetaDeDatos.resolve("agente.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        bloqueoDeInstanciaUnica = canal.tryLock();
        return bloqueoDeInstanciaUnica != null;
    }

    /** Carpeta donde está el .jar (o la carpeta de clases al correr desde el código fuente). */
    static Path obtenerCarpetaDelJar() {
        try {
            Path ubicacion = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(ubicacion) ? ubicacion : ubicacion.getParent();
        } catch (URISyntaxException | SecurityException | NullPointerException excepcion) {
            return Paths.get("").toAbsolutePath();
        }
    }
}
