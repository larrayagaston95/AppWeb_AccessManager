package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "fichadas")
public class Fichada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idEmpresa", nullable = false)
    private Long idEmpresa;

    @Column(name = "legajo_reloj", nullable = false)
    private String legajoReloj;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "modo_verificacion")
    private String modoVerificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reloj_id", nullable = true)
    private Reloj reloj;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;
}