package org.paron.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final PublicKey publicKey;
    private final String issuer;
    private final String audience;

    public AuthFilter(ObjectProvider<PublicKey> publicKeyProvider,
                      @Value("${gateway.jwt.issuer}") String issuer,
                      @Value("${gateway.jwt.audience}") String audience) {
        this.publicKey = publicKeyProvider.getIfAvailable();
        this.issuer = issuer;
        this.audience = audience;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isPublicPath(exchange)) {
            return chain.filter(exchange);
        }

        if (publicKey == null) {
            return error(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Gateway public key is not configured");
        }

        String authHeader = exchange.getRequest().getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return error(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            var signed = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token);
            if (!Jwts.SIG.RS256.getId().equals(signed.getHeader().getAlgorithm())) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unexpected JWT algorithm");
            }
            Claims claims = signed.getPayload();

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            ServerWebExchange authenticated = exchange.mutate()
                    .request(builder -> builder.headers(headers -> {
                        headers.remove(HEADER_USER_ID);
                        headers.remove(HEADER_USER_ROLES);
                        headers.set(HEADER_USER_ID, userId);
                        if (role != null) {
                            headers.set(HEADER_USER_ROLES, role);
                        }
                    }))
                    .build();

            return chain.filter(authenticated);
        } catch (JwtException | IllegalArgumentException e) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private boolean isPublicPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.startsWith("/actuator/")
                || path.endsWith("/ping")
                || path.startsWith("/api/v1/auth/");
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":\"" + status.name() + "\",\"message\":\"" + message + "\"}";
        DataBuffer buffer = DefaultDataBufferFactory.sharedInstance
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
