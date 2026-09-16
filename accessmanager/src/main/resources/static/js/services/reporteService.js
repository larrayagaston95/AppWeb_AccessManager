const mapaMeses = {
    "Enero": 1, "Febrero": 2, "Marzo": 3, "Abril": 4, "Mayo": 5, "Junio": 6,
    "Julio": 7, "Agosto": 8, "Septiembre": 9, "Octubre": 10, "Noviembre": 11, "Diciembre": 12
};

function getAuthHeaders() {
    const token = localStorage.getItem('access_token_am');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
}

/**
 * Exporta el PDF individual del empleado seleccionado en el período indicado.
 * Abre el PDF en una nueva pestaña (inline preview).
 */
export async function exportarIndividual() {
    const selectEmpleado = document.getElementById('selectEmpleado');
    const selectAnio     = document.getElementById('selectAnio');
    const selectMes      = document.getElementById('selectMes');

    const legajo   = selectEmpleado ? selectEmpleado.value.trim() : '';
    const anio     = selectAnio     ? selectAnio.value             : '2026';
    const mesTexto = selectMes      ? selectMes.value              : 'Junio';

    if (!legajo) {
        alert('Por favor, seleccione un empleado antes de exportar el PDF.');
        return;
    }

    const nombreEmpleado = (selectEmpleado && selectEmpleado.selectedIndex !== -1)
        ? selectEmpleado.options[selectEmpleado.selectedIndex].text.split('(')[0].trim()
        : 'Empleado';

    const mesNumero = mapaMeses[mesTexto] || 6;

    const url = `http://localhost:8080/api/reportes/asistencia`
        + `?legajo=${encodeURIComponent(legajo)}`
        + `&anio=${anio}`
        + `&mes=${mesNumero}`
        + `&nombreEmpleado=${encodeURIComponent(nombreEmpleado)}`;

    console.log('📥 Descargando PDF individual:', url);
    
    try {
        const response = await fetch(url, { headers: getAuthHeaders() });
        if (!response.ok) throw new Error('Error al descargar el PDF (Status: ' + response.status + ')');
        
        const rawBlob = await response.blob();
        const file = new Blob([rawBlob], { type: 'application/pdf' });
        const fileURL = URL.createObjectURL(file);
        window.open(fileURL, '_blank');
        
        // No revocamos la URL inmediatamente para darle tiempo al navegador de abrir la pestaña y renderizar el PDF
    } catch (error) {
        console.error(error);
        alert('Ocurrió un problema al descargar el reporte individual.');
    }
}

/**
 * Exporta el PDF masivo filtrado por sucursal (ID) y sección (ID) en el período indicado.
 * Abre el PDF en una nueva pestaña (inline preview).
 *
 * @param {string|number} [anioParam]  - Año a usar (opcional; si no se pasa, lee el select).
 * @param {string|number} [mesParam]   - Número de mes a usar (opcional; si no se pasa, lee el select).
 */
export async function exportarMasivo(anioParam, mesParam) {
    const selectSucursal = document.getElementById('selectSucursal');
    const selectSeccion  = document.getElementById('selectSeccion');
    const selectAnio     = document.getElementById('selectAnio');
    const selectMes      = document.getElementById('selectMes');

    // IDs numéricos que ya vienen del value de los <select> poblados por la BD
    const sucursalId = selectSucursal ? selectSucursal.value : '';
    const sectorId   = selectSeccion  ? selectSeccion.value  : '';

    if (!sucursalId || !sectorId) {
        alert('Por favor, seleccione una Sucursal y una Sección antes de generar el reporte masivo.');
        return;
    }

    // Si se pasan como argumento (desde app.js) se usan directamente; si no, leemos los selects
    const anio     = anioParam ?? (selectAnio ? selectAnio.value : '2026');
    const mesTexto = selectMes ? selectMes.value : 'Junio';
    const mesNumero = mesParam ?? mapaMeses[mesTexto] ?? 6;

    const url = `http://localhost:8080/api/reportes/asistencia-masiva`
        + `?sucursalId=${encodeURIComponent(sucursalId)}`
        + `&sectorId=${encodeURIComponent(sectorId)}`
        + `&anio=${anio}`
        + `&mes=${mesNumero}`;

    console.log('📥 Descargando PDF masivo:', url);
    
    try {
        const response = await fetch(url, { headers: getAuthHeaders() });
        if (!response.ok) throw new Error('Error al descargar el PDF masivo (Status: ' + response.status + ')');
        
        const rawBlob = await response.blob();
        const file = new Blob([rawBlob], { type: 'application/pdf' });
        const fileURL = URL.createObjectURL(file);
        window.open(fileURL, '_blank');
        
    } catch (error) {
        console.error(error);
        alert('Ocurrió un problema al descargar el reporte masivo.');
    }
}