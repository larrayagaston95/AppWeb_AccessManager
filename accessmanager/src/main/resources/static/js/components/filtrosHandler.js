// Mapa local de meses necesario para la traducción en los filtros
const mapaMeses = {
    "Enero": 1, "Febrero": 2, "Marzo": 3, "Abril": 4, "Mayo": 5, "Junio": 6,
    "Julio": 7, "Agosto": 8, "Septiembre": 9, "Octubre": 10, "Noviembre": 11, "Diciembre": 12
};

import { fetchSucursales, fetchSectoresPorSucursal, fetchEmpleadosPorSector } from '../api/apiService.js';

export async function inicializarCombosInteligentes() {
    // 1. Inicializamos Select2 en los elementos de la cabecera
    $('#selectSucursal, #selectSeccion, #selectSector, #selectEmpleado').select2({
        placeholder: "Seleccione una opción...",
        allowClear: false,
        width: '100%'
    });

    // 2. CARGA INICIAL: Traemos las sucursales reales de la BD al arrancar
    try {
        const sucursales = await fetchSucursales(1); // Empresa ID 1 por defecto
        const selectSucursal = document.getElementById('selectSucursal');

        if (selectSucursal && sucursales.length > 0) {
            let opciones = '<option value="">-- Seleccione Sucursal --</option>';
            sucursales.forEach(suc => {
                opciones += `<option value="${suc.nombre}">${suc.nombre}</option>`;
            });
            selectSucursal.innerHTML = opciones;
            $(selectSucursal).trigger('change.select2'); // Notificamos a Select2
        }
    } catch (error) {
        console.error("No se pudieron cargar las sucursales dinámicas:", error);
    }

    // 3. EVENTO CASCADA 1: Al cambiar SUCURSAL, buscamos sus SECCIONES en la BD
    $('#selectSucursal').on('change', async function() {
        const sucursalSeleccionada = this.value;
        const selectSeccion = document.getElementById('selectSeccion') || document.getElementById('selectSector');

        if (!sucursalSeleccionada) return;
        console.log(`📡 Buscando sectores en BD para: ${sucursalSeleccionada}`);

        try {
            const sectores = await fetchSectoresPorSucursal(sucursalSeleccionada);
            let opcionesSectores = '<option value="1">-- Todas --</option>';

            sectores.forEach(sec => {
                opcionesSectores += `<option value="${sec.id}">${sec.nombre}</option>`;
            });

            if (selectSeccion) {
                selectSeccion.innerHTML = opcionesSectores;
                $(selectSeccion).trigger('change'); // Dispara la cascada hacia empleados
            }
        } catch (error) {
            console.error("Error al actualizar combo de secciones:", error);
        }
    });

    // 4. EVENTO CASCADA 2: Al cambiar SECCIÓN, buscamos sus EMPLEADOS en la BD
    $('#selectSeccion, #selectSector').on('change', async function() {
        const sectorId = this.value;
        const selectEmpleado = document.getElementById('selectEmpleado');

        if (!sectorId) return;
        console.log(`📡 Buscando empleados en BD para el sector ID: ${sectorId}`);

        try {
            const empleados = await fetchEmpleadosPorSector(sectorId);
            let opcionesEmpleados = '<option value="">-- Seleccione Empleado --</option>';

            empleados.forEach(emp => {
                // Armamos el formato "Nombre Apellido (Legajo)" dinámico de tu DB
                opcionesEmpleados += `<option value="${emp.legajo}">${emp.nombre} ${emp.apellido} (${emp.legajo})</option>`;
            });

            if (selectEmpleado) {
                selectEmpleado.innerHTML = opcionesEmpleados;
                $(selectEmpleado).trigger('change.select2'); // Refrescamos la UI de Select2
            }
        } catch (error) {
            console.error("Error al actualizar combo de empleados:", error);
        }
    });
}