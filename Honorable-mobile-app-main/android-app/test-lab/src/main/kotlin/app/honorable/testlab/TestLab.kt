package app.honorable.testlab

import app.honorable.search.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.image.BufferedImage
import java.io.*
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.*
import kotlin.math.absoluteValue

private val mediaRoot: Path = Paths.get("test-media").toAbsolutePath().normalize()
private val indexFile: Path = mediaRoot.resolve(".memories-test-index")
private val imageExt = setOf("jpg", "jpeg", "png", "webp")
private val heifExt = setOf("heic", "heif")
private val videoExt = setOf("mp4", "mov", "m4v", "webm", "mkv")
private val mediaLikeExt = imageExt + heifExt + videoExt

data class DiscoveryEntry(val path:Path,val relative:String,val format:String,val kind:MediaKind?,val supported:Boolean,val reason:String?=null)

/** Development source only: production continues to discover files through Android MediaStore. */
interface FileMediaSource { fun inspect():List<DiscoveryEntry>;fun discover(): List<Path> = inspect().filter{it.supported}.map{it.path} }
class DirectoryMediaSource(private val root: Path) : FileMediaSource {
    override fun inspect():List<DiscoveryEntry> {
        require(root.exists()) { "Missing test-media directory: $root" }
        return Files.walk(root).use { paths -> paths.iterator().asSequence().filter { !Files.isSymbolicLink(it)&&it.isRegularFile()&&!it.fileName.toString().startsWith(".")&&it.extension.isNotBlank()&&it.extension.lowercase() !in setOf("md","json") }.sorted().map{path->
            val ext=path.extension.lowercase();val relative=root.relativize(path).toString().replace(File.separatorChar,'/')
            when { ext in imageExt->DiscoveryEntry(path,relative,ext,MediaKind.IMAGE,true);ext in videoExt->DiscoveryEntry(path,relative,ext,MediaKind.VIDEO,commandExists("ffmpeg")&&videoDuration(path)!=null,if(commandExists("ffmpeg")&&videoDuration(path)!=null)null else "FFmpeg cannot decode/probe this file");ext in heifExt->DiscoveryEntry(path,relative,ext,MediaKind.IMAGE,false,"HEIC/HEIF decoder unavailable in this Codespace; convert to JPG/PNG for testing");else->DiscoveryEntry(path,relative,ext,null,false,"unsupported media format")
            }
        }.toList() }
    }
}

