package com.ritvik.passvault.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Kdf {
    const val ARGON2ID = "argon2id"
    const val PBKDF2_SHA256 = "pbkdf2-sha256"
}

/** The exact parameters used for one vault. They are stored in the vault header next to the salt. */
class KdfParams(
    val algorithm: String,
    val salt: ByteArray,
    val iterations: Int,
    /** Argon2 memory cost in KiB. Unused (0) for PBKDF2. */
    val memoryKiB: Int,
    val parallelism: Int,
)

/** The settings used when a *new* vault (or a new master password) is created. */
class KdfSettings private constructor(
    val algorithm: String,
    val iterations: Int,
    val memoryKiB: Int,
    val parallelism: Int,
) {
    fun toParams(salt: ByteArray) = KdfParams(algorithm, salt, iterations, memoryKiB, parallelism)

    companion object {
        /** Default: 64 MiB, 3 passes, 1 lane. Tune so unlocking takes roughly 0.5 to 1 s on a cheap phone. */
        fun argon2id(memoryKiB: Int = 64 * 1024, iterations: Int = 3, parallelism: Int = 1) =
            KdfSettings(Kdf.ARGON2ID, iterations, memoryKiB, parallelism)

        fun pbkdf2(iterations: Int = 600_000) = KdfSettings(Kdf.PBKDF2_SHA256, iterations, 0, 1)
    }
}

/**
 * Lower bounds enforced when reading a header from disk, so a tampered or downgraded header
 * cannot make the app derive keys with trivially weak parameters.
 */
class KdfMinimums(
    val argon2MemoryKiB: Int,
    val argon2Iterations: Int,
    val pbkdf2Iterations: Int,
) {
    companion object {
        /** OWASP's minimum Argon2id configuration (19 MiB, 2 passes) and 210k PBKDF2-SHA256 rounds. */
        val STRICT = KdfMinimums(argon2MemoryKiB = 19 * 1024, argon2Iterations = 2, pbkdf2Iterations = 210_000)

        /** For fast unit tests only. Never use in production code. */
        val NONE = KdfMinimums(argon2MemoryKiB = 0, argon2Iterations = 1, pbkdf2Iterations = 1)
    }
}

/** Turns a master password into a 32-byte key. One implementation per algorithm. */
interface KeyDeriver {
    val algorithm: String
    fun derive(password: CharArray, params: KdfParams): ByteArray
}

/**
 * PBKDF2-HMAC-SHA256. Built into the JDK and Android (API 26+). Useful as a fallback
 * and for fast JVM unit tests. Production vaults should use Argon2id.
 */
class Pbkdf2KeyDeriver : KeyDeriver {
    override val algorithm = Kdf.PBKDF2_SHA256

    override fun derive(password: CharArray, params: KdfParams): ByteArray {
        val spec = PBEKeySpec(password, params.salt, params.iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
