package com.offlinepay.crypto

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays

object CryptoManager {
    private const val PREFS_NAME = "offlinepay_crypto_keys"
    private const val KEY_PRIVATE = "ec_private_key"
    private const val KEY_PUBLIC = "ec_public_key"

    @Synchronized
    fun getOrCreateKeyPair(context: Context): KeyPair {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val privStr = prefs.getString(KEY_PRIVATE, null)
        val pubStr = prefs.getString(KEY_PUBLIC, null)

        if (privStr != null && pubStr != null) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val privBytes = Base64.decode(privStr, Base64.DEFAULT)
                val pubBytes = Base64.decode(pubStr, Base64.DEFAULT)
                val privateKey: PrivateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val publicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                return KeyPair(publicKey, privateKey)
            } catch (_: Exception) {
                // Generate a fresh key if parsing fails
            }
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val pair = kpg.generateKeyPair()

        prefs.edit()
            .putString(KEY_PRIVATE, Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
            .putString(KEY_PUBLIC, Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
            .apply()

        return pair
    }

    fun getPublicKeyJwk(context: Context): String {
        val pair = getOrCreateKeyPair(context)
        val pub = pair.public as ECPublicKey
        val x = toFixed32(pub.w.affineX)
        val y = toFixed32(pub.w.affineY)
        val xB64 = Base64.encodeToString(x, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val yB64 = Base64.encodeToString(y, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        return JSONObject().apply {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", xB64)
            put("y", yB64)
        }.toString()
    }

    fun canonicalString(
        deviceTransactionId: String,
        offlineToken: String,
        merchantId: String?,
        amount: String,
        transactedAt: String,
        deviceId: String?
    ): String {
        val normalizedAmount = try {
            BigDecimal(amount).stripTrailingZeros().toPlainString()
        } catch (_: Exception) {
            amount
        }
        return listOf(
            deviceTransactionId,
            offlineToken,
            merchantId ?: "",
            normalizedAmount,
            transactedAt,
            deviceId ?: ""
        ).joinToString("\u0000")
    }

    fun sign(context: Context, canonical: String): String {
        val pair = getOrCreateKeyPair(context)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(pair.private)
        signature.update(canonical.toByteArray(StandardCharsets.UTF_8))
        val der = signature.sign()
        val raw = derToRaw(der)
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun verify(canonical: String, signatureRawBase64Url: String, jwkJson: String): Boolean {
        return try {
            val raw = Base64.decode(signatureRawBase64Url, Base64.URL_SAFE or Base64.NO_PADDING)
            if (raw.size != 64) return false
            val key = parsePublicKeyJwk(jwkJson)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(canonical.toByteArray(StandardCharsets.UTF_8))
            verifier.verify(toDerSignature(raw))
        } catch (_: Exception) {
            false
        }
    }

    private fun parsePublicKeyJwk(jwkJson: String): PublicKey {
        val json = JSONObject(jwkJson)
        val xBytes = Base64.decode(json.getString("x"), Base64.URL_SAFE or Base64.NO_PADDING)
        val yBytes = Base64.decode(json.getString("y"), Base64.URL_SAFE or Base64.NO_PADDING)
        val point = ECPoint(BigInteger(1, xBytes), BigInteger(1, yBytes))
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }
        val ecSpec = params.getParameterSpec(ECParameterSpec::class.java)
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, ecSpec))
    }

    private fun derToRaw(der: ByteArray): ByteArray {
        var i = 2 // Skip 0x30 and seq length
        i++ // 0x02
        val rLen = der[i++].toInt() and 0xFF
        val r = stripLeadingZero(Arrays.copyOfRange(der, i, i + rLen))
        i += rLen
        i++ // 0x02
        val sLen = der[i++].toInt() and 0xFF
        val s = stripLeadingZero(Arrays.copyOfRange(der, i, i + sLen))

        val out = ByteArray(64)
        System.arraycopy(r, 0, out, 32 - r.size, r.size)
        System.arraycopy(s, 0, out, 64 - s.size, s.size)
        return out
    }

    private fun toDerSignature(raw: ByteArray): ByteArray {
        val r = Arrays.copyOfRange(raw, 0, 32)
        val s = Arrays.copyOfRange(raw, 32, 64)
        return encodeSequence(encodeInteger(r), encodeInteger(s))
    }

    private fun encodeInteger(value: ByteArray): ByteArray {
        var first = 0
        while (first < value.size - 1 && value[first] == 0.toByte()) {
            first++
        }
        val trimmed = Arrays.copyOfRange(value, first, value.size)
        val needPad = (trimmed[0].toInt() and 0x80) != 0
        val content = ByteArray(trimmed.size + if (needPad) 1 else 0)
        var off = 0
        if (needPad) {
            content[0] = 0x00
            off = 1
        }
        System.arraycopy(trimmed, 0, content, off, trimmed.size)
        val out = ByteArray(2 + content.size)
        out[0] = 0x02
        out[1] = content.size.toByte()
        System.arraycopy(content, 0, out, 2, content.size)
        return out
    }

    private fun encodeSequence(vararg parts: ByteArray): ByteArray {
        var total = 0
        for (p in parts) total += p.size
        val out = ByteArray(2 + total)
        out[0] = 0x30
        out[1] = total.toByte()
        var off = 2
        for (p in parts) {
            System.arraycopy(p, 0, out, off, p.size)
            off += p.size
        }
        return out
    }

    private fun stripLeadingZero(value: ByteArray): ByteArray {
        var first = 0
        while (first < value.size - 1 && value[first] == 0.toByte()) {
            first++
        }
        return Arrays.copyOfRange(value, first, value.size)
    }

    private fun toFixed32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val first = if (raw.size > 32 && raw[0] == 0.toByte()) 1 else 0
        val len = raw.size - first
        val out = ByteArray(32)
        System.arraycopy(raw, first, out, 32 - len, len)
        return out
    }
}
