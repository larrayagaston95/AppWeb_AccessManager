package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Fichada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FichadaRepository extends JpaRepository<Fichada, Long> {
    // Inicialmente con los métodos heredados de JpaRepository nos alcanza
}