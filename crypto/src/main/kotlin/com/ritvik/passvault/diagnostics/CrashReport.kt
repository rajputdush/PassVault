package com.ritvik.passvault.diagnostics

/**
 * Builds a crash report that is safe to share. It holds only the kinds of exception and the code locations
 * (class, method, line). Exception messages are left out on purpose because they can contain what the user was
 * typing or other private text.
 */
object CrashReport {
    private const val MAX_FRAMES = 25
    private const val MAX_CAUSES = 4

    fun format(error: Throwable, appVersion: String, androidVersion: String, device: String, timeMs: Long): String {
        val out = StringBuilder()
        out.append("PassVault crash report\n")
        out.append("App version: ").append(appVersion).append('\n')
        out.append("Android: ").append(androidVersion).append('\n')
        out.append("Device: ").append(device).append('\n')
        out.append("Time (UTC ms): ").append(timeMs).append("\n\n")
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSES) {
            out.append(if (depth == 0) "Error: " else "Caused by: ").append(current.javaClass.name).append('\n')
            current.stackTrace.take(MAX_FRAMES).forEach { out.append("    at ").append(it.toString()).append('\n') }
            current = current.cause?.takeIf { it !== current }
            depth++
        }
        return out.toString()
    }
}
