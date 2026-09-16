package com.atomg.accessmanager.dto;

/**
 * DTO de respuesta para el resumen agregado de asistencia de un sector.
 * Un registro por empleado con sus totales del período solicitado.
 */
public class ResumenSectorDTO {

    private String legajo;
    private String nombre;
    private String apellido;
    private long   diasTrabajados;
    private double totalHoras;
    private double totalExtras;

    // Constructor JPQL (Spring Data lo invoca con new ResumenSectorDTO(...))
    public ResumenSectorDTO(String legajo, String nombre, String apellido,
                            long diasTrabajados, double totalHoras, double totalExtras) {
        this.legajo         = legajo;
        this.nombre         = nombre;
        this.apellido       = apellido;
        this.diasTrabajados = diasTrabajados;
        this.totalHoras     = Math.round(totalHoras  * 100.0) / 100.0;
        this.totalExtras    = Math.round(totalExtras * 100.0) / 100.0;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getLegajo()         { return legajo;         }
    public String getNombre()         { return nombre;         }
    public String getApellido()       { return apellido;       }
    public long   getDiasTrabajados() { return diasTrabajados; }
    public double getTotalHoras()     { return totalHoras;     }
    public double getTotalExtras()    { return totalExtras;    }
}
