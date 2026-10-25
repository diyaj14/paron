package org.paron.auth;

import io.jsonwebtoken.Jwts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/** Explicitly configured demo login; disabled unless all three credentials exist. */
@RestController
@RequestMapping("/api/v1/auth/demo")
public class DemoSessionController {
    private final PrivateKey privateKey;
    private final String issuer, audience, username, password;
    private final long ttlMinutes;

    public DemoSessionController(@Value("${auth.private-key}") String pem,
                                 @Value("${auth.issuer}") String issuer,
                                 @Value("${auth.audience}") String audience,
                                 @Value("${auth.demo.username}") String username,
                                 @Value("${auth.demo.password}") String password,
                                 @Value("${auth.demo.token-ttl-minutes}") long ttlMinutes) {
        this.privateKey = pem == null || pem.isBlank() ? null : readPrivateKey(pem);
        this.issuer = issuer; this.audience = audience; this.username = username; this.password = password; this.ttlMinutes = ttlMinutes;
    }

    @PostMapping("/session")
    public ResponseEntity<?> session(@Valid @RequestBody LoginRequest request) {
        if (privateKey == null || username.isBlank() || password.isBlank()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        if (!username.equals(request.username()) || !password.equals(request.password())) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Instant expiry = Instant.now().plusSeconds(ttlMinutes * 60);
        String token = Jwts.builder().issuer(issuer).audience().add(audience).and().subject(request.username())
                .claim("role", "DEMO_CUSTOMER").issuedAt(new Date()).expiration(Date.from(expiry)).signWith(privateKey, Jwts.SIG.RS256).compact();
        return ResponseEntity.ok(new SessionResponse(token, "Bearer", ttlMinutes * 60, request.username()));
    }

    private static PrivateKey readPrivateKey(String value) {
        try {
            String base64 = value.replace("\\n", "\n").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) { throw new IllegalArgumentException("Invalid SESSION_JWT_PRIVATE_KEY", e); }
    }
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record SessionResponse(String accessToken, String tokenType, long expiresIn, String userId) {}
}
