package com.fluxtech.agente.reloj.hikvision.sdk;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * Estructuras que devuelve el SDK por cada evento encontrado en la memoria del reloj.
 *
 * Recorrido del dato:
 *   NET_DVR_GetNextRemoteConfig llena un EventoDeAccesoSdk (NET_DVR_ACS_EVENT_CFG).
 *   ConversorDeEventosSdk toma de ahí la hora, el tipo de evento y, dentro del detalle
 *   (NET_DVR_ACS_EVENT_DETAIL), el número de empleado y el modo de verificación.
 *
 * El orden y el tamaño de los campos copian HCNetSDK.h (nombre original en cada comentario).
 * Los campos que el agente no usa se declaran igual porque ocupan lugar en la memoria.
 */
public final class EstructurasDelEventoDeAccesoSdk {

    private EstructurasDelEventoDeAccesoSdk() {
        // Agrupador de estructuras: no se instancia
    }

    /** NET_VCA_POINT: coordenada relativa (dos números decimales). */
    @Structure.FieldOrder({"posicionHorizontal", "posicionVertical"})
    public static class PuntoDeCoordenadas extends Structure {
        public float posicionHorizontal; // fX
        public float posicionVertical;   // fY
    }

    /** NET_DVR_IPADDR: dirección IP del equipo remoto (IPv4 e IPv6 como texto). */
    @Structure.FieldOrder({"direccionIpv4", "direccionIpv6"})
    public static class DireccionIp extends Structure {
        public byte[] direccionIpv4 = new byte[16];  // sIpV4
        public byte[] direccionIpv6 = new byte[128]; // byIPv6
    }

    /** NET_DVR_ACS_EVENT_DETAIL: detalle del evento (quién fichó y cómo). */
    @Structure.FieldOrder({"tamanioDeLaEstructura", "numeroDeTarjeta", "tipoDeTarjeta", "numeroDeListaBlanca",
            "canalDeReporte", "tipoDeLector", "numeroDeLector", "numeroDePuerta", "numeroDeVerificacionMultiple",
            "numeroDeEntradaDeAlarma", "numeroDeSalidaDeAlarma", "numeroDeSensor", "numeroDeCanalRs485",
            "numeroDeGrupoMultitarjeta", "canalDeAcceso", "numeroDeDispositivo", "numeroDeControlador",
            "numeroDeEmpleadoNumerico", "controladorLocal", "puertoDeRed", "tipoDeZona", "direccionMac",
            "tipoDeLectura", "atributoDelEvento", "numeroDeSerieDelEvento", "controladorDeCanal", "lamparaDelCanal",
            "adaptadorInfrarrojo", "emisorInfrarrojo", "cantidadDeCanalesDeGrabacion", "canalesDeGrabacion",
            "tipoDePersona", "modoDeVerificacionActual", "estadoDeAsistencia", "valorDeEstado",
            "numeroDeEmpleadoComoTexto", "reservado1", "usaBarbijo", "unidadDeTemperatura", "temperaturaAnormal",
            "temperaturaActual", "coordenadasDeTemperatura", "reservado"})
    public static class DetalleDelEventoDeAcceso extends Structure {
        public int tamanioDeLaEstructura;                       // dwSize
        public byte[] numeroDeTarjeta = new byte[32];           // byCardNo
        public byte tipoDeTarjeta;                              // byCardType
        public byte numeroDeListaBlanca;                        // byWhiteListNo
        public byte canalDeReporte;                             // byReportChannel
        public byte tipoDeLector;                               // byCardReaderKind
        public int numeroDeLector;                              // dwCardReaderNo
        public int numeroDePuerta;                              // dwDoorNo
        public int numeroDeVerificacionMultiple;                // dwVerifyNo
        public int numeroDeEntradaDeAlarma;                     // dwAlarmInNo
        public int numeroDeSalidaDeAlarma;                      // dwAlarmOutNo
        public int numeroDeSensor;                              // dwCaseSensorNo
        public int numeroDeCanalRs485;                          // dwRs485No
        public int numeroDeGrupoMultitarjeta;                   // dwMultiCardGroupNo
        public short canalDeAcceso;                             // wAccessChannel
        public byte numeroDeDispositivo;                        // byDeviceNo
        public byte numeroDeControlador;                        // byDistractControlNo
        public int numeroDeEmpleadoNumerico;                    // dwEmployeeNo (firmwares viejos)
        public short controladorLocal;                          // wLocalControllerID
        public byte puertoDeRed;                                // byInternetAccess
        public byte tipoDeZona;                                 // byType
        public byte[] direccionMac = new byte[6];               // byMACAddr
        public byte tipoDeLectura;                              // bySwipeCardType
        public byte atributoDelEvento;                          // byEventAttribute
        public int numeroDeSerieDelEvento;                      // dwSerialNo
        public byte controladorDeCanal;                         // byChannelControllerID
        public byte lamparaDelCanal;                            // byChannelControllerLampID
        public byte adaptadorInfrarrojo;                        // byChannelControllerIRAdaptorID
        public byte emisorInfrarrojo;                           // byChannelControllerIREmitterID
        public int cantidadDeCanalesDeGrabacion;                // dwRecordChannelNum
        public Pointer canalesDeGrabacion;                      // pRecordChannelData
        public byte tipoDePersona;                              // byUserType
        public byte modoDeVerificacionActual;                   // byCurrentVerifyMode
        public byte estadoDeAsistencia;                         // byAttendanceStatus
        public byte valorDeEstado;                              // byStatusValue
        public byte[] numeroDeEmpleadoComoTexto = new byte[32]; // byEmployeeNo (prioritario según Hikvision)
        public byte reservado1;                                 // byRes1
        public byte usaBarbijo;                                 // byMask
        public byte unidadDeTemperatura;                        // byThermometryUnit
        public byte temperaturaAnormal;                         // byIsAbnomalTemperature
        public float temperaturaActual;                         // fCurrTemperature
        public PuntoDeCoordenadas coordenadasDeTemperatura = new PuntoDeCoordenadas(); // struRegionCoordinates
        public byte[] reservado = new byte[48];                 // byRes
    }

