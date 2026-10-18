package com.vidgravity.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

data class StudioProjectListing(val projects: List<StudioProject>, val unreadableCount: Int)

data class StudioExportRecord(val workId: String, val fingerprint: String, val uri: String? = null,
    val width: Int = 0, val height: Int = 0)

/** Call on IO. Project content and runtime export records are separate to avoid autosave races. */
class StudioRepository(context: Context) {
    companion object {private val diskLock=Any()}
    private val root = File(context.filesDir, "studio-projects")
    private fun file(id: String, suffix: String = ".json"): File {
        require(id.matches(Regex("[a-fA-F0-9-]{36}"))) { "Invalid project identifier." }
        return File(root, id + suffix)
    }
    fun save(project:StudioProject):Unit=synchronized(diskLock) {
        val target=file(project.id)
        // Validate the complete serialized document before touching either last-good copy.
        val encoded=StudioProjectCodec.encode(project)
        if(target.exists() || File(target.path+".bak").exists()) {
            val previous=StudioProjectCodec.decode(read(target))
            require(previous.id==project.id) { "Project identity mismatch. Existing file was preserved." }
            if(previous.updatedAt>project.updatedAt) return@synchronized
            write(File(root,"recovery/${target.name}"),StudioProjectCodec.encode(previous))
        }
        write(target,encoded)
    }
    @Synchronized fun load(id: String): StudioProject = StudioProjectCodec.decode(read(file(id))).also {
        require(it.id == id) { "Project identity does not match its file. Files were preserved." }
    }
    @Synchronized fun list(): List<StudioProject> = scan().projects
    @Synchronized fun scan(): StudioProjectListing {
        var unreadable = 0
        val projects = root.listFiles().orEmpty()
            .map { File(root, it.name.removeSuffix(".bak")) }.distinctBy { it.name }
            .filter { it.name.endsWith(".json") && !it.name.endsWith(".export.json") && !it.name.endsWith(".request.json") }
            .mapNotNull { file ->
                try { StudioProjectCodec.decode(read(file)).also { require(file.name == it.id + ".json") } }
                catch (failure: Exception) { unreadable++; null }
            }.sortedByDescending { it.updatedAt }
        return StudioProjectListing(projects, unreadable)
    }
    /** Recovery is explicit and creates a new project; corrupted originals are never overwritten. */
    @Synchronized fun recoverable(): List<StudioProject> = File(root,"recovery").listFiles().orEmpty()
        .map {File(it.parentFile,it.name.removeSuffix(".bak"))}.distinctBy {it.name}
        .filter {it.name.endsWith(".json")}.mapNotNull {runCatching {StudioProjectCodec.decode(read(it))}.getOrNull()}
    @Synchronized fun recoverAsCopy(id:String):StudioProject {
        val previous=StudioProjectCodec.decode(read(File(root,"recovery/${file(id).name}")))
        require(previous.id==id)
        return previous.copy(id=java.util.UUID.randomUUID().toString(),title=(previous.title.take(85)+" recovered"),updatedAt=System.currentTimeMillis()).also {save(it)}
    }
    @Synchronized fun duplicate(project: StudioProject): StudioProject = project.copy(
        id = java.util.UUID.randomUUID().toString(), title = (project.title.take(90) + " copy"), updatedAt = System.currentTimeMillis()).also { save(it) }
    @Synchronized fun trash(id: String) {
        val project = load(id)
        write(File(root,"trash/${file(id).name}"),StudioProjectCodec.encode(project))
        AtomicFile(file(id)).delete()
    }
    @Synchronized fun trashed(): List<StudioProject> = File(root,"trash").listFiles().orEmpty()
        .map { File(it.parentFile,it.name.removeSuffix(".bak")) }.distinctBy { it.name }
        .filter { it.name.endsWith(".json") }.mapNotNull { runCatching { StudioProjectCodec.decode(read(it)) }.getOrNull() }
        .sortedByDescending { it.updatedAt }
    @Synchronized fun restore(id: String) {
        val target=File(root,"trash/${file(id).name}")
        check(!file(id).exists()) { "A project with this identifier already exists." }
        save(StudioProjectCodec.decode(read(target)))
        AtomicFile(target).delete()
    }
    @Synchronized fun delete(id: String) {
        AtomicFile(file(id)).delete()
        AtomicFile(file(id, ".export.json")).delete()
    }
    @Synchronized fun saveRequest(workId: String, project: StudioProject) = write(file(workId, ".request.json"), StudioProjectCodec.encode(project))
    @Synchronized fun request(workId: String): StudioProject = StudioProjectCodec.decode(read(file(workId, ".request.json")))
    @Synchronized fun deleteRequest(workId: String) { AtomicFile(file(workId, ".request.json")).delete() }
    @Synchronized fun record(projectId: String): StudioExportRecord? {
        val target = file(projectId, ".export.json")
        if (!target.exists() && !File(target.path + ".bak").exists()) return null
        val json = JSONObject(read(target))
        return StudioExportRecord(json.getString("workId"), json.getString("fingerprint"),
            if (json.isNull("uri")) null else json.getString("uri"), json.optInt("width"), json.optInt("height"))
    }
    @Synchronized fun setRecord(projectId: String, record: StudioExportRecord) {
        write(file(projectId, ".export.json"), JSONObject().put("workId", record.workId)
            .put("fingerprint", record.fingerprint).put("uri", record.uri ?: JSONObject.NULL)
            .put("width", record.width).put("height", record.height).toString())
    }
    private fun read(file:File):String=synchronized(diskLock) {
        AtomicFile(file).openRead().use { stream ->
            val output=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true) {
                val count=stream.read(buffer)
                if(count<0) break
                require(output.size()+count<=1_024_000) { "Project metadata is too large. Original file was preserved." }
                output.write(buffer,0,count)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }
    }
    private fun write(file: File, content: String):Unit=synchronized(diskLock) {
        check(file.parentFile!!.exists() || file.parentFile!!.mkdirs()) { "Project storage is unavailable." }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(content.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (failure: Exception) { atomic.failWrite(stream); throw failure }
    }
}
