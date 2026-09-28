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
     * Retorna el próximo número de legajo disponible para la empresa
     * (multi-tenant).
     * Calcula MAX(legajo_reloj numérico) entre los empleados de la empresa y
     * devuelve MAX + 1.
     * Si la empresa no tiene empleados aún, devuelve 101.
     */
    /**
     * Calcula el proximo numero de legajo para un nuevo empleado.
     * Recorrido del dato:
     * 1. La Vista solicita el proximo legajo al Controlador al abrir el modal de
     * creacion.
     * 2. El Controlador invoca este Servicio.
     * 3. El Servicio consulta el Repositorio (Modelo) para obtener la lista de
     * empleados de la empresa.
     * 4. Se calcula el maximo valor numerico de los legajos existentes.
     * 5. Se retorna el valor maximo mas uno, o 101 como valor por defecto si no
     * existen registros.
     * 
     * @param identificadorDeLaEmpresa ID unico de la empresa.
     * @return El proximo numero de legajo disponible.
     */
    public int proximoLegajo(Long identificadorDeLaEmpresa) {
        try {
            // Se obtienen todos los empleados asociados a la empresa solicitada desde la
            // base de datos
            List<Empleado> listaDeEmpleadosDeLaEmpresa = obtenerTodos(identificadorDeLaEmpresa);

            // Se itera sobre la lista para convertir los legajos (que son String) a Integer
            // y encontrar el valor maximo
            java.util.OptionalInt maximoLegajoEncontrado = listaDeEmpleadosDeLaEmpresa.stream()
                    .mapToInt(empleadoEnIteracion -> {
                        try {
                            // Se intenta convertir el legajo limpiando espacios en blanco a su valor
                            // numerico entero
                            return Integer.parseInt(empleadoEnIteracion.getLegajoReloj().trim());
                        } catch (NumberFormatException excepcionDeFormatoNumerico) {
                            // En caso de que el legajo no sea numerico (ej. letras), se asume un valor de 0
                            // para no romper el calculo
                            return 0;
                        }
                    })
                    .max();

            // Se evalua si se encontro algun legajo numerico. Si es asi, se retorna el
            // valor maximo + 1.
            // Si la empresa no tiene empleados registrados (es decir, esta vacia), se
            // revierte el cambio y se devuelve 101.
            return maximoLegajoEncontrado.isPresent() ? maximoLegajoEncontrado.getAsInt() + 1 : 101;
        } catch (Exception excepcionGeneral) {
            // Se captura cualquier fallo general durante la consulta a la base de datos o
            // el procesamiento de la logica
            System.err.println(
                    "Error en EmpleadoService.java -> proximoLegajo: Ocurrio un error al calcular el proximo legajo - "
                            + excepcionGeneral.getMessage());
            // Se retorna el valor por defecto en caso de falla critica para permitir la
            // continuidad operativa
            return 101;
        }
    }

    private void mapearDatos(Empleado emp, Map<String, Object> data, Long empresaId) {
        if (data.containsKey("nombre"))
            emp.setNombre((String) data.get("nombre"));
        if (data.containsKey("apellido"))
            emp.setApellido((String) data.get("apellido"));
        if (data.containsKey("legajoReloj"))
            emp.setLegajoReloj((String) data.get("legajoReloj"));

        if (data.containsKey("horasJornadaBase")) {
            Object hObj = data.get("horasJornadaBase");
            if (hObj instanceof Number) {
                emp.setHorasJornadaBase(((Number) hObj).intValue());
            } else if (hObj instanceof String) {
                emp.setHorasJornadaBase(Integer.parseInt((String) hObj));
            }
        }

        if (data.containsKey("telefono"))
            emp.setTelefono((String) data.get("telefono"));

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
