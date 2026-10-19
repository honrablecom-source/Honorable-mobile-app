package app.honorable.editor

object PhotoDestinationSafety {
    fun requireSeparate(sourceUri: String, destinationUri: String, sourceDevice: Long, sourceInode: Long,
                        destinationDevice: Long, destinationInode: Long) {
        require(sourceUri != destinationUri) { "Choose a new copy destination" }
        require(sourceInode > 0 && destinationInode > 0) { "Choose a local writable destination" }
        require(sourceDevice != destinationDevice || sourceInode != destinationInode) { "A copy cannot replace the original" }
    }
}
