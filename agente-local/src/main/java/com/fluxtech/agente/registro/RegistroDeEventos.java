package com.fluxtech.agente.registro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Registro de lo que hace el agente: escribe en la consola y en datos/agente.log.
 *
 * Recorrido del dato:
 *   Cualquier clase del agente llama a informar(), avisar() o registrarError().
 *   Esta clase arma la línea con fecha y nivel, la imprime en la consola y la agrega al archivo.
 *   Cuando el archivo supera 5 MB se renombra a agente.log.1 para que no crezca sin límite.
 *
 * Formato obligatorio de errores (regla de trazabilidad):
 *   "Error en [Nombre del Archivo] -> [Nombre de la Función]: [Descripción del fallo]"
 */
public final class RegistroDeEventos {

    private static final long TAMANIO_MAXIMO_DEL_ARCHIVO_EN_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter FORMATO_DE_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Archivo de registro; queda null hasta que se llama a iniciar() (por ejemplo, en el diagnóstico). */
    private static Path archivoDeRegistro;

    /** Si es true se registran también los mensajes de detalle (cada evento crudo de un reloj). */
    private static boolean registrarDetalle;

    private RegistroDeEventos() {
        // Clase de utilidades: no se instancia
    }

    /** Prepara la carpeta y el archivo de registro. Se llama una vez al arrancar el agente. */
    public static synchronized void iniciar(Path carpetaDeDatos, boolean incluirDetalle) {
        registrarDetalle = incluirDetalle;
        try {
            // Paso 1: crear la carpeta datos/ si todavía no existe
            Files.createDirectories(carpetaDeDatos);
            // Paso 2: recordar dónde escribir de ahora en adelante
            archivoDeRegistro = carpetaDeDatos.resolve("agente.log");
        } catch (IOException excepcion) {
            System.err.println("Error en RegistroDeEventos.java -> iniciar: no se pudo crear la carpeta de registro ("
                    + excepcion.getMessage() + ")");
        }
    }

    /** Mensaje informativo normal (fichadas leídas, subidas, arranque). */
    public static void informar(String mensaje) {
        escribirLinea("INFO ", mensaje);
    }

    /** Situación esperable que conviene revisar (reloj apagado, sin internet). */
    public static void avisar(String mensaje) {
        escribirLinea("AVISO", mensaje);
    }

    /** Error con el formato obligatorio: "Error en [archivo] -> [función]: [descripción]". */
    public static void registrarError(String nombreDelArchivo, String nombreDeLaFuncion, String descripcionDelFallo) {
        escribirLinea("ERROR", "Error en " + nombreDelArchivo + " -> " + nombreDeLaFuncion + ": " + descripcionDelFallo);
    }

    /** Detalle técnico: solo se registra si log.detalle=true en agente.properties. */
    public static void detallar(String mensaje) {
        if (registrarDetalle) {
            escribirLinea("DETAL", mensaje);
        }
    }

    /** Arma la línea final, la muestra en consola y la agrega al archivo (rotándolo si es muy grande). */
    private static synchronized void escribirLinea(String nivel, String mensaje) {
        String lineaCompleta = FORMATO_DE_FECHA.format(LocalDateTime.now()) + " " + nivel + " " + mensaje;
        System.out.println(lineaCompleta);

        // Sin archivo configurado (por ejemplo en el diagnóstico) solo se usa la consola
        if (archivoDeRegistro == null) {
            return;
        }
        try {
            // Rotación: si el archivo pasó el límite, se guarda como agente.log.1 y se empieza uno nuevo
            if (Files.exists(archivoDeRegistro) && Files.size(archivoDeRegistro) > TAMANIO_MAXIMO_DEL_ARCHIVO_EN_BYTES) {
                Files.move(archivoDeRegistro, archivoDeRegistro.resolveSibling("agente.log.1"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(archivoDeRegistro, (lineaCompleta + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException excepcion) {
            System.err.println("Error en RegistroDeEventos.java -> escribirLinea: no se pudo escribir el archivo de registro ("
                    + excepcion.getMessage() + ")");
        }
    }

    /** Convierte cualquier excepción en una descripción breve y entendible para el registro. */
    public static String describirFallo(Throwable excepcion) {
        // Fallas de red típicas: se traducen a un mensaje que entiende quien instala
        if (excepcion instanceof java.net.ConnectException
                || excepcion instanceof java.net.http.HttpConnectTimeoutException
                || excepcion instanceof java.net.http.HttpTimeoutException
                || excepcion instanceof java.net.UnknownHostException
                || excepcion instanceof java.net.NoRouteToHostException) {
            return "no responde (¿apagado, desconectado o dirección incorrecta?)";
        }
        String mensaje = excepcion.getMessage();
        if (mensaje == null || mensaje.isBlank()) {
            mensaje = excepcion.getClass().getSimpleName();
        }
        // Se recorta para que una respuesta enorme no llene el registro
        return mensaje.length() > 300 ? mensaje.substring(0, 300) + "..." : mensaje;
    }
}
