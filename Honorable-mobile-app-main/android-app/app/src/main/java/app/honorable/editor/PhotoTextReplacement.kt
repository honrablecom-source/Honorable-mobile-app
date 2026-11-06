package app.honorable.editor

import java.util.Collections

/** Only current editable metadata; never searches pixels, OCR, originals or the media library. */
internal class PhotoTextReplacement private constructor(val base:PhotoEdit,val edit:PhotoEdit,changes:List<Change>,val lockedMatches:Int,val hiddenMatches:Int){
    data class Change(val label:String,val before:String,val after:String,val matches:Int)
    val changes:List<Change> = Collections.unmodifiableList(changes.toList())
    val matches:Int get()=changes.sumOf{it.matches}
    val changed:Boolean get()=edit!=base
    companion object {
        private fun validate(value:String,find:Boolean){
            require(value.length<=200&&(!find||value.isNotBlank())){"Find needs 1–200 characters; replacement allows up to 200."}
            require(value.count{it=='\n'}<=2&&value.none{it.isISOControl()&&it!='\n'}&&value.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)==value){"Use valid text with up to three lines and no control characters."}
        }
        private fun count(text:String,find:String):Int{var n=0;var at=0;while(true){val next=text.indexOf(find,at);if(next<0)return n;n++;at=next+find.length}}
        fun prepare(base:PhotoEdit,find:String,replacement:String):PhotoTextReplacement {
            validate(find,true);validate(replacement,false)
            val changes=mutableListOf<Change>();var locked=0;var hidden=0
            fun replace(label:String,text:String,max:Int):String {
                val n=count(text,find);if(n==0||find==replacement)return text
                require(text.length.toLong()+n.toLong()*(replacement.length-find.length)<=max){"$label would exceed its $max-character limit. Nothing was replaced."}
                val value=text.replace(find,replacement)
                require(value.isNotBlank()&&value.count{it=='\n'}<=2){"$label must keep nonblank text with up to three lines. Nothing was replaced."}
                changes.add(Change(label,text,value,n));return value
            }
            val caption=base.finish.caption?.let{old->val text=replace("Caption",old.text,120);require(text.toByteArray(Charsets.UTF_8).size<=360){"Caption would exceed its text size limit. Nothing was replaced."};if(text==old.text)old else old.copy(text=text)}
            val objects=base.layers.objects.mapIndexed{index,old->
                if(old.type!=PhotoObjectType.TEXT)old
                else if(old.locked){locked+=count(old.text,find);old}
                else if(!old.visible||old.opacity==0f){hidden+=count(old.text,find);old}
                else{val text=replace("Text object ${index+1}",old.text,200);if(text==old.text)old else old.copy(text=text)}
            }
            val result=if(changes.isEmpty())base else base.copy(finish=base.finish.copy(caption=caption),layers=PhotoLayers(objects))
            return PhotoTextReplacement(base,result,changes,locked,hidden)
        }
    }
}
