package com.fluxtech.agente.modelo;

import com.fluxtech.agente.json.AyudaMapasJson;
import com.fluxtech.agente.util.ConversorDeFechas;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Una fichada leída de un reloj, pendiente de subir a la nube.
 *
 * Recorrido del dato:
 *   El lector del reloj (ISAPI o SDK) o el receptor crean una FichadaLeida por cada fichada válida.
 *   La cola la guarda en disco hasta que el servicio de la nube confirma que la recibió.
 *   Para viajar a la nube y para guardarse se convierte en Map con convertirEnMapa().
 */
public final class FichadaLeida {

    /** Id del reloj en la nube (el que figura en el panel). */
    private final long idDelReloj;
    /** Número de empleado tal como está cargado en el reloj (debe coincidir con el legajo del panel). */
    private final String legajoDelEmpleado;
    /** Hora de pared del reloj, con precisión de segundos. */
    private final LocalDateTime fechaHoraDeLaFichada;
    /** Cómo se identificó el empleado (huella, tarjeta, rostro), según lo informa el reloj. */
    private final String modoDeVerificacion;

    public FichadaLeida(long idDelReloj, String legajoDelEmpleado, LocalDateTime fechaHoraDeLaFichada, String modoDeVerificacion) {
        this.idDelReloj = idDelReloj;
        this.legajoDelEmpleado = legajoDelEmpleado.trim();
        this.fechaHoraDeLaFichada = fechaHoraDeLaFichada.withNano(0);
        this.modoDeVerificacion = modoDeVerificacion == null ? "" : modoDeVerificacion;
    }

    /**
     * Identificador único de la fichada: mismo reloj + mismo legajo + mismo segundo = misma fichada.
     * Se usa para no encolar ni subir dos veces la misma fichada.
     */
    public String obtenerClaveUnica() {
        return idDelReloj + "|" + legajoDelEmpleado + "|" + ConversorDeFechas.formatearConSegundos(fechaHoraDeLaFichada);
    }

    /** Formato que espera la nube en POST /api/agente/fichadas (y el que se guarda en disco). */
    public Map<String, Object> convertirEnMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("relojId", idDelReloj);
        mapa.put("legajo", legajoDelEmpleado);
        mapa.put("fechaHora", ConversorDeFechas.formatearConSegundos(fechaHoraDeLaFichada));
        mapa.put("modo", modoDeVerificacion);
        return mapa;
    }

    /** Reconstruye una fichada guardada en disco. Devuelve null si el registro está incompleto. */
    public static FichadaLeida crearDesdeMapa(Map<String, Object> mapa) {
        LocalDateTime fechaHora = ConversorDeFechas.interpretarFechaDelReloj(AyudaMapasJson.obtenerTexto(mapa, "fechaHora"));
        String legajo = AyudaMapasJson.obtenerTexto(mapa, "legajo");
        if (fechaHora == null || legajo.isBlank()) {
            return null;
        }
        return new FichadaLeida(AyudaMapasJson.obtenerNumero(mapa, "relojId", -1), legajo, fechaHora,
                AyudaMapasJson.obtenerTexto(mapa, "modo"));
    }

    public long obtenerIdDelReloj() {
        return idDelReloj;
    }

    public String obtenerLegajoDelEmpleado() {
        return legajoDelEmpleado;
    }

    public LocalDateTime obtenerFechaHoraDeLaFichada() {
        return fechaHoraDeLaFichada;
    }
}
