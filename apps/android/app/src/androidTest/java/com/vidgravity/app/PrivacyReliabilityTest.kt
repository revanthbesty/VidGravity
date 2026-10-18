package com.vidgravity.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PrivacyReliabilityTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun corruptedAndOversizeProjectsArePreservedAndLastGoodSaveRecoversAsCopy() {
        val repository=StudioRepository(context)
        val p=StudioProject(title="Privacy recovery",clips=listOf(StudioMediaImports.video(context,MediaLibraryIndexer.localUri(context,studioFixture("native-source.mp4")))))
        val target=File(context.filesDir,"studio-projects/${p.id}.json")
        var recovered:StudioProject?=null
        try {
            repository.save(p);repository.save(p.copy(title="Later edit",updatedAt=p.updatedAt+1))
            target.writeText("corrupt")
            assertTrue(repository.scan().unreadableCount>=1)
            recovered=repository.recoverAsCopy(p.id)
            assertNotEquals(p.id,recovered.id);assertEquals(p.clips,recovered.clips)
            assertEquals("corrupt",target.readText())
            target.writeText("x".repeat(1_024_001))
            assertTrue(runCatching {repository.load(p.id)}.isFailure)
            assertEquals(1_024_001L,target.length())
        } finally {repository.delete(p.id);recovered?.let {repository.delete(it.id)};File(context.filesDir,"studio-projects/recovery/${p.id}.json").delete()}
    }
    @Test fun traversalAndIdentityMismatchCannotReadOrOverwriteOtherProjects() {
        val repository=StudioRepository(context)
        assertTrue(runCatching {repository.load("../../preferences")}.isFailure)
        val p=StudioProject(title="Identity",clips=listOf(StudioClip(uri="content://media/video/12",name="Video",sourceDurationMs=1000)))
        val other=UUID.randomUUID().toString();val target=File(context.filesDir,"studio-projects/$other.json")
        try {target.parentFile!!.mkdirs();target.writeText(StudioProjectCodec.encode(p));assertTrue(runCatching {repository.load(other)}.isFailure);assertEquals(StudioProjectCodec.encode(p),target.readText())} finally {target.delete()}
    }
    @Test fun privacyPreferencesDriveProviderAndCacheClearPreservesProjectsAndMedia() {
        val fake=RecordingAccounts();val prefs=PrivacyPreferences(context);val old=prefs.load()
        val media=File(context.cacheDir,"privacy-kept.mp4").apply {writeText("not a disposable thumbnail")}
        val thumbnail=File(context.cacheDir,"thumbnails/temporary.png").apply {parentFile!!.mkdirs();writeText("thumbnail")}
        try {
            prefs.save(PrivacyChoices(true,true),fake);assertEquals(PrivacyChoices(true,true),PrivacyPreferences(context).load());assertTrue(fake.analytics && fake.crashes)
            prefs.save(PrivacyChoices(),fake);assertFalse(fake.analytics || fake.crashes)
            PrivacyStorage.clearThumbnails(context);assertFalse(thumbnail.exists());assertTrue(media.exists())
        } finally {prefs.save(old,fake);media.delete()}
    }
    @Test fun providerRejectsSensitiveMetadataAndWriteAccessButAllowsMediaReads() {
        val secret=File(context.cacheDir,"privacy-private.json").apply {writeText("private metadata")}
        val media=File(context.cacheDir,"privacy-safe.mp4").apply {writeText("media")}
        try {
            val secretUri=MediaLibraryIndexer.localUri(context,secret);val mediaUri=MediaLibraryIndexer.localUri(context,media)
            assertTrue(runCatching {context.contentResolver.openInputStream(secretUri)?.close()}.isFailure)
            assertTrue(runCatching {context.contentResolver.openOutputStream(mediaUri)?.close()}.isFailure)
            assertEquals("media",context.contentResolver.openInputStream(mediaUri)!!.bufferedReader().use {it.readText()})
        } finally {secret.delete();media.delete()}
    }
}
internal class RecordingAccounts:AccountService {
    override val available=true
    override val profile=MutableStateFlow<AccountProfile?>(null)
    var analytics=false;var crashes=false
    override suspend fun signIn(email:String,password:String,register:Boolean) {profile.value=AccountProfile(email,"")}
    override suspend fun resetPassword(email:String) {}
    override suspend fun saveProfile(name:String) {profile.value=profile.value!!.copy(displayName=name)}
    override suspend fun deleteAccount(password:String) {profile.value=null}
    override fun signOut() {profile.value=null}
    override fun consent(analytics:Boolean,crashes:Boolean) {this.analytics=analytics;this.crashes=crashes}
}
