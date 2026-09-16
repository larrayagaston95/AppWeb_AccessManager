import { fetchSucursales, fetchSectoresPorSucursal, fetchEmpleadosPorSector } from '../api/apiService.js';

// Mapa local de meses para traducir texto → número
const mapaMeses = {
    "Enero": 1, "Febrero": 2, "Marzo": 3, "Abril": 4, "Mayo": 5, "Junio": 6,
    "Julio": 7, "Agosto": 8, "Septiembre": 9, "Octubre": 10, "Noviembre": 11, "Diciembre": 12
};

/**
 * Inicializa Select2 y carga la cascada Sucursal → Sección → Empleado.
 * Debe ejecutarse UNA SOLA VEZ después de que el DOM del header ya fue renderizado.
 */
export async function inicializarCombosInteligentes() {
    console.log('🚀 Inicializando combos inteligentes (BD real)...');

    const $sucursal = $('#selectSucursal');
    const $sector   = $('#selectSeccion');   // único ID de sección en el header
    const $empleado = $('#selectEmpleado');

    // Registramos eventos de cascada con namespace propio para poder des-registrarlos
    // limpiamente si en algún futuro se re-inicia (evitamos listeners duplicados).
    $(document).off('change.cascada');

    // ── 1. INICIALIZACIÓN SELECT2 ────────────────────────────────────────────
    $sucursal.add($sector).add($empleado).select2({
        placeholder: 'Seleccione una opción...',
        allowClear: false,
        width: '100%'
    });

    // ── 2. CARGA INICIAL DE SUCURSALES ───────────────────────────────────────
    try {
        const sucursales = await fetchSucursales(1);
        $sucursal.empty().append('<option value="">-- Seleccione Sucursal --</option>');

        if (sucursales && sucursales.length > 0) {
            sucursales.forEach(suc => {
                // El backend serializa idsucursal como "id" en SucursalController
                const idReal = suc.id ?? suc.idsucursal;
                $sucursal.append(new Option(suc.nombre, idReal));
            });
        }

        $sucursal.trigger('change.select2');
        console.log(`✅ Sucursales cargadas: ${sucursales.length}`);
    } catch (err) {
        console.error('❌ Error al cargar sucursales:', err);
    }

    // ── 3. CASCADA: SUCURSAL → SECCIÓN ──────────────────────────────────────
    $(document).on('change.cascada', '#selectSucursal', async function () {
        const sucursalId = $(this).val();

        // Limpiamos los combos dependientes
        $sector.empty().append('<option value="">-- Seleccione Sección --</option>').val('').trigger('change.select2');
        $empleado.empty().append('<option value="">-- Seleccione Empleado --</option>').val('').trigger('change.select2');

        if (!sucursalId) return;

        console.log(`🔍 Cargando sectores para sucursalId=${sucursalId}`);
        const sectores = await fetchSectoresPorSucursal(sucursalId);
        let opciones = '<option value="">-- Seleccione Sección --</option>';

        if (sectores && sectores.length > 0) {
            sectores.forEach(sec => {
                opciones += `<option value="${sec.id}">${sec.nombre}</option>`;
            });
        }

        $sector.html(opciones).trigger('change.select2');
        console.log(`✅ Sectores cargados: ${sectores.length}`);
    });

    // ── 4. CASCADA: SECCIÓN → EMPLEADO ───────────────────────────────────────
    $(document).on('change.cascada', '#selectSeccion', async function () {
        const sectorId = $(this).val();

        $empleado.empty().append('<option value="">-- Seleccione Empleado --</option>').val('').trigger('change.select2');

        if (!sectorId) return;

        console.log(`🔍 Cargando empleados para sectorId=${sectorId}`);
        const empleados = await fetchEmpleadosPorSector(sectorId);
        let opciones = '<option value="">-- Seleccione Empleado --</option>';

        if (empleados && empleados.length > 0) {
            empleados.forEach(emp => {
                // El backend devuelve { id, legajo, nombre, apellido }
                const legajo = emp.legajo ?? emp.legajoReloj ?? emp.legajo_reloj ?? '';
                opciones += `<option value="${legajo}">${emp.nombre} ${emp.apellido} (${legajo})</option>`;
            });
        }

        $empleado.html(opciones).trigger('change.select2');
        console.log(`✅ Empleados cargados: ${empleados.length}`);
    });
}