/**
 * formulariosAltaUI.js
 * Vista de los tres formularios del panel SuperAdmin: alta de cliente, de sucursal y de sector.
 * Solo lee los campos, muestra el resultado y actualiza los desplegables; las llamadas al
 * servidor las hace superAdminService.js.
 *
 * Recorrido del dato (igual para los tres formularios):
 *   1. El usuario completa el formulario y presiona el botón.
 *   2. Se deshabilita el botón y se llama a la función del servicio (crearCliente / crearSucursal / crearSector).
 *   3. Con la respuesta se muestra el aviso y se pide al panel que recargue los desplegables
 *      (una empresa nueva aparece en "Alta de sucursal"; una sucursal nueva en "Alta de sector").
 */
import { crearCliente, crearSucursal, crearSector } from '../services/superAdminService.js';

/**
 * @param {(idDelAviso: string, texto: string, esError?: boolean) => void} mostrarAviso
 * @param {() => Promise<void>} recargarEmpresas    vuelve a pedir las empresas y redibuja los desplegables
 * @param {() => Promise<void>} recargarSucursales  vuelve a pedir las sucursales y redibuja los desplegables
 */
export function inicializarFormulariosDeAlta(mostrarAviso, recargarEmpresas, recargarSucursales) {
    conectarFormulario({
        idDelFormulario: 'formCrearCliente', idDelBoton: 'btnCrearCliente', idDelAviso: 'toastCliente',
        textoDelBoton: '<i class="bi bi-check-circle me-1"></i> Generar Cliente y Cuenta',
        enviar: () => crearCliente(leerCampo('empNombre'), leerCampo('adminUser'), leerCampo('adminPass')),
        describirResultado: respuesta => `Cliente creado. Empresa ID: ${respuesta.empresa_id} | Usuario: ${respuesta.username}`,
        alTerminar: recargarEmpresas
    }, mostrarAviso);

    conectarFormulario({
        idDelFormulario: 'formCrearSucursal', idDelBoton: 'btnCrearSucursal', idDelAviso: 'toastSucursal',
        textoDelBoton: '<i class="bi bi-plus-circle me-1"></i> Generar Sucursal',
        enviar: () => crearSucursal(leerCampo('empresaSelect'), leerCampo('sucNombre')),
        describirResultado: respuesta => `Sucursal creada. ID: ${respuesta.sucursal_id}`,
        alTerminar: recargarSucursales
    }, mostrarAviso);

    conectarFormulario({
        idDelFormulario: 'formCrearSector', idDelBoton: 'btnCrearSector', idDelAviso: 'toastSector',
        textoDelBoton: '<i class="bi bi-node-plus-fill me-1"></i> Generar Sector',
        enviar: () => crearSector(leerCampo('sucursalSelect'), leerCampo('sectorNombre')),
        describirResultado: respuesta => `Sector creado. ID: ${respuesta.sector_id} en Sucursal ID: ${respuesta.sucursal_id}`,
        alTerminar: async () => {}
    }, mostrarAviso);
}

/** Comportamiento común: botón ocupado mientras se envía, aviso con el resultado, formulario limpio. */
function conectarFormulario(definicion, mostrarAviso) {
    document.getElementById(definicion.idDelFormulario).addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const boton = document.getElementById(definicion.idDelBoton);
        boton.disabled = true;
        boton.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Creando...';
        try {
            const respuesta = await definicion.enviar();
            mostrarAviso(definicion.idDelAviso, definicion.describirResultado(respuesta));
            document.getElementById(definicion.idDelFormulario).reset();
            await definicion.alTerminar();
        } catch (error) {
            // El servicio ya registró el error en consola con el formato obligatorio
            mostrarAviso(definicion.idDelAviso, error.message, true);
        } finally {
            boton.disabled = false;
            boton.innerHTML = definicion.textoDelBoton; // texto fijo del código, no viene del servidor
        }
    });
}

function leerCampo(idDelCampo) {
    return document.getElementById(idDelCampo).value.trim();
}
