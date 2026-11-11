const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const source = fs.readFileSync(path.join(__dirname, '../Honorable-mobile-app-main/mobile-react-native/android/app/src/main/java/com/honorablemobile/HonorableVideoEditorActivity.kt'), 'utf8');
const scene = source.slice(source.indexOf('    private fun suggestScene()'), source.indexOf('    private fun suggestScene()') + source.slice(source.indexOf('    private fun suggestScene()')).indexOf('\n    private fun ', 1));

test('scene admission captures a full review and project before starting inference', () => {
  const capture = scene.indexOf('val review=captureCaptionReview()?:return');
  assert.ok(capture > scene.indexOf('if(!allowOnDeviceAi())return'));
  assert.ok(scene.indexOf('val current=project?:return') > capture);
  assert.ok(scene.indexOf('analysisCancellation.set(false)') > capture);
  assert.match(scene, /VideoSceneSelection\.capture\(current\.segments/);
});

test('full approval precedes cancellation messages and every returned trim change', () => {
  const callback = scene.slice(scene.lastIndexOf('runOnUiThread {'));
  const release = callback.indexOf('analyzing = false');
  const guard = callback.indexOf('if (!canReviewSceneResult(review,current,selection)) return@runOnUiThread');
  assert.ok(release >= 0 && guard > release);
  for (const action of ['if(analysisCancellation.get())', 'result.onSuccess', 'start.progress =', 'end.progress =']) {
    assert.ok(callback.indexOf(action) > guard, action);
  }
  assert.match(callback, /selection\.stillMatches\(current\.segments/);
});

test('execute production scene/full-review predicates against controlled session boundaries', { skip: !process.env.KOTLIN_HOME && 'Set KOTLIN_HOME to run the isolated Kotlin predicate harness' }, () => {
  // Extract exact production methods. Surrounding Android/session dependencies are
  // controlled fixtures: this exercises predicates, not Activity or Android UI.
  const gate = source.match(/    private fun canReviewSceneResult\([^\n]+\n[^\n]+/)[0].replace('private fun', 'fun');
  const apply = source.match(/    private fun canApplyCaptionReview\([^\n]+\n[^\n]+\n[^\n]+\n    }/)[0];
  const fixture = `
data class State(val key:Int) { fun stillMatches(other:State)=this==other }
data class VideoCaptionReviewState(val state:State,val generation:Long,val mode:String,val namespace:String,val sources:Map<String,String>,val durations:Map<String,Long>)
class VideoEditProject(var segments:List<Int> = listOf(1,2))
data class VideoSceneSelection(val selectedIndex:Int,val clips:List<Int>)
class Fixture {
 var project=VideoEditProject(); var selectedClip=0; var permitted=true
 var captionReviewGeneration=0L; var accessMode="guest"; var namespace="guest-private"
 var state=State(1); var sources=mapOf("primary" to "synthetic://original")
 var durations=mapOf("primary" to 5000L)
 fun canRequestTimelineCaptionImport()=permitted
 fun videoLookNamespace()=namespace
 fun closeState()=state
 fun sourceUris()=sources
 fun sourceDurations()=durations
 fun review()=VideoCaptionReviewState(state,captionReviewGeneration,accessMode,namespace,sources.toMap(),durations.toMap())
 ${apply}
 ${gate}
}
fun main() {
 var checks=0
 fun expect(value:Boolean) { check(value); checks++ }
 val mutations=listOf<Pair<String,(Fixture)->Unit>>(
  "admission" to {it.permitted=false}, "generation" to {it.captionReviewGeneration++},
  "mode" to {it.accessMode="account"}, "namespace" to {it.namespace="other-private"},
  "state" to {it.state=State(2)}, "source" to {it.sources=mapOf("primary" to "synthetic://replacement")},
  "duration" to {it.durations=mapOf("primary" to 6000L)}, "selection" to {it.selectedClip=1},
  "timeline" to {it.project.segments=listOf(2,1)}, "identity" to {it.project=VideoEditProject()})
 for((name,mutate) in mutations) {
  val f=Fixture(); val current=f.project; val original=current.segments; val review=f.review()
  val selection=VideoSceneSelection(0,original.toList())
  expect(f.canReviewSceneResult(review,current,selection))
  mutate(f)
  check(!f.canReviewSceneResult(review,current,selection)) { name }; checks++
  val after=f.project.segments
  expect(!f.canReviewSceneResult(review,current,selection))
  expect(after===f.project.segments) // Refusal itself cannot mutate the timeline.
 }
 val f=Fixture(); val review=f.review(); val selection=VideoSceneSelection(0,f.project.segments.toList())
 expect(!f.canReviewSceneResult(review,VideoEditProject(),selection))
 f.captionReviewGeneration++; f.state=review.state // Restored visible state cannot revive the old generation.
 expect(!f.canReviewSceneResult(review,f.project,selection))
 println("Passed $checks isolated production-predicate assertions")
}
`;
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'noble-scene-review-'));
  try {
    const input = path.join(temp, 'SceneReview.kt');
    const jar = path.join(temp, 'scene-review.jar');
    fs.writeFileSync(input, fixture);
    const compile = spawnSync('java', ['-cp', path.join(process.env.KOTLIN_HOME, 'lib/*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-kotlin-home', process.env.KOTLIN_HOME, input, '-include-runtime', '-d', jar], { encoding: 'utf8', timeout: 60000 });
    assert.equal(compile.status, 0, compile.stderr || String(compile.error));
    const run = spawnSync('java', ['-jar', jar], { encoding: 'utf8', timeout: 15000 });
    assert.equal(run.status, 0, run.stderr || String(run.error));
    assert.match(run.stdout, /Passed 42 isolated production-predicate assertions/);
  } finally { fs.rmSync(temp, { recursive: true, force: true }); }
});
