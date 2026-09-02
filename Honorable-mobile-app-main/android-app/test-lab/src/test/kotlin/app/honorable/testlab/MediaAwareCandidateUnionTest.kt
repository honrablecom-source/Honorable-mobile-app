package app.honorable.testlab

import app.honorable.search.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MediaAwareCandidateUnionTest {
    private fun media(id:Long,kind:MediaKind,ocr:String="",frames:List<VideoFrame> = emptyList())=MediaRecord(id,kind,0,ocr=ocr,embedding=floatArrayOf(1f,0f),videoFrames=frames)
    private fun match(record:MediaRecord,score:Double)=SearchMatch(record,score,emptyList())

    @Test fun `V1 behavior does not enable cross-media fusion`() {
        assertFalse(MediaAwareCandidateUnion.enabledFor(SeranModelProfile.SERAN_V1))
        assertTrue(MediaAwareCandidateUnion.enabledFor(SeranModelProfile.SERAN_V2))
        assertTrue(MediaAwareCandidateUnion.enabledFor(SeranModelProfile.SERAN_V3))
    }

    @Test fun `moment and animated film language select implicit video intent`() {
        assertTrue(MediaAwareCandidateUnion.hasImplicitVideoIntent(QueryParser().parse("the moment the path is centered")))
        assertTrue(MediaAwareCandidateUnion.hasImplicitVideoIntent(QueryParser().parse("the animated film about a dragon")))
        assertFalse(MediaAwareCandidateUnion.hasImplicitVideoIntent(QueryParser().parse("red haired woman portrait")))
    }

    @Test fun `explicit image query cannot be displaced by video`() {
        val query=QueryParser().parse("photo of a dog")
        val image=match(media(1,MediaKind.IMAGE),2.0);val video=match(media(2,MediaKind.VIDEO),20.0)
        assertEquals(listOf(1L),MediaAwareCandidateUnion.merge(query,listOf(image),listOf(video)).map{it.media.id})
    }

    @Test fun `explicit video query still searches video lane`() {
        val query=QueryParser().parse("video of a dog")
        val image=match(media(1,MediaKind.IMAGE),20.0);val video=match(media(2,MediaKind.VIDEO),2.0)
        assertEquals(listOf(2L),MediaAwareCandidateUnion.merge(query,listOf(image),listOf(video)).map{it.media.id})
    }

    @Test fun `frames beyond bounded aggregation do not add a global score advantage`() {
        val query=QueryParser().parse("dog running")
        val frame=VideoFrame(0,"dog running",setOf("dog","running"),floatArrayOf(1f,0f))
        val short=media(1,MediaKind.VIDEO,frames=(0..2).map{frame.copy(timestampMs=it*2_000L)})
        val long=media(2,MediaKind.VIDEO,frames=(0..30).map{frame.copy(timestampMs=it*2_000L)})
        val ranked=SearchRanker().rank(query,listOf(short,long),floatArrayOf(1f,0f))
        assertEquals(ranked.first{it.media.id==1L}.score,ranked.first{it.media.id==2L}.score,1e-9)
    }

    @Test fun `unspecified fusion preserves image lane ordering and one result per video`() {
        val query=QueryParser().parse("dog running")
        val images=listOf(match(media(2,MediaKind.IMAGE),4.0),match(media(1,MediaKind.IMAGE),2.0))
        val video=match(media(3,MediaKind.VIDEO),40.0)
        val merged=MediaAwareCandidateUnion.merge(query,images,listOf(video,video))
        assertEquals(2L,merged.first().media.id)
        assertEquals(1,merged.count{it.media.id==3L})
        assertTrue(merged.indexOfFirst{it.media.id==2L}<merged.indexOfFirst{it.media.id==1L})
    }

    @Test fun `OCR evidence remains available in image lane`() {
        val query=QueryParser().parse("photo saying \"package delivered\"")
        val result=SearchRanker().rank(query,listOf(media(1,MediaKind.IMAGE,"package delivered")),floatArrayOf(1f,0f)).single()
        assertTrue(result.breakdown.ocr>0.0)
    }
}