data class LabIndex(val records: List<MediaRecord>, val elapsedMs: Long, val imageAnalysisMs: List<Long>, val warnings: List<String>)
data class RefreshDelta(val added:Int,val updated:Int,val removed:Int)
fun refreshDelta(old:List<MediaRecord>,current:List<MediaRecord>):RefreshDelta { val before=old.associateBy{it.uri};val after=current.associateBy{it.uri};return RefreshDelta(after.keys.count{it !in before},after.count{(uri,r)->before[uri]!=null&&before[uri]!!.capturedAtEpochMs!=r.capturedAtEpochMs},before.keys.count{it !in after}) }
class TinyClipBridge : Closeable {
    private val script=Paths.get("android-app/test-lab/tinyclip_bridge.py").toAbsolutePath()
    private val process=ProcessBuilder("python3",script.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    private val writer=process.outputStream.bufferedWriter();private val reader=process.inputStream.bufferedReader()
    val active:Boolean
    init { active=reader.readLine()?.contains("\"ready\": true")==true }
    @Synchronized private fun embed(kind:String,value:String):FloatArray? { if(!active)return null;writer.write("{\"kind\":${json(kind)},\"value\":${json(value)}}\n");writer.flush();val line=reader.readLine()?:return null;val body=Regex("\"vector\"\\s*:\\s*\\[([^]]+)]").find(line)?.groupValues?.get(1)?:return null;return body.split(',').map{it.trim().toFloat()}.toFloatArray().takeIf{it.size==512} }
    fun image(path:Path)=embed("image",path.toString());fun text(value:String)=embed("text",value)
    override fun close(){writer.close();process.destroy()}
    companion object { fun available()=Paths.get("android-app/test-lab/models/tinyclip/model_int8.onnx").isRegularFile()&&commandExists("python3") }
}

interface LabVisionUnderstandingService { val modelId:String;val analysisVersion:Int;fun active():Boolean;fun cached(path:Path):Boolean;fun analyze(path:Path,timeoutMs:Long=300_000):VisionUnderstanding? }
class OllamaVisionUnderstandingService:LabVisionUnderstandingService {
    override val modelId="moondream:1.8b";override val analysisVersion=2
    override fun active()=runCatching{java.net.URI("http://127.0.0.1:11434/api/tags").toURL().openStream().use{it.readBytes()}.toString(UTF_8).contains(modelId)}.getOrDefault(false)
    override fun cached(path:Path):Boolean=validCache(path,Paths.get(path.toString()+".vlm.json").takeIf{it.isRegularFile()}?.readText())
    override fun analyze(path:Path,timeoutMs:Long):VisionUnderstanding? = runCatching {
        val cache=Paths.get(path.toString()+".vlm.json")
        val cachedRaw=cache.takeIf{it.isRegularFile()}?.readText()
        val stat=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes::class.java)
        val modifiedNs=stat.lastModifiedTime().to(java.util.concurrent.TimeUnit.NANOSECONDS)
        val valid=validCache(path,cachedRaw)
        val raw=if(valid)cachedRaw!! else {
            val bridge=Paths.get("android-app/test-lab/ollama_vlm.py").toAbsolutePath()
            val process=ProcessBuilder("python3",bridge.toString(),path.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start()
            require(process.waitFor(timeoutMs.coerceAtLeast(1),java.util.concurrent.TimeUnit.MILLISECONDS)){process.destroyForcibly();"vision budget exceeded"};require(process.exitValue()==0)
            process.inputStream.bufferedReader().readText().also { require(it.isNotBlank());val parsed=it.dropLastWhile(Char::isWhitespace).dropLast(1)+",\"source_mtime_ns\":$modifiedNs,\"source_size\":${stat.size()}}";cache.writeText(parsed) }
        }
        fun value(name:String)=Regex("\"$name\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(raw)?.groupValues?.get(1)?.replace("\\\"","\"").orEmpty()
        fun values(name:String)=Regex("\"$name\"\\s*:\\s*\\[([^]]*)]").find(raw)?.groupValues?.get(1)?.let{body->Regex("\"([^\"]+)\"").findAll(body).map{it.groupValues[1]}.toSet()}.orEmpty()
        VisionUnderstanding(value("caption"),values("objects"),values("activities"),values("scene"),values("colors"),values("clothing"),values("environment"),values("textual_context"),values("attributes"),modelId,analysisVersion,System.currentTimeMillis(),Regex("\"analysis_time_ms\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toLongOrNull()?:0)
    }.getOrNull()
    private fun validCache(path:Path,raw:String?):Boolean { if(raw==null)return false;val stat=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes::class.java);return Regex("\"source_mtime_ns\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toLongOrNull()==stat.lastModifiedTime().to(java.util.concurrent.TimeUnit.NANOSECONDS)&&Regex("\"source_size\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toLongOrNull()==stat.size()&&valueFrom(raw,"model_id")==modelId&&Regex("\"analysis_version\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()==analysisVersion }
    private fun valueFrom(raw:String,name:String)=Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1).orEmpty()
}

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "index" -> indexCommand()
        "enrich" -> enrichCommand(option(args,"--limit")?.toIntOrNull() ?: 10)
        "search" -> searchCommand(option(args, "--query") ?: error("Missing --query"), option(args, "--top")?.toIntOrNull() ?: 10,"--debug" in args,"--show-ranking" in args)
        "evaluate" -> evaluateCommand()
        "interactive" -> interactiveCommand()
        "list" -> listCommand()
        "serve" -> serve(option(args, "--port")?.toIntOrNull() ?: 4174)
        else -> error("Use: index | enrich [--limit N] | search --query TEXT [--top N] | interactive | evaluate | serve [--port N]")
    }
}

private fun option(args: Array<String>, name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
private fun commandExists(name: String) = runCatching { ProcessBuilder("sh", "-c", "command -v $name").start().waitFor() == 0 }.getOrDefault(false)

private fun indexCommand() {
    val source=DirectoryMediaSource(mediaRoot);val discovered=source.inspect();val previous=runCatching{readIndex()}.getOrNull()
    val index = buildIndex(previous)
    writeIndex(index)
    println("SEMANTIC MODEL: ${if(index.records.any{it.embedding?.size==512})"ACTIVE" else "UNAVAILABLE"}")
    println("VISION MODEL: moondream:1.8b ${if(index.records.any{it.visionUnderstanding!=null})"ACTIVE" else "UNAVAILABLE"}")
    index.warnings.forEach { println("WARNING: $it") }
    val delta=refreshDelta(previous?.records.orEmpty(),index.records)
    println("Discovered media: ${discovered.size}");println("Images: ${discovered.count{it.kind==MediaKind.IMAGE&&it.supported}}");println("Videos: ${discovered.count{it.kind==MediaKind.VIDEO&&it.supported}}")
    println("Indexed: ${index.records.size}");println("Skipped: ${discovered.count{!it.supported}}");println("Refresh: added=${delta.added} updated=${delta.updated} removed=${delta.removed}")
    discovered.filter{!it.supported}.forEach{println("SKIPPED: ${it.relative} — ${it.reason}")}
    println("Total indexing time: ${index.elapsedMs} ms")
    println("Average image analysis time: ${"%.2f".format(index.imageAnalysisMs.average().takeUnless { it.isNaN() } ?: 0.0)} ms")
    println("Average VLM image time: ${"%.2f".format(index.records.mapNotNull{it.visionUnderstanding?.analysisTimeMs}.average().takeUnless{it.isNaN()}?:0.0)} ms")
    println("Development index: $indexFile")
}

/** Explicit progressive worker. Fast indexing and search never wait for this command. */
private fun enrichCommand(limit:Int) {
    require(limit in 1..100){"--limit must be 1..100"};val index=readIndex();val service=OllamaVisionUnderstandingService()
    require(service.active()){ "Local Ollama model ${service.modelId} is unavailable" }
    val queue=VisionEnrichmentQueue();val byId=index.records.associateBy{it.id};val now=System.currentTimeMillis()
    index.records.filter{it.kind==MediaKind.IMAGE&&it.visionUnderstanding?.let{v->v.modelId==service.modelId&&v.analysisVersion==service.analysisVersion}!=true}.forEach { record ->
        queue.offer(record.id,EnrichmentSignals(record.capturedAtEpochMs,isScreenshotOrDocument=record.isScreenshot,isRecentEvent=now-record.capturedAtEpochMs<7*86_400_000L))
    }
    val enriched=mutableMapOf<Long,VisionUnderstanding>();val timings=mutableListOf<Long>()
    repeat(limit.coerceAtMost(queue.size())) { queue.poll()?.let { job -> val record=byId.getValue(job.mediaId);val path=mediaRoot.resolve(record.uri);service.analyze(path)?.let{v->enriched[record.id]=v;timings+=v.analysisTimeMs;println("Vision ✓ ${record.displayName} (${v.analysisTimeMs} ms)")} } }
    writeIndex(index.copy(records=index.records.map{record->enriched[record.id]?.let{record.copy(visionUnderstanding=it)}?:record},imageAnalysisMs=timings))
    runCatching{ProcessBuilder("ollama","stop",service.modelId).start().waitFor()}
    println("Enriched: ${enriched.size}; remaining: ${queue.size()}; average VLM: ${"%.2f".format(timings.average().takeUnless{it.isNaN()}?:0.0)} ms")
}

private fun buildIndex(previous:LabIndex?=null): LabIndex {
    val started = System.nanoTime(); val timings = mutableListOf<Long>(); val warnings = linkedSetOf<String>()
    val hasTesseract = commandExists("tesseract"); val hasFfmpeg = commandExists("ffmpeg") && commandExists("ffprobe")
    if (!hasTesseract) warnings += "OCR unavailable: install tesseract for real local OCR."
    if (!hasFfmpeg) warnings += "Video decoder unavailable: install ffmpeg/ffprobe for representative-frame analysis."
    warnings += "Android ML Kit labels are unavailable on JVM; label score is disabled."
    if(!TinyClipBridge.available()) warnings += "TinyCLIP assets unavailable; run test-lab/download-model.sh."
    val vision:LabVisionUnderstandingService=OllamaVisionUnderstandingService();if(!vision.active())warnings += "Ollama vision unavailable at localhost:11434; run enrich later when available."
    val paths=DirectoryMediaSource(mediaRoot).discover();val prior=previous?.records?.associateBy{it.uri}.orEmpty()
    val clip=if(TinyClipBridge.available())runCatching{TinyClipBridge()}.getOrNull() else null
    val records = paths.mapNotNull { path ->
        val begin = System.nanoTime(); val ext = path.extension.lowercase(); val kind = if (ext in videoExt) MediaKind.VIDEO else MediaKind.IMAGE
        val relative = mediaRoot.relativize(path).toString().replace(File.separatorChar, '/')
        var image: BufferedImage? = null; var frames = emptyList<VideoFrame>(); var duration: Long? = null
        if (kind == MediaKind.IMAGE) image = runCatching { ImageIO.read(path.toFile()) }.getOrNull()
        else if (hasFfmpeg) {
            duration = videoDuration(path)
            frames = analyzeVideo(path, duration ?: 0L, hasTesseract, warnings,clip)
        }
        val ocr = if (image != null && hasTesseract) ocr(path) else ""
        val colors = image?.let(::colors) ?: emptySet()
        if (kind == MediaKind.IMAGE) timings += (System.nanoTime() - begin) / 1_000_000
        val embedding=if(kind==MediaKind.IMAGE)clip?.image(path) else frames.firstOrNull()?.embedding
        if(kind==MediaKind.IMAGE&&image==null&&embedding==null){warnings += "Skipped $relative: no image decoder could read it";return@mapNotNull null}
        if(kind==MediaKind.VIDEO&&(duration==null||frames.isEmpty())){warnings += "Skipped $relative: FFmpeg could not produce representative frames";return@mapNotNull null}
        val understanding=prior[relative]?.takeIf{it.capturedAtEpochMs==Files.getLastModifiedTime(path).toMillis()&&it.visionUnderstanding?.modelId==vision.modelId&&it.visionUnderstanding.analysisVersion==vision.analysisVersion}?.visionUnderstanding
        MediaRecord(
            id = relative.hashCode().toLong().absoluteValue, kind = kind,
            capturedAtEpochMs = Files.getLastModifiedTime(path).toMillis(), ocr = ocr,
            labels = emptySet(), metadataTerms = setOf(if (kind == MediaKind.VIDEO) "video" else "photo"), embedding=embedding,
            dominantColors = colors, isScreenshot = path.name.contains("screenshot", true),
            uri = relative, displayName = path.fileName.toString(), durationMs = duration, videoFrames = frames,visionUnderstanding=understanding
        )
    }
    clip?.close();return LabIndex(records, (System.nanoTime() - started) / 1_000_000, timings, warnings.toList())
}

private fun colors(image: BufferedImage): Set<String> {
    val samples = IntArray(256) { i -> image.getRGB((i % 16 * image.width / 16).coerceAtMost(image.width - 1), (i / 16 * image.height / 16).coerceAtMost(image.height - 1)) }
    return ColorEvidenceAnalyzer.dominantColors(samples)
}

private fun ocr(path: Path): String = runCatching {
    val p = ProcessBuilder("tesseract", path.toString(), "stdout", "--psm", "11").redirectError(ProcessBuilder.Redirect.DISCARD).start()
    p.inputStream.bufferedReader().readText().also { p.waitFor() }
}.getOrDefault("")

private fun videoDuration(path: Path): Long? = runCatching {
    val p = ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", path.toString()).start()
    (p.inputStream.bufferedReader().readText().trim().toDouble() * 1000).toLong().also { p.waitFor() }
}.getOrNull()

private fun analyzeVideo(path: Path, durationMs: Long, hasTesseract: Boolean, warnings: MutableSet<String>,clip:TinyClipBridge?): List<VideoFrame> {
    if (durationMs <= 0) return emptyList()
    val temp = Files.createTempDirectory("memories-frames-")
    return try {
        listOf(0.0, .25, .5, .75).mapNotNull { fraction ->
            val at = (durationMs * fraction).toLong(); val output = temp.resolve("$at.jpg")
            val process = ProcessBuilder("ffmpeg", "-loglevel", "error", "-ss", "${at / 1000.0}", "-i", path.toString(), "-frames:v", "1", "-vf", "scale=384:-1", output.toString()).start()
            if (process.waitFor() != 0) null else ImageIO.read(output.toFile())?.let { image ->
                VideoFrame(at, if (hasTesseract) ocr(output) else "", emptySet(), clip?.image(output), colors(image), imageFingerprint(image))
            }
        }
    } catch (e: Exception) { warnings += "Video analysis failed for ${path.fileName}: ${e.message}"; emptyList() }
    finally { temp.toFile().deleteRecursively() }
}

private fun imageFingerprint(image: BufferedImage): Long {
    var bits = 0L; var sum = 0L; val values = IntArray(64) { i -> image.getRGB(i % 8 * image.width / 8, i / 8 * image.height / 8).let { p -> ((p shr 16 and 255) + (p shr 8 and 255) + (p and 255)) / 3 }.also { sum += it } }
    values.forEachIndexed { i, value -> if (value >= sum / 64) bits = bits or (1L shl i) }; return bits
}

private fun writeIndex(index: LabIndex) = DataOutputStream(BufferedOutputStream(indexFile.outputStream())).use { out ->
    out.writeInt(3); out.writeLong(index.elapsedMs); out.writeInt(index.records.size)
    index.records.forEach { r ->
        out.writeLong(r.id); out.writeUTF(r.kind.name); out.writeLong(r.capturedAtEpochMs); out.writeUTF(r.uri); out.writeUTF(r.displayName); out.writeLong(r.durationMs ?: -1); out.writeBoolean(r.isScreenshot)
        out.writeUTF(r.ocr); writeStrings(out, r.labels); writeStrings(out, r.metadataTerms); writeStrings(out, r.dominantColors);writeVector(out,r.embedding); out.writeInt(r.videoFrames.size)
        r.videoFrames.forEach { f -> out.writeLong(f.timestampMs); out.writeUTF(f.ocr); writeStrings(out, f.labels); writeStrings(out, f.dominantColors);writeVector(out,f.embedding); out.writeLong(f.sceneFingerprint ?: 0);writeVision(out,f.visionUnderstanding) };writeVision(out,r.visionUnderstanding)
    }
}
private fun writeStrings(out: DataOutputStream, values: Set<String>) { out.writeInt(values.size); values.forEach(out::writeUTF) }
private fun writeVector(out:DataOutputStream,value:FloatArray?){out.writeInt(value?.size?:0);value?.forEach(out::writeFloat)}
private fun readVector(input:DataInputStream)=FloatArray(input.readInt()){input.readFloat()}.takeIf{it.isNotEmpty()}
private fun readStrings(input: DataInputStream) = buildSet { repeat(input.readInt()) { add(input.readUTF()) } }
private fun writeVision(out:DataOutputStream,v:VisionUnderstanding?){out.writeBoolean(v!=null);if(v!=null){out.writeUTF(v.caption);writeStrings(out,v.objects);writeStrings(out,v.activities);writeStrings(out,v.scenes);writeStrings(out,v.colors);writeStrings(out,v.clothing);writeStrings(out,v.environment);writeStrings(out,v.textualContext);writeStrings(out,v.attributes);out.writeUTF(v.modelId);out.writeInt(v.analysisVersion);out.writeLong(v.analyzedAtEpochMs);out.writeLong(v.analysisTimeMs)}}
private fun readVision(input:DataInputStream):VisionUnderstanding?{if(!input.readBoolean())return null;return VisionUnderstanding(input.readUTF(),readStrings(input),readStrings(input),readStrings(input),readStrings(input),readStrings(input),readStrings(input),readStrings(input),readStrings(input),input.readUTF(),input.readInt(),input.readLong(),input.readLong())}
private fun readIndex(): LabIndex {
    require(indexFile.exists()) { "No test index. Run ./gradlew indexTestMedia first." }
    return DataInputStream(BufferedInputStream(indexFile.inputStream())).use { input ->
        require(input.readInt() == 3) { "Unsupported test index version; re-run indexTestMedia" }; val elapsed = input.readLong()
        val records = List(input.readInt()) {
            val id=input.readLong();val kind=MediaKind.valueOf(input.readUTF());val captured=input.readLong();val uri=input.readUTF();val name=input.readUTF();val duration=input.readLong().takeIf{it>=0};val screenshot=input.readBoolean();val text=input.readUTF();val labels=readStrings(input);val metadata=readStrings(input);val colors=readStrings(input);val embedding=readVector(input)
            val frames=List(input.readInt()){val timestamp=input.readLong();val frameOcr=input.readUTF();val frameLabels=readStrings(input);val frameColors=readStrings(input);val frameEmbedding=readVector(input);VideoFrame(timestamp,frameOcr,frameLabels,frameEmbedding,frameColors,input.readLong(),readVision(input))}
            val vision=readVision(input);MediaRecord(id,kind,captured,ocr=text,labels=labels,metadataTerms=metadata,dominantColors=colors,isScreenshot=screenshot,uri=uri,displayName=name,durationMs=duration,videoFrames=frames,embedding=embedding,visionUnderstanding=vision)
        }; LabIndex(records, elapsed, emptyList(), emptyList())
    }
}

private data class TimedSearch(val query: SearchQuery, val matches: List<SearchMatch>, val latencyMs: Double, val vectorMs: Double)
private fun search(index: LabIndex, raw: String, top: Int = 10, kind: MediaKind? = null, clip:TinyClipBridge?=null): TimedSearch {
    val start=System.nanoTime();var query=QueryParser().parse(raw);if(kind!=null)query=query.copy(mediaKind=kind)
    val owned=clip?:if(TinyClipBridge.available())TinyClipBridge()else null;val vectorStart=System.nanoTime();val full=owned?.text(raw);val concepts=query.semanticConcepts.associateWith{owned?.text(it)}.filterValues{it!=null}.mapValues{it.value!!};val vectorMs=(System.nanoTime()-vectorStart)/1e6
    val vectors=LocalVectorIndex();index.records.forEach{r->r.embedding?.let{vectors.upsert(r.id,it)}}
    val matches=HybridSearchEngine(vectors).search(query,index.records.associateBy{it.id},QueryEmbeddings(full,concepts)).take(top)
    if(clip==null)owned?.close()
    return TimedSearch(query,matches,(System.nanoTime()-start)/1e6,vectorMs)
}

private data class TimedRefinement(val result:ProgressiveSearchResult,val latencyMs:Double)
private fun refineSearch(index:LabIndex,raw:String,cap:Int,budgetMs:Long,service:LabVisionUnderstandingService=OllamaVisionUnderstandingService()):TimedRefinement {
    val started=System.nanoTime();val fast=search(index,raw,10);val config=GatedVisionConfig(enabled=true,maxCandidates=cap)
    val trigger=SmartVisionTrigger.decide(fast.query,fast.matches,config)
    if(!trigger.shouldRefine||!service.active())return TimedRefinement(ProgressiveSearchResult(fast.matches), (System.nanoTime()-started)/1e6)
    val selected=fast.matches.asSequence().filter{m->m.media.visionUnderstanding?.let{it.modelId==service.modelId&&it.analysisVersion==service.analysisVersion}!=true}.take(cap).toList()
    val enriched=mutableMapOf<Long,VisionUnderstanding>();var calls=0;var exhausted=false
    for(match in selected){val elapsed=(System.nanoTime()-started)/1_000_000;val remaining=if(budgetMs==0L)300_000L else budgetMs-elapsed;if(remaining<=0){exhausted=true;break};val path=mediaRoot.resolve(match.media.uri);val wasCached=service.cached(path);val vision=service.analyze(path,remaining);if(!wasCached)calls++;if(vision==null){exhausted=budgetMs>0;break};enriched[match.media.id]=vision}
    if(enriched.isEmpty())return TimedRefinement(ProgressiveSearchResult(fast.matches,vlmCalls=calls,budgetExhausted=exhausted),(System.nanoTime()-started)/1e6)
    val updated=index.copy(records=index.records.map{r->enriched[r.id]?.let{r.copy(visionUnderstanding=it)}?:r});writeIndex(updated)
    val refined=search(updated,raw,10).matches
    return TimedRefinement(ProgressiveSearchResult(fast.matches,refined,calls,exhausted),(System.nanoTime()-started)/1e6)
}

private fun searchCommand(raw: String, top: Int,debug:Boolean=false,showRanking:Boolean=false) {
    val index=readIndex();if(index.records.size<2)println("WARNING: Only ${index.records.size} media item is indexed. Search quality cannot be meaningfully evaluated.")
    val cap=(System.getenv("MEMORIES_SEARCH_VLM_TOP_N")?.toIntOrNull()?:3).coerceIn(0,5);val budget=System.getenv("MEMORIES_SEARCH_VLM_BUDGET_MS")?.toLongOrNull()?.coerceAtLeast(0)?:0
    val progressive=if(cap>0)refineSearch(index,raw,cap,budget).result else ProgressiveSearchResult(search(index,raw,top.coerceIn(1,100)).matches)
    val result=TimedSearch(QueryParser().parse(raw),(progressive.refined?:progressive.fast).take(top),0.0,0.0);println("SEMANTIC MODEL: ${if(result.matches.any{it.media.embedding!=null})"ACTIVE" else "UNAVAILABLE"}")
    val decision=confidenceDecision(result.matches);println("\nQUERY:\n$raw")
    if(!showRanking&&!debug){val best=result.matches.firstOrNull();if(decision.confident&&best!=null)println("\nBEST MATCH:\n${best.media.displayName}\n\nConfidence: HIGH\nWhy: ${best.explanations.firstOrNull()?:"strong visual semantic match"}")else println("\nNO CONFIDENT MATCH${best?.let{"\n\nBest candidate:\n${it.media.displayName}"}.orEmpty()}\n\nConfidence: LOW")}
    else {println("\nRank\tFilename\tFinal\tTinyCLIP\tVLM caption\tVLM object\tVLM activity\tVLM scene/color\tOCR\tColor\tTop1 margin\tDecision");result.matches.forEachIndexed { i,m -> println("${i+1}\t${m.media.displayName}\t${f(m.score)}\t${f(m.breakdown.fullSemantic)}\t${f(m.breakdown.vlmCaption)}\t${f(m.breakdown.vlmObjects)}\t${f(m.breakdown.vlmActivities)}\t${f(m.breakdown.vlmScenes)}\t${f(m.breakdown.ocr)}\t${f(m.breakdown.colors)}\t${if(i==0)f(decision.margin) else "-"}\t${if(i==0)if(decision.confident)"CONFIDENT" else "NOT CONFIDENT: ${decision.reason}" else "candidate"}") }}
    if(debug)println("Filename evidence: DISABLED\nQuery concepts: ${result.query.semanticConcepts}\nMIN_CONFIDENCE=${System.getenv("MEMORIES_MIN_CONFIDENCE")?:"0.30"}\nMIN_TOP1_MARGIN=${System.getenv("MEMORIES_MIN_TOP1_MARGIN")?:"0.03"}")
    println("VLM calls: ${progressive.vlmCalls}; top-N cap: $cap; budget exhausted: ${progressive.budgetExhausted}")
}
private fun listCommand(){val indexed=runCatching{readIndex().records.mapTo(mutableSetOf()){it.uri}}.getOrDefault(emptySet());println("Filename | Type | Format | Supported | Indexed | Reason");DirectoryMediaSource(mediaRoot).inspect().forEach{e->println("${e.relative} | ${e.kind?.name?:"-"} | ${e.format.uppercase()} | ${if(e.supported)"YES" else "NO"} | ${if(e.relative in indexed)"YES" else "NO"} | ${e.reason?:"-"}")}}
private fun interactiveCommand(){val index=readIndex();val clip=if(TinyClipBridge.available())TinyClipBridge()else null;println("Honorable Memories AI Test\nSEMANTIC MODEL: ${if(clip?.active==true)"ACTIVE" else "UNAVAILABLE"}\nType a description, or 'quit'.");while(true){print("\nSearch description:\n> ");System.out.flush();val q=readlnOrNull()?.trim()?:break;if(q.equals("quit",true)||q.equals("exit",true))break;if(q.isNotEmpty()){val r=search(index,q,10,clip=clip);println("Rank\tFilename\tType\tFinal\tSemantic\tOCR\tColor\tLabel\tTimestamp\tMatch");r.matches.forEachIndexed{i,m->println("${i+1}\t${m.media.displayName}\t${m.media.kind}\t${f(m.score)}\t${f(m.breakdown.fullSemantic)}\t${f(m.breakdown.ocr)}\t${f(m.breakdown.colors)}\t${f(m.breakdown.labels)}\t${m.bestTimestampMs?.let(::timestamp)?:"-"}\t${m.explanations.joinToString("; ")}")}}};clip?.close()}
private fun f(v:Double)="%.3f".format(v)
private fun timestamp(ms:Long)="%02d:%02d".format(ms/60000,(ms/1000)%60)

private fun evaluateCommand() {
    val helper=Paths.get("android-app/test-lab/evaluation_labels.py").toAbsolutePath();val process=ProcessBuilder("python3",helper.toString(),"export").start();val lines=process.inputStream.bufferedReader().readLines();require(process.waitFor()==0){process.errorStream.bufferedReader().readText()};require(lines.isNotEmpty()){ "evaluation.json has no cases" }
    fun decode(value:String)=String(Base64.getDecoder().decode(value),UTF_8)
    data class Truth(val query:String,val expected:Set<String>,val category:String,val difficulty:String,val startMs:Long?,val endMs:Long?)
    val truths=lines.map{line->val p=line.split('\t');require(p.size==6){"Invalid evaluation export"};Truth(decode(p[0]),decode(p[1]).split('\u001f').filter(String::isNotBlank).toSet(),decode(p[2]),decode(p[3]).ifBlank{"unspecified"},p[4].toDoubleOrNull()?.times(1000)?.toLong(),p[5].toDoubleOrNull()?.times(1000)?.toLong())}
    val initial=readIndex();val evalCases=truths.map{truth->val ids=initial.records.filter{it.displayName in truth.expected||it.uri in truth.expected}.map{it.id}.toSet();require(ids.isNotEmpty()){ "Expected media is not indexed for query '${truth.query}': ${truth.expected}" };EvaluationCase(truth.query,ids,truth.difficulty,truth.category)}
    data class Outcome(val matches:List<SearchMatch>,val latencyMs:Double,val calls:Int=0)
    data class Strategy(val name:String,val run:(Truth)->Outcome)
    val tiny=initial.copy(records=initial.records.map{it.copy(visionUnderstanding=null,videoFrames=it.videoFrames.map{frame->frame.copy(visionUnderstanding=null)})})
    fun timed(block:()->List<SearchMatch>):Outcome{val started=System.nanoTime();val matches=block();return Outcome(matches,(System.nanoTime()-started)/1e6)}
    val strategies=listOf(
        Strategy("TINYCLIP ONLY"){truth->timed{search(tiny,truth.query,10).matches}},
        Strategy("CACHED VLM ONLY"){truth->timed{val q=QueryParser().parse(truth.query);SearchRanker().rank(q,initial.records.map{it.copy(embedding=null,ocr="",labels=emptySet(),metadataTerms=emptySet(),dominantColors=emptySet())}).take(10)}},
        Strategy("HYBRID WITHOUT NEW VLM"){truth->timed{search(readIndex(),truth.query,10).matches}},
        Strategy("GATED HYBRID"){truth->timed{val result=search(readIndex(),truth.query,10);ConfidenceGatedVision.decide(result.matches,GatedVisionConfig(enabled=true,maxCandidates=3));result.matches}},
        Strategy("HYBRID + TOP-1 NEW VLM"){truth->val r=refineSearch(readIndex(),truth.query,1,0);Outcome(r.result.refined?:r.result.fast,r.latencyMs,r.result.vlmCalls)},
        Strategy("HYBRID + TOP-3 NEW VLM"){truth->val r=refineSearch(readIndex(),truth.query,3,0);Outcome(r.result.refined?:r.result.fast,r.latencyMs,r.result.vlmCalls)}
    )
    println("Total queries: ${truths.size}");if(truths.size<20)println("WARNING: Fewer than 20 queries; do not use these results to tune production ranking.")
    var hybridRanked:List<List<Long>> = emptyList()
    strategies.forEach{strategy->val cold=truths.map(strategy.run);val warm=truths.map(strategy.run);val ranked=cold.map{out->out.matches.map{it.media.id}};var metricCursor=0;val metrics=SearchEvaluationHarness.evaluate(evalCases){ranked[metricCursor++]};if(strategy.name=="HYBRID WITHOUT NEW VLM")hybridRanked=ranked
        println("${strategy.name}: Recall@1=${f(metrics.recallAt1)} Recall@5=${f(metrics.recallAt5)} Recall@10=${f(metrics.recallAt10)} MRR=${f(metrics.mrr)} search=${f(cold.map{it.latencyMs}.average())}ms cold=${f(cold.map{it.latencyMs}.average())}ms warm=${f(warm.map{it.latencyMs}.average())}ms VLM calls/query=${f(cold.sumOf{it.calls}.toDouble()/truths.size)}")
        val timestampCases=truths.indices.filter{truths[it].startMs!=null&&truths[it].endMs!=null};if(timestampCases.isNotEmpty()){val correct=timestampCases.count{i->cold[i].matches.firstOrNull()?.bestTimestampMs?.let{it in truths[i].startMs!!..truths[i].endMs!!}==true};println("  Video timestamp accuracy: $correct/${timestampCases.size}")}
    }
    truths.indices.groupBy{truths[it].category}.filterValues{it.size>=2}.forEach{(category,indices)->val subset=indices.map{evalCases[it]};val ranked=indices.map{hybridRanked[it]};var cursor=0;val m=SearchEvaluationHarness.evaluate(subset){ranked[cursor++]};println("CATEGORY $category (${indices.size}): Recall@1=${f(m.recallAt1)} Recall@5=${f(m.recallAt5)} Recall@10=${f(m.recallAt10)} MRR=${f(m.mrr)}")}
}

private fun serve(port:Int) {
    require(port != 8080) { "Port 8080 is reserved; Honorable will not use it." }
    val current=AtomicReference(readIndex());val clip=if(TinyClipBridge.available())TinyClipBridge()else null;val server=HttpServer.create(InetSocketAddress("0.0.0.0",port),0);server.executor=Executors.newCachedThreadPool()
    server.createContext("/"){exchange->static(exchange)}
    server.createContext("/health"){exchange->exchange.respond(200,"application/json; charset=utf-8","{\"service\":\"honorable-linux-demo\",\"status\":\"ok\",\"shell\":\"honorable-phone\"}")}
    server.createContext("/api/media"){exchange->exchange.respond(200,"application/json",mediaJson(current.get()))}
    server.createContext("/api/status"){exchange->exchange.respond(200,"application/json",statusJson(current.get()))}
    server.createContext("/api/search"){exchange->val params=queryParams(exchange.requestURI.rawQuery);val kind=when(params["type"]){"IMAGE"->MediaKind.IMAGE;"VIDEO"->MediaKind.VIDEO;else->null};exchange.respond(200,"application/json",searchJson(search(current.get(),params["q"].orEmpty(),params["top"]?.toIntOrNull()?.coerceIn(1,50)?:10,kind,clip)))}
    server.createContext("/api/refresh"){exchange->if(exchange.requestMethod!="POST")exchange.respond(405,"text/plain","POST required")else runCatching{val next=buildIndex(current.get());writeIndex(next);current.set(next);exchange.respond(200,"application/json",mediaJson(next))}.getOrElse{exchange.respond(500,"application/json","{\"error\":${json(it.message?:"refresh failed")}}")}}
    server.createContext("/media/"){exchange->serveMedia(exchange)}
    server.start()
    println("HONORABLE LINUX DEMO")
    println("STATUS: RUNNING")
    println("PORT: $port")
    val codespace=System.getenv("CODESPACE_NAME")
    val forwardingDomain=System.getenv("GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN")
    if(!codespace.isNullOrBlank()&&!forwardingDomain.isNullOrBlank())println("URL: https://$codespace-$port.$forwardingDomain")
    else println("URL: http://localhost:$port (open/forward port $port)")
    println("BIND: 0.0.0.0:$port")
    println("Serving only: $mediaRoot")
    println("SEMANTIC MODEL: ${if(current.get().records.any{it.embedding?.size==512})"ACTIVE" else "UNAVAILABLE"}")
}
private val UTF_8=StandardCharsets.UTF_8
private fun queryParams(raw:String?)=raw.orEmpty().split('&').filter{it.isNotBlank()}.associate{val p=it.split('=',limit=2);URLDecoder.decode(p[0],UTF_8) to URLDecoder.decode(p.getOrElse(1){""},UTF_8)}
private fun HttpExchange.respond(code:Int,type:String,body:String){responseHeaders.add("Content-Type",type);val bytes=body.toByteArray();sendResponseHeaders(code,bytes.size.toLong());responseBody.use{it.write(bytes)}}
private fun static(exchange:HttpExchange){val name=when(exchange.requestURI.path){"/","/index.html"->"index.html";"/phone.css"->"phone.css";"/honorable-parity.css"->"honorable-parity.css";"/phone.js"->"phone.js";else->null};if(name==null){exchange.respond(404,"text/plain","Not found");return};val root=Paths.get("android-app/test-lab/web").toAbsolutePath().normalize();val file=root.resolve(name).normalize();if(!file.startsWith(root)||!file.isRegularFile()){exchange.respond(404,"text/plain","Not found");return};exchange.respond(200,when(file.extension){"css"->"text/css; charset=utf-8";"js"->"text/javascript; charset=utf-8";else->"text/html; charset=utf-8"},file.readText())}
private fun serveMedia(exchange:HttpExchange){val relative=runCatching{URLDecoder.decode(exchange.requestURI.rawPath.removePrefix("/media/"),UTF_8)}.getOrNull();val root=runCatching{mediaRoot.toRealPath()}.getOrNull();val file=relative?.let{runCatching{mediaRoot.resolve(it).normalize().toRealPath()}.getOrNull()};if(root==null||file==null||!file.startsWith(root)||!file.isRegularFile()||file.extension.lowercase() !in mediaLikeExt){exchange.respond(404,"text/plain","Not found");return};val size=Files.size(file);val range=exchange.requestHeaders.getFirst("Range")?.let{Regex("bytes=(\\d+)-(\\d*)").matchEntire(it)};val start=range?.groupValues?.get(1)?.toLongOrNull()?.coerceIn(0,size-1)?:0;val end=range?.groupValues?.get(2)?.toLongOrNull()?.takeIf{it>=start}?.coerceAtMost(size-1)?:size-1;exchange.responseHeaders.add("Content-Type",Files.probeContentType(file)?:"application/octet-stream");exchange.responseHeaders.add("Accept-Ranges","bytes");if(range!=null)exchange.responseHeaders.add("Content-Range","bytes $start-$end/$size");exchange.sendResponseHeaders(if(range!=null)206 else 200,end-start+1);file.inputStream().use{input->input.skip(start);exchange.responseBody.use{output->var remaining=end-start+1;val buffer=ByteArray(65536);while(remaining>0){val n=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());if(n<0)break;output.write(buffer,0,n);remaining-=n}}}}
private fun json(s:String)=buildString{append('"');s.forEach{when(it){'"'->append("\\\"");'\\'->append("\\\\");'\n'->append("\\n");else->append(it)}};append('"')}
private fun mediaJson(index:LabIndex)="{\"count\":${index.records.size},\"photos\":${index.records.count{it.kind==MediaKind.IMAGE}},\"videos\":${index.records.count{it.kind==MediaKind.VIDEO}},\"items\":["+index.records.sortedByDescending{it.capturedAtEpochMs}.joinToString(","){m->"{\"name\":${json(m.displayName)},\"uri\":${json(m.uri)},\"type\":${json(m.kind.name)},\"duration\":${m.durationMs?:"null"},\"modified\":${m.capturedAtEpochMs}}"}+"]}"
private fun statusJson(index:LabIndex)="{\"engine\":\"REAL\",\"indexed\":${index.records.size},\"tinyclip\":${index.records.any{it.embedding?.size==512}},\"ocr\":${json(if(commandExists("tesseract"))"ACTIVE" else "UNAVAILABLE")},\"vlm\":${json(if(index.records.any{it.visionUnderstanding!=null})"CACHED" else "FALLBACK")},\"video\":${json(if(commandExists("ffmpeg")&&commandExists("ffprobe"))"ACTIVE" else "UNAVAILABLE")},\"debug\":${System.getenv("DEMO_DEBUG").equals("true",true)}}"
private fun searchJson(r:TimedSearch):String{val decision=confidenceDecision(r.matches);return "{\"model\":\"TinyCLIP/hybrid\",\"latencyMs\":${f(r.latencyMs)},\"confident\":${decision.confident},\"margin\":${f(decision.margin)},\"decision\":${json(decision.reason)},\"results\":["+r.matches.mapIndexed{i,m->"{\"rank\":${i+1},\"name\":${json(m.media.displayName)},\"uri\":${json(m.media.uri)},\"type\":${json(m.media.kind.name)},\"score\":${f(m.score)},\"semantic\":${f(m.breakdown.fullSemantic)},\"vlm\":${f(m.breakdown.vlmCaption+m.breakdown.vlmObjects+m.breakdown.vlmActivities+m.breakdown.vlmScenes)},\"ocr\":${f(m.breakdown.ocr)},\"color\":${f(m.breakdown.colors)},\"metadata\":${f(m.breakdown.metadata)},\"timestamp\":${m.bestTimestampMs?:"null"},\"why\":${json(m.explanations.joinToString("; "))}}"}.joinToString(",")+"]}"}

private const val HTML="""<!doctype html><meta name=viewport content="width=device-width"><title>Memories AI Test Lab</title><style>body{font:15px system-ui;max-width:1100px;margin:30px auto;padding:0 16px;background:#10131a;color:#eee}form{display:flex;gap:8px}input{flex:1}input,select,button{padding:12px;border-radius:8px;border:1px solid #555;background:#202532;color:white}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:16px;margin-top:20px}.card{background:#1b202b;padding:10px;border-radius:10px}img,video{width:100%;height:160px;object-fit:cover;background:#000}.muted{color:#aaa;font-size:12px}</style><h1>Memories AI Test Lab</h1><p>SEMANTIC MODEL: <b>FALLBACK</b> · local files only</p><form id=f><input id=q placeholder="red car in snow" autofocus><select id=t><option value="">Photos + Videos</option><option>IMAGE</option><option>VIDEO</option></select><button>Search</button></form><p id=status></p><div id=r class=grid></div><script>f.onsubmit=async e=>{e.preventDefault();status.textContent='Searching…';let d=await(await fetch('/api/search?q='+encodeURIComponent(q.value)+'&type='+t.value)).json();status.textContent=d.results.length+' results · '+d.latencyMs+' ms';r.innerHTML=d.results.map(x=>'<article class=card>'+(x.type==='VIDEO'?'<video controls src="/media/'+encodeURI(x.uri)+(x.timestamp!=null?'#t='+(x.timestamp/1000):'')+'"></video>':'<img src="/media/'+encodeURI(x.uri)+'">')+'<b>'+x.rank+'. '+x.name+'</b><div>Score '+x.score+(x.timestamp!=null?' · '+Math.floor(x.timestamp/60000)+':'+String(Math.floor(x.timestamp/1000)%60).padStart(2,'0'):'')+'</div><div class=muted>semantic '+x.semantic+' · OCR '+x.ocr+' · color '+x.color+' · label '+x.label+'</div><p>'+(x.why||'No positive evidence')+'</p></article>').join('')}</script>"""
