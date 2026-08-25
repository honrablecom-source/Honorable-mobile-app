package app.honorable.testlab

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import app.honorable.search.*

class MediaDiscoveryTest {
    @Test fun commonImagesUppercaseNestedAndHidden() {
        val root=Files.createTempDirectory("discovery-");Files.createDirectories(root.resolve("nested"))
        listOf("a.jpg","b.jpeg","c.png","d.webp","UPPER.JPG","nested/inside.PNG",".hidden.jpg").forEach{p->Files.write(root.resolve(p),byteArrayOf(1))}
        val entries=DirectoryMediaSource(root).inspect();val names=entries.map{it.relative}.toSet()
        assertTrue(setOf("a.jpg","b.jpeg","c.png","d.webp","UPPER.JPG","nested/inside.PNG").all{it in names});assertFalse(".hidden.jpg" in names)
        assertTrue(entries.all{it.supported})
        root.toFile().deleteRecursively()
    }

    @Test fun unsupportedAndHeifAreReported() {
        val root=Files.createTempDirectory("unsupported-");Files.write(root.resolve("photo.heic"),byteArrayOf(1));Files.write(root.resolve("notes.gif"),byteArrayOf(1))
        val entries=DirectoryMediaSource(root).inspect();assertEquals(2,entries.size);assertTrue(entries.all{!it.supported&&it.reason!=null});root.toFile().deleteRecursively()
    }

    @Test fun videoExtensionsAreDiscoveredSeparatelyFromDecoding() {
        val root=Files.createTempDirectory("videos-");listOf("a.mp4","b.MOV","c.m4v","d.webm","e.mkv").forEach{Files.write(root.resolve(it),byteArrayOf(1))}
        val entries=DirectoryMediaSource(root).inspect();assertEquals(5,entries.size);assertTrue(entries.all{it.kind==app.honorable.search.MediaKind.VIDEO});assertTrue(entries.all{!it.supported&&it.reason!=null});root.toFile().deleteRecursively()
    }

    @Test fun discoveryRefreshReflectsAddedAndRemovedFiles() {
        val root=Files.createTempDirectory("refresh-");val first=root.resolve("first.jpg");Files.write(first,byteArrayOf(1));val source=DirectoryMediaSource(root);assertEquals(setOf("first.jpg"),source.inspect().map{it.relative}.toSet())
        Files.write(root.resolve("second.png"),byteArrayOf(1));Files.delete(first);assertEquals(setOf("second.png"),source.inspect().map{it.relative}.toSet());root.toFile().deleteRecursively()
    }

    @Test fun indexRefreshDetectsAddedChangedAndStaleRecords() {
        fun record(id:Long,uri:String,modified:Long)=MediaRecord(id,MediaKind.IMAGE,modified,uri=uri)
        val old=listOf(record(1,"keep.jpg",1),record(2,"deleted.jpg",1),record(3,"changed.png",1))
        val current=listOf(record(1,"keep.jpg",1),record(3,"changed.png",2),record(4,"added.webp",1))
        assertEquals(RefreshDelta(1,1,1),refreshDelta(old,current))
    }
}
