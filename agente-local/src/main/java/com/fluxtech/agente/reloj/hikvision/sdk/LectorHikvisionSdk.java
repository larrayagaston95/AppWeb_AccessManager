package com.fluxtech.agente.reloj.hikvision.sdk;

import com.fluxtech.agente.modelo.EventoDeReloj;
import com.fluxtech.agente.modelo.InformacionDelEquipo;
import com.fluxtech.agente.registro.RegistroDeEventos;
import com.fluxtech.agente.reloj.LectorDeReloj;
import com.fluxtech.agente.reloj.MetodoDeLecturaNoSoportadoException;
import com.fluxtech.agente.reloj.hikvision.CodigosDeEventoHikvision;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee un reloj Hikvision con el SDK oficial (protocolo propio, puerto 8000), igual que iVMS-4200.
 * Se usa cuando el reloj no permite leer su memoria por ISAPI.
 * Dependencias de terceros: HCNetSDK y JNA (justificación en BibliotecaHcNetSdk.java).
 *
 * Recorrido del dato:
 *   1. obtenerInformacionDelEquipo(): abre sesión (SesionDelSdkHikvision) y devuelve el número de serie.
 *   2. leerEventosEntre(): NET_DVR_StartRemoteConfig con la condición de búsqueda (rango de fechas);
 *      después NET_DVR_GetNextRemoteConfig en bucle hasta que el SDK informa "búsqueda terminada".
 *      Cada resultado se convierte con ConversorDeEventosSdk en un EventoDeReloj.
 *   3. cerrarConexion(): cierra la sesión.
 */
public final class LectorHikvisionSdk implements LectorDeReloj {

    private static final String NOMBRE_DEL_ARCHIVO = "LectorHikvisionSdk.java";
    /** Tiempo máximo esperando al reloj durante una búsqueda antes de abandonarla. */
    private static final long MILISEGUNDOS_MAXIMOS_DE_ESPERA = 60_000;
    private static final long MILISEGUNDOS_ENTRE_CONSULTAS = 20;

    private final String direccionIp;
    private final int puertoDelSdk;
    private final String usuario;
    private final String contrasenia;
    private final Path carpetaDelSdk;
    private SesionDelSdkHikvision sesion;

    public LectorHikvisionSdk(String direccionIp, int puertoDelSdk, String usuario, String contrasenia, Path carpetaDelSdk) {
        this.direccionIp = direccionIp;
        this.puertoDelSdk = puertoDelSdk;
        this.usuario = usuario;
        this.contrasenia = contrasenia;
        this.carpetaDelSdk = carpetaDelSdk;
    }

    @Override
    public String obtenerNombreDelMetodo() {
        return "SDK";
    }

