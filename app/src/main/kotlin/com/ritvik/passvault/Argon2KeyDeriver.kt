package com.ritvik.passvault

import com.ritvik.passvault.crypto.Kdf
import com.ritvik.passvault.crypto.KdfParams
import com.ritvik.passvault.crypto.KeyDeriver
import com.ritvik.passvault.crypto.toUtf8Bytes
import com.ritvik.passvault.crypto.wipe
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

/** Argon2id through the native Argon2Kt library (the reference C implementation). */
class Argon2KeyDeriver(private val argon2: Argon2Kt = Argon2Kt()) : KeyDeriver {
    override val algorithm = Kdf.ARGON2ID

    override fun derive(password: CharArray, params: KdfParams): ByteArray {
        val passwordBytes = password.toUtf8Bytes()
        try {
            val result = argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBytes,
                salt = params.salt,
                tCostInIterations = params.iterations,
                mCostInKibibyte = params.memoryKiB,
                parallelism = params.parallelism,
                hashLengthInBytes = 32,
            )
            return result.rawHashAsByteArray()
        } finally {
            passwordBytes.wipe()
        }
    }
}
