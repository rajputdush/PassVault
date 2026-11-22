package com.ritvik.passvault.security

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the Have I Been Pwned "Pwned Passwords" range API over HTTPS.
 * Only a 5-character hash prefix is ever sent. [fetchRange] must be called off the main thread.
 */
class BreachClient {
    private val prefixPattern = Regex("^[0-9A-F]{5}$")

    @Throws(IOException::class)
    fun fetchRange(prefix: String): String {
        require(prefixPattern.matches(prefix)) { "Invalid hash prefix" }
        val connection = URL("https://api.pwnedpasswords.com/range/$prefix").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            // Asks the service to add fake entries so the response size does not reveal how many real matches exist.
            connection.setRequestProperty("Add-Padding", "true")
            connection.setRequestProperty("User-Agent", "PassVault-Android")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Unexpected response: ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
