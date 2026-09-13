package app.honorable.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials never cross into JavaScript or plain preferences. Excluded from device backups. */
class SecureAccountStore(context: Context, private val name:String="honorable-account") {
 private val file=AtomicFile(File(context.noBackupFilesDir,"$name.enc"))
 private fun key(): SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  return (store.getKey("honorable-account",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder("honorable-account",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun read():JSONObject? {if(!file.baseFile.exists())return null;return try{val data=JSONObject(file.openRead().bufferedReader().use{it.readText()});val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(data.getString("iv"),Base64.NO_WRAP)));JSONObject(String(cipher.doFinal(Base64.decode(data.getString("data"),Base64.NO_WRAP)),Charsets.UTF_8))}catch(_:Exception){clear();null}}
 fun write(value:JSONObject){val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());val data=JSONObject().put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).put("data",Base64.encodeToString(cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8)),Base64.NO_WRAP));val output=file.startWrite();try{output.write(data.toString().toByteArray());file.finishWrite(output)}catch(error:Exception){file.failWrite(output);throw error}}
 fun clear(){file.delete()}
}
class AccountHttpError(val code:Int):Exception("Account request failed ($code)")
class AccountSession(context:Context,private val baseUrl:String) {
 private val installationStore=SecureAccountStore(context,"honorable-installation")
 private val installationId=(installationStore.read()?.optString("id") ?: java.util.UUID.randomUUID().toString().also{installationStore.write(JSONObject().put("id",it))})
 private val store=SecureAccountStore(context)
 private var session=store.read()?.takeIf{it.optString("apiUrl")==baseUrl.trimEnd('/')}.also{if(it==null)store.clear()}
 private fun request(route:String,body:JSONObject?=null,token:String?=null):JSONObject {
  val url=URL(baseUrl.trimEnd('/')+route);require(url.protocol=="https"){"A secure account API URL is required"}
  val connection=url.openConnection() as HttpURLConnection
  try{connection.connectTimeout=15000;connection.readTimeout=15000;connection.instanceFollowRedirects=false;connection.requestMethod=if(body==null)"GET" else "POST";connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("X-Honorable-Installation",installationId);token?.let{connection.setRequestProperty("Authorization","Bearer $it")};if(body!=null){connection.doOutput=true;connection.outputStream.use{it.write(body.toString().toByteArray())}};val code=connection.responseCode;if(code !in 200..299)throw AccountHttpError(code);return JSONObject(connection.inputStream.bufferedReader().use{it.readText()})}finally{connection.disconnect()}
 }
 private fun refresh(){val old=session?:throw AccountHttpError(401);val next=request("/v1/auth/refresh",JSONObject().put("refreshToken",old.getString("refreshToken")));next.put("apiUrl",baseUrl.trimEnd('/')).put("cached",old.optJSONObject("cached")).put("verifiedAt",old.optLong("verifiedAt"));session=next;store.write(next)}
 private fun authorized(route:String,body:JSONObject?=null):JSONObject {val saved=session?:throw AccountHttpError(401);return try{request(route,body,saved.getString("accessToken"))}catch(error:AccountHttpError){if(error.code!=401)throw error;refresh();request(route,body,session!!.getString("accessToken"))}}
 @Synchronized fun restore():JSONObject {
  val saved=session?:return JSONObject().put("status","welcome")
  if(saved.optLong("expiresAt")<=System.currentTimeMillis()){store.clear();session=null;return JSONObject().put("status","welcome")}
  return try{val snapshot=authorized("/v1/auth/session");session!!.put("cached",snapshot).put("verifiedAt",System.currentTimeMillis());store.write(session!!);JSONObject().put("status","online").put("account",snapshot.getJSONObject("account")).put("entitlements",snapshot.getJSONObject("entitlements"))}
  catch(error:AccountHttpError){if(SessionPolicy.mustReauthenticate(error.code)){store.clear();session=null;JSONObject().put("status","welcome")}else if(error.code>=500)offline(saved)else throw error}
  catch(_:IOException){offline(saved)}
 }
 private fun offline(saved:JSONObject):JSONObject {val cache=saved.optJSONObject("cached");return if(SessionPolicy.canUseOffline(saved.optLong("expiresAt"),saved.optLong("verifiedAt"),System.currentTimeMillis(),cache!=null))JSONObject().put("status","offline").put("account",cache!!.getJSONObject("account"))else JSONObject().put("status","unavailable")}
 @Synchronized fun signIn(idToken:String):JSONObject {val next=request("/v1/auth/google",JSONObject().put("idToken",idToken).put("installationId",installationId)).put("apiUrl",baseUrl.trimEnd('/'));session=next;store.write(next);return restore()}
 @Synchronized fun signOut(){val token=session?.optString("refreshToken");store.clear();session=null;try{if(!token.isNullOrBlank())request("/v1/auth/logout",JSONObject().put("refreshToken",token))}catch(_:Exception){/* Local sign-out is final even without a network. */}}
 @Synchronized fun action(route:String,body:JSONObject?):JSONObject {require(route in setOf("/v1/account","/v1/purchases/restore","/v1/credits/deduct","/v1/search/start","/v1/search/complete")){"Unsupported account action"};return authorized(route,body)}
}
