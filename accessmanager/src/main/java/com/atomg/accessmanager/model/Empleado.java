package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "empleados")
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legajo_reloj", unique = true, nullable = false)
    private String legajoReloj;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(name = "horas_jornada_base", nullable = false)
    private Integer horasJornadaBase = 8;
}