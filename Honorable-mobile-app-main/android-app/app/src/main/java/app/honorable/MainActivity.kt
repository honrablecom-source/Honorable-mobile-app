package app.honorable

import app.honorable.auth.AccountSession
import app.honorable.auth.GoogleAccountSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import app.honorable.search.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(5,5,6)));setContent { HonorableApp() } }
}

private val BackgroundColor=HonorablePalette.surface
private val CanvasColor=HonorablePalette.canvas
private val ScrimColor=HonorablePalette.canvas
private val SurfaceColor=HonorablePalette.surfaceRaised
private val SelectedSurfaceColor=HonorablePalette.surfaceSelected
private val TextColor=HonorablePalette.textPrimary
private val BorderColor=HonorablePalette.border
private val ControlColor=HonorablePalette.textPrimary
private val AccentColor=HonorablePalette.accent
private val SecondaryTextColor=HonorablePalette.textSecondary
private val ConfirmedColor=HonorablePalette.accent
private val ErrorColor=HonorablePalette.danger
private val InterfaceFont=FontFamily.SansSerif
private val DarkColors=darkColorScheme(primary=HonorablePalette.textPrimary,onPrimary=HonorablePalette.canvas,background=HonorablePalette.canvas,onBackground=HonorablePalette.textPrimary,surface=HonorablePalette.surfaceRaised,onSurface=HonorablePalette.textPrimary,surfaceVariant=HonorablePalette.surfaceSelected,onSurfaceVariant=HonorablePalette.textSecondary,outline=HonorablePalette.textTertiary,outlineVariant=HonorablePalette.border,error=HonorablePalette.danger)
private val AppType = Typography(
    displaySmall=Typography().displaySmall.copy(fontFamily=InterfaceFont,fontWeight=FontWeight.Medium,fontSize=46.sp,lineHeight=44.sp,letterSpacing=(-1.9).sp),
    headlineLarge=Typography().headlineLarge.copy(fontFamily=InterfaceFont,fontWeight=FontWeight.Medium,fontSize=36.sp,lineHeight=37.sp,letterSpacing=(-1.1).sp),
    headlineMedium=Typography().headlineMedium.copy(fontFamily=InterfaceFont,fontWeight=FontWeight.Bold,fontSize=28.sp,lineHeight=31.sp,letterSpacing=(-.7).sp),
    titleLarge=Typography().titleLarge.copy(fontFamily=InterfaceFont,fontWeight=FontWeight.Medium,fontSize=22.sp,lineHeight=25.sp,letterSpacing=(-.4).sp),
    titleMedium=Typography().titleMedium.copy(fontFamily=InterfaceFont,fontWeight=FontWeight.Bold,letterSpacing=(-.2).sp),
    bodyLarge=Typography().bodyLarge.copy(fontFamily=InterfaceFont,lineHeight=24.sp,letterSpacing=(-.1).sp),
    labelLarge=Typography().labelLarge.copy(fontFamily=InterfaceFont,fontWeight=FontWeight.Bold,letterSpacing=(-.1).sp),
    labelMedium=Typography().labelMedium.copy(fontFamily=InterfaceFont,fontWeight=FontWeight.SemiBold,letterSpacing=0.sp)
)
private val AppShapes=Shapes(extraSmall=RoundedCornerShape(6.dp),small=RoundedCornerShape(8.dp),medium=RoundedCornerShape(10.dp),large=RoundedCornerShape(12.dp),extraLarge=RoundedCornerShape(16.dp))

@Composable fun HonorableApp() {
    MaterialTheme(colorScheme=DarkColors,typography=AppType,shapes=AppShapes){AccountGate{AppBackground{HonorableShell()}}}
}

@Composable private fun AppBackground(content:@Composable BoxScope.()->Unit){Box(Modifier.fillMaxSize().background(HonorablePalette.canvas),content=content)}

private enum class MainTab(val label:String,val icon:ImageVector){HOME("Home",HonorableIcons.Home),MEMORIES("Memories",HonorableIcons.PhotoLibrary),STUDIO("Studio",HonorableIcons.Tune),PASS("Pass",HonorableIcons.CreditCard),USAGE("Usage",HonorableIcons.Timeline)}
private enum class Overlay { NONE, VIEWER, PRIVACY, PLUS, ENGINE_HEALTH, ACCOUNT }

