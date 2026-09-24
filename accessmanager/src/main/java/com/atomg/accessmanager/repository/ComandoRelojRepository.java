package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.ComandoReloj;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComandoRelojRepository extends JpaRepository<ComandoReloj, Long> {
    List<ComandoReloj> findByRelojIdAndEjecutadoFalseOrderByFechaCreacionAsc(Long relojId);
}
