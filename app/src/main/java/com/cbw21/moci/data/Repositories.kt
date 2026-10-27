package com.cbw21.moci.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class LibraryRepository(context: Context) {
    private val directory = File(context.filesDir, "books")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun loadBooks(): List<VocabBook> = withContext(Dispatchers.IO) {
        if (!directory.exists()) return@withContext emptyList()
        directory.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                try {
                    json.decodeFromString<VocabBook>(file.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.w("LibraryRepository", "Skipping unreadable book ${file.name}", e)
                    null
                }
            }
            .sortedByDescending { it.importedAt }
    }

    suspend fun saveBook(book: VocabBook) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        writeAtomically(
            target = File(directory, "${book.id}.json"),
            content = json.encodeToString(VocabBook.serializer(), book),
        )
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        File(directory, "$bookId.json").delete()
        Unit
    }

    private fun writeAtomically(target: File, content: String) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

class ProgressRepository(context: Context) {
    private val file = File(context.filesDir, "progress.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(): ProgressData = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext ProgressData()
        try {
            json.decodeFromString<ProgressData>(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w("ProgressRepository", "Progress file is unreadable", e)
            ProgressData()
        }
    }

    suspend fun save(progress: ProgressData) = withContext(Dispatchers.IO) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(
            json.encodeToString(ProgressData.serializer(), progress),
            Charsets.UTF_8,
        )
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

class BackupRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(uri: Uri, books: List<VocabBook>, progress: ProgressData) =
        withContext(Dispatchers.IO) {
            val payload = BackupData(
                exportedAt = System.currentTimeMillis(),
                books = books,
                progress = progress,
            )
            val bytes = json.encodeToString(BackupData.serializer(), payload)
                .toByteArray(Charsets.UTF_8)
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("无法写入备份文件。")
            output.use { it.write(bytes) }
        }

    suspend fun read(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            readLimited(input).toString(Charsets.UTF_8)
        } ?: throw IOException("无法读取备份文件。")
        val backup = try {
            json.decodeFromString(BackupData.serializer(), text)
        } catch (e: Exception) {
            throw IOException("备份文件格式不正确，请选择墨词导出的 JSON 备份。", e)
        }
        if (backup.version > BACKUP_VERSION) {
            throw IOException("备份来自更新版本的应用，请先升级墨词。")
        }
        if (backup.books.isEmpty() && backup.progress.books.isEmpty()) {
            throw IOException("备份文件中没有词书或练习记录。")
        }
        backup
    }

    private fun readLimited(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size() > MAX_BACKUP_BYTES) throw IOException("备份文件过大。")
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    }
}
