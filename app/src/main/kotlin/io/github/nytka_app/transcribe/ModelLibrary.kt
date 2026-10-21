package io.github.nytka_app.transcribe

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nytka_app.core.transcribe.ModelFileEntry
import io.github.nytka_app.core.transcribe.ModelFiles
import io.github.nytka_app.core.transcribe.ModelLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The model on the phone, as the screen shows it. */
data class InstalledModel(
    val name: String,
    val layout: ModelLayout,
    val bytes: Long,
)

/** A folder the user picked, read but not yet copied: what will be copied, and the room there is for it. */
data class ModelImport(
    val folder: String,
    val name: String,
    val files: List<ModelFileEntry>,
    val layout: ModelLayout,
    val freeBytes: Long,
) {
    val bytes: Long get() = files.sumOf { it.bytes }
    val fits: Boolean get() = bytes + SPARE_BYTES <= freeBytes

    companion object {
        /** Left free after the copy, so the queue and the system keep room: 200 MB. */
        const val SPARE_BYTES = 200L * 1024 * 1024
    }
}

/**
 * The one recognition model the app keeps, copied from a folder the user picks (Storage Access Framework). The app
 * never downloads a model: its only call to a host that is not the user's server is the firmware check.
 */
interface ModelLibrary {
    val installed: StateFlow<InstalledModel?>

    /** Lists [folder] (a tree URI); throws [IllegalArgumentException] if it holds no model. */
    suspend fun inspect(folder: String): ModelImport

    /** Copies [import] in, replacing the model there was; [progress] gets the bytes copied so far. */
    suspend fun install(
        import: ModelImport,
        progress: (Long) -> Unit,
    )

    suspend fun delete()

    /** The installed model's files, or null. */
    fun current(): ModelFiles?
}

@Singleton
class AndroidModelLibrary
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ModelLibrary {
        private val root = File(context.filesDir, "models")
        private val state = MutableStateFlow(current()?.let(::describe))

        override val installed: StateFlow<InstalledModel?> = state.asStateFlow()

        override fun current(): ModelFiles? =
            root
                .listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.firstNotNullOfOrNull(ModelFiles::inDirectory)

        override suspend fun inspect(folder: String): ModelImport =
            withContext(Dispatchers.IO) {
                val tree = Uri.parse(folder)
                val children = children(tree).map { ModelFileEntry(it.name, it.bytes) }
                val picked =
                    requireNotNull(ModelFiles.pick(children.map { it.name })) {
                        "This folder holds no model: it needs tokens.txt and the model's .onnx files."
                    }
                ModelImport(
                    folder = folder,
                    name = safeName(folderName(tree)),
                    files = children.filter { it.name in picked.names },
                    layout = picked.layout,
                    freeBytes = context.filesDir.usableSpace,
                )
            }

        override suspend fun install(
            import: ModelImport,
            progress: (Long) -> Unit,
        ) = withContext(Dispatchers.IO) {
            val tree = Uri.parse(import.folder)
            val ids = children(tree).associate { it.name to it.id }
            val staging = File(root, ".importing")
            staging.deleteRecursively()
            staging.mkdirs()
            try {
                var copied = 0L
                for (file in import.files) {
                    val id = ids[file.name] ?: throw IOException("The folder changed while it was copied.")
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    val input =
                        context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read ${file.name}.")
                    input.use { source ->
                        File(staging, file.name).outputStream().use { target ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                val read = source.read(buffer)
                                if (read < 0) break
                                target.write(buffer, 0, read)
                                copied += read
                                progress(copied)
                            }
                        }
                    }
                }
                deleteModels()
                if (!staging.renameTo(File(root, import.name))) throw IOException("Cannot move the model into place.")
            } finally {
                staging.deleteRecursively()
            }
            state.value = current()?.let(::describe)
        }

        override suspend fun delete() =
            withContext(Dispatchers.IO) {
                deleteModels()
                state.value = null
            }

        private fun deleteModels() {
            root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { it.deleteRecursively() }
        }

        private data class Child(
            val id: String,
            val name: String,
            val bytes: Long,
        )

        private fun children(tree: Uri): List<Child> {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val columns =
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
            val cursor = context.contentResolver.query(uri, columns, null, null, null) ?: return emptyList()
            return cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        if (it.getString(3) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                        add(Child(it.getString(0), it.getString(1), it.getLong(2)))
                    }
                }
            }
        }

        private fun folderName(tree: Uri): String {
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            return context.contentResolver.query(uri, columns, null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "model"
        }

        private fun describe(model: ModelFiles) = InstalledModel(model.name, model.layout, model.bytes)

        private companion object {
            const val BUFFER_BYTES = 1 shl 20

            fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').ifEmpty { "model" }
        }
    }