    /** NET_DVR_ACS_EVENT_CFG: un evento completo de la memoria del reloj. */
    @Structure.FieldOrder({"tamanioDeLaEstructura", "tipoPrincipal", "subTipo", "fechaHora", "usuarioDeRed",
            "direccionRemota", "detalle", "largoDeLaFoto", "datosDeLaFoto", "tipoDeEventoInductivo", "tipoDeHora",
            "reservado1", "largoDelCodigoQr", "largoDeLaImagenVisible", "largoDeLaImagenTermica", "datosDelCodigoQr",
            "datosDeLaImagenVisible", "datosDeLaImagenTermica", "reservado"})
    public static class EventoDeAccesoSdk extends Structure {
        public int tamanioDeLaEstructura;                       // dwSize
        public int tipoPrincipal;                               // dwMajor
        public int subTipo;                                     // dwMinor (código que se compara con los rechazos)
        public EstructurasDeSesionSdk.FechaHoraSdk fechaHora = new EstructurasDeSesionSdk.FechaHoraSdk(); // struTime
        public byte[] usuarioDeRed = new byte[16];              // sNetUser (MAX_NAMELEN)
        public DireccionIp direccionRemota = new DireccionIp(); // struRemoteHostAddr
        public DetalleDelEventoDeAcceso detalle = new DetalleDelEventoDeAcceso(); // struAcsEventInfo
        public int largoDeLaFoto;                               // dwPicDataLen
        public Pointer datosDeLaFoto;                           // pPicData
        public short tipoDeEventoInductivo;                     // wInductiveEventType
        public byte tipoDeHora;                                 // byTimeType
        public byte reservado1;                                 // byRes1
        public int largoDelCodigoQr;                            // dwQRCodeInfoLen
        public int largoDeLaImagenVisible;                      // dwVisibleLightDataLen
        public int largoDeLaImagenTermica;                      // dwThermalDataLen
        public Pointer datosDelCodigoQr;                        // pQRCodeInfo
        public Pointer datosDeLaImagenVisible;                  // pVisibleLightData
        public Pointer datosDeLaImagenTermica;                  // pThermalData
        public byte[] reservado = new byte[36];                 // byRes

        /** Deja la estructura lista para que el SDK escriba el próximo evento. */
        public void prepararParaRecibir() {
            clear();
            tamanioDeLaEstructura = size();
            write();
        }
    }
}
