package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@Entity
@Table(name = "asistencia_diaria")
public class AsistenciaDiaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "legajo_reloj")
    private String legajoReloj;

    @Column(name = "idEmpresa")
    private Long idEmpresa;

    @Column(name = "horas_normales")
    private Double horasNormales = 0.0;

    @Column(name = "horas_extras")
    private Double horasExtras = 0.0;

    @Column(name = "minutos_tarde")
    private Integer minutosTarde = 0;

    @Column(name = "estado")
    private String estado;
}