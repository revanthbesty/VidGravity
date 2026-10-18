package com.vidgravity.app

import android.content.Context
import java.io.File

data class PrivacyChoices(val analytics:Boolean=false,val crashes:Boolean=false)
class PrivacyPreferences(context:Context) {
    private val prefs=context.getSharedPreferences("vg-privacy",Context.MODE_PRIVATE)
    fun load()=PrivacyChoices(prefs.getBoolean("analytics",false),prefs.getBoolean("crashes",false))
    fun save(value:PrivacyChoices,accounts:AccountService) {
        // Apply to SDK before recording success in the UI.
        accounts.consent(value.analytics,value.crashes)
        check(prefs.edit().putBoolean("analytics",value.analytics).putBoolean("crashes",value.crashes).commit()) {"Preference could not be saved."}
    }
}
data class StorageOverview(val projectBytes:Long,val cacheBytes:Long,val privateBytes:Long,val availableBytes:Long)
object PrivacyStorage {
    private fun bytes(root:File):Long=if(!root.exists()) 0 else root.walkTopDown().onEnter {it.canonicalFile==it.absoluteFile}.filter {it.isFile}.sumOf {it.length()}
    fun overview(context:Context)=StorageOverview(bytes(File(context.filesDir,"studio-projects")),bytes(File(context.cacheDir,"thumbnails")),bytes(context.filesDir),context.filesDir.usableSpace)
    /** Only owned, disposable thumbnails. Never delete imports, work files, projects or exports. */
    fun clearThumbnails(context:Context) {
        MediaThumbnails.clear()
        val cache=File(context.cacheDir,"thumbnails")
        check(!cache.exists() || cache.deleteRecursively()) {"Thumbnail cache could not be cleared."}
    }
}
