package app.honorable.editor

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class PhotoTextReplacementTest {
    private fun text(id:String,value:String="Old Old",visible:Boolean=true,locked:Boolean=false,opacity:Float=1f)=PhotoObject(id,PhotoObjectType.TEXT,text=value,visible=visible,locked=locked,opacity=opacity,curve=45f,textStyle=PhotoTextStyle())
    private fun edit(caption:String?="Old caption",objects:List<PhotoObject> = listOf(text("first")))=PhotoEdit(rotation=90,exposure=.2f,finish=PhotoFinish(warmth=.3f,caption=caption?.let{PhotoCaption(it,x=.4f,size=.08f)}),layers=PhotoLayers(objects))
    private fun reject(block:()->Unit){assertTrue(runCatching(block).isFailure)}
    @Test fun richCaptionAndIndependentObjectsChangeOnlyText(){
        val original=edit(objects=listOf(text("first"),text("second","Old second")));val plan=PhotoTextReplacement.prepare(original,"Old","New")
        assertEquals(4,plan.matches);assertEquals(3,plan.changes.size);assertEquals("Old caption",original.finish.caption!!.text)
        assertEquals(original.copy(finish=original.finish.copy(caption=original.finish.caption!!.copy(text="New caption")),layers=PhotoLayers(original.layers.objects.map{it.copy(text=it.text.replace("Old","New"))})),plan.edit)
        assertSame(original,plan.base);assertEquals(listOf("Caption","Text object 1","Text object 2"),plan.changes.map{it.label})
    }
    @Test fun matchingIsLiteralCaseSensitiveAndNonOverlapping(){
        val plan=PhotoTextReplacement.prepare(edit(null,listOf(text("one","aaaa Aaa a.a"))),"aa","$")
        assertEquals(3,plan.matches);assertEquals("$$ A$ a.a",plan.edit.layers.objects.single().text)
        val punctuation=PhotoTextReplacement.prepare(edit(null,listOf(text("one","a.a aXa"))),"a.a","x");assertEquals("x aXa",punctuation.edit.layers.objects.single().text)
    }
    @Test fun lockedHiddenTransparentAndNonTextObjectsAreKept(){
        val original=edit(null,listOf(text("open"),text("locked",locked=true),text("hidden",visible=false),text("transparent",opacity=0f),text("both",visible=false,locked=true),PhotoObject("shape",PhotoObjectType.RECTANGLE,text="Old")))
        val plan=PhotoTextReplacement.prepare(original,"Old","New");assertEquals(2,plan.matches);assertEquals(4,plan.lockedMatches);assertEquals(4,plan.hiddenMatches)
        for(index in 1..5)assertSame(original.layers.objects[index],plan.edit.layers.objects[index])
    }
    @Test fun invalidLaterObjectRejectsTheWholePreparedChange(){
        val original=edit("Old",listOf(text("short","Old"),text("long","Old"+"x".repeat(197))))
        reject{PhotoTextReplacement.prepare(original,"Old","Longer")};assertEquals("Old",original.finish.caption!!.text);assertEquals("Old",original.layers.objects.first().text)
    }
    @Test fun removingPartialTextIsAllowedButBlankTextIsNeverCreated(){
        val plan=PhotoTextReplacement.prepare(edit("Old caption"),"Old ","");assertEquals("caption",plan.edit.finish.caption!!.text)
        reject{PhotoTextReplacement.prepare(edit("Old",emptyList()),"Old","")};reject{PhotoTextReplacement.prepare(edit(null,listOf(text("one","Old"))),"Old"," ")}
    }
    @Test fun unicodeRoundTripsAndCaptionBoundsAreEnforced(){
        val plan=PhotoTextReplacement.prepare(edit("😀 Café",listOf(text("one","😀 😀"))),"😀","界")
        assertEquals("界 Café",plan.edit.finish.caption!!.text);assertEquals("界 界",plan.edit.layers.objects.single().text)
        reject{PhotoTextReplacement.prepare(edit("😀".repeat(60),emptyList()),"😀","界界界")}
        assertEquals("界".repeat(120),PhotoTextReplacement.prepare(edit("x".repeat(120),emptyList()),"x","界").edit.finish.caption!!.text)
    }
    @Test fun inputsRejectBlankOversizeControlsAndMalformedUtf16(){
        val original=edit();for(find in listOf(""," ","x".repeat(201),"\t","\r","\u0000","\uD800"))reject{PhotoTextReplacement.prepare(original,find,"New")}
        for(replacement in listOf("x".repeat(201),"\t","\uD800"))reject{PhotoTextReplacement.prepare(original,"Old",replacement)}
    }
    @Test fun existingLineLimitsApplyToTheWholeResult(){
        assertEquals("a\nb",PhotoTextReplacement.prepare(edit(null,listOf(text("one","ab"))),"a","a\n").edit.layers.objects.single().text)
        reject{PhotoTextReplacement.prepare(edit(null,listOf(text("one","a a a"))),"a","a\n")};reject{PhotoTextReplacement.prepare(edit(),"Old","a\nb\nc\nd")}
    }
    @Test fun noChangesKeepTheSameModelAndChangeListIsReadOnly(){
        val original=edit();for((find,replacement)in listOf("Missing" to "New","Old" to "Old")){val plan=PhotoTextReplacement.prepare(original,find,replacement);assertFalse(plan.changed);assertSame(original,plan.edit);assertEquals(0,plan.matches)}
        val changed=PhotoTextReplacement.prepare(original,"Old","New");try{(changed.changes as MutableList).clear();fail("Mutable changes")}catch(_:UnsupportedOperationException){}
    }
    @Test fun exactCaptionAndObjectLengthLimitsAreAcceptedWithoutTruncation(){
        val original=edit("x".repeat(120),listOf(text("one","x".repeat(200))));val plan=PhotoTextReplacement.prepare(original,"x","y")
        assertEquals(320,plan.matches);assertEquals(120,plan.edit.finish.caption!!.text.length);assertEquals(200,plan.edit.layers.objects.single().text.length);reject{PhotoTextReplacement.prepare(original,"x","yy")}
    }
    @Test fun preparedTextRetainsNativeProjectSerializationAndScopeBinding(){
        val original=edit();val plan=PhotoTextReplacement.prepare(original,"Old","New");val source="content://synthetic/photo";val fingerprint=PhotoProjectCodec.fingerprint(ByteArrayInputStream("synthetic".toByteArray()))
        val bytes=PhotoProjectCodec.encode(PhotoPresetNamespace.GUEST,source,fingerprint,plan.edit)
        assertEquals(plan.edit,PhotoProjectCodec.decode(bytes,PhotoPresetNamespace.GUEST,source,fingerprint));assertEquals(original.finish.caption!!.x,plan.edit.finish.caption!!.x)
    }
    @Test fun maximumTextItemCountIsBoundedAndAllOccurrencesAreIncluded(){
        val original=edit("a".repeat(120),List(32){text("item$it","a".repeat(200))});val plan=PhotoTextReplacement.prepare(original,"a","b")
        assertEquals(33,plan.changes.size);assertEquals(6520,plan.matches);assertEquals(32,plan.edit.layers.objects.size);reject{PhotoTextReplacement.prepare(original,"a","b".repeat(200))}
    }
}
