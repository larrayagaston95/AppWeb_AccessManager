package com.atomg.accessmanager.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno simple contra fuerza bruta en el login: tras MAX_FALLOS intentos fallidos para el mismo
 * usuario dentro de VENTANA_SEGUNDOS, ese usuario queda bloqueado hasta que pase la ventana.
 * No se limita por IP a propósito: detrás de un proxy (nginx) todos los pedidos llegan con la
 * misma IP y se bloquearía a todos los clientes a la vez.
 * En memoria: se reinicia al reiniciar la app y no se comparte entre instancias.
 */
@Component
public class LoginAttemptService {

    private static final int MAX_FALLOS = 5;
    private static final long VENTANA_SEGUNDOS = 15 * 60;

    private record Registro(int fallos, Instant desde) {}

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    public boolean bloqueado(String username, String ip) {
        return superaLimite("u:" + normalizar(username));
    }

    public void registrarFallo(String username, String ip) {
        sumar("u:" + normalizar(username));
    }

    public void registrarExito(String username, String ip) {
        registros.remove("u:" + normalizar(username));
    }

    private boolean superaLimite(String clave) {
        Registro r = registros.get(clave);
        if (r == null) return false;
        if (vencido(r)) {
            registros.remove(clave);
            return false;
        }
        return r.fallos() >= MAX_FALLOS;
    }

    private void sumar(String clave) {
        registros.compute(clave, (k, r) ->
                (r == null || vencido(r)) ? new Registro(1, Instant.now()) : new Registro(r.fallos() + 1, r.desde()));
        if (registros.size() > 10_000) {
            registros.entrySet().removeIf(e -> vencido(e.getValue()));
        }
    }

    private boolean vencido(Registro r) {
        return r.desde().plusSeconds(VENTANA_SEGUNDOS).isBefore(Instant.now());
    }

    private String normalizar(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
