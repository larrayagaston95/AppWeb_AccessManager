const BASE_URL = 'http://localhost:8080/api/empleados';

function authHeaders() {
    const token = localStorage.getItem('access_token_am');
    if (!token) return { 'Content-Type': 'application/json' };
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
}

async function handleResponse(response) {
    if (response.status === 401 || response.status === 403) {
        throw new Error(response.status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN');
    }
    if (!response.ok) throw new Error('SERVER_ERROR');
    return await response.json();
}

export async function fetchEmpleadosTodos() {
    const response = await fetch(BASE_URL, { headers: authHeaders() });
    return handleResponse(response);
}

export async function createEmpleado(data) {
    const response = await fetch(BASE_URL, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify(data)
    });
    return handleResponse(response);
}

export async function updateEmpleado(id, data) {
    const response = await fetch(`${BASE_URL}/${id}`, {
        method: 'PUT',
        headers: authHeaders(),
        body: JSON.stringify(data)
    });
    return handleResponse(response);
}

export async function deleteEmpleado(id) {
    const response = await fetch(`${BASE_URL}/${id}`, {
        method: 'DELETE',
        headers: authHeaders()
    });
    return handleResponse(response);
}

/**
 * Consulta al backend el próximo legajo disponible para la empresa del usuario autenticado.
 * @returns {Promise<number>} El próximo número de legajo sugerido.
 */
export async function fetchProximoLegajo() {
    const response = await fetch(`${BASE_URL}/proximo-legajo`, { headers: authHeaders() });
    const data = await handleResponse(response);
    return data.proximoLegajo;
}
