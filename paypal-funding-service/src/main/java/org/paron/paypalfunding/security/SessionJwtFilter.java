package org.paron.paypalfunding.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;

/** Validates session tokens when the browser calls this service directly, outside the gateway. */
@Component
public class SessionJwtFilter extends OncePerRequestFilter {
    private static final String USER_ID_HEADER = "X-User-Id";
    private final PublicKey publicKey;
    private final String issuer;
    private final String audience;

    public SessionJwtFilter(@Value("${session-jwt.public-key:}") String publicKeyPem,
                            @Value("${session-jwt.issuer:paron-demo-auth}") String issuer,
                            @Value("${session-jwt.audience:paron-wallet}") String audience) {
        this.publicKey = parsePublicKey(publicKeyPem);
        this.issuer = issuer;
        this.audience = audience;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/funding/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (publicKey == null || authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response);
            return;
        }

        try {
            var parsed = Jwts.parser().verifyWith(publicKey)
                    .requireIssuer(issuer).requireAudience(audience).build()
                    .parseSignedClaims(authorization.substring("Bearer ".length()));
            if (!Jwts.SIG.RS256.getId().equals(parsed.getHeader().getAlgorithm())) {
                unauthorized(response);
                return;
            }
            Claims claims = parsed.getPayload();
            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                unauthorized(response);
                return;
            }

            // Never trust a caller-supplied X-User-Id; use the verified session subject.
            HttpServletRequest authenticatedRequest = new HttpServletRequestWrapper(request) {
                @Override
                public String getHeader(String name) {
                    return USER_ID_HEADER.equalsIgnoreCase(name) ? userId : super.getHeader(name);
                }

                @Override
                public java.util.Enumeration<String> getHeaders(String name) {
                    return USER_ID_HEADER.equalsIgnoreCase(name)
                            ? Collections.enumeration(Collections.singletonList(userId))
                            : super.getHeaders(name);
                }
            };
            filterChain.doFilter(authenticatedRequest, response);
        } catch (JwtException | IllegalArgumentException exception) {
            unauthorized(response);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        if (pem == null || pem.isBlank()) return null;
        try {
            String base64 = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid RSA JWT public key for PayPal funding service", exception);
        }
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"A valid Paron session is required.\"}");
    }
}
