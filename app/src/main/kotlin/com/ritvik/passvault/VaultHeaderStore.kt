package com.ritvik.passvault

import com.ritvik.passvault.crypto.VaultHeader
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persists the vault header in the app's private storage. Writes go to a temp file that is synced
 * and then atomically renamed, so a crash mid-write can never leave a half-written header
 * (which would lock the user out of their vault).
 */
class VaultHeaderStore(private val file: File) {

    fun exists(): Boolean = file.exists()

    /** @throws com.ritvik.passvault.crypto.VaultFormatException if the file is corrupt. */
    fun load(): VaultHeader? =
        if (file.exists()) VaultHeaderJson.decode(file.readText(Charsets.UTF_8)) else null

    fun save(header: VaultHeader) {
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { out ->
            out.write(VaultHeaderJson.encode(header).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
