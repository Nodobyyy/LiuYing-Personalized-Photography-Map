package cn.photomap.core

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A versioned tab-separated manifest keeps backup validation independent of Android. */
object Archive {
    const val MAX_BYTES = 32L * 1024 * 1024 * 1024
    const val MAX_FILES = 100_002
    private fun safe(name: String): Boolean = name.matches(Regex("[a-zA-Z0-9_./-]+")) &&
        !name.startsWith('/') && name.split('/').none { it.isEmpty() || it == "." || it == ".." }
    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val bytes = ByteArray(65536); while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun write(root: File, names: List<String>, output: java.io.OutputStream) {
        require(names.size <= MAX_FILES && names.distinct().size == names.size)
        val files = names.sorted().map { name -> require(safe(name) && name != "manifest.tsv"); name to File(root, name) }
        val manifest = "PHOTOMAP\t1\n" + files.joinToString("\n") { (name, f) -> require(f.isFile); "$name\t${f.length()}\t${hash(f)}" } + "\n"
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.tsv")); zip.write(manifest.toByteArray()); zip.closeEntry()
            files.forEach { (name, file) -> zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
    }
    fun extract(input: java.io.InputStream, target: File, maxBytes: Long = MAX_BYTES): List<String> {
        require(!target.exists() || target.listFiles().isNullOrEmpty())
        target.mkdirs()
        var total = 0L
        val names = hashSetOf<String>()
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                val buffer = ByteArray(65536)
                while (entry != null) {
                    val name = entry.name
                    require(!entry.isDirectory && safe(name) && names.add(name) && names.size <= MAX_FILES) { "备份包含不安全或重复路径" }
                    val output = File(target, name)
                    require(output.canonicalPath.startsWith(target.canonicalPath + File.separator))
                    output.parentFile!!.mkdirs()
                    output.outputStream().use { stream ->
                        var fileBytes = 0L
                        while (true) {
                            val n = zip.read(buffer); if (n < 0) break
                            total += n; fileBytes += n
                            require(total <= maxBytes && (name != "manifest.tsv" || fileBytes < 16*1024*1024)) { "备份超过大小限制" }
                            require(target.usableSpace > 16*1024*1024) { "设备存储空间不足" }
                            stream.write(buffer, 0, n)
                        }
                    }
                    if(name=="manifest.tsv") {
                        val lines=output.readLines();require(lines.firstOrNull()=="PHOTOMAP\t1")
                        val sizes=lines.drop(1).filter {it.isNotBlank()}.map {line->val parts=line.split('\t');require(parts.size==3);parts[1].toLong().also {require(it>=0 && it<=maxBytes)} }
                        var required=0L;sizes.forEach {size->require(required<=maxBytes-size);required+=size}
                        require(target.usableSpace>required+32L*1024*1024) {"恢复所需可用空间不足"}
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
            val manifest = File(target, "manifest.tsv")
            require(manifest.isFile)
            val lines = manifest.readLines()
            require(lines.firstOrNull() == "PHOTOMAP\t1") { "不支持的备份版本" }
            val expected = lines.drop(1).filter { it.isNotEmpty() }.map { line ->
                val values = line.split('\t'); require(values.size == 3 && safe(values[0]))
                val file = File(target, values[0]); require(file.isFile && file.length() == values[1].toLong())
                require(hash(file) == values[2]) { "备份文件校验失败" }; values[0]
            }
            require(expected.distinct().size == expected.size && names == expected.toSet() + "manifest.tsv")
            return expected
        } catch (error: Exception) { target.deleteRecursively(); throw error }
    }
}
