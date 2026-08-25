package app.honorable

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
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

private val BrandNavy=Color(0xFF0D0E12)
private val BrandNavyDeep=Color(0xFF050506)
private val BrandNavyScrim=Color(0xFF08080A)
private val BrandGlass=Color(0xB31A1C22)
private val BrandGlassStrong=Color(0xED111216)
private val BrandIce=Color(0xFFFAFAFC)
private val BrandGlassEdge=Color(0xFFE4E7EC)
private val BubbleBlue=Color(0xFF5872F8)
private val BubbleCyan=Color(0xFF78DFF0)
private val BubbleLilac=Color(0xFFCB9FFF)
private val BubbleMint=Color(0xFF9DE8BF)
private val BubbleBlush=Color(0xFFFFABC7)
private val BubbleFont=FontFamily.SansSerif
private val DarkColors = darkColorScheme(
    primary=BubbleCyan,onPrimary=BrandNavyDeep,primaryContainer=Color(0xFF202742),onPrimaryContainer=BrandIce,
    secondary=BubbleBlue,onSecondary=BrandNavyDeep,secondaryContainer=Color(0xFF22263A),onSecondaryContainer=BrandIce,
    tertiary=BubbleLilac,onTertiary=BrandNavyDeep,background=BrandNavyDeep,onBackground=BrandIce,surface=Color(0xFF15161B),onSurface=BrandIce,
    surfaceVariant=Color(0xFF22242B),onSurfaceVariant=Color(0xFFC7CAD2),outline=Color(0xFF7C818D),outlineVariant=Color(0xFF353841),
    error=BubbleBlush,onError=BrandNavyDeep,errorContainer=Color(0xFF482431),onErrorContainer=BrandIce,
    surfaceTint=BubbleBlue,inverseSurface=BrandIce,inverseOnSurface=BrandNavy,inversePrimary=Color(0xFF3B5DD8),scrim=BrandNavyScrim,
    surfaceBright=Color(0xFF30323A),surfaceDim=BrandNavyDeep,surfaceContainerLowest=BrandNavyDeep,surfaceContainerLow=Color(0xFF0F1014),
    surfaceContainer=Color(0xFF17181D),surfaceContainerHigh=Color(0xFF212329),surfaceContainerHighest=Color(0xFF2A2D35)
)
private val AppType = Typography(
    displaySmall=Typography().displaySmall.copy(fontFamily=BubbleFont,fontWeight=FontWeight.Black,fontSize=46.sp,lineHeight=44.sp,letterSpacing=(-1.9).sp),
    headlineLarge=Typography().headlineLarge.copy(fontFamily=BubbleFont,fontWeight=FontWeight.ExtraBold,fontSize=36.sp,lineHeight=37.sp,letterSpacing=(-1.1).sp),
    headlineMedium=Typography().headlineMedium.copy(fontFamily=BubbleFont,fontWeight=FontWeight.Bold,fontSize=28.sp,lineHeight=31.sp,letterSpacing=(-.7).sp),
    titleLarge=Typography().titleLarge.copy(fontFamily=BubbleFont,fontWeight=FontWeight.ExtraBold,fontSize=22.sp,lineHeight=25.sp,letterSpacing=(-.4).sp),
    titleMedium=Typography().titleMedium.copy(fontFamily=BubbleFont,fontWeight=FontWeight.Bold,letterSpacing=(-.2).sp),
    bodyLarge=Typography().bodyLarge.copy(fontFamily=BubbleFont,lineHeight=24.sp,letterSpacing=(-.1).sp),
    labelLarge=Typography().labelLarge.copy(fontFamily=BubbleFont,fontWeight=FontWeight.Bold,letterSpacing=(-.1).sp),
    labelMedium=Typography().labelMedium.copy(fontFamily=BubbleFont,fontWeight=FontWeight.SemiBold,letterSpacing=0.sp)
)
private val AppShapes=Shapes(extraSmall=RoundedCornerShape(20.dp),small=RoundedCornerShape(28.dp),medium=RoundedCornerShape(38.dp),large=RoundedCornerShape(50.dp),extraLarge=RoundedCornerShape(64.dp))

@Composable fun HonorableApp() {
    MaterialTheme(colorScheme=DarkColors,typography=AppType,shapes=AppShapes){AppBackground{HonorableShell()}}
}

@Composable private fun AppBackground(content:@Composable BoxScope.()->Unit){
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050506),Color(0xFF111318),Color(0xFF08090C))))) {
        Canvas(Modifier.fillMaxSize()){
            drawCircle(Brush.radialGradient(listOf(BubbleBlue.copy(.34f),Color.Transparent)),radius=size.minDimension*.78f,center=Offset(size.width*1.08f,-size.height*.02f))
            drawCircle(Brush.radialGradient(listOf(BubbleLilac.copy(.18f),Color.Transparent)),radius=size.minDimension*.64f,center=Offset(-size.width*.12f,size.height*.48f))
            drawCircle(Brush.radialGradient(listOf(BubbleCyan.copy(.14f),Color.Transparent)),radius=size.minDimension*.70f,center=Offset(size.width*.68f,size.height*1.02f))
            drawCircle(BubbleBlush.copy(.055f),radius=size.minDimension*.22f,center=Offset(size.width*.10f,size.height*.12f))
        }
        content()
    }
}

private enum class MainTab(val label:String,val icon:ImageVector){HOME("Home",Icons.Rounded.Home),MEMORIES("Memories",Icons.Rounded.PhotoLibrary),TERMS("Terms",Icons.Rounded.Policy),ACTIVITY("Activity",Icons.Rounded.Timeline),SETTINGS("Settings",Icons.Rounded.Tune)}
private enum class Overlay { NONE, VIEWER, PRIVACY, PLUS }

@Composable private fun HonorableShell(){
    var tab by rememberSaveable{mutableStateOf(MainTab.HOME)};var overlay by rememberSaveable{mutableStateOf(Overlay.NONE)};var selected by remember{mutableStateOf<SearchMatch?>(null)}
    BackHandler(overlay!=Overlay.NONE){overlay=Overlay.NONE}
    LaunchedEffect(overlay,selected){if(overlay==Overlay.VIEWER&&selected==null)overlay=Overlay.NONE}
    Box(Modifier.fillMaxSize()){
        AnimatedContent(tab,label="main-screen",transitionSpec={(fadeIn(tween(260))+scaleIn(initialScale=.985f,animationSpec=spring(stiffness=Spring.StiffnessMediumLow))) togetherWith fadeOut(tween(140))}){current->
            when(current){
                MainTab.HOME->HomeScreen({tab=MainTab.MEMORIES},{tab=MainTab.TERMS})
                MainTab.MEMORIES->MemoriesScreen{selected=it;overlay=Overlay.VIEWER}
                MainTab.TERMS->TermsScreen()
                MainTab.ACTIVITY->ActivityScreen()
                MainTab.SETTINGS->SettingsScreen({overlay=Overlay.PRIVACY},{overlay=Overlay.PLUS})
            }
        }
        FloatingGlassDock(tab,{tab=it},Modifier.align(Alignment.BottomCenter))
        AnimatedVisibility(overlay!=Overlay.NONE,enter=fadeIn()+scaleIn(initialScale=.97f),exit=fadeOut()+scaleOut(targetScale=.98f)){
            when(overlay){Overlay.VIEWER->selected?.let{MediaViewer(it){overlay=Overlay.NONE}};Overlay.PRIVACY->PrivacyScreen{overlay=Overlay.NONE};Overlay.PLUS->PlusScreen{overlay=Overlay.NONE};else->Unit}
        }
    }
}

enum class OrbState { IDLE, THINKING, SEARCHING, LISTENING, RESULT, LOW_CONFIDENCE, ERROR }

