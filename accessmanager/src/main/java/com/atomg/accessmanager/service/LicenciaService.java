package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Licencia;
import com.atomg.accessmanager.model.ReporteAsistencia;
import com.atomg.accessmanager.repository.LicenciaRepository;
import com.atomg.accessmanager.repository.ReporteAsistenciaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class LicenciaService {

    @Autowired
    private LicenciaRepository licenciaRepository;

    @Autowired
    private ReporteAsistenciaRepository reporteAsistenciaRepository;

    public List<Licencia> obtenerPorEmpleadoYEmpresa(Long empleadoId, Long empresaId) {
        return licenciaRepository.findByEmpleadoIdAndEmpresaId(empleadoId, empresaId);
    }

    public Optional<Licencia> obtenerPorIdYEmpresa(Long id, Long empresaId) {
        return licenciaRepository.findById(id)
                .filter(l -> l.getEmpresaId().equals(empresaId));
    }

    /**
     * Guarda la licencia y genera/actualiza los reportes de asistencia 
     * en el rango de fechas asignando horas 0 y el estado "Licencia: Tipo".
     */
    @Transactional
    public Licencia guardarLicencia(Licencia licencia) {
        Licencia guardada = licenciaRepository.save(licencia);
        Empleado emp = guardada.getEmpleado();
        
        LocalDate actual = guardada.getFechaInicio();
        LocalDate fin = guardada.getFechaFin();
        
        // Bucle día a día
        while (!actual.isAfter(fin)) {
            final LocalDate fechaBucle = actual;
            
            ReporteAsistencia reporte = reporteAsistenciaRepository
                    .buscarPorEmpleadoYFecha(emp.getId(), fechaBucle)
                    .orElse(new ReporteAsistencia());
            
            if (reporte.getId() == null) {
                reporte.setEmpleado(emp);
                reporte.setFecha(fechaBucle);
            }
            
            reporte.setHorasTrabajadas(0.0);
            reporte.setHorasExtras(0.0);
            reporte.setObservaciones("Licencia: " + guardada.getTipoLicencia());
            
            reporteAsistenciaRepository.save(reporte);
            
            actual = actual.plusDays(1);
        }
        
        return guardada;
    }

    /**
     * Actualiza la licencia limpiando primero los reportes de asistencia generados por el 
     * rango viejo, y luego generando los del nuevo rango.
     */
    @Transactional
    public Licencia actualizarLicencia(Long id, LocalDate nuevaInicio, LocalDate nuevaFin, String nuevoTipo, String nuevasObs) {
        Licencia licencia = licenciaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Licencia no encontrada"));
                
        Empleado emp = licencia.getEmpleado();
        LocalDate viejaInicio = licencia.getFechaInicio();
        LocalDate viejaFin = licencia.getFechaFin();
        
        // 1. Limpiar los reportes generados en el rango VIEJO
        LocalDate actual = viejaInicio;
        while (!actual.isAfter(viejaFin)) {
            Optional<ReporteAsistencia> reporteOpt = reporteAsistenciaRepository
                    .buscarPorEmpleadoYFecha(emp.getId(), actual);
            
            if (reporteOpt.isPresent()) {
                ReporteAsistencia reporte = reporteOpt.get();
                if (reporte.getObservaciones() != null && reporte.getObservaciones().startsWith("Licencia:")) {
                    if (reporte.getEntrada() == null && reporte.getSalida() == null) {
                        reporteAsistenciaRepository.delete(reporte);
                    } else {
                        reporte.setObservaciones("");
                        reporteAsistenciaRepository.save(reporte);
                    }
                }
            }
            actual = actual.plusDays(1);
        }
        
        // 2. Aplicar las fechas nuevas a la entidad
        licencia.setFechaInicio(nuevaInicio);
        licencia.setFechaFin(nuevaFin);
        licencia.setTipoLicencia(nuevoTipo);
        licencia.setObservaciones(nuevasObs);
        
        // 3. Volver a llamar al proceso normal de inyección (que generará el NUEVO rango)
        return guardarLicencia(licencia);
    }

    /**
     * Elimina la licencia y, opcionalmente, limpia los reportes de asistencia 
     * asociados para no dejar basura.
     */
    @Transactional
    public void eliminarLicencia(Licencia licencia) {
        Empleado emp = licencia.getEmpleado();
        
        LocalDate actual = licencia.getFechaInicio();
        LocalDate fin = licencia.getFechaFin();
        
        // Iterar para limpiar los reportes (o borrarlos si están vacíos)
        while (!actual.isAfter(fin)) {
            Optional<ReporteAsistencia> reporteOpt = reporteAsistenciaRepository
                    .buscarPorEmpleadoYFecha(emp.getId(), actual);
            
            if (reporteOpt.isPresent()) {
                ReporteAsistencia reporte = reporteOpt.get();
                // Si la observación indica que fue generada por licencia, la borramos
                if (reporte.getObservaciones() != null && reporte.getObservaciones().startsWith("Licencia:")) {
                    // Si no tiene entrada ni salida, es un reporte fantasma de licencia, lo borramos
                    if (reporte.getEntrada() == null && reporte.getSalida() == null) {
                        reporteAsistenciaRepository.delete(reporte);
                    } else {
                        // Si por algún motivo tiene entradas (fichó igual), solo limpiamos la observación
                        reporte.setObservaciones("");
                        reporteAsistenciaRepository.save(reporte);
                    }
                }
            }
            actual = actual.plusDays(1);
        }
        
        licenciaRepository.delete(licencia);
    }
}
