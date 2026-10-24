package com.honorablemobile

import app.honorable.search.EmbeddingService
import app.honorable.search.SearchQuery
import app.honorable.search.SemanticQueryEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemanticQueryEncoderCacheTest {
    @Test fun transientNullIsRetriedOnNextEncodeButOnlyOncePerTextInEachCall() {
        val embeddings = SequencedEmbeddings { text, attempt ->
            if (attempt == 1) null else floatArrayOf(text.length.toFloat(), 1f)
        }
        val encoder = SemanticQueryEncoder(embeddings, cacheSize = 2)
        val query = SearchQuery(
            terms = listOf("red", "car"),
            raw = "red car",
            semanticConcepts = listOf("red car", "a photo of red car", "red car")
        )

        val first = encoder.encode(query)
        assertNull(first.fullQuery)
        assertEquals(0, first.retrievalVectors.size)
        assertEquals(0, first.concepts.size)
        assertEquals(mapOf("red car" to 1, "a photo of red car" to 1), embeddings.calls)

        val second = encoder.encode(query)
        assertArrayEquals(floatArrayOf(7f, 1f), second.fullQuery, 0f)
        assertEquals(2, second.retrievalVectors.size)
        assertEquals(2, second.concepts.size)
        assertEquals(mapOf("red car" to 2, "a photo of red car" to 2), embeddings.calls)

        encoder.encode(query)
        assertEquals(mapOf("red car" to 2, "a photo of red car" to 2), embeddings.calls)
    }

    @Test fun invalidDimensionIsRetriedAndSuccessfulVectorsStayBounded() {
        val embeddings = SequencedEmbeddings { text, attempt ->
            if (text == "bad" && attempt == 1) floatArrayOf(1f) else floatArrayOf(1f, 0f)
        }
        val encoder = SemanticQueryEncoder(embeddings, cacheSize = 2)
        fun encode(text: String) = encoder.encode(SearchQuery(terms = listOf(text), raw = text))

        assertNull(encode("bad").fullQuery)
        assertEquals(1, embeddings.calls["bad"])
        assertArrayEquals(floatArrayOf(1f, 0f), encode("bad").fullQuery, 0f)
        assertEquals(2, embeddings.calls["bad"])

        // Each one-term query also has a photo prompt; both successful vectors fill the LRU.
        encode("new")
        encode("bad")
        assertEquals(3, embeddings.calls["bad"])
    }

    private class SequencedEmbeddings(
        private val answer: (String, Int) -> FloatArray?
    ) : EmbeddingService {
        override val modelId = "synthetic-local"
        override val dimension = 2
        val calls = linkedMapOf<String, Int>()
        override fun image(bytes: ByteArray): FloatArray? = null
        override fun text(query: String): FloatArray? {
            val attempt = (calls[query] ?: 0) + 1
            calls[query] = attempt
            return answer(query, attempt)
        }
    }
}
