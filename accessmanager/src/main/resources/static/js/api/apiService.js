const BASE_URL = 'http://localhost:8080/api/v1/asistencia';

// ── 1. Helper para los headers ────────────────────────────────────────────────
function authHeaders() {
    const token = localStorage.getItem('access_token_am');
    if (!token) return {};
    return {
        'Authorization': `Bearer ${token}`
    };
}

// ── 2. Manejo de respuesta genérico ───────────────────────────────────────────
async function handleResponse(response) {
    if (response.status === 401 || response.status === 403) {
        localStorage.removeItem('access_token_am');
        localStorage.removeItem('username_am');
        localStorage.removeItem('rol_am');
        localStorage.removeItem('empresa_id_am');
        window.location.href = 'login.html';
        throw new Error(response.status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN');
    }
    if (!response.ok) throw new Error('SERVER_ERROR');
    return await response.json();
}

/**
 * Trae el reporte mensual individual de un empleado (día a día).
 * GET /api/v1/asistencia/reporte-mensual?legajo=&anio=&mes=
 */
export async function fetchReporteMensual(legajo, anio, mes) {
    const url = `${BASE_URL}/reporte-mensual?legajo=${legajo}&anio=${anio}&mes=${mes}`;
    const response = await fetch(url, { method: 'GET', headers: authHeaders() });
    return handleResponse(response);
}

/**
 * Trae el detalle de asistencia mensual con validación Multi-Tenant
 * GET /api/v1/asistencia/detalle?legajo=&anio=&mes=
 */
export async function fetchDetalleAsistencia(legajo, anio, mes) {
    const url = `${BASE_URL}/detalle?legajo=${legajo}&anio=${anio}&mes=${mes}`;
    const response = await fetch(url, { method: 'GET', headers: authHeaders() });
    return handleResponse(response);
}

/**
 * Trae la lista de sucursales de la empresa (empresa_id ahora se lee del JWT en backend).
 * GET /api/sucursales
 * Respuesta: [{ id, nombre }]
 */
export async function fetchSucursales() {
    const response = await fetch(
        `http://localhost:8080/api/sucursales`,
        { headers: authHeaders() }
    );
    return handleResponse(response);
}

/**
 * Trae los sectores de una sucursal dado su ID numérico.
 * GET /api/sectores?sucursalId={sucursalId}
 * Respuesta: [{ id, nombre }]
 */
export async function fetchSectoresPorSucursal(sucursalId) {
    try {
        const response = await fetch(
            `http://localhost:8080/api/sectores?sucursalId=${sucursalId}`,
            { headers: authHeaders() }
        );
        return await handleResponse(response);
    } catch (error) {
        console.error('❌ Error en fetchSectoresPorSucursal:', error);
        if (error.message === 'UNAUTHORIZED' || error.message === 'FORBIDDEN') throw error;
        return [];
    }
}

/**
 * Trae los empleados de un sector dado su ID numérico.
 * GET /api/empleados?sectorId={sectorId}
 * Respuesta: [{ id, legajo, nombre, apellido }]
 */
export async function fetchEmpleadosPorSector(sectorId) {
    try {
        const response = await fetch(
            `http://localhost:8080/api/empleados?sectorId=${sectorId}`,
            { headers: authHeaders() }
        );
        return await handleResponse(response);
    } catch (error) {
        console.error('❌ Error en fetchEmpleadosPorSector:', error);
        if (error.message === 'UNAUTHORIZED' || error.message === 'FORBIDDEN') throw error;
        return [];
    }
}

/**
 * Trae el resumen agregado de asistencia del sector (un registro por empleado con totales).
 * GET /api/sectores/resumen?sectorId={id}&anio={año}&mes={mes}
 * Respuesta: [{ legajo, nombre, apellido, diasTrabajados, totalHoras, totalExtras }]
 *
 * Se usa cuando el filtro de Empleado está vacío (modo vista consolidada de sector).
 */
export async function fetchResumenSector(sectorId, anio, mes) {
    try {
        const url = `http://localhost:8080/api/sectores/resumen?sectorId=${sectorId}&anio=${anio}&mes=${mes}`;
        const response = await fetch(url, { headers: authHeaders() });
        return await handleResponse(response);
    } catch (error) {
        console.error('❌ Error en fetchResumenSector:', error);
        if (error.message === 'UNAUTHORIZED' || error.message === 'FORBIDDEN') throw error;
        return [];
    }
}