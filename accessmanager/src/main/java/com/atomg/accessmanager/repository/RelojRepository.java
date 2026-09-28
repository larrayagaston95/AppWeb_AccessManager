package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Reloj;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelojRepository extends JpaRepository<Reloj, Long> {
    Optional<Reloj> findByNumeroSerie(String numeroSerie);
    List<Reloj> findByEmpresaId(Long empresaId);
}
