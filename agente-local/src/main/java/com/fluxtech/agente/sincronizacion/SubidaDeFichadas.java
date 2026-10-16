package com.fluxtech.agente.sincronizacion;

import com.fluxtech.agente.cola.ColaDeFichadasPendientes;
import com.fluxtech.agente.modelo.FichadaLeida;
import com.fluxtech.agente.nube.ServicioNubeFluxTech;
import com.fluxtech.agente.registro.RegistroDeEventos;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Sube a la nube las fichadas pendientes, en lotes y en orden cronológico.
 *
 * Recorrido del dato:
 *   1. Se pide a ColaDeFichadasPendientes un lote (hasta 200) de la más vieja a la más nueva.
 *   2. ServicioNubeFluxTech lo envía a POST /api/agente/fichadas.
 *   3. Si la nube responde bien → confirmarSubida() saca el lote de la cola y se sigue con el próximo.
 *   4. Si falla (sin internet, servidor caído, clave revocada) → el lote queda en la cola y se
 *      reintenta en el próximo ciclo. Nada se pierde.
 */
final class SubidaDeFichadas {

    private static final int TAMANIO_DEL_LOTE = 200;

    private final ColaDeFichadasPendientes colaDePendientes;
    private final ServicioNubeFluxTech servicioNube;

    SubidaDeFichadas(ColaDeFichadasPendientes colaDePendientes, ServicioNubeFluxTech servicioNube) {
        this.colaDePendientes = colaDePendientes;
        this.servicioNube = servicioNube;
    }

    /** Sube todo lo pendiente; se detiene en el primer lote que falle. */
    void subirTodasLasPendientes() throws InterruptedException {
        while (colaDePendientes.contarPendientes() > 0) {
            List<FichadaLeida> lote = colaDePendientes.obtenerLoteEnOrdenCronologico(TAMANIO_DEL_LOTE);
            try {
                Map<String, Object> resumenDeLaNube = servicioNube.subirLoteDeFichadas(lote);
                colaDePendientes.confirmarSubida(lote);
                informarResultado(lote.size(), resumenDeLaNube);
            } catch (IOException | RuntimeException excepcion) {
                // El servicio ya registró el error con su formato; acá solo se decide dejar el lote en la cola
                RegistroDeEventos.avisar(lote.size() + " fichada(s) quedan en la cola para el próximo intento");
                return;
            }
        }
    }

    /** Resumen de la nube en el registro: guardadas, duplicadas, descartadas y avisos (legajo inexistente, etc.). */
    private static void informarResultado(int cantidadEnviada, Map<String, Object> resumenDeLaNube) {
        Object avisos = resumenDeLaNube.get("avisos");
        boolean hayAvisos = avisos instanceof List && !((List<?>) avisos).isEmpty();
        RegistroDeEventos.informar("Subidas " + cantidadEnviada + " fichada(s): guardadas=" + resumenDeLaNube.get("guardadas")
                + " duplicadas=" + resumenDeLaNube.get("duplicadas") + " descartadas=" + resumenDeLaNube.get("descartadas")
                + (hayAvisos ? " avisos=" + avisos : ""));
    }
}
