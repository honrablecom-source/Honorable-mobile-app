package app.honorable.auth

/** Offline access is historical recognition, never authority to spend credits. */
object SessionPolicy {
 fun canUseOffline(expiresAt:Long,verifiedAt:Long,now:Long,hasCachedAccount:Boolean):Boolean =
  hasCachedAccount && expiresAt>now && verifiedAt>0 && verifiedAt<=now && now-verifiedAt<86_400_000
 fun mustReauthenticate(httpStatus:Int)=httpStatus==401||httpStatus==403
}
