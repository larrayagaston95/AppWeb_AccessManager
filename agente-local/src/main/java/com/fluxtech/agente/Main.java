package com.fluxtech.agente;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Punto de entrada.
 *   java -jar agente-fluxtech.jar                       → corre el agente
 *   java -jar agente-fluxtech.jar --probar              → diagnóstico completo
 *   java -jar agente-fluxtech.jar --probar-reloj IP USUARIO CLAVE
 *   java -jar agente-fluxtech.jar --version
 * La configuración (agente.properties) y los datos (carpeta datos/) se buscan junto al .jar,
 * así funciona igual si Windows lo arranca desde otra carpeta.
 */
public final class Main {

    /** Se mantiene referenciado para que el bloqueo dure toda la ejecución. */
    private static FileLock bloqueo;

    private Main() {}

    public static void main(String[] args) throws Exception {
        String modo = args.length > 0 ? args[0] : "";

        if (modo.equals("--version")) {
            System.out.println("Agente FluxTech " + Agente.VERSION);
            return;
        }
        if (modo.equals("--probar-reloj")) {
            if (args.length < 4) {
                System.out.println("Uso: java -jar agente-fluxtech.jar --probar-reloj IP USUARIO CLAVE");
                System.exit(2);
            }
            System.exit(Diagnostico.probarReloj(args[1], args[2], args[3]));
        }

        Path carpeta = carpetaDelJar();
        Config config;
        try {
            config = Config.cargar(carpeta);
        } catch (IOException e) {
            System.err.println("ERROR de configuración: " + e.getMessage());
            System.exit(2);
            return;
        }

        if (modo.equals("--probar")) {
            System.exit(Diagnostico.probarTodo(config));
        }

        // Una sola instancia por PC: Windows lo relanza cada pocos minutos por si se cayó,
        // y si ya está corriendo esta copia termina sin hacer nada.
        Files.createDirectories(config.datos());
        FileChannel canal = FileChannel.open(config.datos().resolve("agente.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        bloqueo = canal.tryLock();
        if (bloqueo == null) {
            System.out.println("El agente ya está corriendo en esta PC.");
            return;
        }

        Log.iniciar(config.datos(), config.logDetalle);
        new Agente(config).ejecutar();
    }

    static Path carpetaDelJar() {
        try {
            Path p = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(p) ? p : p.getParent();
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            return Paths.get("").toAbsolutePath();
        }
    }
}
