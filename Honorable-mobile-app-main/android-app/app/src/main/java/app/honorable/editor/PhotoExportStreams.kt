package app.honorable.editor

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Guard both sides of reads and immediately before each bounded write; callers retain stream ownership. */
internal object PhotoExportStreams {
    private fun read(input:InputStream,requireCurrent:()->Unit,consume:(ByteArray,Int)->Unit){
        requireCurrent();val buffer=ByteArray(256*1024)
        while(true){
            requireCurrent();var count=input.read(buffer);requireCurrent()
            if(count<0)break
            if(count==0){val single=input.read();requireCurrent();if(single<0)break;buffer[0]=single.toByte();count=1}
            requireCurrent();consume(buffer,count)
        }
        requireCurrent()
    }
    fun copy(input:InputStream,output:OutputStream,requireCurrent:()->Unit)=read(input,requireCurrent){buffer,count->output.write(buffer,0,count)}
    fun digest(input:InputStream,requireCurrent:()->Unit):ByteArray {
        val digest=MessageDigest.getInstance("SHA-256")
        read(input,requireCurrent){buffer,count->digest.update(buffer,0,count)}
        requireCurrent();return digest.digest()
    }
}
