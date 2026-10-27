package com.cbw21.moci.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedVocab(
    val title: String,
    val words: List<VocabWord>,
)

class PdfVocabImporter(private val context: Context) {
    suspend fun import(
        uri: Uri,
        onProgress: suspend (currentPage: Int, totalPages: Int) -> Unit,
    ): ImportedVocab = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open the selected file")

        input.use { stream ->
            PDDocument.load(stream).use { document ->
                val pageCount = document.numberOfPages
                if (pageCount <= 0) throw VocabParseException("PDF 中没有可读取的页面。")

                val stripper = PDFTextStripper()
                val pages = ArrayList<String>(pageCount)
                for (page in 1..pageCount) {
                    stripper.startPage = page
                    stripper.endPage = page
                    pages += stripper.getText(document)
                    onProgress(page, pageCount)
                }

                val words = VocabTextParser.parsePages(pages)
                ImportedVocab(
                    title = titleFrom(displayName),
                    words = words,
                )
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }

    private fun titleFrom(displayName: String?): String {
        val fileName = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            .orEmpty()
        val withoutExtension = if (fileName.endsWith(".pdf", ignoreCase = true)) {
            fileName.dropLast(4).trim()
        } else {
            fileName
        }
        return withoutExtension.ifEmpty { "未命名词书" }
    }
}
