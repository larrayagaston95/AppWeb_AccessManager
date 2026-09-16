import { fetchEmpleadosTodos, createEmpleado, updateEmpleado, deleteEmpleado, fetchProximoLegajo } from '../api/empleadoService.js';
import { fetchSucursales, fetchSectoresPorSucursal } from '../api/apiService.js';
import { createLicencia } from '../api/licenciaService.js';

let modalEmpleadoInstancia = null;
let modalLicenciaInstancia = null;
let empleadosCargados = [];

export async function initEmpleadosUI() {
    const btnNuevo = document.getElementById('btnNuevoEmpleado');
    if (btnNuevo && !btnNuevo.dataset.bound) {
        btnNuevo.addEventListener('click', () => abrirModalCrear());
        btnNuevo.dataset.bound = 'true';
    }

    const btnGuardar = document.getElementById('btnGuardarEmpleado');
    if (btnGuardar && !btnGuardar.dataset.bound) {
        btnGuardar.addEventListener('click', () => guardarEmpleado());
        btnGuardar.dataset.bound = 'true';
    }

    // Eventos cascada de selects
    const empSucursal = document.getElementById('empSucursal');
    if (empSucursal && !empSucursal.dataset.bound) {
        empSucursal.addEventListener('change', async (e) => {
            const sucId = e.target.value;
            await cargarSelectSecciones(sucId);
        });
        empSucursal.dataset.bound = 'true';
    }

    modalEmpleadoInstancia = new bootstrap.Modal(document.getElementById('modalEmpleado'));
    modalLicenciaInstancia = new bootstrap.Modal(document.getElementById('modalLicencia'));

    const btnGuardarLicencia = document.getElementById('btnGuardarLicencia');
    if (btnGuardarLicencia && !btnGuardarLicencia.dataset.bound) {
        btnGuardarLicencia.addEventListener('click', () => guardarLicencia());
        btnGuardarLicencia.dataset.bound = 'true';
    }
    await cargarSelectSucursales();
    await recargarTablaEmpleados();
}

async function cargarSelectSucursales() {
    const selectSucursal = document.getElementById('empSucursal');
    if (!selectSucursal) return;
    try {
        const data = await fetchSucursales();
        let html = '<option value="">-- Seleccione --</option>';
        data.forEach(s => html += `<option value="${s.id}">${s.nombre}</option>`);
        selectSucursal.innerHTML = html;
    } catch (e) {
        console.error(e);
    }
}

async function cargarSelectSecciones(sucursalId, selectValue = null) {
    const selectSeccion = document.getElementById('empSeccion');
    if (!selectSeccion) return;
    
    if (!sucursalId) {
        selectSeccion.innerHTML = '<option value="">-- Seleccione Sucursal Primero --</option>';
        return;
    }
    try {
        const data = await fetchSectoresPorSucursal(sucursalId);
        let html = '<option value="">-- Seleccione --</option>';
        data.forEach(s => html += `<option value="${s.id}">${s.nombre}</option>`);
        selectSeccion.innerHTML = html;
        if (selectValue) selectSeccion.value = selectValue;
    } catch (e) {
        console.error(e);
    }
}

export async function recargarTablaEmpleados() {
    const tbody = document.getElementById('tablaCuerpoEmpleados');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">Cargando personal...</td></tr>';
    
    try {
        empleadosCargados = await fetchEmpleadosTodos();
        if (empleadosCargados.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No hay empleados registrados.</td></tr>';
            return;
        }

        let html = '';
        empleadosCargados.forEach(emp => {
            html += `
            <tr>
                <td class="fw-bold text-turquoise">${emp.legajo}</td>
                <td>${emp.nombre}</td>
                <td>${emp.apellido}</td>
                <td><span class="badge bg-secondary shadow-sm">${emp.sectorNombre || ''}</span></td>
                <td class="text-center">${emp.horasJornadaBase} hs</td>
                <td>${emp.telefono || '-'}</td>
                <td class="text-center">
                    <button class="btn btn-sm btn-outline-warning me-2 shadow-sm" title="Licencias" onclick="window.abrirModalLicencia(${emp.id})">
                        <i class="bi bi-briefcase"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-info me-2 shadow-sm" onclick="window.editarEmpleado(${emp.id})">
                        <i class="bi bi-pencil-square"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger shadow-sm" onclick="window.eliminarEmpleado(${emp.id})">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
            `;
        });
        tbody.innerHTML = html;
    } catch (e) {
        console.error(e);
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-danger">Error al cargar empleados.</td></tr>';
    }
}