@Composable private fun HonorableShell(){
    var pendingQuery by rememberSaveable{mutableStateOf("")};var pendingModel by rememberSaveable{mutableStateOf("SERAN_V1")};var tab by rememberSaveable{mutableStateOf(MainTab.HOME)};var overlay by rememberSaveable{mutableStateOf(Overlay.NONE)};var selected by remember{mutableStateOf<SearchMatch?>(null)}
    BackHandler(overlay!=Overlay.NONE){overlay=Overlay.NONE}
    LaunchedEffect(overlay,selected){if(overlay==Overlay.VIEWER&&selected==null)overlay=Overlay.NONE}
    Box(Modifier.fillMaxSize()){
        AnimatedContent(tab,label="main-screen",transitionSpec={(fadeIn(tween(180))) togetherWith fadeOut(tween(140))}){current->
            when(current){
                MainTab.HOME->HomeScreen({q,m->pendingQuery=q;pendingModel=m;tab=MainTab.MEMORIES},{tab=MainTab.MEMORIES},{overlay=Overlay.ACCOUNT})
                MainTab.MEMORIES->MemoriesScreen(pendingQuery,pendingModel,{pendingQuery=""}){selected=it;overlay=Overlay.VIEWER}
                MainTab.STUDIO->ProductStudio{tab=MainTab.MEMORIES}
                MainTab.PASS->ProductPass()
                MainTab.USAGE->ProductUsage()
            }
        }
        BottomNavigation(tab,{tab=it},Modifier.align(Alignment.BottomCenter))
        AnimatedVisibility(overlay!=Overlay.NONE,enter=fadeIn()+scaleIn(initialScale=.97f),exit=fadeOut()+scaleOut(targetScale=.98f)){
            when(overlay){Overlay.ACCOUNT->OverlayScaffold({overlay=Overlay.NONE}){SettingsScreen({overlay=Overlay.PRIVACY},{overlay=Overlay.PLUS},{overlay=Overlay.ENGINE_HEALTH})};Overlay.VIEWER->selected?.let{MediaViewer(it){overlay=Overlay.NONE}};Overlay.PRIVACY->PrivacyScreen{overlay=Overlay.NONE};Overlay.PLUS->ProductStudio{overlay=Overlay.NONE;tab=MainTab.MEMORIES};Overlay.ENGINE_HEALTH->EngineHealthScreen{overlay=Overlay.NONE};else->Unit}
        }
    }
}

@Composable private fun HomeScreen(submit:(String,String)->Unit,openMemories:()->Unit,openAccount:()->Unit){
 val context=LocalContext.current;var query by rememberSaveable{mutableStateOf("")};var model by rememberSaveable{mutableStateOf("SERAN_V1")}
 val models=remember{JSONObject(context.assets.open("product-catalog.json").bufferedReader().use{it.readText()}).getJSONArray("models")}

 LazyColumn(contentPadding=PaddingValues(24.dp,40.dp,24.dp,104.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){
 item{Row(verticalAlignment=Alignment.CenterVertically){Text("honorable",Modifier.weight(1f),fontSize=20.sp);IconButton(onClick=openAccount){Icon(HonorableIcons.AccountCircle,"Account")}}}
 item{Text("What do you\nremember?",Modifier.padding(top=24.dp),fontSize=42.sp,lineHeight=46.sp,fontWeight=FontWeight.Normal)}
 item{MemorySearchField("the video where we were at the beach…",query,onValueChange={query=it}){if(query.isNotBlank())submit(query,model)};ModelPicker(models,model,{model=it})}
 item{val a=LocalAccount.current.account;Text("${a?.optInt("freeMonthlyRemaining")?:0} free · ${a?.optInt("balance")?:0} purchased",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 item{Text("Your memories",fontSize=20.sp);Text("Search the photos and videos on this device.",Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick=openMemories){Text("Open memories")}}
 }
}

@Composable private fun ArchiveSearchCommand(label:String,onClick:()->Unit){OutlinedButton(onClick,Modifier.fillMaxWidth().heightIn(min=64.dp),shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),contentPadding=PaddingValues(16.dp)){Text(label,Modifier.weight(1f),fontSize=16.sp,color=TextColor);Icon(HonorableIcons.Search,"Search",tint=TextColor)}}

@Composable private fun MetadataLabel(text:String,tint:Color){Row(Modifier.clip(CircleShape).background(tint.copy(.13f)).border(1.dp,tint.copy(.22f),CircleShape).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(6.dp).clip(CircleShape).background(tint));Spacer(Modifier.width(7.dp));Text(text,style=MaterialTheme.typography.labelMedium,color=tint)}}

@Composable fun MemorySearchField(hint:String,value:String="",focused:Boolean=false,dark:Boolean=false,onValueChange:(String)->Unit={},onClick:()->Unit){
 val focusRequester=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
 LaunchedEffect(focused){if(focused){focusRequester.requestFocus();keyboard?.show()}}
 Row(Modifier.fillMaxWidth().heightIn(min=64.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(10.dp)).border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(10.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
 BasicTextField(value,onValueChange,Modifier.weight(1f).focusRequester(focusRequester),singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(color=TextColor),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide();onClick()}),decorationBox={inner->if(value.isBlank())Text(hint,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp);inner()});IconButton(onClick={keyboard?.hide();onClick()}){Icon(HonorableIcons.ArrowUpward,"Search",tint=TextColor)}
 }
}