    @Override
    public InformacionDelEquipo obtenerInformacionDelEquipo() throws IOException {
        try {
            SesionDelSdkHikvision sesionAbierta = abrirSesionSiHaceFalta();
            return new InformacionDelEquipo("(informado por SDK)", sesionAbierta.obtenerNumeroDeSerieDelEquipo(), "");
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "obtenerInformacionDelEquipo", RegistroDeEventos.describirFallo(excepcion));
            throw excepcion;
        }
    }

    @Override
    public List<EventoDeReloj> leerEventosEntre(LocalDateTime desde, LocalDateTime hasta) throws IOException, InterruptedException {
        try {
            return buscarEventos(abrirSesionSiHaceFalta(), desde, hasta);
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "leerEventosEntre", RegistroDeEventos.describirFallo(excepcion));
            throw excepcion;
        }
    }

    @Override
    public void cerrarConexion() {
        if (sesion != null) {
            sesion.cerrar();
        }
    }

    /** Carga el SDK (la primera vez) e inicia sesión con el reloj (si todavía no hay sesión). */
    private SesionDelSdkHikvision abrirSesionSiHaceFalta() throws IOException {
        if (sesion == null) {
            sesion = new SesionDelSdkHikvision(CargadorDelSdkHikvision.obtenerBibliotecaInicializada(carpetaDelSdk));
        }
        sesion.iniciar(direccionIp, puertoDelSdk, usuario, contrasenia);
        return sesion;
    }

    /** Ejecuta la búsqueda remota y junta todos los eventos del rango. */
    private List<EventoDeReloj> buscarEventos(SesionDelSdkHikvision sesionAbierta, LocalDateTime desde, LocalDateTime hasta)
            throws IOException, InterruptedException {
        BibliotecaHcNetSdk biblioteca = CargadorDelSdkHikvision.obtenerBibliotecaInicializada(carpetaDelSdk);

        // Paso 1: condición de búsqueda (eventos de control de acceso en el rango de fechas)
        EstructuraCondicionDeBusquedaSdk condicion = new EstructuraCondicionDeBusquedaSdk();
        condicion.completarRangoDeBusqueda(CodigosDeEventoHikvision.TIPO_PRINCIPAL_EVENTO_DE_ACCESO, desde, hasta);

        // Paso 2: iniciar la búsqueda en el reloj
        int identificadorDeBusqueda = biblioteca.NET_DVR_StartRemoteConfig(sesionAbierta.obtenerIdentificadorDeSesion(),
                BibliotecaHcNetSdk.COMANDO_BUSCAR_EVENTOS_DE_ACCESO, condicion.getPointer(), condicion.size(), null, null);
        if (identificadorDeBusqueda < 0) {
            int codigoDeError = biblioteca.NET_DVR_GetLastError();
            String descripcion = "no se pudo iniciar la búsqueda de eventos: " + CodigosDeErrorDelSdk.describir(codigoDeError);
            if (codigoDeError == CodigosDeErrorDelSdk.FUNCION_NO_SOPORTADA_POR_EL_EQUIPO) {
                throw new MetodoDeLecturaNoSoportadoException(descripcion);
            }
            throw new IOException(descripcion);
        }

        // Paso 3: traer los resultados uno por uno y cerrar la búsqueda pase lo que pase
        try {
            return recorrerResultados(biblioteca, identificadorDeBusqueda);
        } finally {
            biblioteca.NET_DVR_StopRemoteConfig(identificadorDeBusqueda);
        }
    }

    /** Bucle de NET_DVR_GetNextRemoteConfig: obtenido → convertir; esperar → reintentar; terminado → salir. */
    private List<EventoDeReloj> recorrerResultados(BibliotecaHcNetSdk biblioteca, int identificadorDeBusqueda)
            throws IOException, InterruptedException {
        List<EventoDeReloj> eventosLeidos = new ArrayList<>();
        EstructurasDelEventoDeAccesoSdk.EventoDeAccesoSdk resultado = new EstructurasDelEventoDeAccesoSdk.EventoDeAccesoSdk();
        long momentoDeLaUltimaRespuesta = System.currentTimeMillis();

        while (true) {
            resultado.prepararParaRecibir();
            int estado = biblioteca.NET_DVR_GetNextRemoteConfig(identificadorDeBusqueda, resultado.getPointer(), resultado.size());

            if (estado == BibliotecaHcNetSdk.ESTADO_SIGUIENTE_REGISTRO_OBTENIDO) {
                resultado.read(); // copia a Java lo que el SDK escribió en la memoria
                EventoDeReloj evento = ConversorDeEventosSdk.convertir(resultado);
                if (evento != null) {
                    eventosLeidos.add(evento);
                }
                momentoDeLaUltimaRespuesta = System.currentTimeMillis();
            } else if (estado == BibliotecaHcNetSdk.ESTADO_SIGUIENTE_ESPERAR) {
                // El reloj todavía está preparando datos: se espera un poco, con un límite
                if (System.currentTimeMillis() - momentoDeLaUltimaRespuesta > MILISEGUNDOS_MAXIMOS_DE_ESPERA) {
                    throw new IOException("el reloj dejó de responder durante la búsqueda de eventos");
                }
                Thread.sleep(MILISEGUNDOS_ENTRE_CONSULTAS);
            } else if (estado == BibliotecaHcNetSdk.ESTADO_SIGUIENTE_BUSQUEDA_TERMINADA) {
                return eventosLeidos;
            } else {
                throw new IOException("la búsqueda de eventos falló: "
                        + CodigosDeErrorDelSdk.describir(biblioteca.NET_DVR_GetLastError()) + " (estado " + estado + ")");
            }
        }
    }
}
