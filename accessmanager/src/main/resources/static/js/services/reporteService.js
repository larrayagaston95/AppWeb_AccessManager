const mapaMeses = {
    "Enero": 1, "Febrero": 2, "Marzo": 3, "Abril": 4, "Mayo": 5, "Junio": 6,
    "Julio": 7, "Agosto": 8, "Septiembre": 9, "Octubre": 10, "Noviembre": 11, "Diciembre": 12
};

/**
 * Lógica del botón de Exportar PDF Individual
 */
export function exportarIndividual() {
    const selectEmpleado = document.getElementById('selectEmpleado');
    const selectAnio = document.getElementById('selectAnio');
    const selectMes = document.getElementById('selectMes');

    const legajo = selectEmpleado ? selectEmpleado.value : '';
    const anio = selectAnio ? selectAnio.value : '2026';
    const mesTexto = selectMes ? selectMes.value : 'Junio';

    if (!legajo || legajo.trim() === "") {
        alert('Por favor, asegúrese de seleccionar un empleado antes de exportar el PDF.');
        return;
    }

    const nombreEmpleado = (selectEmpleado && selectEmpleado.selectedIndex !== -1)
        ? selectEmpleado.options[selectEmpleado.selectedIndex].text.split('(')[0].trim()
        : "Empleado";

    const mesNumero = mapaMeses[mesTexto] || 6;

    const urlReporte = `http://localhost:8080/api/reportes/asistencia?legajo=${legajo}&anio=${anio}&mes=${mesNumero}&nombreEmpleado=${encodeURIComponent(nombreEmpleado)}`;

    console.log("📥 Abriendo PDF Individual desde: " + urlReporte);
    window.open(urlReporte, '_blank');
}

/**
 * Lógica del botón Masivo (Por sucursal y sección)
 */
export function exportarMasivo() {
    const selectSucursal = document.getElementById('selectSucursal');
    const selectSeccion = document.getElementById('selectSeccion') || document.getElementById('selectSector');
    const selectAnio = document.getElementById('selectAnio');
    const selectMes = document.getElementById('selectMes');

    const sucursal = selectSucursal ? selectSucursal.value : "Planta Central";
    const anio = selectAnio ? selectAnio.value : '2026';
    const mesTexto = selectMes ? selectMes.value : 'Junio';
    const empresaId = "1";

    let sectorId = selectSeccion ? selectSeccion.value : "1";
    if (!sectorId || sectorId === "" || sectorId === "-- Todas --") {
        sectorId = "1";
    }

    let sucursalReal = sucursal;
    if (sucursal === "Central" || sucursal === "central") {
        sucursalReal = "Planta Central";
    }

    const mesNumero = mapaMeses[mesTexto] || 6;

    console.log("=== ENVIANDO FILTROS MASIVOS REALES ===");
    const urlMasiva = `http://localhost:8080/api/reportes/asistencia-masiva?empresaId=${empresaId}&sucursal=${encodeURIComponent(sucursalReal)}&sectorId=${sectorId}&anio=${anio}&mes=${mesNumero}`;

    console.log("📥 Abriendo PDF Masivo desde:", urlMasiva);
    window.open(urlMasiva, '_blank');
}