private enum class SearchStage { LANDING, FOCUS, SEARCHING, RESULTS }
@Composable private fun MemoriesScreen(initialQuery:String="",initialModel:String="SERAN_V1",consumeQuery:()->Unit={},openViewer:(SearchMatch)->Unit){
    val context=LocalContext.current;val vm:MemoriesViewModel=viewModel();val backend by vm.state.collectAsStateWithLifecycle();var stage by rememberSaveable{mutableStateOf(SearchStage.LANDING)};var query by rememberSaveable{mutableStateOf(initialQuery)};var filter by rememberSaveable{mutableStateOf("All")}
    var productModel by rememberSaveable{mutableStateOf(initialModel)}
    val productModels=remember{JSONObject(context.assets.open("product-catalog.json").bufferedReader().use{it.readText()}).getJSONArray("models")}
    val productAccount=LocalAccount.current
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){vm.permissionResult()}
    LaunchedEffect(backend){if(backend is MemorySearchState.Results){stage=SearchStage.RESULTS;productAccount.sync()};if(backend is MemorySearchState.Failed)stage=SearchStage.LANDING}
    LaunchedEffect(initialQuery,backend){if(initialQuery.isNotBlank()&&(backend is MemorySearchState.Ready||backend is MemorySearchState.Results||backend is MemorySearchState.Failed)){query=initialQuery;productModel=initialModel;stage=SearchStage.SEARCHING;vm.search(initialQuery,initialModel);consumeQuery()}}
    Column(Modifier.fillMaxSize().padding(top=42.dp)){
        Box(Modifier.padding(horizontal=24.dp)){ModelPicker(productModels,productModel,{productModel=it},stage==SearchStage.SEARCHING)}
        AnimatedContent(stage,Modifier.weight(1f),label="memory-state",transitionSpec={fadeIn(tween(220))+slideInVertically{it/16} togetherWith fadeOut(tween(120))}){current->
            when(current){
                SearchStage.LANDING->when(val state=backend){MemorySearchState.PermissionRequired->PermissionState{permissionLauncher.launch(MediaCapabilityManager(context).requestedPermissions())};is MemorySearchState.Indexing->IndexingState(state.progress);is MemorySearchState.Failed->BackendFailure(state.message);is MemorySearchState.Ready->MemoryLanding(state.count,{stage=SearchStage.FOCUS},{query=it;stage=SearchStage.SEARCHING;vm.search(it,productModel)});else->MemoryLanding(null,{stage=SearchStage.FOCUS},{query=it;stage=SearchStage.SEARCHING;vm.search(it,productModel)})}
                SearchStage.FOCUS->SearchFocus(query,{query=it},{if(query.isNotBlank()){stage=SearchStage.SEARCHING;vm.search(query,productModel)}},{stage=SearchStage.LANDING})
                SearchStage.SEARCHING->SearchInMotion(query)
                SearchStage.RESULTS->SearchResults(query,filter,{filter=it},{stage=SearchStage.FOCUS},(backend as? MemorySearchState.Results)?.matches.orEmpty(),openViewer)
            }
        }
    }
}

