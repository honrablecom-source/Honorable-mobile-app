package app.honorable.editor

/** A textured 2.5D surface from real relative-depth inference, not unseen/full object geometry. */
internal object PhotoDepthMesh {
    const val MAX_TEXTURE_BYTES=1_572_864
    const val MAX_PAYLOAD_BYTES=3_145_728
    class Grid internal constructor(val columns:Int,val rows:Int,val aspect:Double,depths:FloatArray){
        private val values=depths.copyOf()
        fun depths()=values.copyOf()
    }
    fun create(depth:FloatArray,width:Int,height:Int,sourceWidth:Int,sourceHeight:Int,columns:Int=65,rows:Int=65,current:()->Unit={}):Grid{
        require(width in 14..518&&height in 14..518&&depth.size==width*height)
        require(sourceWidth in 1..4096&&sourceHeight in 1..4096&&columns in 2..96&&rows in 2..96)
        val aspect=sourceWidth.toDouble()/sourceHeight;require(aspect in .1..10.0)
        val owned=depth.copyOf();current();require(owned.all{it.isFinite()}){"Depth model returned invalid geometry"}
        val low=owned.minOrNull()!!;val high=owned.maxOrNull()!!;val span=high.toDouble()-low
        val normalized=FloatArray(columns*rows){index->
            if(index%columns==0)current()
            val x=(index%columns).toDouble()*(width-1)/(columns-1);val y=(index/columns).toDouble()*(height-1)/(rows-1)
            val left=x.toInt();val top=y.toInt();val right=minOf(left+1,width-1);val bottom=minOf(top+1,height-1)
            val fx=x-left;val fy=y-top
            val value=(owned[top*width+left]*(1-fx)+owned[top*width+right]*fx)*(1-fy)+(owned[bottom*width+left]*(1-fx)+owned[bottom*width+right]*fx)*fy
            if(span<=1e-12)0.5f else ((value-low)/span).coerceIn(0.0,1.0).toFloat()
        }
        current();return Grid(columns,rows,aspect,normalized)
    }
    fun encode(grid:Grid,png:ByteArray):ByteArray{
        require(grid.columns in 2..96&&grid.rows in 2..96&&grid.aspect.isFinite()&&grid.aspect in .1..10.0)
        require(png.size in 33..MAX_TEXTURE_BYTES&&png.copyOfRange(0,8).contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)))
        val header=java.nio.ByteBuffer.wrap(png).order(java.nio.ByteOrder.BIG_ENDIAN)
        require(header.getInt(8)==13&&header.getInt(12)==0x49484452&&header.getInt(16) in 1..512&&header.getInt(20) in 1..512){"Use the bounded generated PNG texture"}
        val data=grid.depths();require(data.size==grid.columns*grid.rows&&data.all{it.isFinite()&&it in 0f..1f})
        val positions=FloatArray(data.size*3);val normals=FloatArray(data.size*3)
        for(i in data.indices){positions[i*3]=(((i%grid.columns).toDouble()/(grid.columns-1)*2-1)*grid.aspect).toFloat();positions[i*3+1]=(1-(i/grid.columns).toDouble()/(grid.rows-1)*2).toFloat();positions[i*3+2]=(data[i]-.5f)*.5f}
        val indices=IntArray((grid.columns-1)*(grid.rows-1)*6);var at=0
        for(y in 0 until grid.rows-1)for(x in 0 until grid.columns-1){val a=y*grid.columns+x;val b=a+1;val c=a+grid.columns;val d=c+1;for(v in intArrayOf(a,c,b,b,c,d))indices[at++]=v}
        for(i in indices.indices step 3){
            val a=indices[i]*3;val b=indices[i+1]*3;val c=indices[i+2]*3
            val ux=positions[b]-positions[a];val uy=positions[b+1]-positions[a+1];val uz=positions[b+2]-positions[a+2]
            val vx=positions[c]-positions[a];val vy=positions[c+1]-positions[a+1];val vz=positions[c+2]-positions[a+2]
            val nx=uy*vz-uz*vy;val ny=uz*vx-ux*vz;val nz=ux*vy-uy*vx
            for(v in intArrayOf(a,b,c)){normals[v]+=nx;normals[v+1]+=ny;normals[v+2]+=nz}
        }
        for(i in data.indices){val base=i*3;val length=kotlin.math.sqrt((normals[base]*normals[base]+normals[base+1]*normals[base+1]+normals[base+2]*normals[base+2]).toDouble()).toFloat();require(length.isFinite()&&length>0);repeat(3){normals[base+it]/=length}}
        val positionBytes=positions.size*4;val normalAt=positionBytes;val uvAt=normalAt+normals.size*4;val indexAt=uvAt+data.size*8;val pngAt=indexAt+indices.size*2
        fun padded(size:Int)=(size+3)/4*4
        val binary=java.nio.ByteBuffer.allocate(padded(pngAt+png.size)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        positions.forEach{binary.putFloat(it)};normals.forEach{binary.putFloat(it)}
        for(i in data.indices){binary.putFloat((i%grid.columns).toFloat()/(grid.columns-1));binary.putFloat((i/grid.columns).toFloat()/(grid.rows-1))}
        indices.forEach{binary.putShort(it.toShort())};binary.put(png)
        val min=(0..2).map{axis->positions.indices.filter{it%3==axis}.minOf{positions[it]}}
        val max=(0..2).map{axis->positions.indices.filter{it%3==axis}.maxOf{positions[it]}}
        val json="""{"asset":{"version":"2.0","generator":"Noble local Depth Anything V2 Small"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0,"name":"AI depth surface"}],"meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2},"indices":3,"material":0}],"extras":{"source":"Depth Anything V2 Small","relativeDepth":true}}],"materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0},"metallicFactor":0,"roughnessFactor":1},"alphaMode":"BLEND","doubleSided":true}],"textures":[{"sampler":0,"source":0}],"samplers":[{"magFilter":9729,"minFilter":9729,"wrapS":33071,"wrapT":33071}],"images":[{"bufferView":4,"mimeType":"image/png"}],"buffers":[{"byteLength":${binary.capacity()}}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":$positionBytes,"target":34962},{"buffer":0,"byteOffset":$normalAt,"byteLength":${normals.size*4},"target":34962},{"buffer":0,"byteOffset":$uvAt,"byteLength":${data.size*8},"target":34962},{"buffer":0,"byteOffset":$indexAt,"byteLength":${indices.size*2},"target":34963},{"buffer":0,"byteOffset":$pngAt,"byteLength":${png.size}}],"accessors":[{"bufferView":0,"componentType":5126,"count":${data.size},"type":"VEC3","min":${min.joinToString(prefix="[",postfix="]")},"max":${max.joinToString(prefix="[",postfix="]")}},{"bufferView":1,"componentType":5126,"count":${data.size},"type":"VEC3"},{"bufferView":2,"componentType":5126,"count":${data.size},"type":"VEC2"},{"bufferView":3,"componentType":5123,"count":${indices.size},"type":"SCALAR"}]}""".toByteArray(Charsets.UTF_8)
        val jsonBytes=padded(json.size);val size=12+8+jsonBytes+8+binary.capacity();require(size<=MAX_PAYLOAD_BYTES)
        return java.nio.ByteBuffer.allocate(size).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply{
            putInt(0x46546c67);putInt(2);putInt(size);putInt(jsonBytes);putInt(0x4e4f534a);put(json);repeat(jsonBytes-json.size){put(32.toByte())};putInt(binary.capacity());putInt(0x004e4942);put(binary.array())
        }.array()
    }
}
