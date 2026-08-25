package app.honorable.search

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.sqrt

class SearchCoreTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneId.of("UTC"))
    private val parser = QueryParser(fixedClock)

    @Test fun `query parser decomposes difficult multi-part query`() {
        val q=parser.parse("video of my son playing tennis outside wearing a blue shirt")
        assertEquals(MediaKind.VIDEO,q.mediaKind);assertEquals(setOf("blue"),q.colors);assertTrue("playing" in q.activities)
        assertTrue("outdoor" in q.scenes);assertTrue("shirt" in q.objects);assertTrue("son" in q.peopleTerms);assertTrue(q.semanticConcepts.isNotEmpty())
    }
    @Test fun `screenshot parser extracts OCR phrase and subtype`() {
        val q=parser.parse("screenshot showing my Air Canada flight confirmation")
        assertEquals(MediaSubtype.SCREENSHOT,q.mediaSubtype);assertEquals(MediaKind.IMAGE,q.mediaKind);assertTrue(q.ocrTerms.any{it=="air canada"})
    }
    @Test fun `negative terms are separated`() { val q=parser.parse("beach photos without people");assertEquals(setOf("people"),q.negativeTerms);assertFalse("people" in q.terms) }
    @Test fun `last summer uses deterministic device-zone bounds`() {
        val q=parser.parse("videos of tennis outside last summer")
        assertEquals(Instant.parse("2025-06-01T00:00:00Z").toEpochMilli(),q.afterEpochMs);assertEquals(Instant.parse("2025-09-01T00:00:00Z").toEpochMilli()-1,q.beforeEpochMs)
    }
    @Test fun `ambiguous June is not guessed`() { val q=parser.parse("photos around June");assertNull(q.afterEpochMs);assertEquals("around June",q.unresolvedTemporalPhrase) }
    @Test fun `date and time of day can coexist`() { val q=parser.parse("photos from last Christmas at night");assertNotNull(q.afterEpochMs);assertEquals(TimeOfDay.NIGHT,q.timeOfDay) }
    @Test fun `query refinement preserves prior concepts and adds filters`() {
        val refiner=QueryRefiner(parser);val tennis=parser.parse("tennis");val videos=refiner.refine(tennis,"only videos");val lastYear=refiner.refine(videos,"from last year")
        assertTrue("tennis" in lastYear.terms);assertEquals(MediaKind.VIDEO,lastYear.mediaKind);assertNotNull(lastYear.afterEpochMs)
    }
    @Test fun `hard media filter applies`() { val q=SearchQuery(listOf("beach"),mediaKind=MediaKind.VIDEO);val p=MediaRecord(1,MediaKind.IMAGE,0,labels=setOf("beach"));val v=MediaRecord(2,MediaKind.VIDEO,0,labels=setOf("beach"));assertEquals(listOf(2L),SearchRanker().rank(q,listOf(p,v)).map{it.media.id}) }
    @Test fun `video query returns real metadata-only video fallback`() { val q=parser.parse("find the video where my son played tennis");val video=MediaRecord(7,MediaKind.VIDEO,0,uri="content://media/video/7",displayName="VID_7.mp4");val result=SearchRanker().rank(q,listOf(video)).single();assertEquals("content://media/video/7",result.media.uri);assertEquals(MatchConfidence.WEAK,result.confidence) }
    @Test fun `no match does not invent a bundled result`() { val indexed=MediaRecord(9,MediaKind.IMAGE,0,labels=setOf("receipt"),uri="content://media/image/9");assertTrue(SearchRanker().rank(parser.parse("tennis outside"),listOf(indexed)).isEmpty()) }
    @Test fun `hard screenshot filter applies`() { val q=parser.parse("flight screenshot");val photo=MediaRecord(1,MediaKind.IMAGE,0,ocr="flight",isScreenshot=false);val shot=MediaRecord(2,MediaKind.IMAGE,0,ocr="flight",isScreenshot=true);assertEquals(listOf(2L),SearchRanker().rank(q,listOf(photo,shot)).map{it.media.id}) }
    @Test fun `hard location filter applies without remote geocoding`() { val q=SearchQuery(listOf("meal"),location="Toronto");val a=MediaRecord(1,MediaKind.IMAGE,0,location="Toronto, Ontario",labels=setOf("meal"));val b=MediaRecord(2,MediaKind.IMAGE,0,location="Montreal",labels=setOf("meal"));assertEquals(listOf(1L),SearchRanker().rank(q,listOf(b,a)).map{it.media.id}) }
    @Test fun `exact OCR phrase outranks labels`() { val q=parser.parse("screenshot showing Air Canada confirmation");val o=MediaRecord(1,MediaKind.IMAGE,0,ocr="AIR CANADA — Confirmation Q7",isScreenshot=true);val l=MediaRecord(2,MediaKind.IMAGE,0,labels=setOf("air canada","confirmation"),isScreenshot=true);assertEquals(1L,SearchRanker().rank(q,listOf(l,o)).first().media.id) }
    @Test fun `OCR normalizer tolerates punctuation case and common mistakes`() { assertTrue(OcrNormalizer.containsFuzzyToken(OcrNormalizer.normalize("PASS-P0RT"),"passport"));assertEquals("air canada flight",OcrNormalizer.normalize("Air Canada—Flight!")) }
    @Test fun `multi-concept coverage beats one strong concept`() {
        val q=SearchQuery(listOf("red","car","snow"),semanticConcepts=listOf("red car","snow"));val one=MediaRecord(1,MediaKind.IMAGE,0,embedding=floatArrayOf(1f,0f));val balanced=MediaRecord(2,MediaKind.IMAGE,0,embedding=floatArrayOf(.7f,.7f))
        val vectors=mapOf("red car" to floatArrayOf(1f,0f),"snow" to floatArrayOf(0f,1f));assertEquals(2L,SearchRanker().rank(q,listOf(one,balanced),conceptVectors=vectors).first().media.id)
    }
    @Test fun `color evidence improves relevant result`() { val q=parser.parse("red car");val red=MediaRecord(1,MediaKind.IMAGE,0,labels=setOf("car"),dominantColors=setOf("red"));val blue=MediaRecord(2,MediaKind.IMAGE,0,labels=setOf("car"),dominantColors=setOf("blue"));assertEquals(1L,SearchRanker().rank(q,listOf(blue,red)).first().media.id) }
    @Test fun `color analyzer identifies sampled red`() { assertEquals("red",ColorEvidenceAnalyzer.dominantColors(IntArray(64){0xffff2020.toInt()},1).single()) }
    @Test fun `negative labels receive cautious penalty`() { val q=parser.parse("tennis not indoors");val outside=MediaRecord(1,MediaKind.IMAGE,0,labels=setOf("tennis","outdoor"));val inside=MediaRecord(2,MediaKind.IMAGE,0,labels=setOf("tennis","indoors"));assertEquals(1L,SearchRanker().rank(q,listOf(inside,outside)).first().media.id) }
    @Test fun `multiple distinct video frames aggregate and retain best timestamp`() {
        val q=SearchQuery(listOf("tennis","serve"));val weak=MediaRecord(1,MediaKind.VIDEO,0,videoFrames=listOf(VideoFrame(1000,"",setOf("tennis"),null)))
        val strong=MediaRecord(2,MediaKind.VIDEO,0,videoFrames=listOf(VideoFrame(42000,"tennis serve",setOf("outdoor"),null),VideoFrame(48000,"serve",setOf("tennis"),null)))
        val result=SearchRanker().rank(q,listOf(weak,strong)).first();assertEquals(2L,result.media.id);assertEquals(42000L,result.bestTimestampMs);assertTrue(result.explanations.any{it.contains("00:42")})
    }
    @Test fun `representative frame selector drops adjacent duplicate scenes`() { val frames=listOf(FrameCandidate(0,1),FrameCandidate(500,1),FrameCandidate(2000,1),FrameCandidate(2200,-1));val selected=RepresentativeFrameSelector.select(frames);assertEquals(listOf(0L,2000L,2200L),selected.map{it.timestampMs}) }
    @Test fun `weak result exposes weak confidence`() { val match=SearchRanker().rank(SearchQuery(listOf("car")),listOf(MediaRecord(1,MediaKind.IMAGE,0,labels=setOf("car")))).single();assertEquals(MatchConfidence.WEAK,match.confidence) }
    @Test fun `strong hybrid result exposes score breakdown`() { val q=parser.parse("Air Canada flight screenshot");val media=MediaRecord(1,MediaKind.IMAGE,0,ocr="Air Canada flight confirmation",labels=setOf("flight"),isScreenshot=true);val match=SearchRanker().rank(q,listOf(media)).single();assertEquals(MatchConfidence.STRONG,match.confidence);assertTrue(match.breakdown.ocr>0);assertTrue(SearchDebugTool.report(q,match).contains("final=")) }
    @Test fun `semantic query encoder caches full and concept text inference`() { val fake=CountingEmbeddings();val q=SearchQuery(listOf("car"),raw="red car snow",semanticConcepts=listOf("red car","snow"));val encoder=SemanticQueryEncoder(fake);encoder.encode(q);encoder.encode(q);assertEquals(3,fake.calls) }
    @Test fun `embedding failure preserves non-semantic fallback`() { val failed=SemanticQueryEncoder(FailingEmbeddings()).encode(parser.parse("red car"));assertNull(failed.fullQuery);val media=MediaRecord(1,MediaKind.IMAGE,0,labels=setOf("red car"));assertEquals(1L,SearchRanker().rank(parser.parse("red car"),listOf(media),failed.fullQuery,failed.concepts).single().media.id) }
    @Test fun `index compatibility detects preprocessing change`() { val current=IndexCompatibility();assertTrue(current.compatibleWith(current.copy()));assertFalse(current.compatibleWith(current.copy(preprocessingVersion="old")));assertEquals(5,current.schemaVersion) }
    @Test fun `vector index returns nearest normalized vector`() { val index=LocalVectorIndex();index.upsert(1,floatArrayOf(1f,0f));index.upsert(2,floatArrayOf(0f,1f));assertEquals(1L,index.nearest(floatArrayOf(.9f,.1f),1).single().first) }
    @Test fun `evaluation harness computes reproducible retrieval metrics`() { val cases=listOf(EvaluationCase("q1",setOf(2),"easy","object"),EvaluationCase("q2",setOf(4),"hard","ocr"));val metrics=SearchEvaluationHarness.evaluate(cases){if(it.query=="q1")listOf(2,3)else listOf(9,4)};assertEquals(.5,metrics.recallAt1,0.0);assertEquals(1.0,metrics.recallAt5,0.0);assertEquals(.75,metrics.mrr,0.0) }

    private class CountingEmbeddings:EmbeddingService { var calls=0;override val modelId="test";override val dimension=2;override fun image(bytes:ByteArray)=floatArrayOf(1f,0f);override fun text(query:String):FloatArray{calls++;return floatArrayOf(1f,0f)} }
    private class FailingEmbeddings:EmbeddingService { override val modelId="failed";override val dimension=2;override fun image(bytes:ByteArray):FloatArray?=null;override fun text(query:String):FloatArray?=null }
}
