package com.vidgravity.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.*

/** Optional platform-only inference. Never falls back to a network speech recognizer. Main-thread API. */
internal class OfflineDictation(private val context:Context,private val status:(String)->Unit,private val result:(String)->Unit,private val active:(Boolean)->Unit={}) {
    private var recognizer:SpeechRecognizer?=null
    companion object {
        fun available(context:Context):Boolean = Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
    private fun intent()=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
    }
    @androidx.annotation.RequiresApi(31)
    private fun engine():SpeechRecognizer {
        recognizer?.let {return it}
        return SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { speech ->
            recognizer=speech
            speech.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?) {status("Listening in English on this device. Speak a caption, then pause.")}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB:Float) {}
                override fun onBufferReceived(buffer:ByteArray?) {}
                override fun onEndOfSpeech() {status("Processing offline dictation…")}
                override fun onError(error:Int) {active(false); status("Offline dictation unavailable (code $error). Use manual captions or SRT; no cloud fallback was used.")}
                override fun onResults(results:Bundle?) {
                    active(false)
                    val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if(text.isBlank()) status("No caption recognized. Retry or enter text manually.") else {result(text.take(500)); status("Dictated caption added. Review its words and timing.")}
                }
                override fun onPartialResults(partialResults:Bundle?) {}
                override fun onEvent(eventType:Int,params:Bundle?) {}
            })
        }
    }
    fun start() {
        if(Build.VERSION.SDK_INT<31 || !available(context)) {status("On-device speech recognition is unavailable. Manual caption editing and SRT remain available."); return}
        try {active(true); status("Starting offline microphone dictation…"); engine().startListening(intent())}
        catch(failure:Exception) {active(false); status("Offline recognition could not start. Check microphone permission or use manual captions.")}
    }
    /** Explicit user action only. Download size is controlled by the device's speech provider. */
    fun downloadEnglishModel() {
        if(Build.VERSION.SDK_INT<34 || !available(context)) {status("This device cannot report model download progress. Use manual captions."); return}
        try {
            status("Requesting English model · size/storage controlled by your device speech provider.")
            engine().triggerModelDownload(intent(),context.mainExecutor,object:ModelDownloadListener {
                override fun onProgress(completedPercent:Int) {status("English model download · $completedPercent% · provider controls size")}
                override fun onSuccess() {status("English speech model is ready. Tap Dictate English caption.")}
                override fun onScheduled() {status("Model download scheduled by the device provider. Manual captions remain available.")}
                override fun onError(error:Int) {active(false); status("Model download failed (code $error). Manual captions remain available.")}
            })
        } catch(failure:Exception) {status("Model download could not start. No project was changed.")}
    }
    fun close() {active(false); recognizer?.cancel(); recognizer?.destroy(); recognizer=null}
}
