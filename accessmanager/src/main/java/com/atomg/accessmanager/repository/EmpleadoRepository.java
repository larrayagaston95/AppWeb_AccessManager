package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {
    // Método mágico de JPA para buscar un empleado usando el código del reloj
    Optional<Empleado> findByLegajoReloj(String legajoReloj);
}