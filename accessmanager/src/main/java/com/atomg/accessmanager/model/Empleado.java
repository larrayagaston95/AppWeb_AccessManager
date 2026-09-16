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

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(name = "legajo_reloj", nullable = false, unique = true)
    private String legajoReloj;

    @Column(name = "horas_jornada_base", nullable = false)
    private Integer horasJornadaBase;

    @Column(nullable = true)
    private String telefono;

    /**
     * Columna legacy de la tabla: almacena el nombre de la sucursal como texto.
     * Se rellena automáticamente en el servicio a partir del Sector elegido.
     */
    @Column(name = "sucursal", nullable = false)
    private String sucursal;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;
}