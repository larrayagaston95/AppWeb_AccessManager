package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "turnos")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id")
    private Long empresaId;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "hora_entrada")
    private LocalTime horaEntrada;

    @Column(name = "hora_salida")
    private LocalTime horaSalida;

    @Column(name = "tolerancia_minutos")
    private Integer toleranciaMinutos = 15;
}