@Composable fun HonorableOrb(state:OrbState,modifier:Modifier=Modifier,size:Int=150,icon:ImageVector=Icons.Rounded.AutoAwesome){
    val motion=rememberInfiniteTransition(label="orb")
    val active=state==OrbState.SEARCHING
    val pulse by motion.animateFloat(.94f,1.04f,infiniteRepeatable(tween(if(active)820 else 1800,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="pulse")
    val drift by motion.animateFloat(-8f,8f,infiniteRepeatable(tween(2400,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="drift")
    val rotation by motion.animateFloat(0f,360f,infiniteRepeatable(tween(if(active)2200 else 9000,easing=LinearEasing)),label="rotation")
    val accent=when(state){OrbState.ERROR->BubbleBlush;OrbState.LOW_CONFIDENCE->BubbleLilac;OrbState.RESULT->BubbleMint;else->BubbleCyan}
    Box(modifier.size(size.dp).graphicsLayer{scaleX=pulse;scaleY=pulse}.semantics{contentDescription="Honorable scanner ${state.name.lowercase()}"},contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val r=this.size.minDimension*.37f
            drawCircle(Brush.radialGradient(listOf(BubbleBlue.copy(.72f),BubbleBlue.copy(.20f),Color.Transparent)),r*1.34f,center+Offset(drift,0f))
            drawCircle(BubbleLilac.copy(.50f),r*.82f,center+Offset(-r*.44f,-r*.34f))
            drawCircle(BubbleCyan.copy(.46f),r*.70f,center+Offset(r*.48f,-r*.18f))
            drawCircle(BubbleBlush.copy(.28f),r*.55f,center+Offset(r*.20f,r*.52f))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(.34f),BrandGlass.copy(.78f))),r,center)
            drawCircle(Color.White.copy(.42f),r,center,style=Stroke(1.2.dp.toPx()))
            drawArc(accent,rotation,if(active)102f else 52f,false,topLeft=Offset(center.x-r,center.y-r),size=androidx.compose.ui.geometry.Size(r*2,r*2),style=Stroke(if(active)4.dp.toPx() else 2.dp.toPx()))
        }
        Box(Modifier.size((size*.34f).dp).shadow(18.dp,CircleShape,ambientColor=accent.copy(.40f),spotColor=accent.copy(.45f)).clip(CircleShape).background(BrandNavyDeep.copy(.76f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size((size*.16f).dp),tint=accent)}
    }
}

@Composable private fun OrbButton(icon:ImageVector,label:String,onClick:()->Unit){
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)){
        Pressable(onClick){pressed->GlassCircle(Modifier.size(58.dp).graphicsLayer{scaleX=if(pressed).94f else 1f;scaleY=scaleX}){Icon(icon,label,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)}}
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HomeScreen(openMemories:()->Unit,openTerms:()->Unit){
    LazyColumn(contentPadding=PaddingValues(0.dp,30.dp,0.dp,126.dp)){
        item{Box(Modifier.padding(horizontal=22.dp)){BrandHeader()}}
        item{PremiumHero(openMemories)}
        item{Row(Modifier.padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.weight(1f)){ArchiveRoute("01","Memories","Find any moment",Icons.Rounded.PhotoLibrary,BubbleCyan,openMemories)};Box(Modifier.weight(1f)){ArchiveRoute("02","Terms","Know what you sign",Icons.Rounded.Policy,BubbleLilac,openTerms)}}}
        item{Row(Modifier.fillMaxWidth().padding(22.dp,24.dp,22.dp,8.dp).clip(CircleShape).background(BubbleMint.copy(.10f)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).clip(CircleShape).background(BubbleMint.copy(.18f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Lock,null,Modifier.size(19.dp),tint=BubbleMint)};Column(Modifier.padding(start=12.dp).weight(1f)){Text("Yours means yours",fontWeight=FontWeight.Bold);Text("Private on-device intelligence",Modifier.padding(top=2.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text("100%",fontWeight=FontWeight.Black,color=BubbleMint)}}
    }
}

@Composable private fun PremiumHero(openMemories:()->Unit){
    val heroShape=RoundedCornerShape(58.dp)
    Box(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=16.dp).heightIn(min=510.dp).shadow(28.dp,heroShape,ambientColor=BubbleBlue.copy(.24f),spotColor=BubbleBlue.copy(.30f)).clip(heroShape).background(Brush.linearGradient(listOf(Color(0xFF242B65),Color(0xFF171D46),Color(0xFF111634)))).border(1.dp,Color.White.copy(.18f),heroShape)){
        Canvas(Modifier.matchParentSize()){
            drawCircle(BubbleLilac.copy(.45f),size.minDimension*.34f,Offset(size.width*.87f,size.height*.10f))
            drawCircle(BubbleCyan.copy(.22f),size.minDimension*.24f,Offset(size.width*.72f,size.height*.28f))
            drawCircle(BubbleBlush.copy(.20f),size.minDimension*.18f,Offset(size.width*.96f,size.height*.42f))
            drawCircle(Color.White.copy(.12f),size.minDimension*.09f,Offset(size.width*.76f,size.height*.07f))
        }
        Column(Modifier.fillMaxWidth().padding(24.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){BubbleTag("Private by design",BubbleMint);Spacer(Modifier.weight(1f));Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(.12f)),contentAlignment=Alignment.Center){Text("H",fontWeight=FontWeight.Black,fontSize=20.sp,color=BrandIce)}}
            Spacer(Modifier.height(34.dp))
            Text("Find the",style=MaterialTheme.typography.displaySmall.copy(fontSize=62.sp,lineHeight=58.sp),color=BrandIce)
            Text("moment",style=MaterialTheme.typography.displaySmall.copy(fontSize=72.sp,lineHeight=64.sp),color=BubbleCyan)
            Text("you meant.",style=MaterialTheme.typography.displaySmall.copy(fontSize=62.sp,lineHeight=58.sp),color=BrandIce)
            Text("Say what you remember. Honorable understands the scene and brings it back—without sending it anywhere.",Modifier.widthIn(max=320.dp).padding(top=20.dp),color=BrandIce.copy(.72f),style=MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp));ArchiveSearchCommand("Describe a memory",openMemories);Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable private fun ArchiveRoute(number:String,title:String,subtitle:String,icon:ImageVector,tint:Color,onClick:()->Unit){val shape=RoundedCornerShape(42.dp);Column(Modifier.padding(vertical=5.dp).fillMaxWidth().height(190.dp).shadow(16.dp,shape,ambientColor=tint.copy(.10f),spotColor=tint.copy(.16f)).clip(shape).background(Brush.verticalGradient(listOf(tint.copy(.18f),BrandGlass.copy(.70f)))).border(1.dp,Color.White.copy(.12f),shape).clickable(onClick=onClick).padding(16.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(58.dp).clip(CircleShape).background(tint.copy(.20f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(25.dp),tint=tint)};Spacer(Modifier.weight(1f));Text(number,fontWeight=FontWeight.Black,color=tint.copy(.72f))};Spacer(Modifier.weight(1f));Text(title,style=MaterialTheme.typography.titleLarge,color=BrandIce);Text(subtitle,Modifier.padding(top=5.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Icon(Icons.Rounded.ArrowOutward,"Open $title",Modifier.padding(top=10.dp).size(20.dp),tint=tint)}}

@Composable private fun ArchiveSearchCommand(label:String,onClick:()->Unit){Pressable(onClick){pressed->Row(Modifier.fillMaxWidth().heightIn(min=72.dp).graphicsLayer{scaleX=if(pressed).98f else 1f;scaleY=scaleX}.shadow(18.dp,CircleShape,ambientColor=BubbleCyan.copy(.24f),spotColor=BubbleCyan.copy(.28f)).clip(CircleShape).background(Brush.horizontalGradient(listOf(BubbleCyan,BubbleLilac))).padding(start=22.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Search,null,Modifier.size(22.dp),tint=BrandNavyDeep);Text(label,Modifier.padding(start=13.dp).weight(1f),color=BrandNavyDeep,fontWeight=FontWeight.ExtraBold,fontSize=16.sp);Box(Modifier.size(56.dp).clip(CircleShape).background(BrandNavyDeep.copy(.88f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ArrowUpward,"Search",tint=BrandIce)}}}}

@Composable private fun BrandHeader(){Row(Modifier.fillMaxWidth().height(58.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(BubbleBlue,BubbleLilac))),contentAlignment=Alignment.Center){Text("h",fontWeight=FontWeight.Black,color=BrandNavyDeep,fontSize=23.sp)};Text("honorable",Modifier.padding(start=11.dp).weight(1f),style=MaterialTheme.typography.titleLarge,color=BrandIce);BubbleTag("on device",BubbleCyan)}}

@Composable private fun BubbleTag(text:String,tint:Color){Row(Modifier.clip(CircleShape).background(tint.copy(.13f)).border(1.dp,tint.copy(.22f),CircleShape).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(6.dp).clip(CircleShape).background(tint));Spacer(Modifier.width(7.dp));Text(text,style=MaterialTheme.typography.labelMedium,color=tint)}}

@Composable fun GlassSearch(hint:String,value:String="",focused:Boolean=false,dark:Boolean=false,onValueChange:(String)->Unit={},onClick:()->Unit){
    val focusRequester=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(focused){if(focused){focusRequester.requestFocus();keyboard?.show()}}
    GlassSurface(Modifier.fillMaxWidth().heightIn(min=if(focused)80.dp else 74.dp).clickable(enabled=!focused,onClick=onClick),shape=CircleShape,alpha=if(focused).92f else .76f,dark=dark){
        val foreground=if(dark)BrandIce else MaterialTheme.colorScheme.onSurface
        Row(Modifier.fillMaxSize().padding(start=10.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(54.dp).clip(CircleShape).background(BubbleCyan.copy(.13f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.AutoAwesome,null,Modifier.size(21.dp),tint=BubbleCyan)};Spacer(Modifier.width(11.dp));if(focused)BasicTextField(value,onValueChange,Modifier.weight(1f).focusRequester(focusRequester),singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(color=foreground,fontWeight=FontWeight.SemiBold),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide();onClick()}),decorationBox={inner->if(value.isBlank())Text(hint,color=foreground.copy(.48f));inner()})else Text(if(value.isBlank())hint else value,Modifier.weight(1f),color=foreground.copy(.78f),fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Surface(onClick={keyboard?.hide();onClick()},modifier=Modifier.size(58.dp),shape=CircleShape,color=BubbleBlue){Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.ArrowUpward,"Search",Modifier.size(21.dp),tint=BrandNavyDeep)}}}
    }
}

