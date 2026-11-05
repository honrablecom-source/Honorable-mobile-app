package app.honorable.editor

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PhotoColorPaletteStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val namespace = PhotoPresetNamespace.GUEST
    private fun palette(name: String = "Sunset") = PhotoColorPalette(name, listOf(0xffbada55.toInt(), 0xff001122.toInt()))
    private fun shelf(directory: File = File(temporary.root, "palettes"), scope: String = namespace) = PhotoColorPaletteStore(directory, scope)
    private fun file(directory: File = File(temporary.root, "palettes")) = File(directory, "$namespace.bin")

    @Test fun `missing shelf creates no file until explicit save and reload survives new instance`() {
        val store = shelf()
        assertTrue(store.load().values.isEmpty())
        assertFalse(file().parentFile.exists())
        store.save(palette()) {}
        assertEquals(listOf(palette()), shelf().load().values)
        assertEquals(listOf("$namespace.bin"), file().parentFile.listFiles()!!.map { it.name })
    }

    @Test fun `account guest and API scopes cannot read one another`() {
        val scopes = listOf(namespace, PhotoPresetNamespace.account("one", "https://one.example"), PhotoPresetNamespace.account("two", "https://one.example"), PhotoPresetNamespace.account("one", "https://two.example"))
        assertEquals(4, scopes.distinct().size)
        scopes.forEachIndexed { index, scope -> shelf(scope = scope).save(palette("Owner $index")) {} }
        scopes.forEachIndexed { index, scope -> assertEquals(listOf("Owner $index"), shelf(scope = scope).load().values.map { it.name }) }
    }

    @Test fun `name is payload never a filesystem path`() {
        shelf().save(palette("../../name")) {}
        assertEquals(listOf("$namespace.bin"), file().parentFile.listFiles()!!.map { it.name })
        assertEquals("../../name", shelf().load().values.single().name)
        for (scope in listOf("..", "../guest-basic", "account-abc", "guest-basic/other", "")) {
            assertThrows(IllegalArgumentException::class.java) { shelf(scope = scope) }
        }
    }

    @Test fun `duplicate name and over capacity leave previous file byte identical`() {
        val store = shelf()
        repeat(20) { store.save(palette("Palette $it")) {} }
        val old = file().readBytes()
        assertThrows(IllegalArgumentException::class.java) { store.save(palette("Palette 0")) {} }
        assertArrayEquals(old, file().readBytes())
        assertThrows(IllegalArgumentException::class.java) { store.save(palette("Overflow")) {} }
        assertArrayEquals(old, file().readBytes())
    }

    @Test fun `malformed oversized empty or checksum damaged shelves are preserved on save`() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3), ByteArray(PhotoColorPaletteCodec.MAX_BYTES + 1), PhotoColorPaletteCodec.encode(PhotoColorPalettes(listOf(palette()))).apply { this[5] = 12 })) {
            file().parentFile.mkdirs(); file().writeBytes(bytes)
            assertThrows(Exception::class.java) { shelf().load() }
            assertThrows(Exception::class.java) { shelf().save(palette("New")) {} }
            assertArrayEquals(bytes, file().readBytes())
            assertEquals(1, file().parentFile.listFiles()!!.size)
        }
    }

    @Test fun `stale request before admission creates no file`() {
        assertThrows(IllegalStateException::class.java) { shelf().save(palette()) { error("Session changed") } }
        assertFalse(file().parentFile.exists())
    }

    @Test fun `cancellation at commit leaves old bytes and removes temporary output`() {
        val store = shelf(); store.save(palette()) {}
        val old = file().readBytes(); var checks = 0
        assertThrows(IllegalStateException::class.java) {
            store.save(palette("Cancelled")) { if (++checks == 2) error("Session changed") }
        }
        assertEquals(2, checks)
        assertArrayEquals(old, file().readBytes())
        assertEquals(listOf("$namespace.bin"), file().parentFile.listFiles()!!.map { it.name })
    }

    @Test fun `delete reload and undo restore exact entry order and bytes`() {
        val store = shelf(); store.save(palette()) {}; store.save(palette("Second")) {}
        val old = file().readBytes()
        val deletion = store.delete(palette()) {}
        assertEquals(listOf(palette("Second")), shelf().load().values)
        shelf().undo(deletion) {}
        assertArrayEquals(old, file().readBytes())
        assertThrows(IllegalArgumentException::class.java) { shelf().undo(deletion) {} }
    }

    @Test fun `undo never overwrites later saves or another shelf`() {
        val store = shelf(); store.save(palette()) {}
        val deletion = store.delete(palette()) {}
        shelf().save(palette("Later")) {}
        val old = file().readBytes()
        assertThrows(IllegalArgumentException::class.java) { store.undo(deletion) {} }
        assertArrayEquals(old, file().readBytes())
        val other = shelf(scope = PhotoPresetNamespace.account("other", "https://one.example"))
        assertThrows(IllegalArgumentException::class.java) { other.undo(deletion) {} }
        assertTrue(other.load().values.isEmpty())
    }

    @Test fun `cancelled deletion and undo leave their pre operation bytes intact`() {
        val store = shelf(); store.save(palette()) {}; val old = file().readBytes(); var checks = 0
        assertThrows(IllegalStateException::class.java) { store.delete(palette()) { if (++checks == 2) error("Paused") } }
        assertArrayEquals(old, file().readBytes())
        val deletion = store.delete(palette()) {}; val deleted = file().readBytes(); checks = 0
        assertThrows(IllegalStateException::class.java) { store.undo(deletion) { if (++checks == 2) error("Paused") } }
        assertArrayEquals(deleted, file().readBytes())
    }

    @Test fun `stale deletion does not remove a palette with changed colors`() {
        val store = shelf(); store.save(palette()) {}; val old = file().readBytes()
        assertThrows(IllegalArgumentException::class.java) { store.delete(PhotoColorPalette("Sunset", listOf(0xffffffff.toInt()))) {} }
        assertArrayEquals(old, file().readBytes())
    }

    @Test fun `filesystem collision and symbolic links refuse writes outside shelf`() {
        val blocked = temporary.newFile("blocked")
        assertThrows(Exception::class.java) { shelf(blocked).save(palette()) {} }
        val outside = temporary.newFile("outside").apply { writeText("Keep") }
        file().parentFile.mkdirs()
        Files.createSymbolicLink(file().toPath(), outside.toPath())
        assertThrows(IllegalStateException::class.java) { shelf().save(palette()) {} }
        assertEquals("Keep", outside.readText())
        val link = File(temporary.root, "link")
        Files.createSymbolicLink(link.toPath(), file().parentFile.toPath())
        assertThrows(IllegalStateException::class.java) { shelf(link).load() }
    }

    @Test fun `simultaneous writers serialize read modify commit without losing palettes`() {
        val executor = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        try {
            val jobs = (1..2).map { index -> executor.submit { gate.await(); shelf().save(palette("Writer $index")) {} } }
            gate.countDown()
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(setOf("Writer 1", "Writer 2"), shelf().load().values.map { it.name }.toSet())
        } finally { executor.shutdownNow() }
    }
}
