package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.FichajeCrudo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FichajeCrudoRepository extends JpaRepository<FichajeCrudo, Long> {
    // No requiere código acá adentro, hereda todo el CRUD automático de Spring Data
}