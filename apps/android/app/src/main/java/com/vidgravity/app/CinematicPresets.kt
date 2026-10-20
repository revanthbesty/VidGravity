package com.vidgravity.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class CinemaPreset(val id:String=UUID.randomUUID().toString(),val name:String,val style:CinemaStyle) {
    fun validate() {require(id.matches(Regex("[a-fA-F0-9-]{36}")) && name.isNotBlank() && name.length<=40);style.validate(1_800_000);require(style==style.colorOnly()) {"Color presets cannot contain timing or motion."}}
}
internal data class CinemaPresetListing(val presets:List<CinemaPreset>,val unreadable:Int)
/** Local user color presets; separate from editable projects and never delete unreadable files. */
internal class CinemaPresetRepository(context:Context) {
    companion object {private val lock=Any()}
    private val root=File(context.filesDir,"cinema-color-presets")
    private fun file(id:String):File {require(id.matches(Regex("[a-fA-F0-9-]{36}")));return File(root,"$id.json")}
    private fun decode(raw:String):CinemaPreset {
        require(raw.length<16000);val j=JSONObject(raw);require(j.getInt("schema")==1) {"This color preset requires a newer app."}
        return CinemaPreset(j.getString("id"),j.getString("name"),CinemaStyle.decode(j.getJSONObject("style"))).also {it.validate()}
    }
    fun list():CinemaPresetListing=synchronized(lock) {
        var bad=0
        val presets=root.listFiles().orEmpty().map {it.name.removeSuffix(".bak").removeSuffix(".json")}.distinct().mapNotNull {id->
            try {decode(AtomicFile(file(id)).openRead().bufferedReader().use {it.readText()})} catch(failure:Exception) {bad++;null}
        }.sortedBy {it.name.lowercase()}
        CinemaPresetListing(presets,bad)
    }
    fun save(p:CinemaPreset):Unit=synchronized(lock) {
        p.validate();val target=file(p.id)
        if(target.exists() || File(target.path+".bak").exists()) decode(AtomicFile(target).openRead().bufferedReader().use {it.readText()})
        else require(root.listFiles().orEmpty().count {it.name.endsWith(".json")}<12) {"Keep up to twelve custom color presets. Remove an unused preset first."}
        val raw=JSONObject().put("schema",1).put("id",p.id).put("name",p.name).put("style",p.style.json()).toString()
        check(root.exists() || root.mkdirs());val atomic=AtomicFile(target);val output=atomic.startWrite()
        try {output.write(raw.toByteArray(Charsets.UTF_8));atomic.finishWrite(output)} catch(failure:Exception) {atomic.failWrite(output);throw failure}
    }
    fun delete(id:String)=synchronized(lock) {AtomicFile(file(id)).delete()}
}
