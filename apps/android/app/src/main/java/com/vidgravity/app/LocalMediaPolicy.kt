package com.vidgravity.app

/** Pure URI validation; Android's resolver still enforces access and actual media decoding. */
object LocalMediaPolicy {
    fun validUri(value:String):Boolean=runCatching {
        val uri=java.net.URI(value)
        value.length<=8192 && uri.scheme=="content" && uri.rawAuthority?.matches(Regex("[A-Za-z0-9._-]+"))==true && uri.userInfo==null && uri.fragment==null && uri.port==-1
    }.getOrDefault(false)
}
