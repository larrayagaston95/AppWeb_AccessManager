package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.ReporteAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReporteAsistenciaRepository extends JpaRepository<ReporteAsistencia, Long> {

    // Busca todo el historial calculado de un empleado entre dos fechas (ideal para el reporte mensual)
    List<ReporteAsistencia> findByEmpleadoLegajoRelojAndFechaBetweenOrderByFechaAsc(
            String legajoReloj,
            LocalDate fechaInicio,
            LocalDate fechaFin
    );
}