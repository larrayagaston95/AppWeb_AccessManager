package com.fluxtech.agente;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pruebas rápidas para la instalación:
 *   --probar                          → configuración, nube, cada reloj del panel y puerto del receptor
 *   --probar-reloj IP USUARIO CLAVE   → solo un reloj, sin nube (sirve antes de crear el agente)
 */
final class Diagnostico {

    private Diagnostico() {}

    static int probarReloj(String ip, String usuario, String password) throws InterruptedException {
        System.out.println("== Probando reloj Hikvision en " + ip + " ==");
        return probarHikvision(ip, usuario, password, null) ? 0 : 1;
    }

    static int probarTodo(Config config) throws InterruptedException {
        boolean todoOk = true;
        System.out.println("== Diagnóstico del agente FluxTech " + Agente.VERSION + " ==");
        System.out.println("[OK] agente.properties leído. Servidor: " + config.servidorUrl);

        Nube nube = new Nube(config.servidorUrl, config.clave);
        Map<String, Object> cfg = null;
        try {
            cfg = nube.config();
            System.out.println("[OK] Conexión con la nube y clave válidas. Agente: " + Json.texto(cfg, "nombre"));
        } catch (Nube.ClaveInvalidaException e) {
            System.out.println("[FALLA] " + Agente.mensajeCorto(e));
            todoOk = false;
        } catch (IOException | RuntimeException e) {
            System.out.println("[FALLA] No se pudo conectar con la nube: " + Agente.mensajeCorto(e));
            todoOk = false;
        }

        if (cfg != null) {
            Object lista = cfg.get("relojes");
            int cantidad = lista instanceof List ? ((List<?>) lista).size() : 0;
            if (cantidad == 0) {
                System.out.println("[AVISO] La nube no tiene relojes asignados a este agente "
                        + "(cargalos en el panel, en la misma empresa/sucursal del agente).");
            }
            if (lista instanceof List) {
                for (Object o : (List<?>) lista) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> r = (Map<String, Object>) o;
                    System.out.println();
                    System.out.println("== Reloj '" + Json.texto(r, "nombre") + "' (" + Json.texto(r, "marca")
                            + ", serie " + Json.texto(r, "numeroSerie") + ") ==");
                    if (!Json.texto(r, "marca").toLowerCase().contains("hik")) {
                        System.out.println("[AVISO] Marca todavía no soportada por el agente.");
                        continue;
                    }
                    if (Json.texto(r, "ip").isBlank()) {
                        System.out.println("[FALLA] Falta la IP del reloj en el panel.");
                        todoOk = false;
                        continue;
                    }
                    todoOk &= probarHikvision(Json.texto(r, "ip"), Json.texto(r, "usuario"),
                            Json.texto(r, "password"), Json.texto(r, "numeroSerie"));
                }
            }
        }

        System.out.println();
        if (config.receptorHabilitado) {
            try (ServerSocket prueba = new ServerSocket(config.receptorPuerto)) {
                prueba.setReuseAddress(true);
                System.out.println("[OK] Puerto " + config.receptorPuerto + " libre para el receptor.");
            } catch (IOException e) {
                System.out.println("[AVISO] El puerto " + config.receptorPuerto + " está ocupado "
                        + "(si el agente ya está corriendo es normal).");
            }
        }

        System.out.println();
        System.out.println(todoOk ? "Resultado: TODO OK" : "Resultado: hay problemas (ver [FALLA] arriba)");
        return todoOk ? 0 : 1;
    }

    private static boolean probarHikvision(String ip, String usuario, String password, String serieEsperada)
            throws InterruptedException {
        HikvisionIsapi isapi = new HikvisionIsapi(ip, usuario, password);
        try {
            HikvisionIsapi.InfoEquipo info = isapi.info();
            System.out.println("[OK] Responde: modelo " + info.modelo + ", serie " + info.serie + ", firmware " + info.firmware);
            if (serieEsperada != null && !Agente.coincideSerie(serieEsperada, info.serie)) {
                System.out.println("[FALLA] El número de serie no coincide con el del panel (" + serieEsperada
                        + "). ¿La IP apunta a otro equipo?");
                return false;
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[FALLA] No se pudo conectar o autenticar: " + Agente.mensajeCorto(e));
            return false;
        }

        try {
            LocalDateTime ahora = LocalDateTime.now().withNano(0);
            List<HikvisionIsapi.Evento> eventos = isapi.eventos(ahora.minusDays(2), ahora.plusMinutes(1));
            long fichadas = eventos.stream().filter(HikvisionIsapi::esFichada).count();
            System.out.println("[OK] Lectura de memoria (ISAPI) funciona: " + eventos.size()
                    + " evento(s) con legajo en las últimas 48 h, " + fichadas + " se toman como fichada.");
            int mostrados = 0;
            for (int k = eventos.size() - 1; k >= 0 && mostrados < 8; k--, mostrados++) {
                HikvisionIsapi.Evento e = eventos.get(k);
                System.out.println("     legajo " + e.legajo + "  " + e.fechaHora + "  código " + e.minor
                        + (HikvisionIsapi.esFichada(e) ? "  -> fichada" : "  -> rechazo (no se carga)"));
            }
            if (eventos.isEmpty()) {
                System.out.println("     (Fichá una vez en el reloj y volvé a correr la prueba para ver el evento.)");
            }
            return true;
        } catch (IOException | RuntimeException e) {
            System.out.println("[AVISO] No se pudo leer la memoria por ISAPI: " + Agente.mensajeCorto(e));
            System.out.println("        El agente igual puede recibir los eventos si configurás el reloj para");
            System.out.println("        enviarlos a esta PC (modo receptor). Ver README.");
            return false;
        }
    }
}
