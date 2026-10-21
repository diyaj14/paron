package org.paron.syncservice.signature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.OfflineTransactionDto;
import org.paron.syncservice.model.OfflineTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/*
 * Signed-receipt verification (Step 0b).
 *
 * The customer wallet signs a deterministic canonical string with an ECDSA
 * P-256 key. This component:
 *  1. rebuilds the same canonical string from the received fields,
 *  2. imports the shipped JWK public key (JWK x/y -> java EC key),
 *  3. converts the WebCrypto raw r||s signature to the DER/ASN.1 encoding
 *     Java expects,
 *  4. verifies.
 *
 * If this ever returns false, sync-service REFUSES the transaction before
 * it enters the settlement pipeline — forgery and tampering stop here.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SignatureVerifier {

    private final ObjectMapper objectMapper;

    /*
     * Must match canonicalString() in pwa/public/src/app.js EXACTLY:
     * the six base fields joined with NUL, amount normalized to the
     * shortest plain decimal (150.00 -> "150", 150.50 -> "150.5").
     */
    public String canonicalString(OfflineTransactionDto txn) {
        return canonical(
                txn.getDeviceTransactionId(),
                txn.getOfflineToken(),
                txn.getMerchantId(),
                txn.getAmount(),
                txn.getTransactedAt() != null ? txn.getTransactedAt().toString() : null,
                txn.getDeviceId());
    }

    public String canonicalString(OfflineTransaction txn) {
        return canonical(
                txn.getDeviceTransactionId(),
                txn.getOfflineToken(),
                txn.getMerchantId(),
                txn.getAmount(),
                txn.getTransactedAt() != null ? txn.getTransactedAt().toString() : null,
                txn.getDeviceId());
    }

    private String canonical(String deviceTransactionId, String offlineToken, String merchantId,
                             BigDecimal amount, String transactedAt, String deviceId) {
        return String.join("\u0000",
                nullable(deviceTransactionId),
                nullable(offlineToken),
                nullable(merchantId),
                normalizedAmount(amount),
                nullable(transactedAt),
                nullable(deviceId));
    }

    public boolean isValid(OfflineTransactionDto txn) {
        return verify(canonicalString(txn), txn.getSignature(), txn.getPublicKey());
    }

    /*
     * Re-verify a stored transaction using its persisted signature + public
     * key — this is the cryptographic evidence the dispute arbiter re-checks
     * when two receipts contradict each other. A forged/tampered row that
     * somehow made it into the DB will fail here.
     */
    public boolean isValid(OfflineTransaction txn) {
        return verify(canonicalString(txn), txn.getSignature(), txn.getPublicKey());
    }

    private boolean verify(String canonical, String signature, String publicKey) {
        if (signature == null || publicKey == null) {
            return false;
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(signature);
            if (raw.length != 64) {
                // P-256 => r and s, 32 bytes each
                return false;
            }
            PublicKey key = parsePublicKey(publicKey);
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(toDerSignature(raw));
        } catch (Exception e) {
            log.warn("Signature verification failed. error={}", e.getMessage());
            return false;
        }
    }

    private PublicKey parsePublicKey(String jwkJson) throws Exception {
        JsonNode jwk = objectMapper.readTree(jwkJson);
        byte[] x = Base64.getUrlDecoder().decode(jwk.get("x").asText());
        byte[] y = Base64.getUrlDecoder().decode(jwk.get("y").asText());
        ECPoint w = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(w, ecSpec);
        return KeyFactory.getInstance("EC").generatePublic(keySpec);
    }

    /* WebCrypto emits raw r||s (IEEE P1363); Java wants DER/ASN.1. */
    private byte[] toDerSignature(byte[] raw) {
        byte[] r = Arrays.copyOfRange(raw, 0, 32);
        byte[] s = Arrays.copyOfRange(raw, 32, 64);
        return encodeSequence(encodeInteger(r), encodeInteger(s));
    }

    private byte[] encodeInteger(byte[] value) {
        int first = 0;
        while (first < value.length - 1 && value[first] == 0x00) {
            first++;
        }
        byte[] trimmed = Arrays.copyOfRange(value, first, value.length);
        boolean needPad = (trimmed[0] & 0x80) != 0;
        byte[] content = new byte[trimmed.length + (needPad ? 1 : 0)];
        int off = 0;
        if (needPad) {
            content[0] = 0x00;
            off = 1;
        }
        System.arraycopy(trimmed, 0, content, off, trimmed.length);
        byte[] out = new byte[2 + content.length];
        out[0] = 0x02;
        out[1] = (byte) content.length;
        System.arraycopy(content, 0, out, 2, content.length);
        return out;
    }

    private byte[] encodeSequence(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[2 + total];
        out[0] = 0x30;
        out[1] = (byte) total;
        int off = 2;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    private String nullable(String value) {
        return value != null ? value : "";
    }

    private String normalizedAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.stripTrailingZeros().toPlainString();
    }
}