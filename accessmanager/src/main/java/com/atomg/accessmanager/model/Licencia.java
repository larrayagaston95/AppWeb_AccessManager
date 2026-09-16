package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

/**
 * Representa una licencia o ausencia justificada de un empleado.
 * Cubre rangos de fechas (vacaciones, enfermedad, estudio, maternidad, etc.).
 */
@Data
@Entity
@Table(name = "licencias")
public class Licencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    /**
     * Redundante con empleado.empresaId, pero facilita las consultas de seguridad multi-tenant
     * sin necesidad de navegar la relacion empleado -> sector -> sucursal -> empresa.
     */
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    /**
     * Tipo de licencia: "Vacaciones", "Enfermedad", "Estudio", "Maternidad/Paternidad", "Otro".
     */
    @Column(name = "tipo_licencia", nullable = false)
    private String tipoLicencia;

    @Column(columnDefinition = "TEXT")
    private String observaciones;
}