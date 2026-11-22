package com.ritvik.passvault.crypto

import java.nio.CharBuffer
import java.security.SecureRandom

private val secureRandom = SecureRandom()

/** Returns [size] bytes from the platform's cryptographically secure random source. */
fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

/** Overwrites the array with zeros. Call this as soon as a key or password is no longer needed. */
fun ByteArray.wipe() {
    fill(0)
}

fun CharArray.wipe() {
    fill('\u0000')
}

/** UTF-8 encodes the characters without going through an immutable String. */
fun CharArray.toUtf8Bytes(): ByteArray {
    val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(this))
    val out = ByteArray(buffer.remaining())
    buffer.get(out)
    if (buffer.hasArray()) buffer.array().fill(0)
    return out
}
