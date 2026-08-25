package app.honorable.search

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Versioned, bounded local index. Raw media and frames are never copied into it. */
class LocalMediaDatabase(context: Context) : SQLiteOpenHelper(context, "honorable-media.db", null, SCHEMA_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE media_index(id INTEGER PRIMARY KEY, uri TEXT NOT NULL UNIQUE, kind TEXT NOT NULL, captured_at INTEGER NOT NULL, location TEXT, latitude REAL, longitude REAL, ocr TEXT NOT NULL DEFAULT '', labels TEXT NOT NULL DEFAULT '', dominant_colors TEXT NOT NULL DEFAULT '', embedding BLOB, model_id TEXT, embedding_dimension INTEGER, preprocessing_version TEXT, indexed_at INTEGER NOT NULL, content_modified_at INTEGER NOT NULL DEFAULT 0, display_name TEXT NOT NULL DEFAULT '', duration_ms INTEGER, vision_caption TEXT, vision_terms TEXT, vision_model_id TEXT, vision_version INTEGER, vision_analyzed_at INTEGER)""")
        db.execSQL("""CREATE TABLE video_frame(media_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, ocr TEXT NOT NULL DEFAULT '', labels TEXT NOT NULL DEFAULT '', dominant_colors TEXT NOT NULL DEFAULT '', embedding BLOB, scene_fingerprint INTEGER, PRIMARY KEY(media_id,timestamp_ms), FOREIGN KEY(media_id) REFERENCES media_index(id) ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX media_captured_at ON media_index(captured_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations must be additive. A model/dimension mismatch invalidates embeddings only, not OCR/metadata.
        if (oldVersion < 2) db.execSQL("ALTER TABLE media_index ADD COLUMN content_modified_at INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE media_index ADD COLUMN latitude REAL")
            db.execSQL("ALTER TABLE media_index ADD COLUMN longitude REAL")
            db.execSQL("ALTER TABLE media_index ADD COLUMN dominant_colors TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE media_index ADD COLUMN preprocessing_version TEXT")
            db.execSQL("ALTER TABLE video_frame ADD COLUMN dominant_colors TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE video_frame ADD COLUMN scene_fingerprint INTEGER")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE media_index ADD COLUMN display_name TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE media_index ADD COLUMN duration_ms INTEGER")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE media_index ADD COLUMN vision_caption TEXT")
            db.execSQL("ALTER TABLE media_index ADD COLUMN vision_terms TEXT")
            db.execSQL("ALTER TABLE media_index ADD COLUMN vision_model_id TEXT")
            db.execSQL("ALTER TABLE media_index ADD COLUMN vision_version INTEGER")
            db.execSQL("ALTER TABLE media_index ADD COLUMN vision_analyzed_at INTEGER")
        }
    }
    fun upsert(record: MediaRecord, modifiedAt: Long) {
        val values=ContentValues().apply {
            put("id",record.id);put("uri",record.uri);put("kind",record.kind.name);put("captured_at",record.capturedAtEpochMs)
            put("location",record.location);put("ocr",record.ocr);put("labels",record.labels.joinToString(SEPARATOR));put("dominant_colors",record.dominantColors.joinToString(SEPARATOR))
            put("embedding",record.embedding?.let(::floatsToBytes));put("embedding_dimension",record.embedding?.size);put("indexed_at",System.currentTimeMillis());put("content_modified_at",modifiedAt)
            put("display_name",record.displayName);record.durationMs?.let{put("duration_ms",it)}
            record.visionUnderstanding?.let { vision -> put("vision_caption",vision.caption);put("vision_terms",vision.terms().joinToString(SEPARATOR));put("vision_model_id",vision.modelId);put("vision_version",vision.analysisVersion);put("vision_analyzed_at",vision.analyzedAtEpochMs) }
        }
        writableDatabase.beginTransaction();try {
            writableDatabase.insertWithOnConflict("media_index",null,values,SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.delete("video_frame","media_id=?",arrayOf(record.id.toString()))
            record.videoFrames.forEach { frame -> writableDatabase.insert("video_frame",null,ContentValues().apply { put("media_id",record.id);put("timestamp_ms",frame.timestampMs);put("ocr",frame.ocr);put("labels",frame.labels.joinToString(SEPARATOR));put("dominant_colors",frame.dominantColors.joinToString(SEPARATOR));put("embedding",frame.embedding?.let(::floatsToBytes));frame.sceneFingerprint?.let{put("scene_fingerprint",it)} }) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    fun modifiedTimes(): Map<String,Long> = readableDatabase.query("media_index",arrayOf("uri","content_modified_at"),null,null,null,null,null).use { c -> buildMap { while(c.moveToNext()) put(c.getString(0),c.getLong(1)) } }
    fun records(): List<MediaRecord> = readableDatabase.query("media_index",null,null,null,null,null,null).use { c -> buildList {
        fun text(name:String)=c.getString(c.getColumnIndexOrThrow(name))
        while(c.moveToNext()) { val mediaId=c.getLong(c.getColumnIndexOrThrow("id"));val visionModel=c.getString(c.getColumnIndexOrThrow("vision_model_id"));add(MediaRecord(
            id=c.getLong(c.getColumnIndexOrThrow("id")),kind=MediaKind.valueOf(text("kind")),capturedAtEpochMs=c.getLong(c.getColumnIndexOrThrow("captured_at")),location=c.getString(c.getColumnIndexOrThrow("location")),
            ocr=text("ocr"),labels=text("labels").split(SEPARATOR).filter(String::isNotBlank).toSet(),embedding=c.getBlob(c.getColumnIndexOrThrow("embedding"))?.let(::bytesToFloats),
            dominantColors=text("dominant_colors").split(SEPARATOR).filter(String::isNotBlank).toSet(),isScreenshot=text("display_name").contains("screenshot",true),uri=text("uri"),displayName=text("display_name"),
            durationMs=c.getColumnIndexOrThrow("duration_ms").let { i -> if(c.isNull(i)) null else c.getLong(i) },metadataTerms=setOf(if(MediaKind.valueOf(text("kind"))==MediaKind.VIDEO) "video" else "photo"),videoFrames=frames(mediaId),
            visionUnderstanding=visionModel?.let{VisionUnderstanding(c.getString(c.getColumnIndexOrThrow("vision_caption")).orEmpty(),objects=c.getString(c.getColumnIndexOrThrow("vision_terms")).orEmpty().split(SEPARATOR).filter(String::isNotBlank).toSet(),modelId=it,analysisVersion=c.getInt(c.getColumnIndexOrThrow("vision_version")),analyzedAtEpochMs=c.getLong(c.getColumnIndexOrThrow("vision_analyzed_at")))}
        )) }
    } }
    private fun frames(mediaId:Long):List<VideoFrame> = readableDatabase.query("video_frame",null,"media_id=?",arrayOf(mediaId.toString()),null,null,"timestamp_ms").use { c -> buildList { while(c.moveToNext()) add(VideoFrame(c.getLong(c.getColumnIndexOrThrow("timestamp_ms")),c.getString(c.getColumnIndexOrThrow("ocr")),c.getString(c.getColumnIndexOrThrow("labels")).split(SEPARATOR).filter(String::isNotBlank).toSet(),c.getBlob(c.getColumnIndexOrThrow("embedding"))?.let(::bytesToFloats),c.getString(c.getColumnIndexOrThrow("dominant_colors")).split(SEPARATOR).filter(String::isNotBlank).toSet(),c.getColumnIndexOrThrow("scene_fingerprint").let{i->if(c.isNull(i))null else c.getLong(i)})) } }
    fun removeDeleted(existingUris: Set<String>) {
        writableDatabase.query("media_index", arrayOf("uri"), null, null, null, null, null).use { cursor ->
            val stale = mutableListOf<String>(); while (cursor.moveToNext()) cursor.getString(0).takeIf { it !in existingUris }?.let(stale::add)
            writableDatabase.beginTransaction(); try { stale.chunked(250).forEach { batch -> batch.forEach { writableDatabase.delete("media_index", "uri=?", arrayOf(it)) } }; writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() }
        }
    }
    companion object {
        const val SCHEMA_VERSION = 5;private const val SEPARATOR="\u001f"
        private fun floatsToBytes(values:FloatArray)=ByteBuffer.allocate(values.size*4).order(ByteOrder.LITTLE_ENDIAN).apply{values.forEach(::putFloat)}.array()
        private fun bytesToFloats(bytes:ByteArray)=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).let{b->FloatArray(bytes.size/4){b.float}}
    }
}
