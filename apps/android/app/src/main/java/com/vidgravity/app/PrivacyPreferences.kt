package com.vidgravity.app

import android.content.Context
import java.io.File

class PrivacyPreferences(context:Context) {
    private val prefs=context.getSharedPreferences("vg-privacy",Context.MODE_PRIVATE)
    fun load()=PrivacyChoices(prefs.getBoolean("analytics",false),prefs.getBoolean("crashes",false))
    fun save(value:PrivacyChoices,accounts:AccountService) {
        PrivacyConsent.update(value,
            persist={prefs.edit().putBoolean("analytics",it.analytics).putBoolean("crashes",it.crashes).commit()},
            apply={accounts.consent(it.analytics,it.crashes)})
    }
}
data class StorageOverview(val projectBytes:Long,val cacheBytes:Long,val privateBytes:Long,val availableBytes:Long)
object PrivacyStorage {
    private fun bytes(root:File):Long=if(!root.exists()) 0 else root.canonicalFile.walkTopDown()
        .onEnter {!java.nio.file.Files.isSymbolicLink(it.toPath())}
        .filter {it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath())}.sumOf {it.length()}
    fun overview(context:Context)=StorageOverview(bytes(File(context.filesDir,"studio-projects")),bytes(File(context.cacheDir,"thumbnails")),bytes(context.filesDir),context.filesDir.usableSpace)
    /** Only owned, disposable thumbnails. Never delete imports, work files, projects or exports. */
    fun clearThumbnails(context:Context) {
        MediaThumbnails.clear()
        val cache=File(context.cacheDir,"thumbnails")
        require(cache.canonicalFile == File(context.cacheDir.canonicalFile,"thumbnails")) {"Unexpected thumbnail directory."}
        if(cache.exists()) java.nio.file.Files.walkFileTree(cache.toPath(),object:java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file:java.nio.file.Path,attrs:java.nio.file.attribute.BasicFileAttributes):java.nio.file.FileVisitResult {
                java.nio.file.Files.delete(file);return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir:java.nio.file.Path,error:java.io.IOException?):java.nio.file.FileVisitResult {
                if(error!=null) throw error
                java.nio.file.Files.delete(dir);return java.nio.file.FileVisitResult.CONTINUE
            }
        })
    }
}
