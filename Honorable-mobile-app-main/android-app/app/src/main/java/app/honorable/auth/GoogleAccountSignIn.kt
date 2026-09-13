package app.honorable.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Existing Credential Manager flow, shared by the Compose and RN Android entry points. */
object GoogleAccountSignIn {
 suspend fun token(activity:Activity,clientId:String):String {
  require(clientId.isNotBlank()){ "Google Sign-In is not configured" }
  val option=GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
  val credential=CredentialManager.create(activity).getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
  require(credential is CustomCredential&&credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL){"Google did not return an ID token"}
  return GoogleIdTokenCredential.createFrom(credential.data).idToken
 }
 suspend fun clear(activity:Activity){CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())}
}
