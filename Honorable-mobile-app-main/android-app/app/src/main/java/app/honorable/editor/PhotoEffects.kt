package app.honorable.editor

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

enum class PhotoEffectTool(val title: String) {
    NEGATIVE("Negative"), SOLARIZE("Solarize"), SEPIA("Sepia"), DUOTONE("Duotone"), GAMMA("Gamma"),
    PIXELATE("Pixelate"), BLUR("Soft blur"), EDGES("Edge outline"), EMBOSS("Emboss"),
    CHROMATIC("Chromatic offset"), HALFTONE("Halftone dots"), SCANLINES("Scanlines")
}

/** Bounded local effects. They never carry media paths, credentials or image data. */
data class PhotoEffects(
    val negative: Boolean = false, val solarize: Float = -1f, val sepia: Float = 0f,
    val duotone: Float = 0f, val dark: Int = 0xFF14243A.toInt(), val light: Int = 0xFFFFDC99.toInt(),
    val gamma: Float = 1f, val pixelate: Int = 0, val blur: Int = 0,
    val edges: Float = 0f, val emboss: Float = 0f, val chromatic: Int = 0,
    val halftone: Int = 0, val scanlines: Float = 0f,
) {
    init {
        require(solarize.isFinite() && (solarize == -1f || solarize in 0f..1f))
        for(v in listOf(sepia,duotone,edges,emboss,scanlines))require(v.isFinite() && v in 0f..1f)
        require(dark ushr 24 == 255 && light ushr 24 == 255)
        require(gamma.isFinite() && gamma in .4f..2.5f)
        require(pixelate == 0 || pixelate in 2..64)
        require(blur in 0..20 && chromatic in -20..20)
        require(halftone == 0 || halftone in 3..32)
    }
    val isIdentity: Boolean get() = !negative && solarize == -1f && sepia == 0f && duotone == 0f && gamma == 1f &&
        pixelate == 0 && blur == 0 && edges == 0f && emboss == 0f && chromatic == 0 && halftone == 0 && scanlines == 0f
    internal fun write(out: DataOutputStream) {
        out.writeBoolean(negative)
        for(v in listOf(solarize,sepia,duotone))out.writeFloat(v)
        out.writeInt(dark);out.writeInt(light);out.writeFloat(gamma);out.writeInt(pixelate);out.writeInt(blur)
        out.writeFloat(edges);out.writeFloat(emboss);out.writeInt(chromatic);out.writeInt(halftone);out.writeFloat(scanlines)
    }
    companion object {
        internal fun read(input: DataInputStream) = PhotoEffects(input.readBoolean(),input.readFloat(),input.readFloat(),input.readFloat(),
            input.readInt(),input.readInt(),input.readFloat(),input.readInt(),input.readInt(),input.readFloat(),input.readFloat(),
            input.readInt(),input.readInt(),input.readFloat())
    }
}

/** At most one full-size scratch array. Every effect preserves each source pixel's alpha. */
object PhotoEffectsKernel {
    private fun channel(p: Int, shift: Int) = (p ushr shift) and 255
    private fun pack(original: Int, r: Double, g: Double, b: Double): Int = (original and -0x1000000) or
        (r.roundToInt().coerceIn(0,255) shl 16) or (g.roundToInt().coerceIn(0,255) shl 8) or b.roundToInt().coerceIn(0,255)
    private fun gray(p: Int) = .2126 * channel(p,16) + .7152 * channel(p,8) + .0722 * channel(p,0)
    private fun blend(a: Int, b: Int, amount: Float) = pack(a,
        channel(a,16)+(channel(b,16)-channel(a,16))*amount.toDouble(),
        channel(a,8)+(channel(b,8)-channel(a,8))*amount.toDouble(),
        channel(a,0)+(channel(b,0)-channel(a,0))*amount.toDouble())

