/**
 * agentesUI.js
 * Vista de la sección "Agentes locales" del panel SuperAdmin.
 * Solo dibuja y responde a los clics: todas las llamadas al servidor las hace agenteAdminService.js.
 *
 * Recorrido del dato:
 *   1. fluxtech-admin.js llama a inicializarSeccionDeAgentes() con funciones que devuelven las
 *      empresas y sucursales ya cargadas.
 *   2. El usuario completa el formulario → crearAgente() del servicio → se muestra la clave UNA vez.
 *   3. La tabla se refresca cada 30 segundos con listarAgentes() para ver quién está "En línea".
 *   4. Botones "Nueva clave" y "Revocar" → servicio → se vuelve a dibujar la tabla.
 *   Todo texto que viene del servidor se inserta con textContent (nunca innerHTML) para evitar XSS.
 */
import { listarAgentes, crearAgente, revocarAgente, regenerarClaveDelAgente } from '../services/agenteAdminService.js';

const MILISEGUNDOS_ENTRE_ACTUALIZACIONES = 30000;

/**
 * Punto de entrada de la sección.
 * @param {() => Array} obtenerEmpresas   devuelve [{id, nombre}] ya cargadas por el panel
 * @param {() => Array} obtenerSucursales devuelve [{id, nombre, empresa}] (empresa = nombre de la empresa)
 * @param {(texto: string, esError?: boolean) => void} mostrarAviso muestra un mensaje en la tarjeta
 */
export async function inicializarSeccionDeAgentes(obtenerEmpresas, obtenerSucursales, mostrarAviso) {
    const selectorDeEmpresa = document.getElementById('agenteEmpresaSelect');
    const selectorDeSucursal = document.getElementById('agenteSucursalSelect');

    // Al elegir empresa se ofrecen solo sus sucursales (el endpoint informa la empresa por nombre, que es único)
    selectorDeEmpresa.addEventListener('change', () => {
        const empresaElegida = obtenerEmpresas().find(empresa => String(empresa.id) === selectorDeEmpresa.value);
        const sucursalesDeLaEmpresa = empresaElegida
            ? obtenerSucursales().filter(sucursal => sucursal.empresa === empresaElegida.nombre)
            : [];
        cargarOpcionesDeSucursal(selectorDeSucursal, sucursalesDeLaEmpresa);
    });

    document.getElementById('btnCopiarClave').addEventListener('click', () => copiarClaveAlPortapapeles(mostrarAviso));

    document.getElementById('formCrearAgente').addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const botonCrear = document.getElementById('btnCrearAgente');
        botonCrear.disabled = true;
        try {
            const nombreDelAgente = document.getElementById('agenteNombre').value.trim();
            const respuesta = await crearAgente(nombreDelAgente, selectorDeEmpresa.value, selectorDeSucursal.value);
            mostrarClaveUnaSolaVez(respuesta.clave);
            document.getElementById('agenteNombre').value = '';
            await dibujarTablaDeAgentes(mostrarAviso);
        } catch (error) {
            mostrarAviso(error.message, true);
        } finally {
            botonCrear.disabled = false;
        }
    });

    await dibujarTablaDeAgentes(mostrarAviso);
    setInterval(() => dibujarTablaDeAgentes(mostrarAviso), MILISEGUNDOS_ENTRE_ACTUALIZACIONES);
}

/** Pide la lista al servicio y redibuja la tabla completa. */
async function dibujarTablaDeAgentes(mostrarAviso) {
    const cuerpoDeLaTabla = document.getElementById('tablaAgentes');
    try {
        const agentes = await listarAgentes();
        cuerpoDeLaTabla.replaceChildren();
        if (agentes.length === 0) {
            cuerpoDeLaTabla.appendChild(crearFilaDeMensaje('Todavía no hay agentes.', 'text-muted text-center'));
            return;
        }
        agentes.forEach(agente => cuerpoDeLaTabla.appendChild(crearFilaDeAgente(agente, mostrarAviso)));
    } catch (error) {
        cuerpoDeLaTabla.replaceChildren(crearFilaDeMensaje(error.message, 'text-danger text-center'));
    }
}

