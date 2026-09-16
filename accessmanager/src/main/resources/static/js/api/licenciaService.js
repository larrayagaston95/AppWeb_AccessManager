const BASE_URL = 'http://localhost:8080/api/licencias';

function getHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('access_token_am')
    };
}

export async function fetchLicenciasPorEmpleado(empleadoId) {
    const response = await fetch(`${BASE_URL}?empleadoId=${empleadoId}`, {
        headers: getHeaders()
    });
    
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.error || 'Error al obtener las licencias del empleado');
    }
    
    return await response.json();
}

export async function createLicencia(payload) {
    const response = await fetch(BASE_URL, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify(payload)
    });
    
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.error || 'Error al registrar la licencia');
    }
    
    return await response.json();
}