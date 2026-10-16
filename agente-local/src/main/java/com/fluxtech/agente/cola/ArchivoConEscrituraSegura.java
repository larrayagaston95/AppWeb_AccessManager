package com.fluxtech.agente.cola;

import com.fluxtech.agente.registro.RegistroDeEventos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Lectura y escritura de archivos de datos sin riesgo de dejarlos a medio escribir.
 *
 * Regla: se escribe primero un archivo temporal y después se reemplaza el original en un solo paso.
 * Si se corta la luz en medio, queda el archivo anterior completo, nunca uno dañado.
 */
final class ArchivoConEscrituraSegura {

    private static final String NOMBRE_DEL_ARCHIVO = "ArchivoConEscrituraSegura.java";

    private ArchivoConEscrituraSegura() {
        // Clase de utilidades: no se instancia
    }

    /** Reemplaza el contenido del archivo de forma atómica. Registra el error si no puede. */
    static void escribir(Path archivoDestino, String contenido) {
        Path archivoTemporal = archivoDestino.resolveSibling(archivoDestino.getFileName() + ".tmp");
        try {
            // Paso 1: escribir todo en el temporal
            Files.writeString(archivoTemporal, contenido, StandardCharsets.UTF_8);
            try {
                // Paso 2: reemplazar el original en una sola operación
                Files.move(archivoTemporal, archivoDestino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException sistemaSinMovimientoAtomico) {
                Files.move(archivoTemporal, archivoDestino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "escribir",
                    "no se pudo guardar " + archivoDestino.getFileName() + ": " + excepcion.getMessage());
        }
    }

    /** Devuelve el contenido del archivo, o null si no existe o no se pudo leer. */
    static String leerSiExiste(Path archivo) {
        if (!Files.exists(archivo)) {
            return null;
        }
        try {
            return Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "leerSiExiste",
                    "no se pudo leer " + archivo.getFileName() + ": " + excepcion.getMessage());
            return null;
        }
    }

    /** Conserva una copia de un archivo dañado para poder revisarlo después. */
    static void guardarCopiaDeArchivoDaniado(Path archivo) {
        Path copia = archivo.resolveSibling(archivo.getFileName() + ".daniado-" + System.currentTimeMillis());
        try {
            Files.copy(archivo, copia, StandardCopyOption.REPLACE_EXISTING);
            RegistroDeEventos.avisar(archivo.getFileName() + " estaba dañado; se guardó una copia en " + copia.getFileName());
        } catch (IOException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "guardarCopiaDeArchivoDaniado", excepcion.getMessage());
        }
    }
}
