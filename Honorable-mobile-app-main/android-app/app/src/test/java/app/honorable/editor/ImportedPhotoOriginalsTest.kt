package app.honorable.editor
import org.junit.Assert.*
import org.junit.Test

class ImportedPhotoOriginalsTest {
    @Test fun localDocumentProvenanceIsBoundedAndDeduplicated() {
        assertEquals(listOf("content://media/document/image%3A1"),ImportedPhotoOriginals.validate(listOf("content://media/document/image%3A1","content://media/document/image%3A1")))
        assertEquals(emptyList<String>(),ImportedPhotoOriginals.validate(emptyList()))
    }
    @Test fun nonLocalAndMalformedProvenanceIsRejected() {
        for(value in listOf("https://example.test/a","file:///private/a","content:///missing-authority","content://media/a#fragment","content://media/a\u0000","content://media/\uD800"))assertTrue(runCatching{ImportedPhotoOriginals.validate(listOf(value))}.isFailure)
    }
    @Test fun oversizeListsAndUrisAreRejectedBeforeOpeningProviders() {
        assertTrue(runCatching{ImportedPhotoOriginals.validate(List(17){"content://media/$it"})}.isFailure)
        assertTrue(runCatching{ImportedPhotoOriginals.validate(listOf("content://media/"+"a".repeat(2048)))}.isFailure)
    }
}
