// Mapa local de meses necesario para la traducción en los filtros
const mapaMeses = {
    "Enero": 1, "Febrero": 2, "Marzo": 3, "Abril": 4, "Mayo": 5, "Junio": 6,
    "Julio": 7, "Agosto": 8, "Septiembre": 9, "Octubre": 10, "Noviembre": 11, "Diciembre": 12
};

import { fetchSucursales, fetchSectoresPorSucursal, fetchEmpleadosPorSector } from '../api/apiService.js';

export async function inicializarCombosInteligentes() {
    console.log("🚀 Inicializando Combos Inteligentes con estructura real de BD...");

    const $sucursal = $('#selectSucursal');
    const $sector = $('#selectSeccion, #selectSector');
    const $empleado = $('#selectEmpleado');

    // 1. Inicializamos Select2
    $('#selectSucursal, #selectSeccion, #selectSector, #selectEmpleado').select2({
        placeholder: "Seleccione una opción...",
        allowClear: false,
        width: '100%'
    });

    // 2. CARGA INICIAL: Traemos las sucursales reales de la BD al arrancar
        try {
            const sucursales = await fetchSucursales(1); // Empresa ID 1 por defecto
            $sucursal.empty().append('<option value="">-- Seleccione Sucursal --</option>');

            if (sucursales && sucursales.length > 0) {
                sucursales.forEach(suc => {
                    // 👈 AQUÍ VA LA LÍNEA: enviamos suc.nombre como texto y como valor
                    $sucursal.append(new Option(suc.nombre, suc.nombre));
                });
            }
            $sucursal.trigger('change.select2');
        } catch (err) {
            console.error("❌ Error al cargar sucursales:", err);
        }


    // 3. EVENTO CASCADA 1: Al cambiar SUCURSAL -> Carga SECTORES de esa sucursal
    $(document).off('change.cascada', '#selectSucursal').on('change.cascada', '#selectSucursal', async function() {
        const sucursalId = $(this).val();

        // Limpiamos los combos dependientes hacia abajo
        $sector.empty().append('<option value="">-- Seleccione Sección --</option>').val('').trigger('change.select2');
        $empleado.empty().append('<option value="">-- Seleccione Empleado --</option>').val('').trigger('change.select2');

        if (!sucursalId) return;

        try {
            const sectores = await fetchSectoresPorSucursal(sucursalId);
            let opciones = '<option value="">-- Seleccione Sección --</option>';

            if (sectores && sectores.length > 0) {
                sectores.forEach(sec => {
                    opciones += `<option value="${sec.id}">${sec.nombre}</option>`;
                });
            }

            $sector.html(opciones).trigger('change.select2');
        } catch (error) {
            console.error("❌ Error al traer sectores:", error);
        }
    });

    // 4. EVENTO CASCADA 2: Al cambiar SECCIÓN -> Carga EMPLEADOS de ese sector
    $(document).off('change.cascada', '#selectSeccion, #selectSector').on('change.cascada', '#selectSeccion, #selectSector', async function() {
        const sectorId = $(this).val();

        // Limpiamos únicamente el combo de empleados
        $empleado.empty().append('<option value="">-- Seleccione Empleado --</option>').val('').trigger('change.select2');

        if (!sectorId) return;

        try {
            const empleados = await fetchEmpleadosPorSector(sectorId);
            let opciones = '<option value="">-- Seleccione Empleado --</option>';

            if (empleados && empleados.length > 0) {
                empleados.forEach(emp => {
                    // Formato: Gaston Larraya (101)
                    opciones += `<option value="${emp.legajo_reloj || emp.legajo}">${emp.nombre} ${emp.apellido} (${emp.legajo_reloj || emp.legajo})</option>`;
                });
            }

            $empleado.html(opciones).trigger('change.select2');
        } catch (error) {
            console.error("❌ Error al traer empleados:", error);
        }
    });
}