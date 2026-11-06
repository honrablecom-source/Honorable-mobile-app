package app.honorable.editor

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class PhotoProjectVersionsTest {
    private val scope=PhotoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/photo")
    private val original="Synthetic original photo bytes".toByteArray()
    private val fingerprint=PhotoProjectCodec.fingerprint(ByteArrayInputStream(original))
    private val edit=PhotoEdit(exposure=.25f,finish=PhotoFinish(caption=PhotoCaption("Keep caption")),layers=PhotoLayers(listOf(PhotoObject("text",PhotoObjectType.TEXT,text="Keep object"))),alphaGradient=PhotoAlphaGradient(enabled=true))
    private fun version(name:String="First",originals:List<String> = listOf("content://synthetic/protected"))=PhotoSavedVersion.create(scope,name,fingerprint,edit,originals)
    private fun reject(block:()->Unit){assertTrue(runCatching(block).isFailure)}
    @Test fun catalogRoundTripsRichEditsProvenanceAndEmptyShelf(){
        val catalog=PhotoVersionCatalog(listOf(version()));val decoded=PhotoVersionCodec.decode(scope,PhotoVersionCodec.encode(scope,catalog))
        assertEquals(catalog,decoded);assertEquals(edit,decoded.entries.single().edit(scope,fingerprint));assertEquals(listOf("content://synthetic/protected"),decoded.entries.single().originals)
        assertEquals(PhotoVersionCatalog(),PhotoVersionCodec.decode(scope,PhotoVersionCodec.encode(scope,PhotoVersionCatalog())))
    }
    @Test fun ownerOriginalAndChangedSourceBytesCannotReuseAnEntry(){
        val entry=version();val other=PhotoVersionScope(PhotoPresetNamespace.GUEST,"content://synthetic/other")
        val owner=PhotoVersionScope(PhotoPresetNamespace.account("other","https://example.test"),scope.source)
        reject{entry.edit(other,fingerprint)};reject{entry.edit(owner,fingerprint)}
        reject{entry.edit(scope,PhotoProjectCodec.fingerprint(ByteArrayInputStream("Changed original".toByteArray())))}
        reject{PhotoVersionCodec.decode(other,PhotoVersionCodec.encode(scope,PhotoVersionCatalog(listOf(entry))))}
        reject{PhotoVersionCodec.encode(owner,PhotoVersionCatalog(listOf(entry)))}
        reject{PhotoVersionScope("../owner",scope.source)};reject{PhotoVersionScope(PhotoPresetNamespace.GUEST,"file:///private")}
    }
    @Test fun immutableEntriesDetachEncodedArraysCatalogAndProvenanceInputs(){
        val originals=mutableListOf("content://synthetic/protected");val entry=version(originals=originals);val values=mutableListOf(entry);val catalog=PhotoVersionCatalog(values)
        originals.clear();values.clear();entry.encoded().fill(0)
        assertEquals(1,catalog.entries.size);assertEquals(edit,entry.edit(scope,fingerprint));assertEquals(listOf("content://synthetic/protected"),entry.originals)
        try{(catalog.entries as MutableList).clear();fail("Mutable shelf")}catch(_:UnsupportedOperationException){}
        try{(entry.originals as MutableList).clear();fail("Mutable source list")}catch(_:UnsupportedOperationException){}
    }
    @Test fun namesIdentityAndNormalizedUniquenessAreStrict(){
        PhotoVersionName.validate("😀".repeat(20));for(name in listOf(""," leading","bad\n","x".repeat(41),"\uD800"))reject{PhotoVersionName.validate(name)}
        val entry=version("Title");reject{PhotoVersionCatalog(listOf(entry,version("ＴＩＴＬＥ")))}
        reject{PhotoVersionCatalog(listOf(entry,PhotoSavedVersion.create(scope,"Other",fingerprint,edit,emptyList(),entry.id)))}
        reject{PhotoSavedVersion.create(scope,"Bad",fingerprint,edit,emptyList(),"not-an-id")}
        assertFalse(scope.storageName.contains("Title"));version("../safe payload")
    }
    @Test fun entryCountAndTotalEncodedBudgetRefuseWithoutTruncatingEdits(){
        val twenty=List(20){version("Version $it")};assertEquals(20,PhotoVersionCatalog(twenty).entries.size);reject{PhotoVersionCatalog(twenty+version("Extra"))}
        val rich=PhotoEdit(layers=PhotoLayers(List(32){PhotoObject("text$it",PhotoObjectType.TEXT,text="界".repeat(200))}))
        val originals=List(16){"content://synthetic/$it/"+"x".repeat(1900)}
        val large=List(20){PhotoSavedVersion.create(scope,"Large $it",fingerprint,rich,originals)}
        assertTrue(74L+large.sumOf{it.encodedSize.toLong()}>PhotoVersionCodec.MAX_BYTES);reject{PhotoVersionCatalog(large)}
    }
    private fun forged(bytes:ByteArray,change:(ByteArray)->ByteArray):ByteArray {
        val body=change(bytes.copyOfRange(40,bytes.size));return ByteBuffer.allocate(40+body.size).putInt(0x4e505653).putInt(body.size).put(MessageDigest.getInstance("SHA-256").digest(body)).put(body).array()
    }
    @Test fun corruptFutureTrailingMalformedUtf8CountsAndLengthsFailClosed(){
        val bytes=PhotoVersionCodec.encode(scope,PhotoVersionCatalog(listOf(version())))
        reject{PhotoVersionCodec.decode(scope,bytes.copyOf(bytes.size-1))};reject{PhotoVersionCodec.decode(scope,bytes.copyOf().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})}
        for(transform in listOf<(ByteArray)->ByteArray>({it.also{data->data[0]=2}},{it.also{data->data[33]=21}},{it+byteArrayOf(0)},{it.also{data->data[50]=(-1).toByte();data[51]=(-1).toByte()}},{it.also{data->data[52]=0xc3.toByte();data[53]=0x28}}))reject{PhotoVersionCodec.decode(scope,forged(bytes,transform))}
        reject{PhotoVersionCodec.decode(scope,ByteArray(PhotoVersionCodec.MAX_BYTES+1))}
    }
    @Test fun fingerprintsBindActualStreamBytesWithoutCopyingPixelsIntoTheCatalog(){
        assertNotEquals(fingerprint,PhotoProjectCodec.fingerprint(ByteArrayInputStream(original+byteArrayOf(0))))
        reject{PhotoProjectCodec.fingerprint(ByteArrayInputStream(byteArrayOf()))}
        reject{PhotoProjectCodec.fingerprint(ByteArrayInputStream(original)){error("Cancelled")}}
        val bytes=PhotoVersionCodec.encode(scope,PhotoVersionCatalog(listOf(version(originals=emptyList()))))
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains(String(original)));assertFalse(String(bytes,Charsets.ISO_8859_1).contains(scope.source))
    }
    @Test fun provenanceIsBoundedCanonicalAndNeverAcceptsFileOrMalformedSources(){
        val entry=version(originals=listOf("content://synthetic/protected","content://synthetic/protected"));assertEquals(1,entry.originals.size)
        reject{version(originals=listOf("file:///private"))};reject{version(originals=List(17){"content://synthetic/$it"})}
        reject{PhotoSavedVersion.decode(scope,entry.id,entry.name,entry.fingerprint,entry.encoded(),listOf("content://synthetic/protected","content://synthetic/protected"))}
    }
}
