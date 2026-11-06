package app.honorable.editor

/** Retain incoming metadata exactly. Decode stays bounded/strict; failures never become empty edits. */
internal class PhotoSessionRecovery(bytes:ByteArray) {
    private val payload=bytes.copyOf()
    fun bytesForState():ByteArray=payload.copyOf()
    fun decode(scope:String?):Result<PhotoSessionSnapshot> = runCatching {
        requireNotNull(scope){"Photo session is unavailable"}
        PhotoSessionCodec.decode(payload,scope)
    }
}
