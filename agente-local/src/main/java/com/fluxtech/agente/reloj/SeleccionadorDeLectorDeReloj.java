package com.fluxtech.agente.reloj;

import com.fluxtech.agente.configuracion.ConfiguracionLocal;
import com.fluxtech.agente.configuracion.ConfiguracionLocal.ModoDeLecturaHikvision;
import com.fluxtech.agente.modelo.ConfiguracionDeReloj;
import com.fluxtech.agente.reloj.hikvision.LectorHikvisionIsapi;
import com.fluxtech.agente.reloj.hikvision.sdk.UbicacionDelSdkHikvision;
import com.fluxtech.agente.reloj.hikvision.sdk.LectorHikvisionSdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decide con qué método se lee cada reloj y en qué orden se prueban.
 *
 * Regla de negocio (hikvision.modo en agente.properties):
 *   - isapi: solo ISAPI.
 *   - sdk: solo el SDK oficial.
 *   - automatico (por defecto): primero ISAPI, que no requiere instalar nada; si falla y el SDK está
 *     instalado, se prueba el SDK. El método que funcionó se recuerda para ese reloj y se usa primero
 *     en los ciclos siguientes, así no se pierde tiempo probando algo que ya se sabe que no anda.
 *
 * Recorrido del dato:
 *   LecturaDeRelojes pide obtenerLectoresEnOrdenDePrueba(reloj) → recibe la lista de lectores.
 *   Cuando uno funciona llama a recordarMetodoQueFunciono(); así el próximo ciclo empieza por ese.
 */
public final class SeleccionadorDeLectorDeReloj {

    private final ConfiguracionLocal configuracionLocal;
    /** idDelReloj → nombre del método que funcionó la última vez ("ISAPI" o "SDK"). */
    private final Map<Long, String> metodoQueFuncionoPorReloj = new ConcurrentHashMap<>();

    public SeleccionadorDeLectorDeReloj(ConfiguracionLocal configuracionLocal) {
        this.configuracionLocal = configuracionLocal;
    }

    /** Lectores a probar para este reloj, en orden. Lista vacía si la marca no está soportada. */
    public List<LectorDeReloj> obtenerLectoresEnOrdenDePrueba(ConfiguracionDeReloj reloj) {
        List<LectorDeReloj> lectoresEnOrden = new ArrayList<>();
        if (!reloj.esMarcaHikvision()) {
            return lectoresEnOrden;
        }
        ModoDeLecturaHikvision modo = configuracionLocal.obtenerModoDeLecturaHikvision();
        boolean sdkInstalado = UbicacionDelSdkHikvision.estaInstaladoEn(configuracionLocal.obtenerCarpetaDelSdkHikvision());

        if (modo == ModoDeLecturaHikvision.ISAPI) {
            lectoresEnOrden.add(crearLectorIsapi(reloj));
        } else if (modo == ModoDeLecturaHikvision.SDK) {
            lectoresEnOrden.add(crearLectorSdk(reloj));
        } else if ("SDK".equals(metodoQueFuncionoPorReloj.get(reloj.obtenerIdDelReloj()))) {
            // Automático, y la última vez funcionó el SDK: se empieza por él
            lectoresEnOrden.add(crearLectorSdk(reloj));
            lectoresEnOrden.add(crearLectorIsapi(reloj));
        } else {
            // Automático: ISAPI primero; el SDK solo si está instalado
            lectoresEnOrden.add(crearLectorIsapi(reloj));
            if (sdkInstalado) {
                lectoresEnOrden.add(crearLectorSdk(reloj));
            }
        }
        return lectoresEnOrden;
    }

    public void recordarMetodoQueFunciono(long idDelReloj, String nombreDelMetodo) {
        metodoQueFuncionoPorReloj.put(idDelReloj, nombreDelMetodo);
    }

    /** Lector ISAPI para pruebas manuales (diagnóstico), sin depender del panel. */
    public LectorDeReloj crearLectorIsapiDirecto(String direccion, String usuario, String contrasenia) {
        return new LectorHikvisionIsapi(direccion, usuario, contrasenia);
    }

    /** Lector SDK para pruebas manuales (diagnóstico), sin depender del panel. */
    public LectorDeReloj crearLectorSdkDirecto(String direccionIp, String usuario, String contrasenia) {
        return new LectorHikvisionSdk(direccionIp, configuracionLocal.obtenerPuertoDelSdkHikvision(), usuario, contrasenia,
                configuracionLocal.obtenerCarpetaDelSdkHikvision());
    }

    private LectorDeReloj crearLectorIsapi(ConfiguracionDeReloj reloj) {
        // ISAPI usa la dirección tal cual está en el panel (puede incluir el puerto HTTP)
        return crearLectorIsapiDirecto(reloj.obtenerDireccionEnLaRedLocal(), reloj.obtenerUsuarioDelReloj(),
                reloj.obtenerContraseniaDelReloj());
    }

    private LectorDeReloj crearLectorSdk(ConfiguracionDeReloj reloj) {
        // El SDK usa solo la IP; el puerto es el del SDK (8000 por defecto)
        return crearLectorSdkDirecto(reloj.obtenerIpSinPuerto(), reloj.obtenerUsuarioDelReloj(), reloj.obtenerContraseniaDelReloj());
    }
}
