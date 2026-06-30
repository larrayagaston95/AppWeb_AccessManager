package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.ReporteAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReporteAsistenciaRepository extends JpaRepository<ReporteAsistencia, Long> {

    // 1. Tu consulta individual existente (Sigue funcionando igual por legajo)
    List<ReporteAsistencia> findByEmpleadoLegajoRelojAndFechaBetweenOrderByFechaAsc(
            String legajoReloj,
            LocalDate fechaInicio,
            LocalDate fechaFin
    );

    // 🚀 2. CORREGIDA: Consulta masiva usando las nuevas relaciones estructurales
    // Hibernate va a navegar automáticamente de Empleado -> Empresa (id) y de Empleado -> Sector (id)
    List<ReporteAsistencia> findByEmpleadoEmpresaIdAndEmpleadoSucursalAndEmpleadoSectorIdAndFechaBetweenOrderByEmpleadoLegajoRelojAscFechaAsc(
            Long empresaId,
            String sucursal,
            Long sectorId,
            LocalDate fechaInicio,
            LocalDate fechaFin
    );
}