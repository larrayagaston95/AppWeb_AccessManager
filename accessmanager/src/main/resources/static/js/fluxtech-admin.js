/**
 * fluxtech-admin.js
 * Punto de entrada del panel SuperAdmin de FluxTech (fluxtech-admin.html).
 *
 * Responsabilidades (solo coordinación, sin lógica de negocio ni llamadas HTTP directas):
 *   1. Verificar que haya una sesión de SuperAdmin; si no, volver al login.
 *   2. Cargar empresas y sucursales con superAdminService.js y llenar los desplegables.
 *   3. Inicializar las secciones: formularios de alta (formulariosAltaUI.js) y agentes (agentesUI.js).
 *
 * Recorrido del dato:
 *   Carga de la página → verificación de sesión → listarEmpresas()/listarSucursales() del servicio
 *   → se guardan en memoria y se dibujan los desplegables → cada sección usa esos datos.
 */
import { listarEmpresas, listarSucursales } from './services/superAdminService.js';
import { inicializarFormulariosDeAlta } from './components/formulariosAltaUI.js';
import { inicializarSeccionDeAgentes } from './components/agentesUI.js';

document.addEventListener('DOMContentLoaded', async () => {

    // Paso 1: solo un SuperAdmin con sesión puede ver este panel
    const tokenDeSesion = localStorage.getItem('access_token_am');
    const rolDelUsuario = localStorage.getItem('rol_am');
    if (!tokenDeSesion || rolDelUsuario !== 'ROLE_SUPERADMIN') {
        localStorage.clear();
        window.location.href = 'login.html';
        return;
    }

    document.getElementById('btnCerrarSesion').addEventListener('click', () => {
        localStorage.clear();
        window.location.href = 'login.html';
    });

    // Datos compartidos por las secciones (se recargan al crear una empresa o una sucursal)
    let empresasCargadas = [];
    let sucursalesCargadas = [];

    async function recargarEmpresas() {
        try {
            empresasCargadas = await listarEmpresas();
            llenarDesplegable('empresaSelect', empresasCargadas, empresa => `${empresa.nombre}  (ID: ${empresa.id})`);
            llenarDesplegable('agenteEmpresaSelect', empresasCargadas, empresa => `${empresa.nombre}  (ID: ${empresa.id})`);
        } catch (error) {
            mostrarErrorEnDesplegable('empresaSelect', 'Error al cargar empresas');
        }
    }

    async function recargarSucursales() {
        try {
            sucursalesCargadas = await listarSucursales();
            llenarDesplegable('sucursalSelect', sucursalesCargadas,
                sucursal => `${sucursal.nombre}  (${sucursal.empresa || 'Sin empresa'})`);
        } catch (error) {
            mostrarErrorEnDesplegable('sucursalSelect', 'Error al cargar sucursales');
        }
    }

    // Paso 2: datos iniciales
    await Promise.all([recargarEmpresas(), recargarSucursales()]);

    // Paso 3: secciones del panel
    inicializarFormulariosDeAlta(mostrarAviso, recargarEmpresas, recargarSucursales);
    await inicializarSeccionDeAgentes(() => empresasCargadas, () => sucursalesCargadas,
        (texto, esError = false) => mostrarAviso('toastAgente', texto, esError));
});

/** Aviso dentro de la tarjeta (en lugar de alert), visible 6 segundos. */
function mostrarAviso(idDelAviso, texto, esError = false) {
    const elementoDelAviso = document.getElementById(idDelAviso);
    if (!elementoDelAviso) return;
    elementoDelAviso.textContent = texto;
    elementoDelAviso.className = `sa-toast show alert ${esError ? 'alert-danger' : 'alert-success'} border-0`;
    setTimeout(() => { elementoDelAviso.className = 'sa-toast'; }, 6000);
}

/** Llena un desplegable con textContent (los nombres vienen del servidor: nunca innerHTML). */
function llenarDesplegable(idDelDesplegable, elementos, armarTexto) {
    const desplegable = document.getElementById(idDelDesplegable);
    if (!desplegable) return;
    const opciones = elementos.map(elemento => new Option(armarTexto(elemento), elemento.id));
    desplegable.replaceChildren(new Option('-- Seleccione --', ''), ...opciones);
}

function mostrarErrorEnDesplegable(idDelDesplegable, texto) {
    const desplegable = document.getElementById(idDelDesplegable);
    if (desplegable) desplegable.replaceChildren(new Option(texto, ''));
}
