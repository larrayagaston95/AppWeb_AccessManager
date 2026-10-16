package com.atomg.accessmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agente local instalado en la PC de un cliente. Lee los relojes de su red y sube las
 * fichadas a la nube autenticándose con su clave (header X-Agent-Key).
 *
 * De la clave solo se guarda un prefijo (para buscarla) y su hash SHA-256: la clave
 * completa se muestra una única vez al crearla.
 */
@Data
@Entity
@Table(name = "agentes")
public class Agente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Empresa empresa;

    /** Si es null, el agente atiende todos los relojes de la empresa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Sucursal sucursal;

    @JsonIgnore
    @Column(name = "clave_prefijo", nullable = false, unique = true, length = 16)
    private String clavePrefijo;

    @JsonIgnore
    @Column(name = "clave_hash", nullable = false, length = 64)
    private String claveHash;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultima_conexion")
    private LocalDateTime ultimaConexion;

    @Column(name = "version_agente", length = 32)
    private String versionAgente;

    /** Resumen legible del último reporte del agente (relojes OK / con error, pendientes). */
    @Column(name = "ultimo_estado", length = 2000)
    private String ultimoEstado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
