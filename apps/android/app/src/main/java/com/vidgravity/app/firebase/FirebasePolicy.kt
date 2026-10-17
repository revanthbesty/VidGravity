package com.vidgravity.app.firebase

/** Pure validation shared by client operations; server rules remain authoritative. */
object FirebasePolicy {
    const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024
    fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,80}"))
    fun validDisplayName(name: String) = name.isNotBlank() && name.length <= 80
    fun validUpload(size: Long, type: String) = size in 1..MAX_UPLOAD_BYTES &&
        type in setOf("image/jpeg", "image/png", "image/webp")
}
