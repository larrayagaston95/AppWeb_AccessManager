/**
 * agenteAdminService.js
 * Servicio del panel SuperAdmin para los agentes locales.
 * Es el ÚNICO archivo del panel que llama a /api/superadmin/agentes: la vista (agentesUI.js)
 * nunca hace fetch directamente.
 *
 * Recorrido del dato:
 *   Vista (clic del usuario) → función de este servicio → enviarPedidoConSesion()
 *   → AgenteAdminController (Spring) → AdministracionDeAgentesService → respuesta JSON → vista.
 *   Los errores se registran en consola como "Error en agenteAdminService.js -> [función]: ..."
 */
import { enviarPedidoConSesion } from './peticionesConSesion.js';

const ARCHIVO = 'agenteAdminService.js';
const DIRECCION_BASE_DE_AGENTES = '/api/superadmin/agentes';

/**
 * GET /api/superadmin/agentes
 * Respuesta: [{ id, nombre, empresa, sucursal, activo, enLinea, ultimaConexion, version, estado }]
 */
export async function listarAgentes() {
    return enviarPedidoConSesion(ARCHIVO, 'listarAgentes', DIRECCION_BASE_DE_AGENTES);
}

/**
 * POST /api/superadmin/agentes con { nombre, empresa_id, sucursal_id }
 * Respuesta: { id, clave, aviso } — la clave se muestra una sola vez.
 */
export async function crearAgente(nombreDelAgente, idDeLaEmpresa, idDeLaSucursal) {
    return enviarPedidoConSesion(ARCHIVO, 'crearAgente', DIRECCION_BASE_DE_AGENTES, {
        method: 'POST',
        body: JSON.stringify({ nombre: nombreDelAgente, empresa_id: idDeLaEmpresa, sucursal_id: idDeLaSucursal || null })
    });
}

/** POST /api/superadmin/agentes/{id}/revocar → { message }. La clave deja de funcionar de inmediato. */
export async function revocarAgente(idDelAgente) {
    return enviarPedidoConSesion(ARCHIVO, 'revocarAgente',
        `${DIRECCION_BASE_DE_AGENTES}/${encodeURIComponent(idDelAgente)}/revocar`, { method: 'POST' });
}

/** POST /api/superadmin/agentes/{id}/regenerar-clave → { id, clave, aviso }. La clave anterior deja de funcionar. */
export async function regenerarClaveDelAgente(idDelAgente) {
    return enviarPedidoConSesion(ARCHIVO, 'regenerarClaveDelAgente',
        `${DIRECCION_BASE_DE_AGENTES}/${encodeURIComponent(idDelAgente)}/regenerar-clave`, { method: 'POST' });
}
