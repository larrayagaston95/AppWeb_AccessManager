package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reportes_asistencia")
public class ReporteAsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(nullable = false)
    private LocalDate fecha;

    private LocalDateTime entrada;
    private LocalDateTime salida;

    @Column(name = "horas_trabajadas")
    private Double horasTrabajadas = 0.0;

    @Column(name = "horas_extras")
    private Double horasExtras = 0.0;

    private String observaciones;
}