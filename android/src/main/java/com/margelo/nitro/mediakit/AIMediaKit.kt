package com.margelo.nitro.mediakit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.ArrayBuffer
import com.margelo.nitro.core.Promise
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

 
class AIMediaKit : HybridAIMediaKitSpec() {
    companion object {
        const val TAG = "HybridAIMediaKit"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun createVideoFromImages(
        imageUris: Array<String>,
        outputPath: String,
        fps: Double,
        bitrate: Double,
        width: Double,
        height: Double
    ): Promise<String> {
        return Promise.async {
            coroutineScope.launch {
                try {
                    if (imageUris.isEmpty()) {
                        throw IllegalArgumentException("No images provided")
                    }
                    if (fps <= 0 || bitrate <= 0 || width <= 0 || height <= 0) {
                        throw IllegalArgumentException("Invalid FPS, bitrate, or resolution")
                    }

                    val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
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

                    val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    val surface = encoder.createInputSurface()
                    encoder.start()

                    val trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()

                    val frameDurationUs = (1_000_000 / fps).toLong()
                    var presentationTimeUs = 0L
                    val bufferInfo = MediaCodec.BufferInfo()

                    imageUris.forEachIndexed { index, uri ->
                        val bitmap = BitmapFactory.decodeFile(uri.replace("file://", ""))
                            ?: throw IllegalStateException("Failed to load image at index $index")

                        // Scale bitmap to match output resolution
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            width.toInt(),
                            height.toInt(),
                            true
                        )
                        bitmap.recycle()

                        val canvas: Canvas = surface.lockCanvas(null)
                        canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                        surface.unlockCanvasAndPost(canvas)
                        scaledBitmap.recycle()

                        while (true) {
                            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                            if (outputBufferIndex >= 0) {
                                val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                                    ?: continue
                                bufferInfo.set(
                                    0,
                                    outputBuffer.remaining(),
                                    presentationTimeUs,
                                    if (index == imageUris.size - 1) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                                )
                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                }
                                encoder.releaseOutputBuffer(outputBufferIndex, false)
                                break
                            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                // Handle format change if needed
                            }
                        }

                        presentationTimeUs += frameDurationUs
                    }

                    // Drain remaining buffers
                    while (true) {
                        val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                        if (outputBufferIndex >= 0) {
                            val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                                ?: continue
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                encoder.releaseOutputBuffer(outputBufferIndex, false)
                                break
                            }
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                            encoder.releaseOutputBuffer(outputBufferIndex, false)
                        }
                    }

                    encoder.stop()
                    encoder.release()
                    muxer.stop()
                    muxer.release()
                    outputPath
                } catch (e: Exception) {
                    throw e
                }
            }
        }
    }

    override fun saveSkiaImage(imageData: ArrayBuffer, outputPath: String): Promise<String> {
        return Promise.async {
            coroutineScope.launch {
                try {
                    val byteArray = imageData.getBuffer()
                    val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                        ?: throw IllegalStateException("Failed to create bitmap from image data")

                    FileOutputStream(outputPath.replace("file://", "")).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()
                    outputPath
                } catch (e: Exception) {
                    throw e
                }
            }
        }
    }
}