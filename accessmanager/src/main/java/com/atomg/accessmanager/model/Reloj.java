package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@Entity
@Table(name = "relojes")
public class Reloj {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_serie", nullable = false, unique = true)
    private String numeroSerie;

    @Column(name = "descripcion")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Sucursal sucursal;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "marca")
    private String marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Sector sector;

    @Column(name = "ultima_conexion")
    private java.time.LocalDateTime ultimaConexion;
}
