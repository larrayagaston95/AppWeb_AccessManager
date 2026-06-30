package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "empleados")
@Data
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legajo_reloj", nullable = false, unique = true)
    private String legajoReloj;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    private String telefono;

    // 🚀 NUEVO: Agregamos el campo sucursal que mapea con la base de datos
    @Column(nullable = false)
    private String sucursal;

    @Column(name = "horas_jornada_base", nullable = false)
    private Integer horasJornadaBase;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;
}