/**
 * peticionesConSesion.js
 * Envío de pedidos HTTP con la sesión del usuario, compartido por los servicios del panel
 * (superAdminService.js y agenteAdminService.js). Las vistas nunca lo usan directamente.
 *
 * Recorrido del dato:
 *   Un servicio llama a enviarPedidoConSesion() → se agrega el token de la sesión → fetch al backend
 *   → si la respuesta es exitosa se devuelve el JSON; si no, se registra en consola
 *   "Error en [archivo] -> [función]: [descripción]" y se lanza un Error con el mensaje del servidor.
 */

/** Cabeceras con el token guardado al iniciar sesión (login.js). */
function armarCabecerasConSesion() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('access_token_am')}`
    };
}

/**
 * @param {string} archivoQueLlama   nombre del servicio, para el registro de errores (ej. "superAdminService.js")
 * @param {string} funcionQueLlama   nombre de la función del servicio (ej. "crearCliente")
 * @param {string} direccion         ruta del endpoint (ej. "/api/superadmin/clientes")
 * @param {object} opciones          opciones de fetch (method, body)
 * @returns {Promise<any>} JSON de la respuesta
 */
export async function enviarPedidoConSesion(archivoQueLlama, funcionQueLlama, direccion, opciones = {}) {
    try {
        const respuesta = await fetch(direccion, { headers: armarCabecerasConSesion(), ...opciones });
        // Algunas respuestas de error no traen JSON: se usa un objeto vacío en ese caso
        const datos = await respuesta.json().catch(() => ({}));
        if (!respuesta.ok) {
            throw new Error(datos.error || `El servidor respondió ${respuesta.status}`);
        }
        return datos;
    } catch (error) {
        console.error(`Error en ${archivoQueLlama} -> ${funcionQueLlama}: ${error.message}`);
        throw error;
    }
}