@Composable private fun MemoryLanding(count:Int?,focus:()->Unit,search:(String)->Unit){
 LazyColumn(contentPadding=PaddingValues(24.dp,24.dp,24.dp,104.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){
 item{Text("Memories",fontSize=32.sp);Text(count?.let{"$it items ready to search"}?:"Your local library",Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
 item{ArchiveSearchCommand("What do you remember?",focus)}
 item{Text("Try a scene",fontSize=18.sp);Text("Example photograph · search your own library",Modifier.padding(top=8.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 item{FeaturedMemoryPrompt(R.drawable.prompt_beach,"A day by the water"){search("white beach with tall grass")}}
 }
}

@Composable private fun FeaturedMemoryPrompt(imageRes:Int,text:String,onClick:()->Unit){Column(Modifier.fillMaxWidth().clickable(onClick=onClick)){Image(painterResource(imageRes),"Example scene",Modifier.fillMaxWidth().height(280.dp),contentScale=ContentScale.Crop);Text(text,Modifier.padding(vertical=12.dp),fontSize=16.sp)}}

@Composable private fun PermissionState(grant:()->Unit){Column(Modifier.fillMaxSize().padding(24.dp,40.dp,24.dp,104.dp),verticalArrangement=Arrangement.Center){Icon(HonorableIcons.PhotoLibrary,null,Modifier.size(32.dp));Text("Choose your memories",Modifier.padding(top=24.dp),fontSize=32.sp);Text("Choose the photos and videos you want Honorable to search. Your media stays on this device.",Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant);ArchiveSearchCommand("Choose photos & videos",grant)}}

@Composable private fun IndexingState(progress:IndexProgress){val fraction=if(progress.total>0)progress.processed.toFloat()/progress.total else 0f;Column(Modifier.fillMaxSize().padding(24.dp,40.dp,24.dp,104.dp),verticalArrangement=Arrangement.Center){Text("Preparing your library",fontSize=28.sp);Text(if(progress.total>0)"${progress.processed} of ${progress.total} items"else"Finding media on this device…",Modifier.padding(vertical=24.dp));LinearProgressIndicator(progress={fraction.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=HonorablePalette.textPrimary);Text("Your originals stay untouched.",Modifier.padding(top=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}

@Composable private fun BackendEmpty(message:String){Column(Modifier.fillMaxWidth().padding(24.dp)){Text(message,fontSize=24.sp);Text("Try a place, color, date or something visible in the scene.",Modifier.padding(top=16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}

@Composable private fun BackendFailure(message:String){Column(Modifier.fillMaxWidth().padding(24.dp)){Text("Search couldn't finish.",fontSize=24.sp);Text(message,Modifier.padding(top=16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Your original media is unchanged.",Modifier.padding(top=24.dp),fontSize=13.sp)}}

@Composable private fun SearchFocus(query:String,onQuery:(String)->Unit,search:()->Unit,close:()->Unit){Column(Modifier.fillMaxSize().padding(24.dp,16.dp,24.dp,104.dp)){IconButton(onClick=close){Icon(HonorableIcons.Close,"Close search")};Text("What do you\nremember?",Modifier.padding(vertical=32.dp),fontSize=40.sp,lineHeight=44.sp,fontWeight=FontWeight.Normal);MemorySearchField("the video where we were at the beach…",value=query,focused=true,onValueChange=onQuery,onClick=search)}}

@Composable private fun SearchInMotion(query:String){Column(Modifier.fillMaxSize().padding(24.dp,48.dp,24.dp,104.dp),verticalArrangement=Arrangement.Center){CircularProgressIndicator(Modifier.size(28.dp),color=HonorablePalette.textPrimary,strokeWidth=2.dp);Text("Searching your memories…",Modifier.padding(top=32.dp),fontSize=28.sp);Text(query,Modifier.padding(top=16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}

@Composable private fun SearchResults(query:String,filter:String,onFilter:(String)->Unit,edit:()->Unit,matches:List<SearchMatch>,openViewer:(SearchMatch)->Unit){
 val ranked=matches.filter{filter=="All"||filter=="Videos"&&it.media.kind==MediaKind.VIDEO||filter=="Photos"&&it.media.kind==MediaKind.IMAGE||filter=="Screenshots"&&it.media.isScreenshot}
 LazyColumn(contentPadding=PaddingValues(16.dp,16.dp,16.dp,104.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
 item{TextButton(onClick=edit){Text("Change search")};Text(query,fontSize=28.sp);Text("${ranked.size} matches · Best first",Modifier.padding(top=8.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 if(ranked.isNotEmpty())item{BestMatchCard(ranked.first()){openViewer(ranked.first())}}else item{BackendEmpty("No clear match yet")}
 item{LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(listOf("All","Photos","Videos","Screenshots")){CircleFilter(it,filter==it){onFilter(it)}}}}
 items(ranked.drop(1).chunked(2)){pair->Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){pair.forEach{match->RealMemory(match,Modifier.weight(1f).height(200.dp)){openViewer(match)}};if(pair.size==1)Spacer(Modifier.weight(1f))}}
 }
}

@Composable private fun BestMatchCard(match:SearchMatch,onClick:()->Unit){Column(Modifier.fillMaxWidth().clickable(onClick=onClick)){Box(Modifier.fillMaxWidth().height(380.dp)){AsyncImage(Uri.parse(match.media.uri),"Leading result",Modifier.fillMaxSize(),contentScale=ContentScale.Crop);match.bestTimestampMs?.let{Text(formatMoment(it),Modifier.align(Alignment.BottomEnd).padding(12.dp).background(HonorablePalette.canvas).padding(8.dp),fontSize=12.sp)}};Text(confidenceLabel(match),Modifier.padding(vertical=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}

@Composable private fun RealMemory(match:SearchMatch,modifier:Modifier,onClick:()->Unit){Box(modifier.clickable(onClick=onClick).semantics{contentDescription="Open ${match.media.kind.name.lowercase()} result"}){AsyncImage(Uri.parse(match.media.uri),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);match.bestTimestampMs?.let{Text(formatMoment(it),Modifier.align(Alignment.BottomEnd).padding(8.dp).background(HonorablePalette.canvas).padding(4.dp),fontSize=12.sp)}}}

@Composable private fun MediaViewer(match:SearchMatch,close:()->Unit){val context=LocalContext.current;val media=match.media;var chrome by remember{mutableStateOf(true)};Box(Modifier.fillMaxSize().background(HonorablePalette.canvas)){AsyncImage(Uri.parse(media.uri),"Memory",Modifier.fillMaxSize().clickable{chrome=!chrome},contentScale=ContentScale.Fit);if(chrome){Row(Modifier.statusBarsPadding().fillMaxWidth().background(HonorablePalette.canvas.copy(.8f)).padding(8.dp),horizontalArrangement=Arrangement.SpaceBetween){IconButton(onClick=close){Icon(HonorableIcons.ArrowBack,"Back")};IconButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(media.uri)).setDataAndType(Uri.parse(media.uri),if(media.kind==MediaKind.VIDEO)"video/*"else"image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}){Icon(HonorableIcons.OpenInNew,"Open original")}};Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().background(HonorablePalette.canvas.copy(.8f)).padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(match.bestTimestampMs?.let(::formatMoment)?:if(media.kind==MediaKind.VIDEO)"Video"else"Photo",fontSize=13.sp);TextButton(onClick={if(media.kind==MediaKind.IMAGE)context.startActivity(Intent(context,app.honorable.editor.PhotoEditorActivity::class.java).putExtra("uri",media.uri))else android.widget.Toast.makeText(context,"Video sequence editor is Coming Soon",android.widget.Toast.LENGTH_LONG).show()}){Text("Edit")}}}}}

private fun formatMoment(ms:Long):String="${ms/60000}:${(ms/1000%60).toString().padStart(2,'0')}"
private fun confidenceLabel(match:SearchMatch)=when(match.confidence){MatchConfidence.STRONG->"High confidence";MatchConfidence.POSSIBLE->"Possible match";MatchConfidence.WEAK->"Closest match"}

@Composable private fun SettingsScreen(openPrivacy:()->Unit,openPlus:()->Unit,openHealth:()->Unit){val context=LocalContext.current;LazyColumn(contentPadding=PaddingValues(24.dp,16.dp,24.dp,40.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){item{Text("Account",fontSize=32.sp)};item{AccountPanel()};item{SettingCluster("Your library",listOf(SettingItem("Privacy & Data","Your choices",HonorableIcons.Shield,openPrivacy),SettingItem("Photo & video access","Managed by your device",HonorableIcons.PhotoLibrary,{context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))})))};item{SettingCluster("Membership",listOf(SettingItem("Studio","Creative workspace",HonorableIcons.Tune,openPlus)))};item{Text("About Honorable",fontSize=14.sp);Text("On-device search. Private by design.",Modifier.padding(top=12.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}}

@Composable private fun EngineHealthScreen(close:()->Unit){
    val context=LocalContext.current;val database=remember{LocalMediaDatabase(context)};val model=remember{AndroidTinyClipEmbeddingService(context)};val doctor=remember{EngineDoctor(MediaCapabilityManager(context),database,model)}
    var refresh by remember{mutableIntStateOf(0)};var report by remember{mutableStateOf("Running privacy-safe checks…")}
    DisposableEffect(Unit){onDispose{model.close();database.close()}}
    LaunchedEffect(refresh){report=withContext(Dispatchers.IO){doctor.privacySafeReport()}}
    OverlayScaffold(close){LazyColumn(contentPadding=PaddingValues(20.dp,8.dp,20.dp,48.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){item{MetadataLabel("developer diagnostics",ConfirmedColor);Text("Engine health",Modifier.padding(top=16.dp),style=MaterialTheme.typography.displaySmall);Text("Counts, versions, readiness and timings only. No filenames, paths, OCR, captions, or queries.",Modifier.padding(top=10.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)};item{ContentSection{Text(report,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface)}};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={refresh++},modifier=Modifier.weight(1f)){Text("Run checks")};Button(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"Export private diagnostics"))},modifier=Modifier.weight(1f)){Text("Export")}}}}}
}

@Composable private fun PrivacyScreen(close:()->Unit){OverlayScaffold(close){Column(Modifier.fillMaxSize().padding(24.dp)){Text("Privacy & Data",fontSize=32.sp);Text("Your library is yours.",Modifier.padding(vertical=24.dp),fontSize=20.sp);Text("Photo and video search runs locally. Account sign-in and credits use the configured account service.",lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Feedback attachments require your action and consent.",Modifier.padding(top=24.dp),lineHeight=24.sp)}}}

@Composable private fun BottomNavigation(selected:MainTab,onSelect:(MainTab)->Unit,modifier:Modifier=Modifier){Row(modifier.fillMaxWidth().background(HonorablePalette.canvas).navigationBarsPadding().height(64.dp),verticalAlignment=Alignment.CenterVertically){MainTab.entries.forEach{tab->val active=tab==selected;Column(Modifier.weight(1f).fillMaxHeight().clickable{onSelect(tab)}.semantics{this.selected=active;contentDescription=tab.label;role=Role.Tab},verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(tab.icon,null,Modifier.size(22.dp),tint=if(active)HonorablePalette.textPrimary else MaterialTheme.colorScheme.onSurfaceVariant);Text(tab.label,Modifier.padding(top=6.dp),fontSize=11.sp,color=if(active)HonorablePalette.textPrimary else MaterialTheme.colorScheme.onSurfaceVariant)}}}}

@Composable fun NeutralSurface(modifier:Modifier=Modifier,shape:Shape=RoundedCornerShape(10.dp),alpha:Float=1f,shadow:Dp=0.dp,dark:Boolean=false,content:@Composable BoxScope.()->Unit){Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surface),contentAlignment=Alignment.Center,content=content)}

@Composable private fun ContentSection(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(modifier.fillMaxWidth().padding(vertical=16.dp),content=content)}

@Composable private fun SquareIconButton(icon:ImageVector,description:String,onClick:()->Unit,dark:Boolean=false){NeutralSurface(Modifier.size(54.dp).clickable(onClick=onClick),CircleShape,if(dark).86f else .72f,10.dp,dark){Icon(icon,description,Modifier.size(21.dp),tint=if(dark)TextColor else AccentColor)}}
@Composable private fun CircleFilter(label:String,active:Boolean,onClick:()->Unit){OutlinedButton(onClick,shape=RoundedCornerShape(6.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(active)HonorablePalette.textPrimary else Color.Transparent,contentColor=if(active)HonorablePalette.canvas else HonorablePalette.textPrimary),modifier=Modifier.semantics{selected=active}){Text(label)}}

private data class SettingItem(val title:String,val detail:String,val icon:ImageVector,val click:()->Unit)
@Composable private fun SettingCluster(label:String,items:List<SettingItem>){Column{Text(label,Modifier.padding(bottom=8.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);items.forEach{item->HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant);Row(Modifier.fillMaxWidth().heightIn(min=72.dp).clickable(onClick=item.click).padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(item.title,fontSize=16.sp);Text(item.detail,Modifier.padding(top=8.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(HonorableIcons.ChevronRight,null)}}}}

@Composable private fun OverlayScaffold(close:()->Unit,content:@Composable BoxScope.()->Unit){AppBackground{Box(Modifier.fillMaxSize().statusBarsPadding()){SquareIconButton(HonorableIcons.Close,"Close",close);Box(Modifier.fillMaxSize().padding(top=58.dp),content=content)}}}

private class AccountController(context:android.content.Context) {
    init{context.getSharedPreferences("honorable-product",0).edit().putString("account-api",BuildConfig.HONORABLE_ACCOUNT_API_URL).apply()}
    val session=AccountSession(context,BuildConfig.HONORABLE_ACCOUNT_API_URL)
    var status by mutableStateOf("restoring")
    var account by mutableStateOf<JSONObject?>(null)
    var message by mutableStateOf("")
    suspend fun sync(){try{val value=withContext(Dispatchers.IO){session.action("/v1/account",null)};account=value}catch(_:Exception){message="Reconnect to refresh usage."}}
    suspend fun restore(){status="restoring";try{val value=withContext(Dispatchers.IO){session.restore()};account=value.optJSONObject("account");status=value.getString("status")}catch(_:Exception){status="unavailable";message="Connect to the internet to restore your account."}}
    suspend fun signIn(activity:android.app.Activity){status="signing-in";message="";try{val token=GoogleAccountSignIn.token(activity,BuildConfig.HONORABLE_GOOGLE_WEB_CLIENT_ID);val value=withContext(Dispatchers.IO){session.signIn(token)};account=value.optJSONObject("account");status=value.getString("status")}catch(_:Exception){status="welcome";message="Sign-in was not completed. Please try again."}}
    suspend fun signOut(activity:android.app.Activity){account=null;status="welcome";message="";withContext(Dispatchers.IO){session.signOut()};try{GoogleAccountSignIn.clear(activity)}catch(_:Exception){}}
}
private val LocalAccount=staticCompositionLocalOf<AccountController>{error("AccountGate required")}
@Composable private fun AccountGate(content:@Composable ()->Unit){
    val context=LocalContext.current;val controller=remember{AccountController(context)};val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){controller.restore()}
    CompositionLocalProvider(LocalAccount provides controller){when(controller.status){
        "online","offline"->content()
        "restoring","signing-in"->Box(Modifier.fillMaxSize().background(HonorablePalette.canvas),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("honorable",fontSize=27.sp,fontWeight=FontWeight.Bold,color=HonorablePalette.textPrimary);Text("Opening your memories…",Modifier.padding(top=18.dp),fontSize=12.sp,color=Color.Gray)}}
        else->Column(Modifier.fillMaxSize().background(HonorablePalette.canvas).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=26.dp,vertical=30.dp)){
            Text("honorable",fontSize=23.sp,fontWeight=FontWeight.Bold,color=HonorablePalette.textPrimary)
            Box(Modifier.padding(top=30.dp,bottom=22.dp).fillMaxWidth().height(180.dp)){Image(painterResource(R.drawable.prompt_beach),"A quiet beach memory",Modifier.fillMaxSize(),contentScale=ContentScale.Crop);}
            Text("Find any\nmemory.",fontSize=52.sp,lineHeight=51.sp,fontWeight=FontWeight.Medium,letterSpacing=(-2).sp,color=HonorablePalette.textPrimary)
            Text("You remember the moment.\nHonorable finds it.",Modifier.padding(top=16.dp,bottom=24.dp),fontSize=17.sp,lineHeight=25.sp,color=Color(0xFFAAAAAA))
            Button(onClick={scope.launch{controller.signIn(context as android.app.Activity)}},Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=HonorablePalette.textPrimary,contentColor=HonorablePalette.canvas)){Text("Continue with Google",fontWeight=FontWeight.Medium)}
            if(controller.message.isNotBlank())Text(controller.message,Modifier.padding(top=12.dp),color=Color.LightGray,fontSize=12.sp)
            Text("Your memories stay yours.",Modifier.padding(top=24.dp),fontWeight=FontWeight.Bold,color=HonorablePalette.textPrimary)
            Text("Signing in keeps your Memory Passes, credits and account access connected across sessions and devices.",Modifier.padding(top=10.dp),fontSize=12.sp,lineHeight=18.sp,color=Color.Gray)
            if(controller.status=="unavailable")TextButton(onClick={scope.launch{controller.restore()}}){Text("Try restoring again",color=HonorablePalette.textPrimary)}
        }
    }}
}
@Composable private fun AccountPanel(){val controller=LocalAccount.current;val account=controller.account;val profile=account?.optJSONObject("profile");val context=LocalContext.current;val scope=rememberCoroutineScope()
    ContentSection{Text(profile?.optString("name")?.takeIf{it.isNotBlank()}?:"Your account",style=MaterialTheme.typography.titleLarge);profile?.optString("email")?.takeIf{it.isNotBlank()}?.let{Text(it,Modifier.padding(top=6.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)};Text(if(controller.status=="offline")"Offline · previously signed in" else "Signed in",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall);Text("${account?.optInt("balance")?:0} Memory Credits",Modifier.padding(top=16.dp),fontWeight=FontWeight.Bold);Text("Free this month: ${account?.optInt("freeMonthlyRemaining")?:0} / ${account?.optInt("freeMonthlyTotal")?:0}");Text("Memory Pass · ${(account?.optInt("balance")?:0).let{if(it>0)"credits available" else "no credits yet"}}",Modifier.padding(top=6.dp));Text("Subscription · ${account?.optJSONObject("subscription")?.optString("status")?:"NONE"}");if(controller.status=="offline")Text("Reconnect to verify credits and purchases.",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall);TextButton(onClick={scope.launch{controller.signOut(context as android.app.Activity)}}){Text("Sign out",color=HonorablePalette.textPrimary)}}
}

@Composable private fun ProductBalances(){val a=LocalAccount.current.account;Row(Modifier.fillMaxWidth().padding(vertical=24.dp)){Column(Modifier.weight(1f)){Text("Free this month",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${a?.optInt("freeMonthlyRemaining")?:0} / ${a?.optInt("freeMonthlyTotal")?:0}",Modifier.padding(top=8.dp),fontSize=28.sp)};Column(Modifier.weight(1f)){Text("Purchased",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${a?.optInt("balance")?:0}",Modifier.padding(top=8.dp),fontSize=28.sp);Text("Never expires",fontSize=12.sp)}}}

@Composable private fun ProductPass(){val context=LocalContext.current;var all by rememberSaveable{mutableStateOf(false)};val catalog=remember{JSONObject(context.assets.open("product-catalog.json").bufferedReader().use{it.readText()}).getJSONArray("passes")};LazyColumn(contentPadding=PaddingValues(24.dp,40.dp,24.dp,104.dp)){
 item{Text("Memory Credits",fontSize=32.sp);Text("Search at your own pace. Purchased credits never expire.",Modifier.padding(top=16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant);ProductBalances();Text(if(all)"All 31 passes"else"Choose a pass",fontSize=20.sp);Text("Reference USD prices · purchases not connected.",Modifier.padding(vertical=16.dp),fontSize=12.sp)}
 items(if(all)catalog.length()else 20){i->val product=catalog.getJSONObject(i);HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant);Row(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){if(i<2)Text(if(i==0)"Starter"else"Everyday",fontSize=12.sp,color=MaterialTheme.colorScheme.primary);Text("${product.getInt("credits")} credits",fontSize=if(i<2)24.sp else 16.sp)};Text("$${product.getInt("referenceUsd")}",fontSize=16.sp)}}
 item{TextButton(onClick={all=!all}){Text(if(all)"Show recommended"else"Show all 31 passes")}}
 }}

@Composable private fun ProductUsage(){val a=LocalAccount.current.account;val usage=a?.optJSONObject("usage");val tx=a?.optJSONArray("transactions");var count by rememberSaveable{mutableStateOf(20)};LazyColumn(contentPadding=PaddingValues(24.dp,40.dp,24.dp,104.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
 item{Text("Your usage",fontSize=32.sp);Text("${a?.optInt("freeMonthlyRemaining")?:0}",Modifier.padding(top=24.dp),fontSize=64.sp);Text("Free credits remaining",color=MaterialTheme.colorScheme.onSurfaceVariant);LinearProgressIndicator(progress={((a?.optInt("freeMonthlyRemaining")?:0).toFloat()/(a?.optInt("freeMonthlyTotal")?:15).coerceAtLeast(1)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(vertical=20.dp),color=HonorablePalette.textPrimary);Text("Resets ${a?.optString("nextResetAt")?.take(10)?:"—"}",fontSize=13.sp);Text("${a?.optInt("balance")?:0} purchased credits",Modifier.padding(top=24.dp),fontSize=22.sp);Text("This month",Modifier.padding(top=24.dp),fontSize=20.sp);Text("${usage?.optJSONObject("month")?.optInt("searchCount")?:0} searches",Modifier.padding(top=12.dp));listOf("FAST","VIDEO","TURBO").forEachIndexed{i,name->Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(name);Text("${usage?.optJSONObject("byModel")?.optInt("SERAN_V${i+1}")?:0}")}};Text("Recent activity",Modifier.padding(top=24.dp),fontSize=20.sp)}
 items(minOf(count,tx?.length()?:0)){i->val t=tx!!.getJSONObject(i);HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant);Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(t.optString("description"));Text(t.optString("createdAt").take(10),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text("${t.optInt("credits")}")}}
 if((tx?.length()?:0)>count)item{TextButton(onClick={count+=20}){Text("Load more")}}
 }}

@Composable private fun ProductStudio(openMemories:()->Unit){val active=LocalAccount.current.account?.optJSONObject("subscription")?.optString("status")=="ACTIVE";var note by remember{mutableStateOf("")};LazyColumn(contentPadding=PaddingValues(24.dp,40.dp,24.dp,104.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){
 item{Text("Honorable Studio",fontSize=16.sp);Text(if(active)"Continue creating."else"Make something\nof a memory.",Modifier.padding(top=32.dp),fontSize=36.sp,lineHeight=40.sp);Text("Start with a photo. Keep the original.",Modifier.padding(top=16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
 item{Column(Modifier.fillMaxWidth().heightIn(min=180.dp).background(MaterialTheme.colorScheme.surface).clickable(onClick=openMemories).padding(24.dp)){Icon(HonorableIcons.PhotoLibrary,null,Modifier.size(32.dp));Text("Edit a photo",Modifier.padding(top=32.dp),fontSize=24.sp);Text("Adjust · crop · rotate · save a copy",Modifier.padding(top=12.dp),fontSize=13.sp)}}
 item{Text("Workspaces",fontSize=20.sp);Text("Video, Nodes and Code have foundations in the web workspace. Advanced native processing is unavailable.",Modifier.padding(top=12.dp),lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 item{TextButton(onClick={note="Monthly pricing is not finalized. Store subscriptions are not connected."}){Text(if(active)"Studio member"else"Explore Studio membership")};if(note.isNotBlank())Text(note,fontSize=13.sp)}
 }}

@Composable private fun ModelPicker(models:org.json.JSONArray,selected:String,onSelect:(String)->Unit,disabled:Boolean=false){
 var open by remember{mutableStateOf(false)}
 val model=(0 until models.length()).map{models.getJSONObject(it)}.firstOrNull{it.getString("model")==selected}
 Box{TextButton(onClick={open=true},enabled=!disabled){Text("${model?.optString("name")?:"FAST"} · ${model?.optInt("credits")?:1} credit${if(model?.optInt("credits")==1)""else"s"}")};DropdownMenu(open,{open=false}){(0 until models.length()).forEach{i->val choice=models.getJSONObject(i);DropdownMenuItem(text={Text("${choice.getString("name")} · ${choice.getInt("credits")} credits${if(choice.getBoolean("available"))""else" · Coming soon"}")},enabled=choice.getBoolean("available"),onClick={onSelect(choice.getString("model"));open=false})}}}
}
