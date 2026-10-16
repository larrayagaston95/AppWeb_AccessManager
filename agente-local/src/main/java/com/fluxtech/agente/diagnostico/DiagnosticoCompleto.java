package com.fluxtech.agente.diagnostico;

import com.fluxtech.agente.configuracion.ConfiguracionLocal;
import com.fluxtech.agente.json.AyudaMapasJson;
import com.fluxtech.agente.modelo.ConfiguracionDeReloj;
import com.fluxtech.agente.nube.ClaveDelAgenteRechazadaException;
import com.fluxtech.agente.nube.ServicioNubeFluxTech;
import com.fluxtech.agente.registro.RegistroDeEventos;
import com.fluxtech.agente.sincronizacion.CicloDeSincronizacion;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

/**
 * Diagnóstico de la instalación completa (java -jar agente-fluxtech.jar --probar, o probar.bat).
 *
 * Recorrido del dato:
 *   1. Confirma que agente.properties se leyó (ya validado por ConfiguracionLocal).
 *   2. Pide la configuración a la nube: valida conexión y clave, y trae la lista de relojes.
 *   3. Prueba cada reloj Hikvision con DiagnosticoDeReloj (ISAPI y/o SDK).
 *   4. Verifica que el puerto del receptor esté libre.
 *   Devuelve 0 si todo está bien y 1 si hay algo para corregir (instalar.ps1 usa este valor).
 */
public final class DiagnosticoCompleto {

    private DiagnosticoCompleto() {
        // Clase de utilidades: no se instancia
    }

    @SuppressWarnings("unchecked")
    public static int ejecutar(ConfiguracionLocal configuracion) throws InterruptedException {
        boolean todoCorrecto = true;
        System.out.println("== Diagnóstico del agente FluxTech " + CicloDeSincronizacion.VERSION_DEL_AGENTE + " ==");
        System.out.println("[OK]    agente.properties leído. Servidor: " + configuracion.obtenerDireccionDelServidor()
                + " | modo Hikvision: " + configuracion.obtenerModoDeLecturaHikvision());

        // Paso 2: conexión con la nube
        Map<String, Object> configuracionDeLaNube = null;
        try {
            configuracionDeLaNube = new ServicioNubeFluxTech(configuracion.obtenerDireccionDelServidor(),
                    configuracion.obtenerClaveDelAgente()).descargarConfiguracion();
            System.out.println("[OK]    Conexión con la nube y clave válidas. Agente: "
                    + AyudaMapasJson.obtenerTexto(configuracionDeLaNube, "nombre"));
        } catch (ClaveDelAgenteRechazadaException claveRechazada) {
            System.out.println("[FALLA] " + claveRechazada.getMessage());
            todoCorrecto = false;
        } catch (IOException | RuntimeException excepcion) {
            System.out.println("[FALLA] No se pudo conectar con la nube: " + RegistroDeEventos.describirFallo(excepcion));
            todoCorrecto = false;
        }

        // Paso 3: cada reloj
        if (configuracionDeLaNube != null) {
            Object relojes = configuracionDeLaNube.get("relojes");
            List<Object> listaDeRelojes = relojes instanceof List ? (List<Object>) relojes : List.of();
            if (listaDeRelojes.isEmpty()) {
                System.out.println("[AVISO] La nube no tiene relojes para este agente (cargarlos en el panel, misma empresa/sucursal).");
            }
            for (Object elemento : listaDeRelojes) {
                todoCorrecto &= probarRelojDelPanel(ConfiguracionDeReloj.crearDesdeMapaDeLaNube((Map<String, Object>) elemento), configuracion);
            }
        }

        // Paso 4: puerto del receptor
        System.out.println();
        if (configuracion.estaHabilitadoElReceptor()) {
            verificarPuertoDelReceptor(configuracion.obtenerPuertoDelReceptor());
        }
        System.out.println();
        System.out.println(todoCorrecto ? "Resultado: TODO OK" : "Resultado: hay problemas (ver [FALLA] arriba)");
        return todoCorrecto ? 0 : 1;
    }

    private static boolean probarRelojDelPanel(ConfiguracionDeReloj reloj, ConfiguracionLocal configuracion) throws InterruptedException {
        System.out.println();
        System.out.println("== Reloj '" + reloj.obtenerNombreParaMostrar() + "' (" + reloj.obtenerMarca()
                + ", serie " + reloj.obtenerNumeroDeSerieEnElPanel() + ") ==");
        if (!reloj.esMarcaHikvision()) {
            System.out.println("[AVISO] Marca todavía no soportada por el agente.");
            return true;
        }
        if (!reloj.tieneDatosDeConexionCompletos()) {
            System.out.println("[FALLA] Faltan IP, usuario o contraseña del reloj en el panel.");
            return false;
        }
        boolean probarSoloSdk = configuracion.obtenerModoDeLecturaHikvision() == ConfiguracionLocal.ModoDeLecturaHikvision.SDK;
        String metodo = DiagnosticoDeReloj.probarTodosLosMetodos(reloj.obtenerDireccionEnLaRedLocal(), reloj.obtenerUsuarioDelReloj(),
                reloj.obtenerContraseniaDelReloj(), configuracion.obtenerCarpetaDelSdkHikvision(),
                configuracion.obtenerPuertoDelSdkHikvision(), reloj.obtenerNumeroDeSerieEnElPanel(), probarSoloSdk);
        return metodo != null;
    }

    private static void verificarPuertoDelReceptor(int puerto) {
        try (ServerSocket socketDePrueba = new ServerSocket(puerto)) {
            socketDePrueba.setReuseAddress(true);
            System.out.println("[OK]    Puerto " + puerto + " libre para el receptor.");
        } catch (IOException puertoOcupado) {
            System.out.println("[AVISO] El puerto " + puerto + " está ocupado (si el agente ya está corriendo, es normal).");
        }
    }
}
