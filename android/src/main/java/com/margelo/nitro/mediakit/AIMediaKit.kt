package com.margelo.nitro.mediakit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.ArrayBuffer
import com.margelo.nitro.core.Promise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

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
            withContext(Dispatchers.IO) {
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
                val surface: Surface = encoder.createInputSurface()
                encoder.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var presentationTimeUs = 0L
                val frameDurationUs = (1_000_000 / fps).toLong()

                // Must start muxer after encoder starts
                var formatChanged = false

                imageUris.forEachIndexed { index, uri ->
                    val bitmap = BitmapFactory.decodeFile(uri.replace("file://", ""))
                        ?: throw IllegalStateException("Failed to load image at index $index")

                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width.toInt(), height.toInt(), true)
                    bitmap.recycle()

                    val canvas: Canvas = surface.lockCanvas(null)
                    canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                    surface.unlockCanvasAndPost(canvas)
                    scaledBitmap.recycle()

                    // Give encoder time to produce output
                    while (true) {
                        val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                        if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue
                        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (formatChanged) throw IllegalStateException("Format already changed")
                            val newFormat = encoder.outputFormat
                            muxer.addTrack(newFormat)
                            muxer.start()
                            formatChanged = true
                            continue
                        }

                        if (outputBufferIndex >= 0) {
                            val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                                ?: throw IllegalStateException("Failed to get output buffer")

                            bufferInfo.presentationTimeUs = presentationTimeUs
                            bufferInfo.flags = if (index == imageUris.lastIndex) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0

                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                muxer.writeSampleData(0, outputBuffer, bufferInfo)
                            }

                            encoder.releaseOutputBuffer(outputBufferIndex, false)
                            break
                        }
                    }

                    presentationTimeUs += frameDurationUs
                }

                // Drain remaining encoder output
                var done = false
                while (!done) {
                    val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputBufferIndex >= 0) {
                        val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                            ?: continue
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            done = true
                        }
                        muxer.writeSampleData(0, outputBuffer, bufferInfo)
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                    } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        continue
                    }
                }

                encoder.stop()
                encoder.release()
                muxer.stop()
                muxer.release()

                outputPath
            }
        }
    }

    override fun saveSkiaImage(imageData: ArrayBuffer, outputPath: String): Promise<String> {
        return Promise.async {
            withContext(Dispatchers.IO) {
                val byteArray = imageData.getBuffer()
                val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    ?: throw IllegalStateException("Failed to create bitmap from image data")

                FileOutputStream(outputPath.replace("file://", "")).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
                outputPath
            }
        }
    }
}
