/**
 * dashboardService.js
 * Servicio para consumir el endpoint GET /api/dashboard/resumen
 * Importa los helpers de autenticación de apiService para reutilizar headers y manejo de errores.
 */

const DASHBOARD_BASE = 'http://localhost:8080/api/dashboard';

function authHeaders() {
    const token = localStorage.getItem('access_token_am');
    if (!token) return {};
    return { 'Authorization': `Bearer ${token}` };
}

async function handleDashboardResponse(response) {
    if (response.status === 401 || response.status === 403) {
        localStorage.clear();
        window.location.href = 'login.html';
        throw new Error(response.status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN');
    }
    if (!response.ok) throw new Error('SERVER_ERROR');
    return await response.json();
}

/**
 * Obtiene el resumen de asistencia del día actual.
 * @param {number|null} sucursalId - ID de la sucursal a filtrar, o null para toda la empresa.
 * @returns {Promise<{totalEmpleados, presentes, ausentes, llegadasTarde}>}
 */
export async function fetchResumenDashboard(sucursalId) {
    const params = sucursalId ? `?sucursalId=${sucursalId}` : '';
    const url = `${DASHBOARD_BASE}/resumen${params}`;
    const response = await fetch(url, { headers: authHeaders() });
    return handleDashboardResponse(response);
}
