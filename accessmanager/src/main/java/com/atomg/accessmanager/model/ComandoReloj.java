package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "comando_reloj")
public class ComandoReloj {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reloj_id", nullable = false)
    private Reloj reloj;

    @Column(nullable = false)
    private String comando;

    @Column(nullable = false)
    private Boolean ejecutado = false;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}
