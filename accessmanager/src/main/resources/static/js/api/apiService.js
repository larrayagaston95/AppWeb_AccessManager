const BASE_URL = 'http://localhost:8080/api/v1/asistencia';

/**
 * Realiza la petición HTTP GET al backend para traer las asistencias.
 * Inyecta el token de seguridad en las cabeceras.
 */
export async function fetchReporteMensual(legajo, anio, mes) {
    const token = localStorage.getItem('access_token_am');
    const url = `${BASE_URL}/reporte-mensual?legajo=${legajo}&anio=${anio}&mes=${mes}`;

    const response = await fetch(url, {
        method: 'GET',
        headers: {
            'X-Access-Token': token
        }
    });

    if (response.status === 401) {
        throw new Error('UNAUTHORIZED');
    }

    if (!response.ok) {
        throw new Error('SERVER_ERROR');
    }

    return await response.json();
}

/**
 * Trae la lista de todas las sucursales de la empresa
 */
export async function fetchSucursales(empresaId = 1) {
    const token = localStorage.getItem('access_token_am');
    const response = await fetch(`http://localhost:8080/api/sucursales?empresaId=${empresaId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    if (!response.ok) throw new Error('Error al traer sucursales');
    return await response.json();
}

/**
 * Trae los sectores filtrados por el nombre de la sucursal
 */
export async function fetchSectoresPorSucursal(sucursalNombre) {
    const token = localStorage.getItem('access_token_am');
    const response = await fetch(`http://localhost:8080/api/sectores?sucursal=${encodeURIComponent(sucursalNombre)}`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    if (!response.ok) throw new Error('Error al traer sectores');
    return await response.json();
}

/**
 * Trae los empleados filtrados por el ID del sector/sección
 */
export async function fetchEmpleadosPorSector(sectorId) {
    const token = localStorage.getItem('access_token_am');
    const response = await fetch(`http://localhost:8080/api/empleados?sectorId=${sectorId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
    });
    if (!response.ok) throw new Error('Error al traer empleados');
    return await response.json();
}