package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fichajes_crudos")
public class FichajeCrudo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "id_empleado_reloj", nullable = false)
    private String idEmpleadoRelog;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    // Constructor vacío obligatorio para JPA
    public FichajeCrudo() {}

    // Constructor para inicializar datos rápido
    public FichajeCrudo(Long empresaId, String idEmpleadoRelog, LocalDateTime fechaHora) {
        this.empresaId = empresaId;
        this.idEmpleadoRelog = idEmpleadoRelog;
        this.fechaHora = fechaHora;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long empresaId) { this.empresaId = empresaId; }
    public String getIdEmpleadoRelog() { return idEmpleadoRelog; }
    public void setIdEmpleadoRelog(String idEmpleadoRelog) { this.idEmpleadoRelog = idEmpleadoRelog; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
}