private enum class SearchStage { LANDING, FOCUS, SEARCHING, RESULTS }
@Composable private fun MemoriesScreen(openViewer:(SearchMatch)->Unit){
    val vm:MemoriesViewModel=viewModel();val backend by vm.state.collectAsStateWithLifecycle();var stage by rememberSaveable{mutableStateOf(SearchStage.LANDING)};var query by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("All")}
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){vm.permissionResult()}
    LaunchedEffect(backend){if(backend is MemorySearchState.Results)stage=SearchStage.RESULTS}
    Column(Modifier.fillMaxSize().padding(top=42.dp)){
        AnimatedContent(stage,Modifier.weight(1f),label="memory-state",transitionSpec={fadeIn(tween(220))+slideInVertically{it/16} togetherWith fadeOut(tween(120))}){current->
            when(current){
                SearchStage.LANDING->when(val state=backend){MemorySearchState.PermissionRequired->PermissionState{permissionLauncher.launch(MemoriesViewModel.permissions())};is MemorySearchState.Indexing->IndexingState(state.progress);is MemorySearchState.Failed->BackendFailure(state.message);is MemorySearchState.Ready->MemoryLanding(state.count,{stage=SearchStage.FOCUS},{query=it;stage=SearchStage.SEARCHING;vm.search(it)});else->MemoryLanding(null,{stage=SearchStage.FOCUS},{query=it;stage=SearchStage.SEARCHING;vm.search(it)})}
                SearchStage.FOCUS->SearchFocus(query,{query=it},{if(query.isNotBlank()){stage=SearchStage.SEARCHING;vm.search(query)}},{stage=SearchStage.LANDING})
                SearchStage.SEARCHING->SearchInMotion(query)
                SearchStage.RESULTS->SearchResults(query,filter,{filter=it},{stage=SearchStage.FOCUS},(backend as? MemorySearchState.Results)?.matches.orEmpty(),openViewer)
            }
        }
    }
}

