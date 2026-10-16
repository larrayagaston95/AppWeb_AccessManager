package com.fluxtech.agente.reloj.hikvision.sdk;

import com.sun.jna.Structure;

import java.time.LocalDateTime;

/**
 * NET_DVR_ACS_EVENT_COND: qué eventos buscar en la memoria del reloj.
 *
 * Recorrido del dato:
 *   LectorHikvisionSdk crea esta estructura con el rango de fechas a leer,
 *   la pasa a NET_DVR_StartRemoteConfig y el reloj devuelve los eventos de ese rango.
 *
 * El orden y el tamaño de los campos copian HCNetSDK.h (nombre original en cada comentario).
 * El primer campo (tamanioDeLaEstructura) le indica al SDK qué versión de la estructura se usa.
 */
@Structure.FieldOrder({"tamanioDeLaEstructura", "tipoPrincipal", "subTipo", "fechaHoraDeInicio", "fechaHoraDeFin",
        "numeroDeTarjeta", "nombreDelTitular", "incluirFotos", "tipoDeHora", "reservado2", "numeroDeSerieInicial",
        "numeroDeSerieFinal", "canalIot", "tipoDeEventoInductivo", "tipoDeBusqueda", "atributoDelEvento",
        "identificadorDePuntoDeControl", "numeroDeEmpleado", "reservado"})
public class EstructuraCondicionDeBusquedaSdk extends Structure {

    public int tamanioDeLaEstructura;                                        // dwSize
    public int tipoPrincipal;                                                // dwMajor (5 = control de acceso, 0 = todos)
    public int subTipo;                                                      // dwMinor (0 = todos)
    public EstructurasDeSesionSdk.FechaHoraSdk fechaHoraDeInicio = new EstructurasDeSesionSdk.FechaHoraSdk(); // struStartTime
    public EstructurasDeSesionSdk.FechaHoraSdk fechaHoraDeFin = new EstructurasDeSesionSdk.FechaHoraSdk();    // struEndTime
    public byte[] numeroDeTarjeta = new byte[32];                            // byCardNo (ACS_CARD_NO_LEN)
    public byte[] nombreDelTitular = new byte[32];                           // byName (NAME_LEN)
    public byte incluirFotos;                                                // byPicEnable (0 = sin fotos)
    public byte tipoDeHora;                                                  // byTimeType (0 = hora local del equipo)
    public byte[] reservado2 = new byte[2];                                  // byRes2
    public int numeroDeSerieInicial;                                         // dwBeginSerialNo (0 = todos)
    public int numeroDeSerieFinal;                                           // dwEndSerialNo (0 = todos)
    public int canalIot;                                                     // dwIOTChannelNo
    public short tipoDeEventoInductivo;                                      // wInductiveEventType
    public byte tipoDeBusqueda;                                              // bySearchType
    public byte atributoDelEvento;                                           // byEventAttribute
    public byte[] identificadorDePuntoDeControl = new byte[64];              // szMonitorID (NET_SDK_MONITOR_ID_LEN)
    public byte[] numeroDeEmpleado = new byte[32];                           // byEmployeeNo (NET_SDK_EMPLOYEE_NO_LEN)
    public byte[] reservado = new byte[140];                                 // byRes

    /** Prepara la búsqueda: todos los eventos de control de acceso entre las dos fechas, sin fotos. */
    public void completarRangoDeBusqueda(int tipoPrincipalDeEvento, LocalDateTime desde, LocalDateTime hasta) {
        tamanioDeLaEstructura = size();
        tipoPrincipal = tipoPrincipalDeEvento;
        subTipo = 0;
        copiarFecha(desde, fechaHoraDeInicio);
        copiarFecha(hasta, fechaHoraDeFin);
        incluirFotos = 0;  // las fotos no hacen falta para fichar y harían la lectura mucho más lenta
        tipoDeHora = 0;    // hora local del reloj (la misma regla de "hora de pared" que en ISAPI)
        write();           // vuelca los valores de Java a la memoria que lee el SDK
    }

    private static void copiarFecha(LocalDateTime fechaHora, EstructurasDeSesionSdk.FechaHoraSdk destino) {
        destino.anio = fechaHora.getYear();
        destino.mes = fechaHora.getMonthValue();
        destino.dia = fechaHora.getDayOfMonth();
        destino.hora = fechaHora.getHour();
        destino.minuto = fechaHora.getMinute();
        destino.segundo = fechaHora.getSecond();
    }
}
