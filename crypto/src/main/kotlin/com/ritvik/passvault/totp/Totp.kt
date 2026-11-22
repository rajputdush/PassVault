package com.ritvik.passvault.totp

import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class TotpAlgorithm(val macName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512"),
}

/** Everything needed to produce codes for one account. */
class TotpConfig(
    val secret: ByteArray,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    /** The service name from the QR code, for example "GitHub". Empty for a bare secret. */
    val issuer: String = "",
    val account: String = "",
)

/** Time-based one-time passwords (RFC 6238), the 6-digit codes authenticator apps show. */
object Totp {
    private const val MIN_SECRET_BYTES = 10

    /**
     * Reads either an `otpauth://totp/...` link (what a QR code contains) or a bare Base32 secret.
     * Returns null for anything else, including counter-based (hotp) links and secrets that are too short.
     */
    fun parse(input: String): TotpConfig? {
        val text = input.trim()
        if (text.isEmpty()) return null
        return if (text.startsWith("otpauth://", ignoreCase = true)) parseUri(text) else parseSecret(text)
    }

    fun isValid(input: String): Boolean = parse(input) != null

    /** The code for [timeMs] (milliseconds since 1970), zero-padded to the configured number of digits. */
    fun code(config: TotpConfig, timeMs: Long): String {
        val counter = Math.floorDiv(timeMs / 1000, config.periodSeconds.toLong())
        val message = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            message[i] = (c and 0xFF).toByte()
            c = c shr 8
        }
        val mac = Mac.getInstance(config.algorithm.macName)
        mac.init(SecretKeySpec(config.secret, config.algorithm.macName))
        val hash = mac.doFinal(message)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        var modulus = 1L
        repeat(config.digits) { modulus *= 10 }
        return (binary % modulus).toString().padStart(config.digits, '0')
    }

    /** Seconds until the current code stops working (1..period). */
    fun secondsRemaining(config: TotpConfig, timeMs: Long): Int {
        val period = config.periodSeconds.toLong()
        return (period - (timeMs / 1000) % period).toInt()
    }

    /** Groups a code for easier reading: "123456" becomes "123 456". */
    fun format(code: String): String = if (code.length % 2 == 0 && code.length <= 8) {
        val half = code.length / 2
        code.substring(0, half) + " " + code.substring(half)
    } else {
        code
    }

    private fun parseSecret(text: String): TotpConfig? {
        val secret = Base32.decode(text) ?: return null
        if (secret.size < MIN_SECRET_BYTES) return null
        return TotpConfig(secret)
    }

    private fun parseUri(text: String): TotpConfig? {
        val rest = text.substring("otpauth://".length)
        val type = rest.substringBefore('/').lowercase()
        if (type != "totp") return null
        val afterType = rest.substringAfter('/', "")
        val label = decode(afterType.substringBefore('?'), plusIsSpace = false)
        val params = HashMap<String, String>()
        for (pair in afterType.substringAfter('?', "").split('&')) {
            if (pair.isEmpty()) continue
            val key = decode(pair.substringBefore('='), plusIsSpace = true).lowercase()
            val value = decode(pair.substringAfter('=', ""), plusIsSpace = true)
            if (key !in params) params[key] = value
        }

        val secret = Base32.decode(params["secret"] ?: return null) ?: return null
        if (secret.size < MIN_SECRET_BYTES) return null

        val algorithm = when ((params["algorithm"] ?: "SHA1").uppercase()) {
            "SHA1" -> TotpAlgorithm.SHA1
            "SHA256" -> TotpAlgorithm.SHA256
            "SHA512" -> TotpAlgorithm.SHA512
            else -> return null
        }
        val digits = params["digits"]?.toIntOrNull() ?: 6
        if (digits !in 6..8) return null
        val period = params["period"]?.toIntOrNull() ?: 30
        if (period !in 1..300) return null

        val labelIssuer = label.substringBefore(':', "").trim()
        val account = label.substringAfter(':', label).trim()
        val issuer = (params["issuer"] ?: labelIssuer).trim()
        return TotpConfig(secret, algorithm, digits, period, issuer, account)
    }

    private fun decode(s: String, plusIsSpace: Boolean): String = try {
        URLDecoder.decode(if (plusIsSpace) s else s.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        s
    }
}