/** Una fila: estado, nombre y versión, empresa/sucursal, última conexión, detalle y acciones. */
function crearFilaDeAgente(agente, mostrarAviso) {
    const fila = document.createElement('tr');
    fila.appendChild(crearCeldaDeEstado(agente));
    fila.appendChild(crearCelda(agente.nombre + (agente.version ? ` (v${agente.version})` : ''), 'fw-bold'));
    fila.appendChild(crearCelda(`${agente.empresa} / ${agente.sucursal}`));
    fila.appendChild(crearCelda(agente.ultimaConexion ? agente.ultimaConexion.replace('T', ' ').substring(0, 19) : '-'));
    fila.appendChild(crearCelda(agente.estado || '-', 'small text-muted'));

    const celdaDeAcciones = crearCelda('', 'text-end text-nowrap');
    celdaDeAcciones.appendChild(crearBoton('Nueva clave', 'btn-outline-warning', async () => {
        if (!confirm(`¿Generar una clave nueva para "${agente.nombre}"? La actual deja de funcionar.`)) return;
        try {
            const respuesta = await regenerarClaveDelAgente(agente.id);
            mostrarClaveUnaSolaVez(respuesta.clave);
            await dibujarTablaDeAgentes(mostrarAviso);
        } catch (error) {
            mostrarAviso(error.message, true);
        }
    }));
    if (agente.activo) {
        celdaDeAcciones.appendChild(crearBoton('Revocar', 'btn-outline-danger', async () => {
            if (!confirm(`¿Revocar "${agente.nombre}"? Deja de poder subir fichadas.`)) return;
            try {
                const respuesta = await revocarAgente(agente.id);
                mostrarAviso(respuesta.message);
                await dibujarTablaDeAgentes(mostrarAviso);
            } catch (error) {
                mostrarAviso(error.message, true);
            }
        }));
    }
    fila.appendChild(celdaDeAcciones);
    return fila;
}

/** Revocado / En línea / Nunca conectó / Sin conexión. */
function crearCeldaDeEstado(agente) {
    const insignia = document.createElement('span');
    if (!agente.activo) { insignia.className = 'badge bg-secondary'; insignia.textContent = 'Revocado'; }
    else if (agente.enLinea) { insignia.className = 'badge bg-success'; insignia.textContent = 'En línea'; }
    else if (!agente.ultimaConexion) { insignia.className = 'badge bg-warning text-dark'; insignia.textContent = 'Nunca conectó'; }
    else { insignia.className = 'badge bg-danger'; insignia.textContent = 'Sin conexión'; }
    const celda = document.createElement('td');
    celda.appendChild(insignia);
    return celda;
}

function cargarOpcionesDeSucursal(selector, sucursales) {
    const opcionTodas = new Option('Todas las de la empresa', '');
    selector.replaceChildren(opcionTodas, ...sucursales.map(sucursal => new Option(sucursal.nombre, sucursal.id)));
}

function mostrarClaveUnaSolaVez(clave) {
    document.getElementById('claveAgenteValor').value = clave;
    document.getElementById('claveAgenteBox').classList.remove('d-none');
}

async function copiarClaveAlPortapapeles(mostrarAviso) {
    const campoDeLaClave = document.getElementById('claveAgenteValor');
    try {
        await navigator.clipboard.writeText(campoDeLaClave.value);
        mostrarAviso('Clave copiada al portapapeles.');
    } catch (error) {
        // Navegadores sin permiso de portapapeles: se selecciona el texto para copiar a mano
        console.error(`Error en agentesUI.js -> copiarClaveAlPortapapeles: ${error.message}`);
        campoDeLaClave.select();
        mostrarAviso('Seleccioné la clave: copiala con Ctrl+C.');
    }
}

function crearCelda(texto, clases) {
    const celda = document.createElement('td');
    if (clases) celda.className = clases;
    celda.textContent = texto ?? '';
    return celda;
}

function crearFilaDeMensaje(texto, clases) {
    const fila = document.createElement('tr');
    const celda = crearCelda(texto, clases);
    celda.colSpan = 6;
    fila.appendChild(celda);
    return fila;
}

function crearBoton(texto, clase, alHacerClic) {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = `btn btn-sm ${clase} ms-1`;
    boton.textContent = texto;
    boton.addEventListener('click', alHacerClic);
    return boton;
}
