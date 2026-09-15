package app.honorable.auth

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Optional, bounded telemetry. No exception messages, stacks, media, paths or queries. */
object SafeBetaTelemetry {
 private val processId=UUID.randomUUID().toString()
 private var processStarted=System.currentTimeMillis()
 private val executor=ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(32),ThreadPoolExecutor.DiscardPolicy())
 @Volatile private var disabled=false
 @Volatile private var started=false
 @Volatile private var ownerAccountId:String?=null
 fun markProcessStart(){processStarted=System.currentTimeMillis()}
 fun disable(context:Context){disabled=true;runCatching{SecureAccountStore(context,"honorable-beta-observation").clear()}}
 @Synchronized fun start(context:Context,owner:String,optOut:Boolean){
  if(ownerAccountId!=owner){started=false;ownerAccountId=owner}
  disabled=optOut;if(optOut){disable(context);return};if(started)return;started=true
  executor.execute {runCatching {
   val storage=SecureAccountStore(context,"honorable-beta-observation");val previous=storage.read()
   val events=JSONArray().put(event(context,"app_session",JSONObject().put("sessionId",processId).put("crashMonitoring",Build.VERSION.SDK_INT>=30))).put(event(context,"performance_sample",JSONObject().put("metric","STARTUP").put("durationMs",(System.currentTimeMillis()-processStarted).coerceIn(0,3600000))))
   if(Build.VERSION.SDK_INT>=30&&previous?.optString("owner")==owner){
    val exits=(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getHistoricalProcessExitReasons(context.packageName,0,5)
    val exit=exits.firstOrNull{it.pid==previous.optInt("pid",-1)&&it.timestamp>previous.optLong("startedAt")&&it.timestamp<processStarted&&it.reason in setOf(ApplicationExitInfo.REASON_CRASH,ApplicationExitInfo.REASON_CRASH_NATIVE,ApplicationExitInfo.REASON_ANR)}
    if(exit!=null){val kind=when(exit.reason){ApplicationExitInfo.REASON_ANR->"ANR";ApplicationExitInfo.REASON_CRASH_NATIVE->"NATIVE";else->"JVM"};val metadata=JSONObject().put("sessionId",previous.optString("sessionId")).put("crashKind",kind).put("appVersion",previous.optString("version","0")).put("occurredAtMs",exit.timestamp);events.put(event(context,"app_crash",metadata,UUID.nameUUIDFromBytes("${exit.timestamp}:${exit.reason}".toByteArray()).toString()))}
   }
   storage.write(JSONObject().put("owner",owner).put("sessionId",processId).put("startedAt",processStarted).put("pid",android.os.Process.myPid()).put("version",context.packageManager.getPackageInfo(context.packageName,0).versionName?:"0"))
   send(context,events,owner)
  }}
 }
 fun emit(context:Context,type:String,metadata:JSONObject=JSONObject()) {val owner=ownerAccountId?:return;if(disabled)return;executor.execute {runCatching{send(context,JSONArray().put(event(context,type,metadata)),owner)}}}
 fun authorizeEditor(context:Context){val saved=SecureAccountStore(context).read()?:throw AccountHttpError(401);AccountSession(context,saved.getString("apiUrl")).action("/v1/beta/authorize",JSONObject().put("flag","studio_enabled"))}
 fun duration(context:Context,metric:String,startedAt:Long){emit(context,"performance_sample",JSONObject().put("metric",metric).put("durationMs",(System.currentTimeMillis()-startedAt).coerceIn(0,3600000)))}
 private fun event(context:Context,type:String,metadata:JSONObject,id:String=UUID.randomUUID().toString()):JSONObject {val info=context.packageManager.getPackageInfo(context.packageName,0);metadata.put("platform","ANDROID");if(!metadata.has("appVersion"))metadata.put("appVersion",info.versionName?:"0");metadata.put("osVersion",Build.VERSION.RELEASE);return JSONObject().put("id",id).put("type",type).put("metadata",metadata)}
 private fun send(context:Context,events:JSONArray,owner:String){
  if(disabled)return
  val saved=SecureAccountStore(context).read()?:return;if(saved.optJSONObject("cached")?.optJSONObject("account")?.optString("accountId")!=owner)return;val url=URL(saved.optString("apiUrl")+"/v1/analytics");if(url.protocol!="https")return
  val connection=url.openConnection() as HttpURLConnection
  try{connection.connectTimeout=2000;connection.readTimeout=2000;connection.instanceFollowRedirects=false;connection.requestMethod="POST";connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Authorization","Bearer "+saved.getString("accessToken"));connection.setRequestProperty("X-Honorable-Platform","ANDROID");connection.doOutput=true;connection.outputStream.use{it.write(JSONObject().put("events",events).toString().toByteArray())};connection.responseCode /* Best effort; never refresh or retry indefinitely. */}finally{connection.disconnect()}
 }
}