    fun applyInPlace(pixels: IntArray, width: Int, height: Int, e: PhotoEffects) {
        require(width > 0 && height > 0 && width.toLong()*height == pixels.size.toLong())
        if(e.isIdentity)return
        val gamma=DoubleArray(256){255*(it/255.0).pow(1.0/e.gamma)}
        for(i in pixels.indices) {
            val original=pixels[i];if(original ushr 24 == 0)continue
            var r=channel(original,16).toDouble();var g=channel(original,8).toDouble();var b=channel(original,0).toDouble()
            if(e.negative){r=255-r;g=255-g;b=255-b}
            if(e.solarize>=0){val cut=e.solarize*255;if(r>cut)r=255-r;if(g>cut)g=255-g;if(b>cut)b=255-b}
            if(e.gamma!=1f){r=gamma[r.roundToInt()];g=gamma[g.roundToInt()];b=gamma[b.roundToInt()]}
            if(e.sepia>0){val rr=.393*r+.769*g+.189*b;val gg=.349*r+.686*g+.168*b;val bb=.272*r+.534*g+.131*b
                r+=(rr-r)*e.sepia;g+=(gg-g)*e.sepia;b+=(bb-b)*e.sepia}
            if(e.duotone>0){val l=(.2126*r+.7152*g+.0722*b).coerceIn(0.0,255.0)/255
                r+=(channel(e.dark,16)+(channel(e.light,16)-channel(e.dark,16))*l-r)*e.duotone
                g+=(channel(e.dark,8)+(channel(e.light,8)-channel(e.dark,8))*l-g)*e.duotone
                b+=(channel(e.dark,0)+(channel(e.light,0)-channel(e.dark,0))*l-b)*e.duotone}
            pixels[i]=pack(original,r,g,b)
        }
        val spatial=e.pixelate>0||e.blur>0||e.edges>0||e.emboss>0||e.chromatic!=0||e.halftone>0
        if(spatial) {
            val scratch=IntArray(pixels.size)
            fun run(operation: (IntArray,IntArray)->Unit){operation(pixels,scratch);scratch.copyInto(pixels)}
            fun scale(n: Int)=max(1,(abs(n)*minOf(width,height)/512.0).roundToInt())
            if(e.pixelate>0)run{src,dst->blocks(src,dst,width,height,scale(e.pixelate),false)}
            if(e.blur>0){val radius=scale(e.blur).coerceAtMost(256)
                run{src,dst->blur(src,dst,width,height,radius,true)}
                run{src,dst->blur(src,dst,width,height,radius,false)}}
            if(e.edges>0)run{src,dst->neighborhood(src,dst,width,height,e.edges,false)}
            if(e.emboss>0)run{src,dst->neighborhood(src,dst,width,height,e.emboss,true)}
            if(e.chromatic!=0)run{src,dst->
                val offset=scale(e.chromatic)*if(e.chromatic<0)-1 else 1
                for(y in 0 until height)for(x in 0 until width){val i=y*width+x;val p=src[i]
                    val r=src[y*width+(x-offset).coerceIn(0,width-1)].takeIf{it ushr 24!=0}?:p
                    val b=src[y*width+(x+offset).coerceIn(0,width-1)].takeIf{it ushr 24!=0}?:p
                    dst[i]=if(p ushr 24==0)p else pack(p,channel(r,16).toDouble(),channel(p,8).toDouble(),channel(b,0).toDouble())}}
            if(e.halftone>0)run{src,dst->blocks(src,dst,width,height,max(3,scale(e.halftone)),true)}
        }
        if(e.scanlines>0){val band=max(1,height/256)
            for(y in 0 until height)if((y/band)%2==1)for(x in 0 until width){val i=y*width+x;val p=pixels[i]
                if(p ushr 24!=0)pixels[i]=pack(p,channel(p,16)*(1-.65*e.scanlines),channel(p,8)*(1-.65*e.scanlines),channel(p,0)*(1-.65*e.scanlines))}}
    }

    private fun blocks(src:IntArray,dst:IntArray,w:Int,h:Int,size:Int,dots:Boolean) {
        for(top in 0 until h step size)for(left in 0 until w step size){
            val right=min(w,left+size);val bottom=min(h,top+size)
            var aa=0L;var rr=0L;var gg=0L;var bb=0L
            for(y in top until bottom)for(x in left until right){val p=src[y*w+x];val a=(p ushr 24).toLong()
                aa+=a;rr+=channel(p,16)*a;gg+=channel(p,8)*a;bb+=channel(p,0)*a}
            val average=if(aa==0L)0 else pack(-1,rr.toDouble()/aa,gg.toDouble()/aa,bb.toDouble()/aa)
            val radius=sqrt((1-gray(average)/255).coerceIn(0.0,1.0))*min(right-left,bottom-top)/2
            val cx=(left+right-1)/2.0;val cy=(top+bottom-1)/2.0
            for(y in top until bottom)for(x in left until right){val i=y*w+x;val p=src[i]
                dst[i]=if(p ushr 24==0)p else if(dots){val level=if((x-cx).pow(2)+(y-cy).pow(2)<=radius*radius)0 else 255
                    pack(p,level.toDouble(),level.toDouble(),level.toDouble())}
                else (p and -0x1000000) or (average and 0xFFFFFF)}
        }
    }
    private fun blur(src:IntArray,dst:IntArray,w:Int,h:Int,radius:Int,horizontal:Boolean) {
        val lines=if(horizontal)h else w;val length=if(horizontal)w else h
        for(line in 0 until lines){
            fun index(position:Int)=if(horizontal)line*w+position else position*w+line
            var a=0L;var r=0L;var g=0L;var b=0L
            fun add(position:Int,sign:Int){if(position !in 0 until length)return;val p=src[index(position)];val alpha=(p ushr 24).toLong()*sign
                a+=alpha;r+=channel(p,16)*alpha;g+=channel(p,8)*alpha;b+=channel(p,0)*alpha}
            for(k in 0..min(radius,length-1))add(k,1)
            for(position in 0 until length){val i=index(position);val p=src[i]
                dst[i]=if(p ushr 24==0||a==0L)p else pack(p,r.toDouble()/a,g.toDouble()/a,b.toDouble()/a)
                add(position-radius,-1);add(position+radius+1,1)}
        }
    }
    private fun neighborhood(src:IntArray,dst:IntArray,w:Int,h:Int,amount:Float,emboss:Boolean) {
        for(y in 0 until h)for(x in 0 until w){val i=y*w+x;val p=src[i]
            if(p ushr 24==0){dst[i]=p;continue}
            fun sample(dx:Int,dy:Int):Double {val q=src[(y+dy).coerceIn(0,h-1)*w+(x+dx).coerceIn(0,w-1)];return gray(if(q ushr 24==0)p else q)}
            val value=if(emboss)(128+sample(1,1)-sample(-1,-1)).coerceIn(0.0,255.0)
                else {val gx=-sample(-1,-1)-2*sample(-1,0)-sample(-1,1)+sample(1,-1)+2*sample(1,0)+sample(1,1)
                    val gy=-sample(-1,-1)-2*sample(0,-1)-sample(1,-1)+sample(-1,1)+2*sample(0,1)+sample(1,1)
                    ((abs(gx)+abs(gy))/4).coerceIn(0.0,255.0)}
            dst[i]=blend(p,pack(p,value,value,value),amount)
        }
    }
}
