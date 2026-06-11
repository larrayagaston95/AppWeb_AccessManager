package com.atomg.accessmanager.dto;

import java.time.LocalDateTime;

public class FichajeDTO {
    private String idEmpleadoRelog;
    private LocalDateTime fechaHora;

    public String getIdEmpleadoRelog() { return idEmpleadoRelog; }
    public void setIdEmpleadoRelog(String idEmpleadoRelog) { this.idEmpleadoRelog = idEmpleadoRelog; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
}