package com.haishinkit.haishin_kit

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaFormat.KEY_LEVEL
import android.media.MediaFormat.KEY_PROFILE
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.WindowManager
import com.haishinkit.codec.CodecOption
import com.haishinkit.rtmp.event.Event
import com.haishinkit.rtmp.event.IEventListener
import com.haishinkit.haishinkit.ProfileLevel
import com.haishinkit.media.source.AudioRecordSource
import com.haishinkit.media.source.Camera2Source
import com.haishinkit.rtmp.RtmpStream
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import com.haishinkit.media.MediaMixer

class RtmpStreamHandler(
    private val plugin: HaishinKitPlugin, handler: RtmpConnectionHandler?
) : MethodChannel.MethodCallHandler, IEventListener, EventChannel.StreamHandler {
    private companion object {
        const val TAG = "RtmpStream"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaMixer: MediaMixer? = null
    
    private var instance: RtmpStream? = null
        set(value) {
            field?.close()
            field = value
        }
    private var channel: EventChannel
    private var eventSink: EventChannel.EventSink? = null
        set(value) {
            field?.endOfStream()
            field = value
        }
    private var camera: Camera2Source? = null
        set(value) {
            field = value
        }

    init {
        handler?.instance?.let {
            instance = RtmpStream(plugin.flutterPluginBinding.applicationContext, it)
            instance?.addEventListener(Event.RTMP_STATUS, this)
            mediaMixer = MediaMixer(plugin.flutterPluginBinding.applicationContext)
            scope.launch {
                mediaMixer?.registerOutput(instance!!)
            }
        }
        channel = EventChannel(
            plugin.flutterPluginBinding.binaryMessenger, "com.haishinkit.eventchannel/${hashCode()}"
        )
        channel.setStreamHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "$TAG#getHasAudio" -> {
                result.success(true)
            }

            "$TAG#setHasAudio" -> {
                val value = call.argument<Boolean?>("value")
                result.success(null)
            }

            "$TAG#getHasVideo" -> {
                result.success(null)
            }

            "$TAG#setHasVideo" -> {
                result.success(null)
            }

            "$TAG#setFrameRate" -> {
                val value = call.argument<Int?>("value")
                result.success(null)
            }

            "$TAG#setSessionPreset" -> {
                // for iOS
                result.success(null)
            }

            "$TAG#setAudioSettings" -> {
                val source = call.argument<Map<String, Any?>>("settings") ?: return
                result.success(null)
            }

            "$TAG#setVideoSettings" -> {
                val source = call.argument<Map<String, Any?>>("settings") ?: return
                result.success(null)
            }

            "$TAG#setScreenSettigns" -> {
                val source = call.argument<Map<String, Any?>>("settings") ?: return
                result.success(null)
            }

            "$TAG#attachAudio" -> {
                val source = call.argument<Map<String, Any?>>("source")
                if (source == null) {
                    scope.launch {
                        mediaMixer?.attachAudio(0, null)
                    }
                } else {
                    val audioRecordSource = AudioRecordSource(plugin.flutterPluginBinding.applicationContext)
                    scope.launch {
                        mediaMixer?.attachAudio(0, audioRecordSource)
                    }
                }
                result.success(null)
            }

            "$TAG#attachVideo" -> {
                val source = call.argument<Map<String, Any?>>("source")
                if (source == null) {
                    scope.launch {
                        mediaMixer?.attachVideo(0, null)
                    }
                    camera = null
                } else {
                    var facing = 0
                    when (source["position"]) {
                        "front" -> {
                            facing = CameraCharacteristics.LENS_FACING_FRONT
                        }
                        "back" -> {
                            facing = CameraCharacteristics.LENS_FACING_BACK
                        }
                    }
                    camera = Camera2Source(plugin.flutterPluginBinding.applicationContext)
                    camera?.let { cameraSource ->
                        scope.launch {
                            mediaMixer?.attachVideo(0, cameraSource)
                        }
                    }
                }
                result.success(null)
            }

            "$TAG#registerTexture" -> {
                val netStream = instance
                val texture = StreamViewTexture(plugin.flutterPluginBinding)
                texture.attachStream(netStream)
                result.success(texture.id)
            }

            "$TAG#unregisterTexture" -> {
                result.success(null)
            }

            "$TAG#updateTextureSize" -> {
                val width = call.argument<Double>("width") ?: 0
                val height = call.argument<Double>("height") ?: 0
                result.success(null)
            }

            "$TAG#publish" -> {
                val name = call.argument<String>("name")
                instance?.publish(name)
                result.success(null)
            }

            "$TAG#play" -> {
                val name = call.argument<String>("name")
                if (name != null) {
                    instance?.play(name)
                }
                result.success(null)
            }

            "$TAG#close" -> {
                instance?.close()
                result.success(null)
            }

            "$TAG#dispose" -> {
                eventSink = null
                camera = null
                scope.cancel()
                mediaMixer = null
                instance = null
                plugin.onDispose(hashCode())
                result.success(null)
            }
        }
    }

    override fun handleEvent(event: Event) {
        val map = HashMap<String, Any?>()
        map["type"] = event.type
        map["data"] = event.data
        plugin.uiThreadHandler.post {
            eventSink?.success(map)
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
    }
}
