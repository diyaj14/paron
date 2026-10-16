package org.paron.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class GatewayConfig {

    @Bean
    public PublicKey jwtPublicKey(@Value("${gateway.jwt.public-key:}") String publicKeyPem) {
        return loadPublicKey(publicKeyPem);
    }

    private PublicKey loadPublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            String base64 = pem
                    .replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid RSA public key in gateway.jwt.public-key", e);
        }
    }
}
