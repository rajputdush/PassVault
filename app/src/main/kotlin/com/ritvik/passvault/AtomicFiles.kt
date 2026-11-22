package com.ritvik.passvault

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Writes to a temp file, syncs it, then atomically renames it over [target]. A crash never leaves a half-written file. */
fun writeAtomically(target: File, bytes: ByteArray) {
    val temp = File(target.parentFile, target.name + ".tmp")
    FileOutputStream(temp).use { out ->
        out.write(bytes)
        out.fd.sync()
    }
    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
