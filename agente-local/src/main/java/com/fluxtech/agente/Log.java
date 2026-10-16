package com.fluxtech.agente;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Log a consola y a datos/agente.log (rota a agente.log.1 al superar 5 MB). */
public final class Log {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static Path archivo;
    private static boolean detalle;

    private Log() {}

    public static synchronized void iniciar(Path directorio, boolean conDetalle) {
        detalle = conDetalle;
        try {
            Files.createDirectories(directorio);
            archivo = directorio.resolve("agente.log");
        } catch (IOException e) {
            System.err.println("No se pudo crear el directorio de logs: " + e.getMessage());
        }
    }

    public static void info(String msg) { escribir("INFO ", msg); }

    public static void aviso(String msg) { escribir("AVISO", msg); }

    public static void error(String msg) { escribir("ERROR", msg); }

    public static void error(String msg, Throwable t) {
        escribir("ERROR", msg + ": " + t.getClass().getSimpleName() + " - " + t.getMessage());
    }

    /** Solo si log.detalle=true (para diagnosticar qué manda cada reloj). */
    public static void detalle(String msg) {
        if (detalle) escribir("DET  ", msg);
    }

    private static synchronized void escribir(String nivel, String msg) {
        String linea = FMT.format(LocalDateTime.now()) + " " + nivel + " " + msg;
        System.out.println(linea);
        if (archivo == null) return;
        try {
            if (Files.exists(archivo) && Files.size(archivo) > MAX_BYTES) {
                Files.move(archivo, archivo.resolveSibling("agente.log.1"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(archivo, (linea + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("No se pudo escribir el log: " + e.getMessage());
        }
    }
}
