package com.ritvik.passvault.sites

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ritvik.passvault.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional real website icons. OFF by default, because asking an icon service for "amazon.com" tells that service
 * you have an account there. When the user switches it on, only the site's domain (never a username, password
 * or title) is sent, over HTTPS, to DuckDuckGo's public icon service, and the picture is cached on the phone.
 * Cache file names are hashes, so the cache folder does not list your sites in plain text.
 */
class IconLoader(context: Context, private val settings: AppSettings) {
    private val dir = File(context.cacheDir, "site_icons")
    private val memory = LruCache<String, ImageBitmap>(80)
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(4)

    private val _enabled = MutableStateFlow(settings.showSiteIcons)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        settings.showSiteIcons = on
        _enabled.value = on
        if (!on) clear()
    }

    /** Whether the user has already answered the editor's "show real logos?" question. */
    val promptDismissed: Boolean get() = settings.iconPromptDismissed

    fun dismissPrompt() {
        settings.iconPromptDismissed = true
    }

    /** Already-loaded icon, or null. Safe to call while drawing. */
    fun peek(domain: String): ImageBitmap? = memory.get(domain)

    suspend fun load(domain: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!enabled.value || !validDomain(domain)) return@withContext null
        memory.get(domain)?.let { return@withContext it }
        if (domain in missing) return@withContext null
        readDisk(domain)?.let {
            memory.put(domain, it)
            return@withContext it
        }
        gate.withPermit {
            if (!enabled.value) return@withContext null
            val bitmap = download(domain)
            if (bitmap == null) {
                missing.add(domain) // do not ask again until the app restarts
                null
            } else {
                writeDisk(domain, bitmap)
                bitmap.asImageBitmap().also { memory.put(domain, it) }
            }
        }
    }

    fun clear() {
        memory.evictAll()
        missing.clear()
        dir.deleteRecursively()
    }

    private fun validDomain(d: String) =
        d.length in 3..253 && d.contains('.') && Regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$").matches(d)

    private fun fileFor(domain: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(domain.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.png")
    }

    private fun readDisk(domain: String): ImageBitmap? {
        val file = fileFor(domain)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.path)?.asImageBitmap()
    }

    private fun writeDisk(domain: String, bitmap: Bitmap) {
        try {
            dir.mkdirs()
            fileFor(domain).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            // The cache is only a convenience.
        }
    }

    private fun download(domain: String): Bitmap? {
        val connection = try {
            URL("https://icons.duckduckgo.com/ip3/$domain.ico").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return null
        }
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "PassVault-Android")
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { readLimited(it, MAX_BYTES) } ?: return null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            if (decoded.width < 16 || decoded.height < 16) return null
            val largest = maxOf(decoded.width, decoded.height)
            if (largest <= MAX_SIDE) decoded else {
                val scale = MAX_SIDE.toFloat() / largest
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 200_000
        const val MAX_SIDE = 96
    }
}
