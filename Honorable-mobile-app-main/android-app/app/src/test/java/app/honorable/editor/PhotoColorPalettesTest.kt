package app.honorable.editor

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

class PhotoColorPalettesTest {
    private val red = 0xffff0000.toInt()
    private val blue = 0xff0000ff.toInt()
    private fun palette(name: String = "Sunset") = PhotoColorPalette(name, listOf(red, blue))
    private fun rejects(block: () -> Unit) { assertThrows(IllegalArgumentException::class.java, block) }

    @Test fun `colors and catalogue are immutable defensive snapshots`() {
        val colors = mutableListOf(red, blue)
        val entry = PhotoColorPalette("Sunset", colors)
        colors.clear()
        assertEquals(listOf(red, blue), entry.colors)
        assertThrows(UnsupportedOperationException::class.java) { (entry.colors as MutableList<Int>).clear() }
        val entries = mutableListOf(entry)
        val catalog = PhotoColorPalettes(entries)
        entries.clear()
        assertEquals(listOf(entry), catalog.values)
        assertThrows(UnsupportedOperationException::class.java) { (catalog.values as MutableList<PhotoColorPalette>).clear() }
    }

    @Test fun `plain Unicode names accept forty code points and reject malformed or unbounded input`() {
        assertEquals("🎨".repeat(40), palette("🎨".repeat(40)).name)
        assertEquals("Été 日本語", palette("Été 日本語").name)
        for (name in listOf("", " ", " a", "a ", "a\nb", "a\u0000b", "x\u007fy", "\ud800", "🎨".repeat(41))) rejects { palette(name) }
    }

    @Test fun `require one to eight distinct opaque colors`() {
        for (colors in listOf(emptyList(), listOf(red, red), listOf(0x00ff0000), listOf(0x7fff0000), (0..8).map { 0xff000000.toInt() or it })) {
            rejects { PhotoColorPalette("Invalid", colors) }
        }
        assertEquals(8, PhotoColorPalette("Eight", (0..7).map { 0xff000000.toInt() or it }).colors.size)
    }

    @Test fun `HEX keeps order leading zeros and uppercase channels without alpha`() {
        val p = PhotoColorPalette("Ink", listOf(0xff00000a.toInt(), 0xffabcdef.toInt(), 0xffffffff.toInt()))
        assertEquals("#00000A\n#ABCDEF\n#FFFFFF", p.hexText())
    }

    @Test fun `save refuses name replacement and catalogue capacity overflow`() {
        val original = PhotoColorPalettes(listOf(palette()))
        rejects { original.add(PhotoColorPalette("Sunset", listOf(blue))) }
        val full = PhotoColorPalettes((1..20).map { palette("Palette $it") })
        rejects { full.add(palette("One more")) }
        rejects { PhotoColorPalettes(listOf(palette(), palette())) }
        assertEquals(1, original.values.size)
        assertEquals(20, full.values.size)
    }

    @Test fun `deletion requires the exact saved entry and never changes the original snapshot`() {
        val original = PhotoColorPalettes(listOf(palette(), palette("Other")))
        rejects { original.remove(PhotoColorPalette("Sunset", listOf(blue))) }
        rejects { original.remove(palette("Missing")) }
        assertEquals(listOf("Other"), original.remove(palette()).values.map { it.name })
        assertEquals(2, original.values.size)
    }

    @Test fun `binary roundtrip preserves complete bounded shelves and Unicode`() {
        for (catalog in listOf(PhotoColorPalettes(), PhotoColorPalettes((1..20).map { PhotoColorPalette("🎨 Palette $it", (0..7).map { 0xff010000.toInt() or it }) }))) {
            val bytes = PhotoColorPaletteCodec.encode(catalog)
            assertTrue(bytes.size <= PhotoColorPaletteCodec.MAX_BYTES)
            assertEquals(catalog, PhotoColorPaletteCodec.decode(bytes))
        }
    }

    @Test fun `checksum detects every single bit corruption and truncated or extended bytes`() {
        val bytes = PhotoColorPaletteCodec.encode(PhotoColorPalettes(listOf(palette())))
        for (index in bytes.indices) {
            val changed = bytes.clone(); changed[index] = (changed[index].toInt() xor 1).toByte()
            rejects { PhotoColorPaletteCodec.decode(changed) }
        }
        for (size in listOf(0, 12, 43, bytes.size - 1)) rejects { PhotoColorPaletteCodec.decode(bytes.copyOf(size)) }
        rejects { PhotoColorPaletteCodec.decode(bytes + 0) }
        rejects { PhotoColorPaletteCodec.decode(ByteArray(PhotoColorPaletteCodec.MAX_BYTES + 1)) }
    }

    private fun signed(payload: (DataOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use(payload)
        return bytes.toByteArray().let { it + MessageDigest.getInstance("SHA-256").digest(it) }
    }

    @Test fun `valid checksum cannot bypass format version count colors or trailing data guards`() {
        for ((magic, version, count) in listOf(Triple(0, 1, 0), Triple(0x4e504c31, 2, 0), Triple(0x4e504c31, 1, -1), Triple(0x4e504c31, 1, 21))) {
            rejects { PhotoColorPaletteCodec.decode(signed { it.writeInt(magic); it.writeInt(version); it.writeInt(count) }) }
        }
        for (size in listOf(0, 9)) rejects {
            PhotoColorPaletteCodec.decode(signed { it.writeInt(0x4e504c31); it.writeInt(1); it.writeInt(1); it.writeUTF("Bad"); it.writeInt(size) })
        }
        rejects { PhotoColorPaletteCodec.decode(signed { it.writeInt(0x4e504c31); it.writeInt(1); it.writeInt(0); it.writeByte(1) }) }
    }

    @Test fun `valid checksum still rejects duplicate names in external payload`() {
        rejects {
            PhotoColorPaletteCodec.decode(signed {
                it.writeInt(0x4e504c31); it.writeInt(1); it.writeInt(2)
                repeat(2) { _ -> it.writeUTF("Duplicate"); it.writeInt(1); it.writeInt(red) }
            })
        }
    }
}
