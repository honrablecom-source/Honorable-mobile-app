package app.honorable.editor

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real PNG/zlib/CRC/ZIP bytes and JDK image decoder; all model masks are explicitly synthetic. */
class PhotoSubjectAssetArchiveTest {
    private val files = mutableListOf<File>()
    private fun temp(extension: String) = Files.createTempFile("noble-subject-test-", extension).toFile().also { files += it }
    @After fun cleanup() { files.forEach { it.delete() }; files.clear() }
    private fun source() = PhotoSubjectAssets.Source.capture(4, 2, IntArray(8) { 0xff000000.toInt() or (0x112233 + it * 0x010203) })
    private fun cuts(source: PhotoSubjectAssets.Source = source()) = PhotoSubjectAssets.prepare(source, listOf(
        PhotoSubjectAssets.Mask(0, 0, 2, 2, floatArrayOf(1f, .5f, 0f, .25f)),
        PhotoSubjectAssets.Mask(2, 0, 2, 1, floatArrayOf(.75f, 1f)),
        PhotoSubjectAssets.Mask(1, 1, 3, 1, floatArrayOf(1f, 0f, .5f)),
    ))
    private fun encode(cut: PhotoSubjectAssets.Cutout): PhotoSubjectAssetArchive.Encoded {
        val file = temp(".png"); val image = BufferedImage(cut.width, cut.height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, cut.width, cut.height, cut.pixels(), 0, cut.width); assertTrue(ImageIO.write(image, "png", file))
        return PhotoSubjectAssetArchive.capture(file, cut)
    }
    private fun png(width: Int, height: Int, raw: ByteArray, extra: String? = null): ByteArray {
        val result = ByteArrayOutputStream(); val out = DataOutputStream(result)
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        fun chunk(name: String, bytes: ByteArray) { val type = name.toByteArray(Charsets.US_ASCII); out.writeInt(bytes.size); out.write(type); out.write(bytes); out.writeInt(CRC32().apply { update(type); update(bytes) }.value.toInt()) }
        val header = ByteArrayOutputStream(); DataOutputStream(header).use { it.writeInt(width); it.writeInt(height); it.write(byteArrayOf(8, 6, 0, 0, 0)) }; chunk("IHDR", header.toByteArray())
        extra?.let { chunk(it, "synthetic metadata".toByteArray()) }
        val compressed = ByteArrayOutputStream(); DeflaterOutputStream(compressed).use { it.write(raw) }; chunk("IDAT", compressed.toByteArray()); chunk("IEND", byteArrayOf())
        return result.toByteArray()
    }
    @Test fun onlyChosenSubjectsHaveCanonicalOrderedByteExactPngsAndCompleteManifest() {
        val input = source(); val cuts = cuts(input); val images = cuts.map(::encode); val zip = temp(".zip")
        val prepared = PhotoSubjectAssetArchive.create(zip, input, listOf(images[2], images[0]))
        assertEquals(listOf(1, 3), prepared.entries.map { it.subject }); assertTrue(prepared.manifest.contains(input.sha256)); assertFalse(prepared.manifest.contains("content://"))
        ZipFile(zip).use { archive ->
            assertEquals(listOf("subjects/001.png", "subjects/003.png", "manifest.json"), archive.entries().toList().map { it.name })
            for (index in listOf(0, 2)) {
                val entry = archive.getEntry("subjects/00${index + 1}.png")
                val bytes = archive.getInputStream(entry).use { it.readBytes() }; assertArrayEquals(images[index].file.readBytes(), bytes)
                val decoded = ImageIO.read(bytes.inputStream()); val actual = decoded.getRGB(0, 0, decoded.width, decoded.height, null, 0, decoded.width)
                assertArrayEquals(cuts[index].pixels(), actual); assertEquals(entry.crc, CRC32().apply { update(bytes) }.value)
            }
            assertEquals(prepared.manifest, archive.getInputStream(archive.getEntry("manifest.json")).use { it.readBytes().toString(Charsets.UTF_8) })
        }
        PhotoSubjectAssetArchive.verify(prepared)
        System.getProperty("noble.subjectEvidence")?.let { path -> File(path).mkdirs(); zip.copyTo(File(path, "synthetic-subject-assets.zip"), overwrite = true) }
    }
    @Test fun initialNonemptyOutputAndAllInputPngFilesRemainUntouchedOnRefusal() {
        val input = source(); val image = encode(cuts(input).first()); val bytes = image.file.readBytes(); val nonempty = temp(".zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssetArchive.create(nonempty, input, listOf(image)) }; assertArrayEquals(byteArrayOf(1, 2, 3), nonempty.readBytes())
        for (selected in listOf(emptyList(), listOf(image, image))) {
            val file = temp(".zip"); assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssetArchive.create(file, input, selected) }; assertFalse(file.exists()); assertArrayEquals(bytes, image.file.readBytes())
        }
    }
    @Test fun anotherSourceOfIdenticalGeometryCannotBorrowTheseCutoutsOrTheirProvenance() {
        val image = encode(cuts().first()); val different = PhotoSubjectAssets.Source.capture(4, 2, IntArray(8) { -1 }); val output = temp(".zip")
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssetArchive.create(output, different, listOf(image)) }; assertFalse(output.exists()); assertTrue(image.file.isFile)
    }
    @Test fun changedReviewedPngAndChangedReviewedZipCannotBeSharedAsApprovedBytes() {
        val input = source(); val image = encode(cuts(input).first()); val original = image.file.readBytes(); val zip = temp(".zip")
        image.file.appendBytes(byteArrayOf(1)); assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssetArchive.create(zip, input, listOf(image)) }; assertFalse(zip.exists())
        image.file.writeBytes(original); val prepared = PhotoSubjectAssetArchive.create(temp(".zip"), input, listOf(image)); prepared.file.appendBytes(byteArrayOf(0))
        assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssetArchive.verify(prepared) }
    }
    @Test fun badPngCrcGeometryFiltersMetadataInflationAndTrailingDataFailClosed() {
        val cut = cuts().first(); val file = temp(".png"); val valid = encode(cut).file.readBytes()
        val cases = listOf(
            valid.copyOf().also { it[29] = (it[29].toInt() xor 1).toByte() },
            png(1, 2, ByteArray(10)),
            png(2, 2, byteArrayOf(5) + ByteArray(8) + byteArrayOf(0) + ByteArray(8)),
            png(2, 2, ByteArray(27)),
            png(2, 2, ByteArray(18), "eXIf"),
            png(2, 2, ByteArray(18), "tEXt"),
            valid + byteArrayOf(0),
        )
        for (bytes in cases) { file.writeBytes(bytes); assertThrows(Exception::class.java) { PhotoSubjectAssetArchive.capture(file, cut) }; assertTrue(file.isFile) }
    }
    @Test fun cancellationDuringPngValidationZipWritingAndFinalVerificationNeverReturnsApproval() {
        val input = source(); val image = encode(cuts(input).first()); var checks = 0
        PhotoSubjectAssetArchive.create(temp(".zip"), input, listOf(image)) { checks++ }
        for (point in listOf(1, 5, checks)) {
            var current = 0; val output = temp(".zip")
            assertThrows(IllegalStateException::class.java) { PhotoSubjectAssetArchive.create(output, input, listOf(image)) { if (++current == point) error("Synthetic owner changed") } }
            assertFalse(output.exists()); assertTrue(image.file.isFile)
        }
    }
    @Test fun preparedEntryListCannotBeMutatedAfterReviewAndBudgetMetadataIsBounded() {
        val input = source(); val image = encode(cuts(input).first()); val prepared = PhotoSubjectAssetArchive.create(temp(".zip"), input, listOf(image))
        assertThrows(UnsupportedOperationException::class.java) { (prepared.entries as MutableList).clear() }
        val forged = PhotoSubjectAssetArchive.Encoded(image.file, image.entry.copy(bytes = PhotoSubjectAssetArchive.MAX_IMAGE_BYTES.toLong() + 1), input.sha256)
        val output = temp(".zip"); assertThrows(IllegalArgumentException::class.java) { PhotoSubjectAssetArchive.create(output, input, listOf(forged)) }; assertFalse(output.exists())
    }
}
