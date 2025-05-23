package com.margelo.nitro.mediakit

import kotlin.random.Random
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.*
import android.view.Surface
import com.margelo.nitro.core.Promise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.IllegalStateException
import androidx.annotation.Keep
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.mediakit.HybridAIMediaKitSpec

@Keep
@DoNotStrip
class AIMediaKit : HybridAIMediaKitSpec() {
    companion object {
        const val TAG = "HybridAIMediaKit"
    }

    override fun createVideoFromImages(
        imageUris: Array<String>,
        outputPath: String,
        fps: Double,
        bitrate: Double,
        width: Double,
        height: Double
    ): Promise<String> {
        return Promise.async {
           // withContext(Dispatchers.IO) {
                if (imageUris.isEmpty()) throw IllegalArgumentException("No images provided")
                if (fps <= 0 || bitrate <= 0 || width <= 0 || height <= 0) {
                    throw IllegalArgumentException("Invalid FPS, bitrate, or resolution")
                }

                var encoder: MediaCodec? = null
                var muxer: MediaMuxer? = null

                try {
                    val videoFormat = MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_AVC,
                        width.toInt(),
                        height.toInt()
                    ).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, bitrate.toInt())
                        setInteger(MediaFormat.KEY_FRAME_RATE, fps.toInt())
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    }

                    encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    val surface: Surface = encoder.createInputSurface()
                    encoder.start()

                    muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                    val bufferInfo = MediaCodec.BufferInfo()
                    var presentationTimeUs = 0L
                    val frameDurationUs = (1_000_000 / fps).toLong()
                    var formatChanged = false
                    var trackIndex = -1

                    imageUris.forEachIndexed { index, uri ->
                        val filePath = uri.removePrefix("file://")
                        val file = File(filePath)

                        if (!file.exists()) {
                            throw IllegalStateException("Image not found at: $filePath")
                        }

                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            ?: throw IllegalStateException("Failed to decode image at index $index")

                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width.toInt(), height.toInt(), true)
                        bitmap.recycle()

                        val canvas = surface.lockCanvas(null)
                        canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                        surface.unlockCanvasAndPost(canvas)
                        scaledBitmap.recycle()

                        var outputReceived = false
                        while (!outputReceived) {
                            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)

                            when {
                                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> continue
                                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                    if (formatChanged) {
                                        throw IllegalStateException("Output format already changed")
                                    }
                                    val newFormat = encoder.outputFormat
                                    trackIndex = muxer.addTrack(newFormat)
                                    muxer.start()
                                    formatChanged = true
                                }
                                outputBufferIndex >= 0 -> {
                                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                                        ?: throw IllegalStateException("Failed to get output buffer")

                                    bufferInfo.presentationTimeUs = presentationTimeUs
                                    bufferInfo.flags = if (index == imageUris.lastIndex)
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0

                                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                    }

                                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                                    outputReceived = true
                                }
                            }
                        }

                        presentationTimeUs += frameDurationUs
                    }

                    // Drain remaining output
                    var done = false
                    while (!done) {
                        val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                        when {
                            outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> continue
                            outputBufferIndex >= 0 -> {
                                val outputBuffer = encoder.getOutputBuffer(outputBufferIndex) ?: continue
                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    done = true
                                }
                                muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                encoder.releaseOutputBuffer(outputBufferIndex, false)
                            }
                        }
                    }

                    outputPath
                } catch (e: Exception) {
                    throw e
                } finally {
                    try {
                        encoder?.stop()
                        encoder?.release()
                    } catch (_: Exception) {}
                    try {
                        muxer?.stop()
                        muxer?.release()
                    } catch (_: Exception) {}
                }
            //}
        }
    }

    override fun generateRandomDouble(start: Double, end: Double): Double {
        return Random.nextDouble(start, end)
    }

}