@Composable private fun MemoryLanding(count:Int?,focus:()->Unit,search:(String)->Unit){
    LazyColumn(contentPadding=PaddingValues(20.dp,4.dp,20.dp,122.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){BubbleTag(count?.let{"$it moments"}?:"local moments",BubbleMint);Spacer(Modifier.weight(1f));BubbleTag("private",BubbleCyan)}}
        item{Text("What do you\nremember?",Modifier.padding(top=14.dp),style=MaterialTheme.typography.displaySmall.copy(fontSize=58.sp,lineHeight=54.sp))}
        item{Text("A color, a place, a tiny detail—start anywhere.",Modifier.padding(bottom=10.dp).widthIn(max=320.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyLarge)}
        item{ArchiveSearchCommand("Start describing",focus)}
        item{Row(Modifier.padding(top=14.dp,bottom=1.dp),verticalAlignment=Alignment.CenterVertically){Text("Start anywhere",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.weight(1f));Text("made for your library",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        item{FeaturedMemoryPrompt("white beach\nwith tall grass"){search("white beach with tall grass")}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){MemoryPromptTile("COLOR + WEATHER","Red car\nin snow",Icons.Rounded.DirectionsCar,BubbleCyan,Modifier.weight(1f)){search("red car in snow")};MemoryPromptTile("OBJECT + LIGHT","Birthday cake\nby a window",Icons.Rounded.Cake,BubbleBlush,Modifier.weight(1f)){search("birthday cake by a window")}}}
        item{Row(Modifier.padding(top=1.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BubbleMint.copy(.09f)).padding(13.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(28.dp).clip(CircleShape).background(BubbleMint),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Check,null,Modifier.size(16.dp),tint=BrandNavyDeep)};Column(Modifier.padding(start=10.dp).weight(1f)){Text("Private by default",style=MaterialTheme.typography.labelLarge,color=BubbleMint);Text("Photos and searches stay on this device.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text(count?.let{"$it ready"}?:"local",style=MaterialTheme.typography.labelMedium,color=BubbleMint)}}
    }
}

@Composable private fun FeaturedMemoryPrompt(text:String,onClick:()->Unit){
    val shape=RoundedCornerShape(42.dp,58.dp,46.dp,50.dp)
    Box(Modifier.fillMaxWidth().height(205.dp).shadow(22.dp,shape,ambientColor=BubbleBlue.copy(.18f),spotColor=BubbleBlue.copy(.22f)).clip(shape).background(Brush.linearGradient(listOf(Color(0xFF20275B),Color(0xFF363F83),Color(0xFF566BC1)))).border(1.dp,Color.White.copy(.17f),shape).clickable(onClick=onClick)){
        Canvas(Modifier.matchParentSize()){drawCircle(BubbleCyan,size.minDimension*.42f,Offset(size.width*.86f,size.height*.19f));drawCircle(BubbleLilac,size.minDimension*.31f,Offset(size.width*.67f,size.height*1.04f));drawCircle(BubbleMint,size.minDimension*.16f,Offset(size.width*.94f,size.height*.80f))}
        Column(Modifier.align(Alignment.CenterStart).padding(start=22.dp)){Text("TRY A SCENE",style=MaterialTheme.typography.labelMedium,color=BubbleCyan);Text(text,Modifier.padding(top=14.dp),style=MaterialTheme.typography.headlineMedium.copy(fontSize=27.sp,lineHeight=26.sp),color=BrandIce);Text("Place + texture + color",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall,color=BrandIce.copy(.64f))}
        Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).size(46.dp).clip(CircleShape).background(BrandIce),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ArrowOutward,"Search $text",Modifier.size(20.dp),tint=BrandNavyDeep)}
    }
}

@Composable private fun MemoryPromptTile(label:String,text:String,icon:ImageVector,tint:Color,modifier:Modifier=Modifier,onClick:()->Unit){
    val shape=RoundedCornerShape(34.dp,42.dp,37.dp,31.dp)
    Box(modifier.height(150.dp).clip(shape).background(BrandGlass.copy(.76f)).border(1.dp,Color.White.copy(.10f),shape).clickable(onClick=onClick).padding(15.dp)){
        Text(label,style=MaterialTheme.typography.labelMedium.copy(fontSize=9.sp),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.align(Alignment.TopEnd).size(40.dp).clip(CircleShape).background(tint.copy(.17f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(20.dp),tint=tint)}
        Text(text,Modifier.align(Alignment.BottomStart),style=MaterialTheme.typography.titleMedium.copy(lineHeight=18.sp),fontWeight=FontWeight.ExtraBold)
        Icon(Icons.Rounded.ArrowOutward,"Search $text",Modifier.align(Alignment.BottomEnd).size(17.dp),tint=tint)
    }
}

@Composable private fun PermissionState(grant:()->Unit){Column(Modifier.fillMaxSize().padding(22.dp,34.dp,22.dp,118.dp),horizontalAlignment=Alignment.CenterHorizontally){BubbleTag("one quick thing",BubbleLilac);Spacer(Modifier.weight(.65f));Box(Modifier.size(168.dp).clip(CircleShape).background(Brush.radialGradient(listOf(BubbleLilac.copy(.42f),BubbleBlue.copy(.12f)))),contentAlignment=Alignment.Center){Box(Modifier.size(98.dp).clip(CircleShape).background(BrandGlassStrong),contentAlignment=Alignment.Center){Icon(Icons.Rounded.PhotoLibrary,null,Modifier.size(42.dp),tint=BubbleLilac)}};Spacer(Modifier.height(30.dp));Text("Let Honorable\nsee your library",style=MaterialTheme.typography.displaySmall.copy(fontSize=48.sp,lineHeight=46.sp),textAlign=TextAlign.Center);Text("It builds a private visual index on this device. Your photos never get uploaded.",Modifier.padding(top=16.dp).widthIn(max=330.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center);Spacer(Modifier.weight(1f));ArchiveSearchCommand("Choose photos & videos",grant);Text("You can change this anytime",Modifier.padding(top=13.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
@Composable private fun IndexingState(progress:IndexProgress){val fraction=if(progress.total>0)progress.processed.toFloat()/progress.total else 0f;Column(Modifier.fillMaxSize().padding(22.dp,34.dp,22.dp,116.dp)){Row{BubbleTag("building your memory map",BubbleCyan);Spacer(Modifier.weight(1f));BubbleTag("offline",BubbleMint)};Spacer(Modifier.weight(.7f));HonorableOrb(OrbState.SEARCHING,Modifier.align(Alignment.CenterHorizontally),size=224);Spacer(Modifier.height(28.dp));Text(if(progress.total>0)"${(fraction*100).toInt()}%" else "Finding moments…",style=MaterialTheme.typography.displaySmall.copy(fontSize=if(progress.total>0)72.sp else 42.sp),color=BrandIce);Text("Making your library searchable",Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleLarge);Box(Modifier.padding(top=24.dp).fillMaxWidth().height(16.dp).clip(CircleShape).background(Color.White.copy(.08f))){Box(Modifier.fillMaxWidth(fraction.coerceIn(0f,1f)).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(BubbleBlue,BubbleCyan,BubbleMint))))};Row(Modifier.fillMaxWidth().padding(top=12.dp)){Text(if(progress.total==0)"Discovering media" else "${progress.processed} of ${progress.total}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.weight(1f));Text("safe to leave",style=MaterialTheme.typography.bodySmall,color=BubbleMint)};Spacer(Modifier.weight(1f));Text("Photos first, then the best moments from each video.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyLarge)}
}
@Composable private fun BackendEmpty(message:String){Column(Modifier.fillMaxSize().padding(26.dp,28.dp,26.dp,120.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Box(Modifier.size(160.dp).clip(CircleShape).background(BubbleLilac.copy(.12f)),contentAlignment=Alignment.Center){Box(Modifier.size(92.dp).clip(CircleShape).background(BubbleLilac.copy(.14f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.SearchOff,null,Modifier.size(38.dp),tint=BubbleLilac)}};Text(message,Modifier.padding(top=26.dp),style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center);Text("Try a place, color, date, or something visible in the scene.",Modifier.padding(top=10.dp).widthIn(max=300.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)}

}

@Composable private fun BackendFailure(message:String){Column(Modifier.fillMaxSize().padding(26.dp,28.dp,26.dp,120.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){BubbleTag("your library is safe",BubbleMint);Spacer(Modifier.height(28.dp));Box(Modifier.size(160.dp).clip(CircleShape).background(BubbleBlush.copy(.12f)),contentAlignment=Alignment.Center){Box(Modifier.size(92.dp).clip(CircleShape).background(BubbleBlush.copy(.15f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ErrorOutline,null,Modifier.size(38.dp),tint=BubbleBlush)}};Text("Something tripped.",Modifier.padding(top=26.dp),style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center);Text(message,Modifier.padding(top=10.dp).widthIn(max=310.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center);Text("Honorable did not upload or change your media.",Modifier.padding(top=20.dp).clip(CircleShape).background(BubbleMint.copy(.09f)).padding(horizontal=15.dp,vertical=11.dp),style=MaterialTheme.typography.labelMedium,color=BubbleMint,textAlign=TextAlign.Center)}
}

@Composable private fun SearchFocus(query:String,onQuery:(String)->Unit,search:()->Unit,close:()->Unit){Column(Modifier.fillMaxSize().padding(20.dp,8.dp,20.dp,108.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){BubbleTag("memory search",BubbleCyan);SquareIconButton(Icons.Rounded.Close,"Close",close)};Spacer(Modifier.height(24.dp));Text("Paint it\nwith words.",style=MaterialTheme.typography.displaySmall.copy(fontSize=54.sp,lineHeight=50.sp));Text("Messy is fine. A feeling can be enough.",Modifier.padding(top=11.dp),color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.weight(.45f));HonorableOrb(if(query.isBlank())OrbState.LISTENING else OrbState.THINKING,Modifier.align(Alignment.CenterHorizontally),size=190);Spacer(Modifier.weight(.45f));GlassSearch("blue shirt at tennis",value=query,focused=true,onValueChange=onQuery,onClick=search);Spacer(Modifier.height(11.dp));ArchiveSearchCommand("Find this moment",search)}}

@Composable private fun SearchInMotion(query:String){Column(Modifier.fillMaxSize().padding(22.dp,34.dp,22.dp,116.dp),horizontalAlignment=Alignment.CenterHorizontally){Row(Modifier.fillMaxWidth()){BubbleTag("understanding your words",BubbleCyan);Spacer(Modifier.weight(1f));BubbleTag("local",BubbleMint)};Spacer(Modifier.weight(1f));HonorableOrb(OrbState.SEARCHING,size=264);Spacer(Modifier.height(34.dp));Text("Looking for\nthat feeling…",style=MaterialTheme.typography.displaySmall.copy(fontSize=48.sp,lineHeight=45.sp),textAlign=TextAlign.Center);Text("“${query.ifBlank{"your moment"}}”",Modifier.padding(top=16.dp).clip(CircleShape).background(BubbleLilac.copy(.10f)).padding(horizontal=16.dp,vertical=10.dp),color=BubbleLilac,style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center);Spacer(Modifier.weight(1f));Row(Modifier.clip(CircleShape).background(Color.White.copy(.06f)).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.CloudOff,null,Modifier.size(17.dp),tint=BubbleMint);Spacer(Modifier.width(8.dp));Text("Nothing uploaded",style=MaterialTheme.typography.labelLarge);Spacer(Modifier.weight(1f));Text("always",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}}

}
@Composable private fun SearchResults(query:String,filter:String,onFilter:(String)->Unit,edit:()->Unit,matches:List<SearchMatch>,openViewer:(SearchMatch)->Unit){
    var more by rememberSaveable(query,filter){mutableStateOf(false)}
    val ranked=matches.filter{filter=="All"||filter=="Videos"&&it.media.kind==MediaKind.VIDEO||filter=="Photos"&&it.media.kind==MediaKind.IMAGE||filter=="Screenshots"&&it.media.isScreenshot}
    Column(Modifier.fillMaxSize()){
        if(ranked.isEmpty()){LazyRow(Modifier.padding(vertical=12.dp),contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){items(listOf("All","Photos","Videos","Screenshots")){CircleFilter(it,filter==it){onFilter(it)}}};BackendEmpty("No confident match yet")} else LazyColumn(contentPadding=PaddingValues(bottom=122.dp)){
            item(key="result-heading"){Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){BubbleTag(if(filter=="Videos")"video memories" else "a private discovery",BubbleCyan);Text("Your moment,\nfound.",Modifier.padding(top=17.dp),style=MaterialTheme.typography.displaySmall.copy(fontSize=48.sp,lineHeight=41.sp),color=MaterialTheme.colorScheme.onSurface)};Box(Modifier.size(88.dp).clip(CircleShape).background(BubbleBlush),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(ranked.size.toString().padStart(2,'0'),fontSize=28.sp,fontWeight=FontWeight.Black,color=BrandNavyDeep);Text(if(filter=="Videos")"Videos" else "Matches",fontSize=9.sp,fontWeight=FontWeight.Black,color=BrandNavyDeep)}}}}
            item(key="best-${ranked.first().media.uri}"){BestMatchCard(ranked.first()){openViewer(ranked.first())}}
            item(key="result-note"){Row(Modifier.padding(horizontal=18.dp,vertical=10.dp).fillMaxWidth().shadow(14.dp,RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).clip(CircleShape).background(if(filter=="Videos")BubbleLilac else BubbleCyan),contentAlignment=Alignment.Center){Icon(if(filter=="Videos")Icons.Rounded.PlayArrow else Icons.Rounded.AutoAwesome,null,tint=BrandNavyDeep)};Column(Modifier.padding(horizontal=12.dp).weight(1f)){Text(if(filter=="Videos")"BEST MOMENT" else "WHY THIS MATCHED",fontSize=9.sp,fontWeight=FontWeight.Black,color=BubbleBlue);Text(ranked.first().explanations.firstOrNull()?:"The scene and details line up.",Modifier.padding(top=4.dp),fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)};Box(Modifier.size(34.dp).clip(CircleShape).background(BrandNavyDeep),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ArrowOutward,"Open",Modifier.size(17.dp),tint=BrandIce)}}}
            item(key="filters"){LazyRow(Modifier.padding(bottom=8.dp),contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){items(listOf("All","Photos","Videos","Screenshots")){CircleFilter(it,filter==it){onFilter(it)}}}}
            if(ranked.size>1&&!more)item(key="more-button"){Row(Modifier.padding(horizontal=14.dp,vertical=10.dp).fillMaxWidth().height(70.dp).clip(CircleShape).background(BubbleLilac.copy(.11f)).clickable{more=true}.padding(start=22.dp,end=9.dp),verticalAlignment=Alignment.CenterVertically){Text("More close matches",Modifier.weight(1f),fontWeight=FontWeight.Bold);Box(Modifier.size(50.dp).clip(CircleShape).background(BubbleLilac.copy(.17f)),contentAlignment=Alignment.Center){Text("+${ranked.size-1}",fontWeight=FontWeight.Black,fontSize=19.sp,color=BubbleLilac)}}}
            if(more){item(key="more-heading"){Row(Modifier.padding(20.dp,28.dp,20.dp,12.dp)){Text("Maybe these too",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.weight(1f));BubbleTag("${ranked.size-1} more",BubbleLilac)}};item(key="more-film"){LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(11.dp)){items(ranked.drop(1),key={it.media.uri}){match->RealMemory(match,Modifier.width(184.dp).height(256.dp)){openViewer(match)}}}}}
        }
    }
}

@Composable private fun BestMatchCard(match:SearchMatch,onClick:()->Unit){
    val media=match.media;val reason=match.explanations.firstOrNull()?:"Strongest local result";val shape=RoundedCornerShape(42.dp)
    Column(Modifier.padding(horizontal=18.dp).fillMaxWidth().shadow(24.dp,shape,ambientColor=BrandNavyDeep.copy(.22f),spotColor=BrandNavyDeep.copy(.26f)).clip(shape).background(BrandNavyDeep).clickable(onClick=onClick).semantics(mergeDescendants=true){contentDescription="Best ${media.kind.name.lowercase()} match. $reason"}){
        Box(Modifier.fillMaxWidth().height(294.dp)){
            AsyncImage(Uri.parse(media.uri),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BrandNavyScrim.copy(.26f),Color.Transparent,BrandNavyScrim.copy(.78f)))))
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){BubbleTag("best match",BubbleMint);Spacer(Modifier.weight(1f));if(media.kind==MediaKind.VIDEO)MiniGlassPill(match.bestTimestampMs?.let(::formatMoment)?:"video")}
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)){Text(if(media.kind==MediaKind.VIDEO)"The exact video moment" else "The scene you described",fontSize=10.sp,fontWeight=FontWeight.Bold,color=BubbleCyan);Text("This feels\nright.",Modifier.padding(top=7.dp),fontSize=42.sp,lineHeight=35.sp,fontWeight=FontWeight.Black,color=BrandIce)}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){Text(if(media.kind==MediaKind.VIDEO)"tennis outside · video" else "tennis outside · visual match",Modifier.weight(1f),fontSize=10.sp,color=BrandIce.copy(.72f));Text("Open ↗",fontSize=10.sp,fontWeight=FontWeight.Black,color=BubbleMint)}
    }
}

@Composable private fun RealMemory(match:SearchMatch,modifier:Modifier,onClick:()->Unit){val media=match.media;val reason=match.explanations.firstOrNull()?:"Local match";val shape=RoundedCornerShape(44.dp);Box(modifier.shadow(14.dp,shape).clip(shape).clickable(onClick=onClick).border(1.dp,Color.White.copy(.14f),shape).semantics(mergeDescendants=true){contentDescription="${media.kind.name.lowercase()} match. $reason"}){AsyncImage(Uri.parse(media.uri),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent,BrandNavyScrim.copy(.94f)))));if(media.kind==MediaKind.VIDEO)Box(Modifier.align(Alignment.TopEnd).padding(10.dp)){MiniGlassPill(match.bestTimestampMs?.let(::formatMoment)?:"video")};Column(Modifier.align(Alignment.BottomStart).padding(16.dp)){BubbleTag(if(media.kind==MediaKind.VIDEO)"video" else "photo",BubbleCyan);Text(reason,Modifier.padding(top=9.dp),color=BrandIce,style=MaterialTheme.typography.labelLarge,maxLines=2,overflow=TextOverflow.Ellipsis)}}}

