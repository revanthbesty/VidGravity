package com.vidgravity.app

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

data class AccountProfile(val email:String, val displayName:String)
interface AccountService {
    val available:Boolean
    val profile:StateFlow<AccountProfile?>
    suspend fun signIn(email:String,password:String,register:Boolean=false)
    suspend fun resetPassword(email:String)
    suspend fun saveProfile(name:String)
    suspend fun deleteAccount(password:String)
    fun signOut()
    fun consent(analytics:Boolean,crashes:Boolean)
}
/** Future capability boundary. Existing editing and export remain free for everyone. */
interface Entitlements { fun canUse(feature:String):Boolean }
object FreeEntitlements:Entitlements { override fun canUse(feature:String)=true }

internal fun accountError(error:Throwable):String = when(error) {
    is java.io.IOException -> "Account service is unavailable. Check your connection; local editing remains available."
    else -> "Account request failed. Check your details, connection and provider configuration. Local projects are unchanged."
}

object GuestAccounts:AccountService {
    override val available=false
    override val profile=kotlinx.coroutines.flow.MutableStateFlow<AccountProfile?>(null)
    override suspend fun signIn(email:String,password:String,register:Boolean) {error("Accounts are not configured")}
    override suspend fun resetPassword(email:String) {error("Accounts are not configured")}
    override suspend fun saveProfile(name:String) {error("Accounts are not configured")}
    override suspend fun deleteAccount(password:String) {error("Accounts are not configured")}
    override fun signOut() {profile.value=null}
    override fun consent(analytics:Boolean,crashes:Boolean) {}
}
