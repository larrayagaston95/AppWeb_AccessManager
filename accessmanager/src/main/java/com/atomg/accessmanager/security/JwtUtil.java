package com.atomg.accessmanager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final Key secretKey;
    private final long jwtExpirationMs;

    public JwtUtil(@Value("${jwt.secret:}") String secret,
                   @Value("${jwt.expiration-minutes:480}") long expirationMinutes) {
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET no configurado: se usa una clave aleatoria. "
                    + "Las sesiones se invalidan en cada reinicio. Configurar JWT_SECRET en producción.");
            this.secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres.");
            }
            this.secretKey = Keys.hmacShaKeyFor(bytes);
        }
        this.jwtExpirationMs = expirationMinutes * 60_000L;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractEmpresaId(String token) {
        Object empresaIdObj = extractAllClaims(token).get("empresa_id");
        if (empresaIdObj instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    public String extractRol(String token) {
        return (String) extractAllClaims(token).get("rol");
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(String username, Long empresaId, String rol) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("empresa_id", empresaId);
        claims.put("rol", rol);
        return createToken(claims, username);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + jwtExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }
}
