package com.atomg.accessmanager.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "sectores")
@Data
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;
}