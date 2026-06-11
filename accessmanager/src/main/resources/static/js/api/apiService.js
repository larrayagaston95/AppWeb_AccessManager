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