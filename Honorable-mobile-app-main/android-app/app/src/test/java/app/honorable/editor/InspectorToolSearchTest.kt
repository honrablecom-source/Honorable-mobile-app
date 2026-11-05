package app.honorable.editor

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class InspectorToolSearchTest {
    private val catalogue=listOf("Color & light" to listOf("White balance","Tone curve","Local light"),"On-device AI" to listOf("Person cutout","Read text"),"Project" to listOf("Save project","Restore project"))
    @Test fun emptyQueryShowsEverySuppliedToolInOriginalSectionAndActionOrder(){
        val r=InspectorToolSearch.find(catalogue,"");assertEquals(7,r.total);assertEquals(7,r.found);assertFalse(r.searching);assertEquals(listOf(0,1,2),r.sections.map{it.index});assertEquals(listOf(0,1,2),r.sections[0].toolIndices)
        assertEquals(r,InspectorToolSearch.find(catalogue," \t\n"))
    }
    @Test fun multiWordQueryMatchesBothCategoryAndToolWithoutReordering(){
        val r=InspectorToolSearch.find(catalogue,"COLOR curve");assertEquals(1,r.found);assertEquals(listOf(InspectorToolSearch.Section(0,listOf(1))),r.sections)
        assertEquals(2,InspectorToolSearch.find(catalogue,"project").found)
        assertEquals(1,InspectorToolSearch.find(catalogue,"light local").found)
    }
    @Test fun compatibilityAccentCaseAndPunctuationNormalizationAreDeterministic(){
        val rows=listOf("Créative color" to listOf("Black-and-white mix","ＣＯＬＯＲ"))
        assertEquals(1,InspectorToolSearch.find(rows,"creative BLACK white").found)
        assertEquals(2,InspectorToolSearch.find(rows,"ＣＲＥＡＴＩＶＥ color").found)
        assertEquals(1,InspectorToolSearch.find(listOf("日本語" to listOf("色を調整")),"色 調整").found)
    }
    @Test fun defaultTurkishLocaleCannotChangeMatching(){val old=Locale.getDefault();try{Locale.setDefault(Locale.forLanguageTag("tr-TR"));assertEquals(1,InspectorToolSearch.find(catalogue,"WHITE").found)}finally{Locale.setDefault(old)}}
    @Test fun duplicateNamesKeepDistinctOriginalIndicesAndSourceIsNotMutated(){
        val labels=mutableListOf("Adjust","Adjust");val source=listOf("Picture" to labels,"Sound" to listOf("Adjust"));val before=labels.toList();val r=InspectorToolSearch.find(source,"adjust")
        assertEquals(3,r.found);assertEquals(listOf(0,1),r.sections[0].toolIndices);assertEquals(1,r.sections[1].index);assertEquals(before,labels)
    }
    @Test fun absentOrRestrictedToolsAreNeverInvented(){
        val guest=catalogue.filterNot{it.first=="On-device AI"};assertEquals(0,InspectorToolSearch.find(guest,"person").found);assertEquals(5,InspectorToolSearch.find(guest,"").total)
        val empty=InspectorToolSearch.find(emptyList(),"");assertEquals(0,empty.total);assertTrue(empty.sections.isEmpty())
    }
    @Test fun unknownAndPunctuationOnlyQueriesReturnExplicitEmptyResult(){
        for(query in listOf("missing", ".*", "[", "save cutout")){val r=InspectorToolSearch.find(catalogue,query);assertEquals(0,r.found);assertTrue(r.searching);assertEquals(7,r.total)}
        assertEquals(1,InspectorToolSearch.find(catalogue,"[white]").found)
    }
    @Test fun queryLimitIsBoundedAndRepeatedTermsDoNotDuplicateActions(){
        assertEquals(1,InspectorToolSearch.find(catalogue,"white white WHITE").found);assertEquals(0,InspectorToolSearch.find(catalogue,"x".repeat(80)).found)
        try{InspectorToolSearch.find(catalogue,"x".repeat(81));fail("Expected query limit")}catch(expected:IllegalArgumentException){assertTrue(expected.message!!.contains("80"))}
    }
}
