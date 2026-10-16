package com.fluxtech.agente.reloj.hikvision.sdk;

/*
 * ============================================================================
 *  DEPENDENCIAS DE TERCEROS (justificación técnica)
 * ============================================================================
 *  1) HCNetSDK (Hikvision) — HCNetSDK.dll en Windows / libhcnetsdk.so en Linux
 *     Qué problema resuelve: algunos relojes Hikvision (sobre todo modelos y firmwares
 *     anteriores a 2019, como el DS-K1A802MF) no permiten leer su memoria de fichadas por
 *     ISAPI (HTTP). La única forma oficial de leerlos es el protocolo propio de Hikvision
 *     (puerto 8000), que es el que usa iVMS-4200. Ese protocolo solo está disponible a
 *     través de esta biblioteca nativa, que Hikvision distribuye gratis en su sitio.
 *     No se incluye en el paquete del agente: se descarga de Hikvision y se copia en la
 *     carpeta sdk-hikvision/ (ver README).
 *
 *  2) JNA 5.14.0 (Java Native Access) — licencia Apache 2.0
 *     Qué problema resuelve: Java no puede llamar directamente a funciones de una DLL.
 *     JNA permite declarar esas funciones como una interfaz Java y mapear las estructuras
 *     de C a clases Java, sin escribir ni compilar código C/JNI propio (que habría que
 *     mantener para cada sistema operativo). Es la biblioteca que usan los ejemplos
 *     oficiales de Hikvision para Java, por lo que las estructuras coinciden con su
 *     documentación. Alternativa descartada: la API nativa de Java 22 (FFM), porque
 *     obligaría a instalar Java 22+ en la PC del cliente.
 * ============================================================================
 */

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/**
 * Funciones de HCNetSDK que usa el agente, declaradas para JNA.
 * Los nombres de los métodos DEBEN coincidir exactamente con los de la DLL de Hikvision
 * (por eso conservan el prefijo NET_DVR_ y no se traducen). Cada uno documenta en español qué hace.
 *
 * Requisito: Java de 64 bits con el SDK de 64 bits (no se pueden mezclar 32 y 64 bits).
 */
public interface BibliotecaHcNetSdk extends Library {

    /** Comando de configuración remota: "buscar eventos de control de acceso" (NET_DVR_GET_ACS_EVENT). */
    int COMANDO_BUSCAR_EVENTOS_DE_ACCESO = 2514;

    /** Respuestas de NET_DVR_GetNextRemoteConfig. */
    int ESTADO_SIGUIENTE_REGISTRO_OBTENIDO = 1000;
    int ESTADO_SIGUIENTE_ESPERAR = 1001;
    int ESTADO_SIGUIENTE_BUSQUEDA_TERMINADA = 1002;
    int ESTADO_SIGUIENTE_FALLO = 1003;

    /** Tipos de NET_DVR_SetSDKInitCfg: carpeta de componentes y bibliotecas de cifrado. */
    int CONFIGURACION_INICIAL_CARPETA_DEL_SDK = 2;
    int CONFIGURACION_INICIAL_RUTA_LIBCRYPTO = 3;
    int CONFIGURACION_INICIAL_RUTA_LIBSSL = 4;

    /** Inicializa el SDK. Se llama una sola vez por proceso. */
    boolean NET_DVR_Init();

    /** Libera los recursos del SDK al cerrar el agente. */
    boolean NET_DVR_Cleanup();

    /** Tiempo máximo de conexión (milisegundos) y cantidad de intentos. */
    boolean NET_DVR_SetConnectTime(int milisegundosDeEspera, int cantidadDeIntentos);

    /** Reconexión automática si se corta la sesión. */
    boolean NET_DVR_SetReconnect(int milisegundosEntreIntentos, boolean habilitada);

    /** Código del último error del SDK (ver CodigosDeErrorDelSdk). */
    int NET_DVR_GetLastError();

    /** Configura rutas antes de NET_DVR_Init (solo existe en SDK recientes). */
    boolean NET_DVR_SetSDKInitCfg(int tipoDeConfiguracion, Pointer valor);

    /**
     * Inicia sesión en el reloj (método recomendado por Hikvision).
     * Devuelve el identificador de sesión (0 o mayor) o -1 si falló.
     * La información del equipo se escribe en el bloque de memoria recibido.
     */
    int NET_DVR_Login_V40(EstructurasDeSesionSdk.DatosDeInicioDeSesion datosDeInicio, Pointer informacionDelEquipoV40);

    /** Inicio de sesión antiguo: se usa si el SDK o el equipo no aceptan el método V40. */
    int NET_DVR_Login_V30(String direccionIp, short puerto, String usuario, String contrasenia, Pointer informacionDelEquipoV30);

    /** Cierra la sesión abierta con el reloj. */
    boolean NET_DVR_Logout(int identificadorDeSesion);

    /** Empieza una búsqueda remota (por ejemplo de eventos). Devuelve el identificador de búsqueda o -1. */
    int NET_DVR_StartRemoteConfig(int identificadorDeSesion, int comando, Pointer condicion, int tamanioDeLaCondicion,
                                  Pointer funcionDeRetorno, Pointer datosDeUsuario);

    /** Trae el siguiente resultado de la búsqueda. Devuelve uno de los ESTADO_SIGUIENTE_... */
    int NET_DVR_GetNextRemoteConfig(int identificadorDeBusqueda, Pointer resultado, int tamanioDelResultado);

    /** Termina la búsqueda y libera sus recursos. */
    boolean NET_DVR_StopRemoteConfig(int identificadorDeBusqueda);
}
