package aimediakit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.margelo.nitro.NitroModules
import com.margelo.nitro.ArrayBuffer
import com.margelo.nitro.Promise
import java.io.FileOutputStream

class AIMediaKit : HybridAIMediaKitSpec() {
  companion object {
    const val TAG = "HybridAIMediaKit"
  }

  override val memorySize: Long get() = 0L

  override fun createVideoFromImages(
    imageUris: Array<String>,
    outputPath: String,
    fps: Double,
    bitrate: Double,
    width: Double,
    height: Double
  ): Promise<String> {
    return Promise { resolve, reject ->
      try {
        if (imageUris.isEmpty()) {
          throw Exception("No images provided")
        }
        if (fps <= 0 || bitrate <= 0 || width <= 0 || height <= 0) {
          throw Exception("Invalid FPS, bitrate, or resolution")
        }

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width.toInt(), height.toInt()).apply {
          setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
          setInteger(MediaFormat.KEY_BIT_RATE, bitrate.toInt())
          setInteger(MediaFormat.KEY_FRAME_RATE, fps.toInt())
          setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()

        val trackIndex = muxer.addTrack(videoFormat)
        muxer.start()

        val frameDurationUs = (1_000_000 / fps).toLong()
        var presentationTimeUs = 0L
        val bufferInfo = MediaCodec.BufferInfo()

        imageUris.forEachIndexed { index, uri ->
          val bitmap = BitmapFactory.decodeFile(uri.replace("file://", ""))
          if (bitmap == null) {
            throw Exception("Failed to load image at index $index")
          }

          val canvas = surface.lockCanvas(null)
          canvas.drawBitmap(bitmap, 0f, 0f, null)
          surface.unlockCanvasAndPost(canvas)
          bitmap.recycle()

          while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputBufferIndex >= 0) {
              val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
              if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                muxer.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
              }
              encoder.releaseOutputBuffer(outputBufferIndex, false)
              break
            }
          }

          presentationTimeUs += frameDurationUs
          bufferInfo.presentationTimeUs = presentationTimeUs
        }

        encoder.signalEndOfInputStream()
        while (true) {
          val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
          if (outputBufferIndex >= 0) {
            val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
              break
            }
            muxer.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
            encoder.releaseOutputBuffer(outputBufferIndex, false)
          }
        }

        encoder.stop()
        encoder.release()
        muxer.stop()
        muxer.release()
        resolve(outputPath)
      } catch (e: Exception) {
        reject(e)
      }
    }
  }

  override fun saveSkiaImage(imageData: ArrayBuffer, outputPath: String): Promise<String> {
    return Promise { resolve, reject ->
      try {
        val bitmap = BitmapFactory.decodeByte waters(imageData.toByteArray())
        if (bitmap == null) {
          throw Exception("Failed to create bitmap from image data")
        }

        FileOutputStream(outputPath.replace("file://", "")).use { out ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        resolve(outputPath)
      } catch (e: Exception) {
        reject(e)
      }
    }
  }
}