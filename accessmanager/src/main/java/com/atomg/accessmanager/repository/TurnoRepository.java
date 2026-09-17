package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByEmpresaId(Long empresaId);
}