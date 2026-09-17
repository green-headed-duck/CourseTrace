package com.coursetrace.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import java.io.File

class GitHistoryService(private val recordsDir: File) {
    private fun openOrCreate(): Git {
        recordsDir.mkdirs()
        val dotGit = File(recordsDir, ".git")
        return if (dotGit.exists()) {
            Git.open(recordsDir)
        } else {
            Git.init().setDirectory(recordsDir).call()
        }
    }

    suspend fun commit(message: String) = withContext(Dispatchers.IO) {
        runCatching {
            openOrCreate().use { git ->
                git.add().addFilepattern(".").call()
                val status = git.status().call()
                if (status.hasUncommittedChanges()) {
                    git.commit()
                        .setMessage(message.take(120))
                        .setAuthor("课迹", "local@coursetrace.invalid")
                        .setCommitter("课迹", "local@coursetrace.invalid")
                        .call()
                }
            }
        }
    }

    suspend fun recentHistory(limit: Int = 30): List<HistoryEntry> = withContext(Dispatchers.IO) {
        runCatching {
            openOrCreate().use { git ->
                git.log().setMaxCount(limit).call().map {
                    HistoryEntry(
                        id = it.name,
                        message = it.fullMessage,
                        timestampSeconds = it.commitTime.toLong(),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun repositorySizeBytes(): Long = withContext(Dispatchers.IO) {
        fun size(file: File): Long = if (file.isFile) file.length() else file.listFiles()?.sumOf(::size) ?: 0L
        size(recordsDir)
    }

    fun repositoryDirectory(): File = recordsDir
}

data class HistoryEntry(
    val id: String,
    val message: String,
    val timestampSeconds: Long,
)
