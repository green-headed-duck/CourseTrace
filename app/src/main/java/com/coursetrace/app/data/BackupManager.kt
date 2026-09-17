package com.coursetrace.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedInputStream
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(private val context: Context) {
    private val recordsDir = context.filesDir.resolve("records")

    suspend fun exportEncrypted(destination: Uri, recoveryPassword: CharArray): BackupResult =
        withContext(Dispatchers.IO) {
            require(recoveryPassword.size >= 12) { "恢复口令至少需要 12 个字符" }
            val random = SecureRandom()
            val salt = ByteArray(16).also(random::nextBytes)
            val iv = ByteArray(12).also(random::nextBytes)
            val key = deriveKey(recoveryPassword, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            }
            var files = 0
            var sourceBytes = 0L
            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: error("无法写入备份文件")
            output.use { raw ->
                BufferedOutputStream(raw).use { buffered ->
                    buffered.write(MAGIC)
                    buffered.write(salt)
                    buffered.write(iv)
                    CipherOutputStream(buffered, cipher).use { encrypted ->
                        ZipOutputStream(encrypted).use { zip ->
                            recordsDir.walkTopDown()
                                .filter(FilePredicate)
                                .forEach { file ->
                                    val relative = file.relativeTo(recordsDir).invariantSeparatorsPath
                                    zip.putNextEntry(ZipEntry(relative).apply { time = file.lastModified() })
                                    file.inputStream().use { input ->
                                        sourceBytes += input.copyTo(zip)
                                    }
                                    zip.closeEntry()
                                    files++
                                }
                            zip.putNextEntry(ZipEntry("backup-metadata.txt"))
                            zip.write("format=1\ncreatedAt=${OffsetDateTime.now()}\n".toByteArray())
                            zip.closeEntry()
                        }
                    }
                }
            }
            recoveryPassword.fill('\u0000')
            BackupResult(files, sourceBytes)
        }

    suspend fun restoreEncrypted(source: Uri, recoveryPassword: CharArray): RestoreResult =
        withContext(Dispatchers.IO) {
            require(recoveryPassword.size >= 12) { "恢复口令至少需要 12 个字符" }
            val restoreRoot = context.cacheDir.resolve("restore-${System.currentTimeMillis()}")
            restoreRoot.mkdirs()
            try {
                val input = context.contentResolver.openInputStream(source) ?: error("无法读取备份文件")
                var files = 0
                var totalBytes = 0L
                input.use { raw ->
                    BufferedInputStream(raw).use { buffered ->
                        val magic = readExactly(buffered, MAGIC.size)
                        require(magic.contentEquals(MAGIC)) { "不是课迹加密备份" }
                        val salt = readExactly(buffered, 16)
                        val iv = readExactly(buffered, 12)
                        require(salt.size == 16 && iv.size == 12) { "备份头损坏" }
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(Cipher.DECRYPT_MODE, deriveKey(recoveryPassword, salt), GCMParameterSpec(128, iv))
                        }
                        CipherInputStream(buffered, cipher).use { decrypted ->
                            ZipInputStream(decrypted).use { zip ->
                                while (true) {
                                    val entry = zip.nextEntry ?: break
                                    if (entry.isDirectory) continue
                                    require(++files <= 20_000) { "备份文件数量异常" }
                                    val target = restoreRoot.resolve(entry.name).canonicalFile
                                    require(target.path.startsWith(restoreRoot.canonicalPath + java.io.File.separator)) { "备份包含非法路径" }
                                    target.parentFile?.mkdirs()
                                    target.outputStream().use { output ->
                                        val buffer = ByteArray(64 * 1024)
                                        while (true) {
                                            val read = zip.read(buffer)
                                            if (read <= 0) break
                                            totalBytes += read
                                            require(totalBytes <= 512L * 1024 * 1024) { "解压数据异常过大" }
                                            output.write(buffer, 0, read)
                                        }
                                    }
                                    zip.closeEntry()
                                }
                            }
                        }
                    }
                }
                val state = restoreRoot.resolve("state.json")
                require(state.isFile && state.length() in 2..(100L * 1024 * 1024)) { "备份缺少有效状态文件" }
                val old = context.filesDir.resolve("records-before-restore-${System.currentTimeMillis()}")
                if (recordsDir.exists()) require(recordsDir.renameTo(old)) { "无法保存恢复前数据" }
                if (!restoreRoot.renameTo(recordsDir)) {
                    old.renameTo(recordsDir)
                    error("无法写入恢复数据")
                }
                RestoreResult(files, totalBytes, old.takeIf { it.exists() })
            } catch (error: Throwable) {
                restoreRoot.deleteRecursively()
                throw error
            } finally {
                recoveryPassword.fill('\u0000')
            }
        }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun readExactly(input: java.io.InputStream, size: Int): ByteArray {
        val output = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(output, offset, size - offset)
            if (read < 0) return output.copyOf(offset)
            offset += read
        }
        return output
    }

    companion object {
        private val MAGIC = "CTRACEBK1".toByteArray(Charsets.US_ASCII)
        private val FilePredicate: (java.io.File) -> Boolean = { file ->
            file.isFile && file.name != "state.json.tmp" && !file.invariantSeparatorsPath.contains("/.git/logs/")
        }
    }
}

data class BackupResult(val fileCount: Int, val uncompressedBytes: Long)
data class RestoreResult(val fileCount: Int, val restoredBytes: Long, val previousDataDirectory: java.io.File?)
