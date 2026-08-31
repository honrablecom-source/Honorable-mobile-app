package app.honorable.search

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Versioned, bounded local index. Raw media and frames are never copied into it. */
class LocalMediaDatabase(context: Context) : SQLiteOpenHelper(context, "honorable-media.db", null, SCHEMA_VERSION) {
    private val appContext=context.applicationContext
    fun context():Context=appContext
    override fun onConfigure(db:SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE media_index(id INTEGER PRIMARY KEY, uri TEXT NOT NULL UNIQUE, kind TEXT NOT NULL, captured_at INTEGER NOT NULL, location TEXT, latitude REAL, longitude REAL, ocr TEXT NOT NULL DEFAULT '', labels TEXT NOT NULL DEFAULT '', dominant_colors TEXT NOT NULL DEFAULT '', embedding BLOB, model_id TEXT, embedding_dimension INTEGER, preprocessing_version TEXT, indexed_at INTEGER NOT NULL, content_modified_at INTEGER NOT NULL DEFAULT 0, display_name TEXT NOT NULL DEFAULT '', duration_ms INTEGER, vision_caption TEXT, vision_terms TEXT, vision_model_id TEXT, vision_version INTEGER, vision_analyzed_at INTEGER)""")
        db.execSQL("""CREATE TABLE video_frame(media_id INTEGER NOT NULL, timestamp_ms INTEGER NOT NULL, ocr TEXT NOT NULL DEFAULT '', labels TEXT NOT NULL DEFAULT '', dominant_colors TEXT NOT NULL DEFAULT '', embedding BLOB, scene_fingerprint INTEGER, PRIMARY KEY(media_id,timestamp_ms), FOREIGN KEY(media_id) REFERENCES media_index(id) ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX media_captured_at ON media_index(captured_at)")
        createOperationalTables(db)
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
        if (oldVersion < 6) createOperationalTables(db)
    }
    private fun createOperationalTables(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS evidence_version(media_id INTEGER NOT NULL, processor TEXT NOT NULL, version TEXT NOT NULL, generated_at INTEGER NOT NULL, PRIMARY KEY(media_id,processor), FOREIGN KEY(media_id) REFERENCES media_index(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS index_job(id TEXT PRIMARY KEY, state TEXT NOT NULL, stage TEXT, created_at INTEGER NOT NULL, started_at INTEGER, finished_at INTEGER, processed INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL DEFAULT 0, failed INTEGER NOT NULL DEFAULT 0, attempt INTEGER NOT NULL DEFAULT 0, failure_kind TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS engine_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO engine_meta(key,value) VALUES('index_generation','0')")
        db.execSQL("CREATE TABLE IF NOT EXISTS engine_error(id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, category TEXT NOT NULL, stage TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS engine_metric(id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, operation TEXT NOT NULL, stage TEXT NOT NULL, duration_ms INTEGER NOT NULL)")
    }
    fun upsert(record: MediaRecord, modifiedAt: Long) {
        val values=ContentValues().apply {
            put("id",record.id);put("uri",record.uri);put("kind",record.kind.name);put("captured_at",record.capturedAtEpochMs)
            put("location",record.location);put("ocr",record.ocr);put("labels",record.labels.joinToString(SEPARATOR));put("dominant_colors",record.dominantColors.joinToString(SEPARATOR))
            put("embedding",record.embedding?.let(::floatsToBytes));put("model_id",record.embedding?.let{"tinyclip"});put("embedding_dimension",record.embedding?.size);put("indexed_at",System.currentTimeMillis());put("content_modified_at",modifiedAt)
            put("display_name",record.displayName);record.durationMs?.let{put("duration_ms",it)}
            record.visionUnderstanding?.let { vision -> put("vision_caption",vision.caption);put("vision_terms",vision.terms().joinToString(SEPARATOR));put("vision_model_id",vision.modelId);put("vision_version",vision.analysisVersion);put("vision_analyzed_at",vision.analyzedAtEpochMs) }
        }
        writableDatabase.beginTransaction();try {
            if(writableDatabase.update("media_index",values,"id=?",arrayOf(record.id.toString()))==0)
                writableDatabase.insertOrThrow("media_index",null,values)
            writableDatabase.delete("video_frame","media_id=?",arrayOf(record.id.toString()))
            record.videoFrames.forEach { frame -> writableDatabase.insert("video_frame",null,ContentValues().apply { put("media_id",record.id);put("timestamp_ms",frame.timestampMs);put("ocr",frame.ocr);put("labels",frame.labels.joinToString(SEPARATOR));put("dominant_colors",frame.dominantColors.joinToString(SEPARATOR));put("embedding",frame.embedding?.let(::floatsToBytes));frame.sceneFingerprint?.let{put("scene_fingerprint",it)} }) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    fun evidenceVersions(mediaId:Long):Map<EvidenceProcessor,String> = readableDatabase.query("evidence_version",arrayOf("processor","version"),"media_id=?",arrayOf(mediaId.toString()),null,null,null).use { c -> buildMap { while(c.moveToNext()) runCatching{EvidenceProcessor.valueOf(c.getString(0))}.getOrNull()?.let{put(it,c.getString(1))} } }
    fun markEvidence(mediaId:Long,processors:Set<EvidenceProcessor>,versions:ProcessorVersions) {
        if(processors.isEmpty())return
        writableDatabase.beginTransaction();try { processors.forEach { processor -> writableDatabase.insertWithOnConflict("evidence_version",null,ContentValues().apply{put("media_id",mediaId);put("processor",processor.name);put("version",versions.key(processor));put("generated_at",System.currentTimeMillis())},SQLiteDatabase.CONFLICT_REPLACE) };incrementGeneration(writableDatabase);writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() }
    }
    fun indexGeneration():Long=readableDatabase.rawQuery("SELECT value FROM engine_meta WHERE key='index_generation'",null).use{if(it.moveToFirst())it.getString(0).toLongOrNull()?:0 else 0}
    private fun incrementGeneration(db:SQLiteDatabase){db.execSQL("UPDATE engine_meta SET value=CAST(value AS INTEGER)+1 WHERE key='index_generation'")}

    fun createJob(id:String) { writableDatabase.insertWithOnConflict("index_job",null,ContentValues().apply{put("id",id);put("state",IndexJobState.QUEUED.name);put("created_at",System.currentTimeMillis())},SQLiteDatabase.CONFLICT_IGNORE) }
    fun updateJob(id:String,state:IndexJobState,stage:String?=null,processed:Int=0,total:Int=0,failed:Int=0,attempt:Int=0,failure:EngineFailureKind?=null) {
        val now=System.currentTimeMillis();writableDatabase.update("index_job",ContentValues().apply{put("state",state.name);put("stage",stage);put("processed",processed);put("total",total);put("failed",failed);put("attempt",attempt);failure?.let{put("failure_kind",it.name)};if(state==IndexJobState.RUNNING&&stage==null)put("started_at",now);if(state in setOf(IndexJobState.COMPLETED,IndexJobState.PARTIAL_FAILURE,IndexJobState.FAILED,IndexJobState.CANCELLED))put("finished_at",now)},"id=?",arrayOf(id))
    }
    fun latestJob():IndexJobSnapshot?=readableDatabase.query("index_job",null,null,null,null,null,"created_at DESC","1").use{c->if(!c.moveToFirst())null else IndexJobSnapshot(state=IndexJobState.valueOf(c.getString(c.getColumnIndexOrThrow("state"))),createdAtEpochMs=c.getLong(c.getColumnIndexOrThrow("created_at")),startedAtEpochMs=c.getColumnIndexOrThrow("started_at").let{if(c.isNull(it))null else c.getLong(it)},finishedAtEpochMs=c.getColumnIndexOrThrow("finished_at").let{if(c.isNull(it))null else c.getLong(it)},processed=c.getInt(c.getColumnIndexOrThrow("processed")),total=c.getInt(c.getColumnIndexOrThrow("total")),failedItems=c.getInt(c.getColumnIndexOrThrow("failed")))}
    fun recordMetric(operation:String,stage:String,durationMs:Long){writableDatabase.insert("engine_metric",null,ContentValues().apply{put("at",System.currentTimeMillis());put("operation",operation);put("stage",stage);put("duration_ms",durationMs)})}
    fun recordError(kind:EngineFailureKind,stage:String?){writableDatabase.insert("engine_error",null,ContentValues().apply{put("at",System.currentTimeMillis());put("category",kind.name);put("stage",stage)})}
    fun modifiedTimes(): Map<String,Long> = readableDatabase.query("media_index",arrayOf("uri","content_modified_at"),null,null,null,null,null).use { c -> buildMap { while(c.moveToNext()) put(c.getString(0),c.getLong(1)) } }
    fun records(): List<MediaRecord> {
        val framesByMediaId=readAllFrames()
        return readableDatabase.query("media_index",null,null,null,null,null,null).use { c -> buildList {
        fun text(name:String)=c.getString(c.getColumnIndexOrThrow(name))
        while(c.moveToNext()) { val mediaId=c.getLong(c.getColumnIndexOrThrow("id"));val visionModel=c.getString(c.getColumnIndexOrThrow("vision_model_id"));add(MediaRecord(
            id=c.getLong(c.getColumnIndexOrThrow("id")),kind=MediaKind.valueOf(text("kind")),capturedAtEpochMs=c.getLong(c.getColumnIndexOrThrow("captured_at")),location=c.getString(c.getColumnIndexOrThrow("location")),
            ocr=text("ocr"),labels=text("labels").split(SEPARATOR).filter(String::isNotBlank).toSet(),embedding=c.getBlob(c.getColumnIndexOrThrow("embedding"))?.let(::bytesToFloats),
            dominantColors=text("dominant_colors").split(SEPARATOR).filter(String::isNotBlank).toSet(),isScreenshot=text("display_name").contains("screenshot",true),uri=text("uri"),displayName=text("display_name"),
            durationMs=c.getColumnIndexOrThrow("duration_ms").let { i -> if(c.isNull(i)) null else c.getLong(i) },metadataTerms=setOf(if(MediaKind.valueOf(text("kind"))==MediaKind.VIDEO) "video" else "photo"),videoFrames=framesByMediaId[mediaId].orEmpty(),
            visionUnderstanding=visionModel?.let{VisionUnderstanding(c.getString(c.getColumnIndexOrThrow("vision_caption")).orEmpty(),objects=c.getString(c.getColumnIndexOrThrow("vision_terms")).orEmpty().split(SEPARATOR).filter(String::isNotBlank).toSet(),modelId=it,analysisVersion=c.getInt(c.getColumnIndexOrThrow("vision_version")),analyzedAtEpochMs=c.getLong(c.getColumnIndexOrThrow("vision_analyzed_at")))}
        )) }
    } } }
    private fun readAllFrames():Map<Long,List<VideoFrame>> = readableDatabase.query("video_frame",null,null,null,null,null,"media_id,timestamp_ms").use { c -> buildMap<Long,MutableList<VideoFrame>> { while(c.moveToNext()) {
        val mediaId=c.getLong(c.getColumnIndexOrThrow("media_id"));getOrPut(mediaId){mutableListOf()}+=VideoFrame(c.getLong(c.getColumnIndexOrThrow("timestamp_ms")),c.getString(c.getColumnIndexOrThrow("ocr")),c.getString(c.getColumnIndexOrThrow("labels")).split(SEPARATOR).filter(String::isNotBlank).toSet(),c.getColumnIndexOrThrow("embedding").let{i->if(c.isNull(i))null else c.getBlob(i).let(::bytesToFloats)},c.getString(c.getColumnIndexOrThrow("dominant_colors")).split(SEPARATOR).filter(String::isNotBlank).toSet(),c.getColumnIndexOrThrow("scene_fingerprint").let{i->if(c.isNull(i))null else c.getLong(i)})
    } } }
    fun removeDeleted(existingUris: Set<String>) {
        writableDatabase.query("media_index", arrayOf("uri"), null, null, null, null, null).use { cursor ->
            val stale = mutableListOf<String>(); while (cursor.moveToNext()) cursor.getString(0).takeIf { it !in existingUris }?.let(stale::add)
            writableDatabase.beginTransaction(); try { stale.chunked(250).forEach { batch -> batch.forEach { writableDatabase.delete("media_index", "uri=?", arrayOf(it)) } };if(stale.isNotEmpty())incrementGeneration(writableDatabase);writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() }
        }
    }
    fun diagnostics(versions:ProcessorVersions=ProcessorVersions()):DatabaseDiagnostics {
        val db=readableDatabase
        fun count(table:String)=db.rawQuery("SELECT COUNT(*) FROM $table",null).use{cursor->if(cursor.moveToFirst())cursor.getInt(0)else 0}
        val healthy=runCatching{db.rawQuery("PRAGMA quick_check(1)",null).use{cursor->cursor.moveToFirst()&&cursor.getString(0)=="ok"}}.getOrDefault(false)
        val stale=records().count{EvidenceInvalidationPlanner.stale(evidenceVersions(it.id),versions,it.kind==MediaKind.VIDEO).isNotEmpty()}
        return DatabaseDiagnostics(healthy,db.version,count("media_index"),count("video_frame"),indexGeneration(),stale,count("engine_error"))
    }
    companion object {
        const val SCHEMA_VERSION = 6;private const val SEPARATOR="\u001f"
        private fun floatsToBytes(values:FloatArray)=ByteBuffer.allocate(values.size*4).order(ByteOrder.LITTLE_ENDIAN).apply{values.forEach(::putFloat)}.array()
        private fun bytesToFloats(bytes:ByteArray)=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).let{b->FloatArray(bytes.size/4){b.float}}
    }
}
