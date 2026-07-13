package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {
    // Trae los empleados de un sector específico
    List<Empleado> findBySectorId(Long sectorId);

    // Este te va a servir para tu lógica existente de fichadas y reportes
    Optional<Empleado> findByLegajoReloj(String legajoReloj);
}