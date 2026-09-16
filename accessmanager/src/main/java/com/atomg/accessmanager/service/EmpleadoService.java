package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Sector;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.SectorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Service
public class EmpleadoService {

    @Autowired
    private EmpleadoRepository empleadoRepository;
    
    @Autowired
    private SectorRepository sectorRepository;

    public List<Empleado> obtenerTodos(Long empresaId) {
        return empleadoRepository.findAll().stream()
                .filter(e -> e.getSector() != null 
                          && e.getSector().getSucursal() != null 
                          && e.getSector().getSucursal().getEmpresa() != null
                          && e.getSector().getSucursal().getEmpresa().getId().equals(empresaId))
                .toList();
    }

    public Empleado obtenerPorId(Long id, Long empresaId) {
        Empleado emp = empleadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + id));
        
        // Validación de pertenencia al Tenant
        if (emp.getSector() == null || emp.getSector().getSucursal() == null || 
            emp.getSector().getSucursal().getEmpresa() == null || 
            !emp.getSector().getSucursal().getEmpresa().getId().equals(empresaId)) {
            throw new RuntimeException("Acceso denegado: El empleado no pertenece a tu empresa.");
        }
        return emp;
    }
    
    public Empleado crearEmpleado(Map<String, Object> data, Long empresaId) {
        Empleado emp = new Empleado();
        emp.setEmpresaId(empresaId);
        mapearDatos(emp, data, empresaId);
        return empleadoRepository.save(emp);
    }
    
    public Empleado actualizarEmpleado(Long id, Map<String, Object> data, Long empresaId) {
        Empleado emp = obtenerPorId(id, empresaId);
        emp.setEmpresaId(empresaId);
        mapearDatos(emp, data, empresaId);
        return empleadoRepository.save(emp);
    }
    
    public void eliminarEmpleado(Long id, Long empresaId) {
        // Validamos pertenencia antes de borrar
        obtenerPorId(id, empresaId);
        empleadoRepository.deleteById(id);
    }

    /**
     * Retorna el próximo número de legajo disponible para la empresa (multi-tenant).
     * Calcula MAX(legajo_reloj numérico) entre los empleados de la empresa y devuelve MAX + 1.
     * Si la empresa no tiene empleados aún, devuelve 101.
     */
    public int proximoLegajo(Long empresaId) {
        List<Empleado> empleados = obtenerTodos(empresaId);
        OptionalInt maxLegajo = empleados.stream()
                .mapToInt(e -> {
                    try {
                        return Integer.parseInt(e.getLegajoReloj().trim());
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .max();
        return maxLegajo.isPresent() ? maxLegajo.getAsInt() + 1 : 101;
    }
    
    private void mapearDatos(Empleado emp, Map<String, Object> data, Long empresaId) {
        if (data.containsKey("nombre")) emp.setNombre((String) data.get("nombre"));
        if (data.containsKey("apellido")) emp.setApellido((String) data.get("apellido"));
        if (data.containsKey("legajoReloj")) emp.setLegajoReloj((String) data.get("legajoReloj"));
        
        if (data.containsKey("horasJornadaBase")) {
            Object hObj = data.get("horasJornadaBase");
            if (hObj instanceof Number) {
                emp.setHorasJornadaBase(((Number) hObj).intValue());
            } else if (hObj instanceof String) {
                emp.setHorasJornadaBase(Integer.parseInt((String) hObj));
            }
        }
        
        if (data.containsKey("telefono")) emp.setTelefono((String) data.get("telefono"));
        
        if (data.containsKey("sectorId")) {
            Object sObj = data.get("sectorId");
            Long sectorId = null;
            if (sObj instanceof Number) {
                sectorId = ((Number) sObj).longValue();
            } else if (sObj instanceof String) {
                sectorId = Long.parseLong((String) sObj);
            }
            if (sectorId != null) {
                Sector sector = sectorRepository.findById(sectorId)
                    .orElseThrow(() -> new RuntimeException("Sector no encontrado"));
                
                // Validación de tenant
                if (sector.getSucursal() == null || sector.getSucursal().getEmpresa() == null ||
                    !sector.getSucursal().getEmpresa().getId().equals(empresaId)) {
                    throw new RuntimeException("Acceso denegado: El sector indicado no pertenece a tu empresa.");
                }
                
                emp.setSector(sector);
                // ── FIX SQL 1364 ─────────────────────────────────────────────────────
                // La tabla 'empleados' tiene la columna 'sucursal' (VARCHAR NOT NULL).
                // Se rellena automáticamente con el nombre de la sucursal del sector.
                emp.setSucursal(sector.getSucursal().getNombre());
            }
        }
    }
}
