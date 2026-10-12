package app.tormang.core.common

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

fun stableId(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
fun naturalKey(name: String): String = Regex("[0-9]+").replace(name) { it.value.padStart(16, '0') }
object ArchiveExtractor {
    const val MAX_PAGE_BYTES = 64L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    fun extract(input: InputStream, destination: File): List<File> {
        destination.mkdirs()
        val pages = mutableListOf<Pair<String, File>>()
        var total = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                var entries = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++entries <= 10_000) { "Too many archive entries" }
                    require(!entry.name.startsWith("/") && entry.name.split('/', '\\').none { it == ".." }) { "Unsafe archive path" }
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    val image = ext in setOf("jpg", "jpeg", "png", "webp", "gif") && !entry.name.startsWith("__MACOSX/")
                    val output = if (image) File(destination, "${pages.size}.$ext") else null
                    var size = 0L
                    val stream = output?.outputStream()
                    try {
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            size += read; total += read
                            require(size <= MAX_PAGE_BYTES && total <= MAX_TOTAL_BYTES) { "Archive exceeds safe extraction limits" }
                            stream?.write(buffer, 0, read)
                        }
                    } finally { stream?.close() }
                    if (output != null && size > 0) pages += entry.name to output
                    zip.closeEntry()
                }
            }
            require(pages.isNotEmpty()) { "Archive contains no supported images" }
            return pages.sortedBy { naturalKey(it.first) }.map { it.second }
        } catch (e: Exception) { destination.deleteRecursively(); throw e }
    }
}
fun Throwable.userMessage(): String = when (this) {
    is java.net.UnknownHostException -> "Нет подключения. Проверьте интернет и повторите."
    is java.net.SocketTimeoutException -> "Источник отвечает слишком долго. Повторите позже."
    is java.io.FileNotFoundException -> "Файл не найден или доступ к папке утрачен."
    is java.io.IOException -> "Не удалось прочитать данные. Проверьте сеть и свободное место."
    else -> message?.take(240) ?: "Не удалось завершить действие. Повторите попытку."
}
