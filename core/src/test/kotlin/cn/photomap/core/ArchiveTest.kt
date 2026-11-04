package cn.photomap.core

import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.nio.file.Files
import java.util.zip.*

class ArchiveTest {
    private fun testFiles(block: (File) -> Unit) { val dir = Files.createTempDirectory("photomap").toFile(); try { block(dir) } finally { dir.deleteRecursively() } }
    private fun zip(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z -> entries.forEach { (name, value) -> z.putNextEntry(ZipEntry(name)); z.write(value.toByteArray()); z.closeEntry() } } }.toByteArray()
    @Test fun roundTrip() = testFiles { root ->
        val data = File(root, "source").apply { mkdirs() }; File(data, "photos").mkdirs(); File(data, "photos/test.jpg").writeText("original"); File(data, "photomap.db").writeText("db")
        val bytes = ByteArrayOutputStream(); Archive.write(data, listOf("photos/test.jpg", "photomap.db"), bytes)
        val dest = File(root, "restored"); assertEquals(2, Archive.extract(bytes.toByteArray().inputStream(), dest).size)
        assertEquals("original", File(dest, "photos/test.jpg").readText())
    }
    @Test fun pathTraversalNeverWritesOutside() = testFiles { root ->
        assertThrows(IllegalArgumentException::class.java) { Archive.extract(zip("../escape" to "bad").inputStream(), File(root, "restore")) }
        assertFalse(File(root, "escape").exists())
    }
    @Test fun sizeLimitCleansStaging() = testFiles { root ->
        val dest = File(root, "restore")
        assertThrows(IllegalArgumentException::class.java) { Archive.extract(zip("large" to "x".repeat(1000)).inputStream(), dest, 100) }
        assertFalse(dest.exists())
    }
    @Test fun changedFileFailsChecksum() = testFiles { root ->
        val content = zip("manifest.tsv" to "PHOTOMAP\t1\nphotomap.db\t2\twrong\n", "photomap.db" to "db")
        assertThrows(IllegalArgumentException::class.java) { Archive.extract(content.inputStream(), File(root,"restore")) }
    }
    @Test fun extraFilesAreRejected() = testFiles { root ->
        assertThrows(IllegalArgumentException::class.java) { Archive.extract(zip("manifest.tsv" to "PHOTOMAP\t1\n", "extra" to "bad").inputStream(), File(root,"restore")) }
    }
}