@Composable private fun MediaViewer(match:SearchMatch,close:()->Unit){val context=LocalContext.current;val media=match.media;val sheet=RoundedCornerShape(topStart=52.dp,topEnd=52.dp,bottomEnd=0.dp,bottomStart=0.dp);Box(Modifier.fillMaxSize().background(BrandNavyDeep)){AsyncImage(Uri.parse(media.uri),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit);Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BrandNavyScrim.copy(.66f),Color.Transparent,BrandNavyScrim.copy(.88f)))));Row(Modifier.statusBarsPadding().padding(18.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){SquareIconButton(Icons.AutoMirrored.Rounded.ArrowBack,"Back",close,true);SquareIconButton(Icons.AutoMirrored.Rounded.OpenInNew,"Open media",{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(media.uri)).setDataAndType(Uri.parse(media.uri),if(media.kind==MediaKind.VIDEO)"video/*" else "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))},true)};Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().clip(sheet).background(Brush.verticalGradient(listOf(Color(0xF21B2249),BrandGlassStrong))).border(1.dp,Color.White.copy(.16f),sheet).padding(22.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(58.dp).clip(CircleShape).background(BubbleCyan.copy(.14f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.AutoAwesome,null,tint=BubbleCyan)};Spacer(Modifier.width(13.dp));Column{BubbleTag("best local match",BubbleMint);Text(if(media.kind==MediaKind.VIDEO)"That video moment" else "That’s the photo",Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleLarge)}};Text(match.explanations.joinToString(" · ").ifBlank{"Strongest local result"},Modifier.padding(top=16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium);Row(Modifier.padding(top=17.dp),verticalAlignment=Alignment.CenterVertically){BubbleTag(confidenceLabel(match),if(match.confidence==MatchConfidence.STRONG)BubbleMint else BubbleLilac);match.bestTimestampMs?.let{Spacer(Modifier.width(9.dp));BubbleTag(formatMoment(it),BubbleCyan)};Spacer(Modifier.weight(1f));Icon(Icons.Rounded.Lock,null,Modifier.size(17.dp),tint=BubbleMint)}}}}

private fun formatMoment(ms:Long):String="${ms/60000}:${(ms/1000%60).toString().padStart(2,'0')}"
private fun confidenceLabel(match:SearchMatch)=when(match.confidence){MatchConfidence.STRONG->"High confidence";MatchConfidence.POSSIBLE->"Possible match";MatchConfidence.WEAK->"Closest match"}

@Composable private fun TermsScreen(){
    var result by rememberSaveable{mutableStateOf(false)}
    if(result){ TermsResult{result=false}; return }
    LazyColumn(contentPadding=PaddingValues(0.dp,42.dp,0.dp,124.dp)){
        item{Column(Modifier.padding(horizontal=20.dp)){Row{BubbleTag("terms, made human",BubbleLilac);Spacer(Modifier.weight(1f));BubbleTag("private",BubbleMint)};Text("Skip the\nfine-print fog.",Modifier.padding(top=24.dp),style=MaterialTheme.typography.displaySmall.copy(fontSize=56.sp,lineHeight=52.sp));Text("Drop in an agreement. Get the parts that matter, in language that actually sounds human.",Modifier.padding(top=14.dp,bottom=28.dp).widthIn(max=330.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyLarge)}}
        item{ArchiveActionRow("01","Paste a link",Icons.Rounded.Link,BubbleCyan){}}
        item{ArchiveActionRow("02","Paste text",Icons.Rounded.ContentPaste,BubbleLilac){}}
        item{ArchiveActionRow("03","Import a file",Icons.Rounded.UploadFile,BubbleBlush){}}
        item{Box(Modifier.padding(20.dp,26.dp,20.dp,0.dp)){PremiumPrimaryButton("Make it clear",Icons.Rounded.AutoAwesome,Modifier.fillMaxWidth()){result=true}}}
        item{Row(Modifier.padding(22.dp,18.dp).clip(CircleShape).background(BubbleMint.copy(.08f)).padding(13.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.CloudOff,null,Modifier.size(18.dp),tint=BubbleMint);Spacer(Modifier.width(9.dp));Text("Analyzed locally · informational only",style=MaterialTheme.typography.labelMedium,color=BubbleMint)}}
    }
}

@Composable private fun ArchiveActionRow(number:String,label:String,icon:ImageVector,tint:Color,onClick:()->Unit){Row(Modifier.padding(horizontal=12.dp,vertical=7.dp).fillMaxWidth().height(88.dp).shadow(12.dp,CircleShape).clip(CircleShape).background(BrandGlass.copy(.70f)).border(1.dp,Color.White.copy(.10f),CircleShape).clickable(onClick=onClick).padding(horizontal=11.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(60.dp).clip(CircleShape).background(tint.copy(.16f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(24.dp),tint=tint)};Text(label,Modifier.padding(start=16.dp).weight(1f),fontWeight=FontWeight.Bold,fontSize=16.sp);Text(number,fontWeight=FontWeight.Black,color=tint.copy(.60f));Spacer(Modifier.width(10.dp));Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(.07f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ArrowOutward,"Open $label",Modifier.size(19.dp),tint=tint)}}

}
@Composable private fun TermsResult(back:()->Unit){BackHandler(onBack=back);LazyColumn(contentPadding=PaddingValues(20.dp,42.dp,20.dp,132.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{Row(verticalAlignment=Alignment.CenterVertically){OrbIcon(Icons.AutoMirrored.Rounded.ArrowBack,"Back",back);Spacer(Modifier.width(14.dp));Column{BubbleTag("analysis ready",BubbleMint);Text("Here’s the vibe",Modifier.padding(top=9.dp),style=MaterialTheme.typography.headlineLarge)}}};item{GlassCard{Row(Modifier.padding(6.dp),verticalAlignment=Alignment.CenterVertically){CircularRiskIndicator(.58f);Spacer(Modifier.width(20.dp));Column{BubbleTag("worth a look",BubbleBlush);Text("A few catches",Modifier.padding(top=9.dp),style=MaterialTheme.typography.titleLarge);Text("Renewal and disputes need your attention.",Modifier.padding(top=5.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}}};item{GlassCard{BubbleTag("the one-minute version",BubbleCyan);Text("It renews yearly. You can cancel before billing, but refunds are limited and disputes go through arbitration.",Modifier.padding(top=14.dp),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)}};items(listOf("Important" to Icons.Rounded.PriorityHigh,"Watch out" to Icons.Rounded.Visibility,"Good" to Icons.Rounded.ThumbUp,"Money" to Icons.Rounded.Payments,"Cancellation" to Icons.Rounded.EventBusy,"Privacy" to Icons.Rounded.Lock,"Your rights" to Icons.Rounded.Balance)){(title,icon)->GlassDisclosure(title,icon)}}}

@Composable private fun ActivityScreen(){LazyColumn(contentPadding=PaddingValues(0.dp,42.dp,0.dp,124.dp)){item{Column(Modifier.padding(horizontal=20.dp)){PageHeader("Your trail","A calm little record of what Honorable understood.",Icons.Rounded.Timeline);Spacer(Modifier.height(24.dp))}};items(listOf(Triple("Tennis outside","12 moments found",Icons.Rounded.Search),Triple("Subscription terms","A few catches surfaced",Icons.Rounded.Policy),Triple("Library synced","48 new memories ready",Icons.Rounded.Sync),Triple("Flight screenshot","Visible text matched",Icons.Rounded.Screenshot)).withIndex().toList()){(index,event)->ArchiveActivityRow(index,event.first,event.second,event.third)}}}

@Composable private fun ArchiveActivityRow(index:Int,title:String,detail:String,icon:ImageVector){val tint=listOf(BubbleCyan,BubbleLilac,BubbleMint,BubbleBlush)[index%4];Row(Modifier.padding(horizontal=12.dp,vertical=7.dp).fillMaxWidth().heightIn(min=106.dp).shadow(12.dp,RoundedCornerShape(48.dp)).clip(RoundedCornerShape(48.dp)).background(BrandGlass.copy(.66f)).border(1.dp,Color.White.copy(.10f),RoundedCornerShape(48.dp)).padding(horizontal=13.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(72.dp).clip(CircleShape).background(tint.copy(.15f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(27.dp),tint=tint)};Column(Modifier.padding(start=16.dp).weight(1f)){Text(title,fontWeight=FontWeight.Bold,fontSize=16.sp);Text(detail,Modifier.padding(top=6.dp),color=MaterialTheme.colorScheme.onSurfaceVariant);Text("today · ${(9+index).toString().padStart(2,'0')}:${(index*13).toString().padStart(2,'0')}",Modifier.padding(top=7.dp),style=MaterialTheme.typography.labelSmall,color=tint)};Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(.06f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ChevronRight,null,tint=tint)}}

}
@Composable private fun SettingsScreen(openPrivacy:()->Unit,openPlus:()->Unit){LazyColumn(contentPadding=PaddingValues(20.dp,42.dp,20.dp,132.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){item{PageHeader("Make it yours","Privacy, intelligence, and the way Honorable feels.",Icons.Rounded.Tune)};item{PremiumUpgradeCard(openPlus)};item{SettingCluster("privacy",listOf(SettingItem("Privacy promise","Everything stays right here",Icons.Rounded.Shield,openPrivacy),SettingItem("Permissions","Photos and videos",Icons.Rounded.Key,{})))};item{SettingCluster("intelligence",listOf(SettingItem("AI & search","Private, on-device understanding",Icons.Rounded.AutoAwesome,{}),SettingItem("Storage & index","Your local memory map",Icons.Rounded.Storage,{})))};item{SettingCluster("honorable",listOf(SettingItem("Appearance","Deep ink · bubble glow",Icons.Rounded.Palette,{}),SettingItem("About","Version 0.1.0",Icons.Rounded.Info,{})))}}}

@Composable private fun PrivacyScreen(close:()->Unit){OverlayScaffold(close){LazyColumn(contentPadding=PaddingValues(0.dp,4.dp,0.dp,42.dp)){item{Column(Modifier.padding(horizontal=20.dp)){BubbleTag("the honorable promise",BubbleMint);Text("Your life.\nStill yours.",Modifier.padding(top=22.dp),style=MaterialTheme.typography.displaySmall.copy(fontSize=59.sp,lineHeight=55.sp));Text("Good intelligence does not need a cloud copy of your memories.",Modifier.padding(top=18.dp,bottom=30.dp).widthIn(max=320.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyLarge)}};item{ArchiveTrustRow("01","Local AI",Icons.Rounded.AutoAwesome,BubbleCyan)};item{ArchiveTrustRow("02","Local text reading",Icons.Rounded.TextFields,BubbleLilac)};item{ArchiveTrustRow("03","Local memory map",Icons.Rounded.Storage,BubbleBlush)};item{ArchiveTrustRow("04","Nothing uploaded",Icons.Rounded.CloudOff,BubbleMint)};item{Row(Modifier.padding(22.dp,26.dp).clip(CircleShape).background(BubbleMint.copy(.09f)).padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Lock,null,Modifier.size(20.dp),tint=BubbleMint);Text("Photos, frames and searches stay on this device.",Modifier.padding(start=11.dp),color=BubbleMint)}}}}}

@Composable private fun ArchiveTrustRow(number:String,title:String,icon:ImageVector,tint:Color){Row(Modifier.padding(horizontal=12.dp,vertical=6.dp).fillMaxWidth().height(82.dp).clip(CircleShape).background(BrandGlass.copy(.68f)).border(1.dp,Color.White.copy(.10f),CircleShape).padding(horizontal=11.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(58.dp).clip(CircleShape).background(tint.copy(.15f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(24.dp),tint=tint)};Text(title,Modifier.padding(start=16.dp).weight(1f),fontWeight=FontWeight.Bold);Text(number,fontWeight=FontWeight.Black,color=tint.copy(.62f));Spacer(Modifier.width(9.dp));Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(.06f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Check,null,Modifier.size(19.dp),tint=tint)}}

}
@Composable private fun PlusScreen(close:()->Unit){var annual by rememberSaveable{mutableStateOf(true)};OverlayScaffold(close){LazyColumn(contentPadding=PaddingValues(0.dp,4.dp,0.dp,42.dp)){item{val shape=RoundedCornerShape(58.dp);Box(Modifier.padding(horizontal=12.dp).fillMaxWidth().height(330.dp).shadow(28.dp,shape,ambientColor=BubbleLilac.copy(.22f),spotColor=BubbleBlue.copy(.24f)).clip(shape).background(Brush.linearGradient(listOf(Color(0xFF393A80),Color(0xFF24285E),BrandGlassStrong))).border(1.dp,Color.White.copy(.18f),shape)){Canvas(Modifier.matchParentSize()){drawCircle(BubbleLilac.copy(.45f),size.minDimension*.32f,Offset(size.width*.84f,size.height*.12f));drawCircle(BubbleCyan.copy(.24f),size.minDimension*.22f,Offset(size.width*.69f,size.height*.31f));drawCircle(BubbleBlush.copy(.22f),size.minDimension*.19f,Offset(size.width*.94f,size.height*.45f))};Column(Modifier.fillMaxSize().padding(26.dp)){BubbleTag("honorable plus",BubbleLilac);Spacer(Modifier.weight(1f));Text("More ways\nto remember.",style=MaterialTheme.typography.displaySmall.copy(fontSize=50.sp,lineHeight=47.sp),color=BrandIce);Text("Same private foundation. More depth when you want it.",Modifier.padding(top=12.dp).widthIn(max=280.dp),color=BrandIce.copy(.72f),style=MaterialTheme.typography.bodyLarge)}}};item{Column(Modifier.padding(20.dp)){Text("Everything in your bubble",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(13.dp));listOf("Deeper memory understanding","Smarter video moments","Terms made human","No ads, ever","Future private tools").forEachIndexed{i,label->val tint=listOf(BubbleCyan,BubbleLilac,BubbleBlush,BubbleMint,BubbleBlue)[i];Row(Modifier.padding(vertical=4.dp).fillMaxWidth().height(60.dp).clip(CircleShape).background(tint.copy(.09f)).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(.16f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Check,null,Modifier.size(19.dp),tint=tint)};Text(label,Modifier.padding(start=13.dp),fontWeight=FontWeight.SemiBold)}};Spacer(Modifier.height(24.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){PlanChoice("Monthly","$5.99",!annual,Modifier.weight(1f)){annual=false};PlanChoice("Annual","$39.99",annual,Modifier.weight(1f)){annual=true}};Spacer(Modifier.height(14.dp));PremiumPrimaryButton("Continue",Icons.AutoMirrored.Rounded.ArrowForward,Modifier.fillMaxWidth()){};Text("Preview only · store setup comes later",Modifier.padding(top=13.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)}}}}}

@Composable private fun FloatingGlassDock(selected:MainTab,onSelect:(MainTab)->Unit,modifier:Modifier=Modifier){Row(modifier.navigationBarsPadding().padding(horizontal=13.dp).padding(bottom=9.dp).fillMaxWidth().height(80.dp).shadow(24.dp,CircleShape,ambientColor=BubbleBlue.copy(.22f),spotColor=BubbleBlue.copy(.28f)).clip(CircleShape).background(BrandGlassStrong).border(1.dp,Color.White.copy(.14f),CircleShape).padding(6.dp),verticalAlignment=Alignment.CenterVertically){MainTab.entries.forEach{tab->val active=tab==selected;val background by animateColorAsState(if(active)BubbleBlue.copy(.20f) else Color.Transparent,label="dock-bg");val foreground=if(active)BubbleCyan else MaterialTheme.colorScheme.onSurfaceVariant;Column(Modifier.weight(if(active)1.12f else 1f).fillMaxHeight().clip(CircleShape).background(background).clickable{onSelect(tab)}.semantics{this.selected=active;contentDescription=tab.label;role=Role.Tab},verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(if(active)32.dp else 27.dp).clip(CircleShape).background(if(active)BubbleCyan.copy(.12f) else Color.Transparent),contentAlignment=Alignment.Center){Icon(tab.icon,null,Modifier.size(if(active)20.dp else 18.dp),tint=foreground)};Text(tab.label,Modifier.padding(top=2.dp),style=MaterialTheme.typography.labelSmall.copy(fontSize=8.sp),color=foreground.copy(alpha=if(active)1f else .74f),fontWeight=if(active)FontWeight.Bold else FontWeight.Medium)}}}

}
@Composable private fun PageHeader(title:String,subtitle:String,icon:ImageVector){Column(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){BubbleTag("honorable",BubbleCyan);Spacer(Modifier.weight(1f));Box(Modifier.size(50.dp).clip(CircleShape).background(BubbleLilac.copy(.14f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(23.dp),tint=BubbleLilac)}};Text(title,Modifier.padding(top=20.dp),style=MaterialTheme.typography.displaySmall.copy(fontSize=54.sp,lineHeight=51.sp));Text(subtitle,Modifier.padding(top=10.dp).widthIn(max=330.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyLarge)}}
@Composable private fun PremiumEyebrow(text:String,light:Boolean=false){Text(text.lowercase(),color=if(light)BrandIce else BubbleCyan,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)}
@Composable private fun AccentIcon(icon:ImageVector,size:Dp=46.dp){Surface(Modifier.size(size),CircleShape,color=BubbleLilac.copy(.14f),border=BorderStroke(1.dp,Color.White.copy(.10f))){Box(contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size((size.value*.42f).dp),tint=BubbleLilac)}}}
@Composable private fun PremiumPrimaryButton(label:String,icon:ImageVector,modifier:Modifier=Modifier,onClick:()->Unit){Button(onClick,modifier.heightIn(min=68.dp),shape=CircleShape,colors=ButtonDefaults.buttonColors(containerColor=BubbleCyan,contentColor=BrandNavyDeep),elevation=ButtonDefaults.buttonElevation(defaultElevation=8.dp,pressedElevation=2.dp),contentPadding=PaddingValues(start=24.dp,end=8.dp)){Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.ExtraBold);Box(Modifier.size(52.dp).clip(CircleShape).background(BrandNavyDeep.copy(.90f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(19.dp),tint=BrandIce)}}}
@Composable private fun PremiumUpgradeCard(onClick:()->Unit){val shape=RoundedCornerShape(46.dp);Row(Modifier.fillMaxWidth().height(154.dp).shadow(20.dp,shape,ambientColor=BubbleLilac.copy(.16f),spotColor=BubbleBlue.copy(.18f)).clip(shape).background(Brush.linearGradient(listOf(BubbleLilac.copy(.36f),BubbleBlue.copy(.24f),BrandGlassStrong))).border(1.dp,Color.White.copy(.16f),shape).clickable(onClick=onClick).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(90.dp).clip(CircleShape).background(Brush.radialGradient(listOf(BubbleLilac.copy(.42f),BubbleBlue.copy(.16f)))),contentAlignment=Alignment.Center){Text("+",fontWeight=FontWeight.Black,fontSize=58.sp,color=BubbleLilac)};Column(Modifier.padding(start=16.dp).weight(1f)){BubbleTag("plus",BubbleLilac);Text("More magic,\nsame privacy",Modifier.padding(top=9.dp),style=MaterialTheme.typography.titleLarge,color=BrandIce)};Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(.10f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ArrowOutward,"Open Honorable Plus",tint=BubbleCyan)}}}

@Composable fun GlassSurface(modifier:Modifier=Modifier,shape:Shape=RoundedCornerShape(38.dp),alpha:Float=.72f,shadow:Dp=0.dp,dark:Boolean=false,content:@Composable BoxScope.()->Unit){val base=if(dark)Color(0xFF111632)else Color(0xFF1C234B);val resolvedAlpha=if(dark)maxOf(alpha,.82f)else alpha;val elevation=if(shadow.value>0f)shadow else 14.dp;Box(modifier.shadow(elevation,shape,ambientColor=BubbleBlue.copy(.12f),spotColor=BubbleLilac.copy(.14f)).clip(shape).background(Brush.linearGradient(listOf(base.copy(resolvedAlpha),BubbleBlue.copy(.10f),BubbleLilac.copy(.07f)))).border(1.dp,Color.White.copy(.13f),shape),contentAlignment=Alignment.Center,content=content)}
@Composable private fun GlassCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){GlassSurface(modifier.fillMaxWidth(),shape=RoundedCornerShape(38.dp)){Column(Modifier.padding(23.dp),content=content)}}
@Composable private fun GlassCircle(modifier:Modifier,content:@Composable BoxScope.()->Unit){GlassSurface(modifier,CircleShape,.66f,8.dp,content=content)}
@Composable private fun OrbIcon(icon:ImageVector,description:String,onClick:()->Unit,dark:Boolean=false)=SquareIconButton(icon,description,onClick,dark)
@Composable private fun SquareIconButton(icon:ImageVector,description:String,onClick:()->Unit,dark:Boolean=false){GlassSurface(Modifier.size(54.dp).clickable(onClick=onClick),CircleShape,if(dark).86f else .72f,10.dp,dark){Icon(icon,description,Modifier.size(21.dp),tint=if(dark)BrandIce else BubbleCyan)}}
@Composable private fun CircleFilter(label:String,active:Boolean,onClick:()->Unit){val color by animateColorAsState(if(active)BubbleCyan.copy(.18f) else BrandGlass.copy(.64f),label="filter");Surface(Modifier.heightIn(min=50.dp).clickable(onClick=onClick).semantics{selected=active},shape=CircleShape,color=color,border=BorderStroke(1.dp,if(active)BubbleCyan.copy(.65f) else Color.White.copy(.10f))){Row(Modifier.padding(horizontal=17.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){if(active){Box(Modifier.size(7.dp).clip(CircleShape).background(BubbleCyan));Spacer(Modifier.width(7.dp))};Text(label,color=if(active)BubbleCyan else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)}}}
@Composable private fun CircularRiskIndicator(score:Float){Box(Modifier.size(112.dp).clip(CircleShape).background(BubbleBlush.copy(.08f)),contentAlignment=Alignment.Center){CircularProgressIndicator({score},Modifier.fillMaxSize().padding(6.dp),color=BubbleBlush,strokeWidth=9.dp,trackColor=Color.White.copy(.08f));Column(horizontalAlignment=Alignment.CenterHorizontally){Text("58",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text("of 100",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable private fun GlassDisclosure(title:String,icon:ImageVector){var open by rememberSaveable(title){mutableStateOf(title=="Important")};GlassCard(Modifier.clickable{open=!open}){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).clip(CircleShape).background(BubbleBlush.copy(.12f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(21.dp),tint=BubbleBlush)};Spacer(Modifier.width(14.dp));Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(.07f)),contentAlignment=Alignment.Center){Icon(if(open)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,if(open)"Collapse" else "Expand",tint=BubbleCyan)}};AnimatedVisibility(open){Text("Automatic renewal and individual arbitration deserve a closer look.",Modifier.padding(top=14.dp,start=62.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
private data class SettingItem(val title:String,val detail:String,val icon:ImageVector,val click:()->Unit)
@Composable private fun SettingCluster(label:String,items:List<SettingItem>){Column{Text(label.lowercase(),Modifier.padding(start=6.dp,bottom=10.dp),style=MaterialTheme.typography.titleMedium,color=BubbleLilac);GlassCard{items.forEachIndexed{i,item->if(i>0)Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clip(CircleShape).background(Color.White.copy(.035f)).clickable(onClick=item.click).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){AccentIcon(item.icon,46.dp);Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(item.title,style=MaterialTheme.typography.titleMedium);Text(item.detail,Modifier.padding(top=3.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(.06f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ChevronRight,null,Modifier.size(18.dp),tint=BubbleCyan)}}}}}
}
@Composable private fun TimelineRow(title:String,detail:String,icon:ImageVector){Row(Modifier.height(IntrinsicSize.Min)){Column(horizontalAlignment=Alignment.CenterHorizontally){GlassCircle(Modifier.size(46.dp)){Icon(icon,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.primary)};Box(Modifier.width(1.dp).weight(1f).background(MaterialTheme.colorScheme.primary.copy(.22f)))};Spacer(Modifier.width(14.dp));GlassCard(Modifier.padding(bottom=10.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(detail,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Today",Modifier.padding(top=8.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}}}
@Composable private fun PrivacyNode(title:String,icon:ImageVector,modifier:Modifier){GlassCard(modifier){Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){GlassCircle(Modifier.size(58.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.primary)};Spacer(Modifier.height(10.dp));Text(title,style=MaterialTheme.typography.titleMedium)}}}
@Composable private fun PlanChoice(name:String,price:String,selected:Boolean,modifier:Modifier,onClick:()->Unit){Surface(modifier.heightIn(min=142.dp).clickable(onClick=onClick).semantics{this.selected=selected},shape=RoundedCornerShape(36.dp),color=if(selected)BubbleLilac.copy(.18f) else BrandGlass.copy(.62f),border=BorderStroke(1.dp,if(selected)BubbleLilac.copy(.72f) else Color.White.copy(.10f))){Column(Modifier.padding(18.dp)){BubbleTag(if(selected)"picked" else "option",if(selected)BubbleLilac else MaterialTheme.colorScheme.onSurfaceVariant);Text(name,Modifier.padding(top=12.dp),style=MaterialTheme.typography.labelLarge,color=BrandIce);Text(price,style=MaterialTheme.typography.headlineMedium,color=BrandIce,fontWeight=FontWeight.Black);if(selected&&name=="Annual")Text("save more",style=MaterialTheme.typography.labelSmall,color=BubbleMint)}}}
@Composable private fun OverlayScaffold(close:()->Unit,content:@Composable BoxScope.()->Unit){AppBackground{Box(Modifier.fillMaxSize().statusBarsPadding()){SquareIconButton(Icons.Rounded.Close,"Close",close);Box(Modifier.fillMaxSize().padding(top=58.dp),content=content)}}}
@Composable private fun SuggestionPill(text:String,onClick:()->Unit){Surface(Modifier.heightIn(min=50.dp).clickable(onClick=onClick),shape=CircleShape,color=BrandGlass.copy(.68f),border=BorderStroke(1.dp,Color.White.copy(.10f))){Row(Modifier.padding(horizontal=17.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){Text(text,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.width(10.dp));Icon(Icons.Rounded.ArrowOutward,null,Modifier.size(16.dp),tint=BubbleCyan)}}}
@Composable private fun MiniGlassPill(text:String){Surface(shape=CircleShape,color=BrandGlassStrong,border=BorderStroke(1.dp,Color.White.copy(.16f))){Text(text.lowercase(),Modifier.padding(horizontal=11.dp,vertical=7.dp),color=BrandIce,style=MaterialTheme.typography.labelSmall)}}
@Composable private fun PrivacyPill(text:String="Private search · Nothing uploaded"){Surface(shape=CircleShape,color=BubbleMint.copy(.11f),border=BorderStroke(1.dp,BubbleMint.copy(.22f))){Row(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Lock,null,Modifier.size(15.dp),tint=BubbleMint);Spacer(Modifier.width(8.dp));Text(text,style=MaterialTheme.typography.labelMedium,color=BubbleMint)}}
}
@Composable private fun SectionLabel(title:String,detail:String){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);Text(detail,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=1.1.sp,color=MaterialTheme.colorScheme.secondary)}}
@Composable private fun Pressable(onClick:()->Unit,content:@Composable (Boolean)->Unit){val source=remember{MutableInteractionSource()};val pressed by source.collectIsPressedAsState();Box(Modifier.clickable(interactionSource=source,indication=null,onClick=onClick)){content(pressed)}}
