package app.honorable

// Lucide paths from installed lucide-react-native, ISC license in assets/Lucide-LICENSE.txt.
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object HonorableIcons {
 private fun icon(name:String,vararg paths:String):ImageVector = ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply { paths.forEach { data -> addPath(pathData=PathParser().parsePathString(data).toNodes(),stroke=SolidColor(Color.White),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) } }.build()
 val Home:ImageVector by lazy { icon("Home","M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8","M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
 val PhotoLibrary:ImageVector by lazy { icon("PhotoLibrary","m22 11-1.296-1.296a2.4 2.4 0 0 0-3.408 0L11 16","M4 8a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2","M 12 7 a 1 1 0 1 0 2 0 a 1 1 0 1 0 -2 0","M 10 2 H 20 Q 22 2 22 4 V 14 Q 22 16 20 16 H 10 Q 8 16 8 14 V 4 Q 8 2 10 2 Z") }
 val Tune:ImageVector by lazy { icon("Tune","M10 5H3","M12 19H3","M14 3v4","M16 17v4","M21 12h-9","M21 19h-5","M21 5h-7","M8 10v4","M8 12H3") }
 val CreditCard:ImageVector by lazy { icon("CreditCard","M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z","M13 5v2","M13 17v2","M13 11v2") }
 val Timeline:ImageVector by lazy { icon("Timeline","M5 21v-6","M12 21V3","M19 21V9") }
 val AccountCircle:ImageVector by lazy { icon("AccountCircle","M17.925 20.056a6 6 0 0 0-11.851.001","M 8 11 a 4 4 0 1 0 8 0 a 4 4 0 1 0 -8 0","M 2 12 a 10 10 0 1 0 20 0 a 10 10 0 1 0 -20 0") }
 val Search:ImageVector by lazy { icon("Search","m21 21-4.34-4.34","M 3 11 a 8 8 0 1 0 16 0 a 8 8 0 1 0 -16 0") }
 val ArrowUpward:ImageVector by lazy { icon("ArrowUpward","m5 12 7-7 7 7","M12 19V5") }
 val ArrowBack:ImageVector by lazy { icon("ArrowBack","m12 19-7-7 7-7","M19 12H5") }
 val OpenInNew:ImageVector by lazy { icon("OpenInNew","M7 7h10v10","M7 17 17 7") }
 val Close:ImageVector by lazy { icon("Close","M18 6 6 18","m6 6 12 12") }
 val Shield:ImageVector by lazy { icon("Shield","M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z") }
 val ChevronRight:ImageVector by lazy { icon("ChevronRight","m9 18 6-6-6-6") }
}
