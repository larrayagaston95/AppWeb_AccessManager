package com.fluxtech.agente.reloj.hikvision.sdk;

import java.util.HashMap;
import java.util.Map;

/**
 * Traduce los códigos de error de HCNetSDK (NET_DVR_GetLastError) a mensajes en español
 * para el registro y el diagnóstico. Valores tomados de la documentación de HCNetSDK.
 */
final class CodigosDeErrorDelSdk {

    static final int USUARIO_O_CONTRASENIA_INCORRECTOS = 1;   // NET_DVR_PASSWORD_ERROR
    static final int FALLO_LA_CONEXION = 7;                   // NET_DVR_NETWORK_FAIL_CONNECT
    static final int FUNCION_NO_SOPORTADA_POR_EL_EQUIPO = 23; // NET_DVR_NOSUPPORT
    static final int USUARIO_BLOQUEADO = 153;                 // NET_DVR_USER_LOCKED

    private static final Map<Integer, String> DESCRIPCIONES = new HashMap<>();

    static {
        DESCRIPCIONES.put(USUARIO_O_CONTRASENIA_INCORRECTOS, "usuario o contraseña del reloj incorrectos");
        DESCRIPCIONES.put(FALLO_LA_CONEXION, "no se pudo conectar al reloj (¿apagado, IP incorrecta o puerto 8000 cerrado?)");
        DESCRIPCIONES.put(8, "error al enviar datos al reloj");
        DESCRIPCIONES.put(10, "el reloj tardó demasiado en responder");
        DESCRIPCIONES.put(17, "parámetros inválidos para el SDK");
        DESCRIPCIONES.put(FUNCION_NO_SOPORTADA_POR_EL_EQUIPO, "el reloj no soporta esta función");
        DESCRIPCIONES.put(29, "el reloj no pudo completar la operación");
        DESCRIPCIONES.put(41, "el SDK se quedó sin memoria");
        DESCRIPCIONES.put(USUARIO_BLOQUEADO, "el usuario del reloj quedó bloqueado por intentos fallidos (esperar unos minutos)");
    }

    private CodigosDeErrorDelSdk() {
        // Clase de constantes: no se instancia
    }

    /** "descripción (código N)" o "error del SDK código N" si el código no está en la tabla. */
    static String describir(int codigoDeError) {
        String descripcion = DESCRIPCIONES.get(codigoDeError);
        return descripcion != null
                ? descripcion + " (código " + codigoDeError + ")"
                : "error del SDK de Hikvision, código " + codigoDeError;
    }

    static boolean esErrorDeCredenciales(int codigoDeError) {
        return codigoDeError == USUARIO_O_CONTRASENIA_INCORRECTOS || codigoDeError == USUARIO_BLOQUEADO;
    }
}
