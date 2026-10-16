package com.fluxtech.agente.reloj.hikvision.sdk;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.nio.charset.StandardCharsets;

/**
 * Estructuras de C del SDK de Hikvision usadas para iniciar sesión y medir el tiempo.
 *
 * Importante para quien mantenga este archivo: JNA ubica cada campo en memoria según el ORDEN
 * declarado en @FieldOrder, no según su nombre. Por eso los nombres están en español, pero el
 * orden y el tamaño de cada campo copian exactamente la estructura de HCNetSDK.h
 * (el nombre original de Hikvision figura en el comentario de cada campo).
 */
public final class EstructurasDeSesionSdk {

    /** Tamaños fijos definidos en HCNetSDK.h. */
    static final int LARGO_DIRECCION_DEL_EQUIPO = 129;   // NET_DVR_DEV_ADDRESS_MAX_LEN
    static final int LARGO_USUARIO_DE_INICIO = 64;       // NET_DVR_LOGIN_USERNAME_MAX_LEN
    static final int LARGO_CONTRASENIA_DE_INICIO = 64;   // NET_DVR_LOGIN_PASSWD_MAX_LEN
    static final int LARGO_NUMERO_DE_SERIE = 48;         // SERIALNO_LEN

    private EstructurasDeSesionSdk() {
        // Agrupador de estructuras: no se instancia
    }

    /** NET_DVR_TIME: fecha y hora como seis enteros. */
    @Structure.FieldOrder({"anio", "mes", "dia", "hora", "minuto", "segundo"})
    public static class FechaHoraSdk extends Structure {
        public int anio;      // dwYear
        public int mes;       // dwMonth
        public int dia;       // dwDay
        public int hora;      // dwHour
        public int minuto;    // dwMinute
        public int segundo;   // dwSecond
    }

    /** NET_DVR_USER_LOGIN_INFO: datos para NET_DVR_Login_V40. */
    @Structure.FieldOrder({"direccionDelEquipo", "usaTransporte", "puerto", "usuario", "contrasenia",
            "funcionDeResultado", "datosDeUsuario", "inicioAsincronico", "tipoDeProxy", "usaHoraUtc",
            "modoDeInicio", "usaHttps", "identificadorDeProxy", "modoDeVerificacion", "reservado"})
    public static class DatosDeInicioDeSesion extends Structure {
        public byte[] direccionDelEquipo = new byte[LARGO_DIRECCION_DEL_EQUIPO]; // sDeviceAddress
        public byte usaTransporte;                                               // byUseTransport
        public short puerto;                                                     // wPort
        public byte[] usuario = new byte[LARGO_USUARIO_DE_INICIO];               // sUserName
        public byte[] contrasenia = new byte[LARGO_CONTRASENIA_DE_INICIO];       // sPassword
        public Pointer funcionDeResultado;                                       // cbLoginResult (no se usa: inicio sincrónico)
        public Pointer datosDeUsuario;                                           // pUser
        public boolean inicioAsincronico;                                        // bUseAsynLogin
        public byte tipoDeProxy;                                                 // byProxyType
        public byte usaHoraUtc;                                                  // byUseUTCTime
        public byte modoDeInicio;                                                // byLoginMode (0 = protocolo privado, puerto 8000)
        public byte usaHttps;                                                    // byHttps
        public int identificadorDeProxy;                                         // iProxyID
        public byte modoDeVerificacion;                                          // byVerifyMode
        public byte[] reservado = new byte[119];                                 // byRes3

        /** Completa dirección, puerto y credenciales copiando los textos en los arreglos de bytes de C. */
        public void completar(String direccionIp, int numeroDePuerto, String nombreDeUsuario, String claveDeAcceso) {
            copiarTextoEnArreglo(direccionIp, direccionDelEquipo);
            copiarTextoEnArreglo(nombreDeUsuario, usuario);
            copiarTextoEnArreglo(claveDeAcceso, contrasenia);
            puerto = (short) numeroDePuerto;
            inicioAsincronico = false;
        }
    }

    /** NET_DVR_LOCAL_SDK_PATH: carpeta donde están los componentes del SDK (HCNetSDKCom). */
    @Structure.FieldOrder({"rutaDeLaCarpeta", "reservado"})
    public static class RutaLocalDelSdk extends Structure {
        public byte[] rutaDeLaCarpeta = new byte[256]; // sPath
        public byte[] reservado = new byte[128];       // byRes
    }

    /** Copia un texto en un arreglo de bytes terminado en cero, como espera el código C. */
    static void copiarTextoEnArreglo(String texto, byte[] destino) {
        byte[] bytesDelTexto = texto.getBytes(StandardCharsets.UTF_8);
        int largoACopiar = Math.min(bytesDelTexto.length, destino.length - 1); // se deja lugar para el cero final
        System.arraycopy(bytesDelTexto, 0, destino, 0, largoACopiar);
    }

    /** Lee un texto de C (bytes hasta el primer cero) y lo devuelve sin espacios sobrantes. */
    static String leerTextoDeArreglo(byte[] origen) {
        int largo = 0;
        while (largo < origen.length && origen[largo] != 0) {
            largo++;
        }
        return new String(origen, 0, largo, StandardCharsets.UTF_8).trim();
    }
}
