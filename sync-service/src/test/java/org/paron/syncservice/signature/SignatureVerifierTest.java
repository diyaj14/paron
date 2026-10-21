package org.paron.syncservice.signature;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.paron.syncservice.dto.OfflineTransactionDto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Round-trips a real ECDSA P-256 signature through the exact encoding
 * the browser wallet uses (JWK public key + base64url raw r||s signature)
 * and confirms SignatureVerifier accepts the genuine one and rejects a
 * tampered one.
 */
class SignatureVerifierTest {

    private final SignatureVerifier verifier = new SignatureVerifier(new ObjectMapper());

    private OfflineTransactionDto sample() {
        OfflineTransactionDto dto = new OfflineTransactionDto();
        dto.setDeviceTransactionId("txn-0001");
        dto.setOfflineToken("header.payload.sig");
        dto.setUserId("user-1");
        dto.setAmount(new BigDecimal("150.00"));
        dto.setMerchantId("merchant-demo-001");
        dto.setTransactedAt(java.time.LocalDateTime.of(2026, 8, 30, 12, 0));
        dto.setDeviceId("dev-abc-123");
        return dto;
    }

    @Test
    void validSignature_shouldVerify() throws Exception {
        OfflineTransactionDto dto = sample();
        String canonical = verifier.canonicalString(dto);

        KeyPair pair = keyPair();
        byte[] derSignature = sign(canonical, pair);
        byte[] raw = derToRaw(derSignature);

        dto.setSignature(Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        dto.setPublicKey(publicKeyJwk(pair));

        assertThat(verifier.isValid(dto)).isTrue();
    }

    @Test
    void tamperedAmount_shouldFailVerification() throws Exception {
        OfflineTransactionDto dto = sample();
        String canonical = verifier.canonicalString(dto);

        KeyPair pair = keyPair();
        byte[] derSignature = sign(canonical, pair);
        byte[] raw = derToRaw(derSignature);

        dto.setSignature(Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        dto.setPublicKey(publicKeyJwk(pair));

        // Attacker bumps the amount AFTER it was signed — amount is now part
        // of the canonical string, so the signature no longer matches.
        dto.setAmount(new BigDecimal("1500.00"));
        assertThat(verifier.isValid(dto)).isFalse();
    }

    @Test
    void wrongKey_shouldFailVerification() throws Exception {
        OfflineTransactionDto dto = sample();
        String canonical = verifier.canonicalString(dto);

        KeyPair signerKey = keyPair();
        KeyPair differentKey = keyPair();
        byte[] derSignature = sign(canonical, signerKey);
        byte[] raw = derToRaw(derSignature);

        dto.setSignature(Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        dto.setPublicKey(publicKeyJwk(differentKey));

        assertThat(verifier.isValid(dto)).isFalse();
    }

    @Test
    void missingSignature_shouldFail() {
        OfflineTransactionDto dto = sample();
        assertThat(verifier.isValid(dto)).isFalse();
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    private byte[] sign(String text, KeyPair pair) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(pair.getPrivate());
        signature.update(text.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    /* DER -> raw r||s: parse 0x30 [len] 0x02 [rLen] r 0x02 [sLen] s, strip 0x00 pads. */
    private byte[] derToRaw(byte[] der) {
        int i = 0;
        i += 1;                    // 0x30
        i += 1;                    // sequence length
        i += 1;                    // 0x02
        int rLen = der[i++];
        byte[] r = stripLeadingZero(decodeInteger(der, i, rLen));
        i += rLen;
        i += 1;                    // 0x02
        int sLen = der[i++];
        byte[] s = stripLeadingZero(decodeInteger(der, i, sLen));
        byte[] out = new byte[64];
        System.arraycopy(r, 0, out, 32 - r.length, r.length);
        System.arraycopy(s, 0, out, 64 - s.length, s.length);
        return out;
    }

    private byte[] decodeInteger(byte[] der, int offset, int len) {
        byte[] value = new byte[len];
        System.arraycopy(der, offset, value, 0, len);
        return value;
    }

    private byte[] stripLeadingZero(byte[] value) {
        int first = 0;
        while (first < value.length - 1 && value[first] == 0x00) {
            first++;
        }
        byte[] out = new byte[value.length - first];
        System.arraycopy(value, first, out, 0, out.length);
        return out;
    }

    /* Java EC public key -> Browser-style JWK (x, y as base64url, 32 bytes each). */
    private String publicKeyJwk(KeyPair pair) {
        ECPublicKey pub = (ECPublicKey) pair.getPublic();
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(toFixed32(pub.getW().getAffineX())));
        jwk.put("y", Base64.getUrlEncoder().withoutPadding().encodeToString(toFixed32(pub.getW().getAffineY())));
        return mapToJson(jwk);
    }

    private byte[] toFixed32(java.math.BigInteger value) {
        byte[] raw = value.toByteArray();
        int first = (raw.length > 32 && raw[0] == 0x00) ? 1 : 0;
        int len = raw.length - first;
        byte[] out = new byte[32];
        System.arraycopy(raw, first, out, 32 - len, len);
        return out;
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        return json.append("}").toString();
    }
}