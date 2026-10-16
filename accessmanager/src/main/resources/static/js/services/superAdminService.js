/**
 * superAdminService.js
 * Servicio del panel SuperAdmin para empresas, sucursales y sectores.
 * Es el único archivo que llama a esos endpoints de /api/superadmin (SuperAdminController en Spring).
 *
 * Recorrido del dato:
 *   Vista (formulariosAltaUI.js o fluxtech-admin.js) → función de este servicio
 *   → enviarPedidoConSesion() → SuperAdminController → respuesta JSON → vuelve a la vista.
 */
import { enviarPedidoConSesion } from './peticionesConSesion.js';

const ARCHIVO = 'superAdminService.js';
const DIRECCION_BASE = '/api/superadmin';

/** GET /api/superadmin/empresas → [{ id, nombre }] */
export async function listarEmpresas() {
    return enviarPedidoConSesion(ARCHIVO, 'listarEmpresas', `${DIRECCION_BASE}/empresas`);
}

/** GET /api/superadmin/sucursales → [{ id, nombre, empresa }] (empresa = nombre de la empresa) */
export async function listarSucursales() {
    return enviarPedidoConSesion(ARCHIVO, 'listarSucursales', `${DIRECCION_BASE}/sucursales`);
}

/**
 * POST /api/superadmin/clientes con { nombre_empresa, username_admin, password_admin }
 * Crea la empresa y su usuario administrador. Respuesta: { message, empresa_id, username }
 */
export async function crearCliente(nombreDeLaEmpresa, usuarioAdministrador, contraseniaDelAdministrador) {
    return enviarPedidoConSesion(ARCHIVO, 'crearCliente', `${DIRECCION_BASE}/clientes`, {
        method: 'POST',
        body: JSON.stringify({ nombre_empresa: nombreDeLaEmpresa, username_admin: usuarioAdministrador, password_admin: contraseniaDelAdministrador })
    });
}

/** POST /api/superadmin/sucursales con { empresa_id, nombre_sucursal } → { message, sucursal_id, empresa_id } */
export async function crearSucursal(idDeLaEmpresa, nombreDeLaSucursal) {
    return enviarPedidoConSesion(ARCHIVO, 'crearSucursal', `${DIRECCION_BASE}/sucursales`, {
        method: 'POST',
        body: JSON.stringify({ empresa_id: idDeLaEmpresa, nombre_sucursal: nombreDeLaSucursal })
    });
}

/** POST /api/superadmin/sectores con { sucursal_id, nombre_sector } → { message, sector_id, sucursal_id } */
export async function crearSector(idDeLaSucursal, nombreDelSector) {
    return enviarPedidoConSesion(ARCHIVO, 'crearSector', `${DIRECCION_BASE}/sectores`, {
        method: 'POST',
        body: JSON.stringify({ sucursal_id: idDeLaSucursal, nombre_sector: nombreDelSector })
    });
}
