package app.honorable.testlab

import app.honorable.search.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO

class FilenameIndependenceTest {
    @Test fun identicalPixelsHaveIdenticalVisualScoreAfterRename() {
        val dir=Files.createTempDirectory("filename-independence-");val first=dir.resolve("dog_running_grass.png");val renamed=dir.resolve("IMG_9274.png")
        val image=BufferedImage(224,224,BufferedImage.TYPE_INT_RGB);val graphics=image.createGraphics();graphics.color=Color.GREEN;graphics.fillRect(0,0,224,224);graphics.dispose();ImageIO.write(image,"png",first.toFile());Files.copy(first,renamed)
        TinyClipBridge().use { clip ->
            val a=clip.image(first)!!;val b=clip.image(renamed)!!
            val query=QueryParser().parse("green image");val text=clip.text(query.raw)!!
            val records=listOf(MediaRecord(1,MediaKind.IMAGE,0,embedding=a,displayName=first.fileName.toString()),MediaRecord(2,MediaKind.IMAGE,0,embedding=b,displayName=renamed.fileName.toString()))
            val scores=SearchRanker().rank(query,records,text).associate{it.media.displayName to it.score}
            assertEquals(scores[first.fileName.toString()]!!,scores[renamed.fileName.toString()]!!,1e-9)
        }
        dir.toFile().deleteRecursively()
    }
}