window.editarEmpleado = async (id) => {
    const emp = empleadosCargados.find(e => e.id === id);
    if (!emp) return;

    document.getElementById('modalEmpleadoTitulo').innerText = 'Editar Empleado';
    document.getElementById('empId').value = emp.id;
    document.getElementById('empNombre').value = emp.nombre;
    document.getElementById('empApellido').value = emp.apellido;
    document.getElementById('empLegajo').value = emp.legajo;
    document.getElementById('empHoras').value = emp.horasJornadaBase;
    document.getElementById('empTelefono').value = emp.telefono || '';
    
    document.getElementById('empSucursal').value = emp.sucursalId;
    await cargarSelectSecciones(emp.sucursalId, emp.sectorId);

    modalEmpleadoInstancia.show();
};

window.eliminarEmpleado = async (id) => {
    if (!confirm('¿Está seguro de eliminar este empleado? Esta acción no se puede deshacer.')) return;
    try {
        await deleteEmpleado(id);
        await recargarTablaEmpleados();
    } catch (e) {
        alert('No se pudo eliminar el empleado. Revise las dependencias.');
    }
};

async function abrirModalCrear() {
    document.getElementById('formEmpleado').reset();
    document.getElementById('modalEmpleadoTitulo').innerText = 'Nuevo Empleado';
    document.getElementById('empId').value = '';
    document.getElementById('empSeccion').innerHTML = '<option value="">-- Seleccione Sucursal Primero --</option>';

    // ── Autocompletar Legajo ──────────────────────────────────────────────────
    const inputLegajo = document.getElementById('empLegajo');
    try {
        const proximo = await fetchProximoLegajo();
        inputLegajo.value = proximo;
        inputLegajo.title = 'Legajo sugerido automáticamente. Puedes modificarlo.';
    } catch (e) {
        console.warn('No se pudo obtener el próximo legajo:', e);
        inputLegajo.value = '';
    }

    modalEmpleadoInstancia.show();
}

async function guardarEmpleado() {
    const id = document.getElementById('empId').value;
    const nombre = document.getElementById('empNombre').value.trim();
    const apellido = document.getElementById('empApellido').value.trim();
    const legajo = document.getElementById('empLegajo').value.trim();
    const horas = document.getElementById('empHoras').value;
    const telefono = document.getElementById('empTelefono').value.trim();
    const sectorId = document.getElementById('empSeccion').value;

    if (!nombre || !apellido || !legajo || !horas || !sectorId) {
        alert('Por favor, complete todos los campos obligatorios (*).');
        return;
    }

    const payload = {
        nombre: nombre,
        apellido: apellido,
        legajoReloj: legajo,
        horasJornadaBase: horas,
        telefono: telefono,
        sectorId: sectorId
    };

    try {
        if (id) {
            await updateEmpleado(id, payload);
        } else {
            await createEmpleado(payload);
        }
        modalEmpleadoInstancia.hide();
        await recargarTablaEmpleados();
    } catch (e) {
        alert('Error al guardar el empleado. Posible legajo duplicado o datos inválidos.');
    }
}

window.abrirModalLicencia = (id) => {
    const emp = empleadosCargados.find(e => e.id === id);
    if (!emp) return;

    document.getElementById('formLicencia').reset();
    document.getElementById('licEmpleadoId').value = emp.id;
    document.getElementById("licEmpleadoNombre").innerText = `Empleado: ${emp.apellido}, ${emp.nombre} (Legajo: ${emp.legajo})`;
    
    modalLicenciaInstancia.show();
};

async function guardarLicencia() {
    const empleadoId = document.getElementById('licEmpleadoId').value;
    const tipo = document.getElementById('licTipo').value;
    const fechaInicio = document.getElementById('licFechaInicio').value;
    const fechaFin = document.getElementById('licFechaFin').value;
    const observaciones = document.getElementById('licObservaciones').value;

    if (!tipo || !fechaInicio || !fechaFin) {
        alert('Complete los campos obligatorios (*).');
        return;
    }

    if (fechaFin < fechaInicio) {
        alert('La fecha de fin no puede ser anterior a la fecha de inicio.');
        return;
    }

    const payload = {
        empleadoId: parseInt(empleadoId),
        tipoLicencia: tipo,
        fechaInicio: fechaInicio,
        fechaFin: fechaFin,
        observaciones: observaciones
    };

    try {
        await createLicencia(payload);
        alert('Licencia registrada con exito.');
        modalLicenciaInstancia.hide();
    } catch (e) {
        alert('Error al registrar la licencia: ' + (e.message || e));
    }
}