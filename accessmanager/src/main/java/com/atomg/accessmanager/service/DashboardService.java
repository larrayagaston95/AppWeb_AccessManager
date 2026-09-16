package com.atomg.accessmanager.service;

import com.atomg.accessmanager.dto.DashboardResumenDTO;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Logica de negocio para el Dashboard de asistencia en tiempo real.
 */
@Service
public class DashboardService {

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private FichadaRepository fichadaRepository;

    /**
     * Calcula el resumen de asistencia del dia de hoy.
     *
     * @param empresaId  ID de la empresa extraida del JWT (garantiza multi-tenant).
     * @param sucursalId ID de la sucursal a filtrar. Si es null o 0, se considera toda la empresa.
     */
    public DashboardResumenDTO calcularResumenHoy(Long empresaId, Long sucursalId) {

        boolean filtrarPorSucursal = sucursalId != null && sucursalId > 0;

        long totalEmpleados;
        List<String> legajos;

        if (filtrarPorSucursal) {
            totalEmpleados = empleadoRepository
                    .countByEmpresaIdAndSectorSucursalIdsucursal(empresaId, sucursalId);
            legajos = empleadoRepository
                    .findLegajosByEmpresaIdAndSucursalId(empresaId, sucursalId);
        } else {
            totalEmpleados = empleadoRepository.countByEmpresaId(empresaId);
            legajos = empleadoRepository.findLegajosByEmpresaId(empresaId);
        }

        if (legajos.isEmpty()) {
            return new DashboardResumenDTO(0L, 0L, 0L, 0L);
        }

        LocalDate hoy = LocalDate.now();
        List<String> legajosPresentes = fichadaRepository
                .findLegajosConFichadaHoy(hoy, legajos);

        long presentes = legajosPresentes.size();
        long ausentes  = totalEmpleados - presentes;

        // llegadasTarde: devuelve 0 hasta que se implemente la logica de turnos fijos
        long llegadasTarde = 0L;

        return new DashboardResumenDTO(totalEmpleados, presentes, ausentes, llegadasTarde);
    }
}