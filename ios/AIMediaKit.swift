import Foundation
import AVFoundation
import UIKit
import margelo

class AIMediaKit: HybridAIMediaKitSpec {
  var hybridContext = margelo.nitro.HybridContext()
  var memorySize: Int { return getSizeOf(self) }

  func createVideoFromImages(
    imageUris: [String],
    outputPath: String,
    fps: Double,
    bitrate: Double,
    width: Double,
    height: Double
  ) throws -> Promise<String> {
    return Promise { resolve, reject in
      // Validate inputs
      guard !imageUris.isEmpty else {
        throw NSError(domain: "AIMediaKit", code: -1, userInfo: [NSLocalizedDescriptionKey: "No images provided"])
      }
      guard let outputUrl = URL(string: outputPath) else {
        throw NSError(domain: "AIMediaKit", code: -2, userInfo: [NSLocalizedDescriptionKey: "Invalid output path"])
      }
      guard fps > 0, bitrate > 0, width > 0, height > 0 else {
        throw NSError(domain: "AIMediaKit", code: -3, userInfo: [NSLocalizedDescriptionKey: "Invalid FPS, bitrate, or resolution"])
      }

      // Set up AVAssetWriter
      do {
        let writer = try AVAssetWriter(outputURL: outputUrl, fileType: .mp4)
        let videoSettings: [String: Any] = [
          AVVideoCodecKey: AVVideoCodecType.h264,
          AVVideoWidthKey: width,
          AVVideoHeightKey: height,
          AVVideoCompressionPropertiesKey: [
            AVVideoAverageBitRateKey: bitrate
          ]
        ]
        let writerInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
        let pixelBufferAdaptor = AVAssetWriterInputPixelBufferAdaptor(
          assetWriterInput: writerInput,
          sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB
          ]
        )
        writer.add(writerInput)

        // Start writing
        writer.startWriting()
        writer.startSession(atSourceTime: .zero)

        let frameDuration = CMTime(value: 1, timescale: Int32(fps))
        var frameCount = 0

        for (index, imageUri) in imageUris.enumerated() {
          guard let imageUrl = URL(string: imageUri),
                let imageData = try? Data(contentsOf: imageUrl),
                let image = UIImage(data: imageData) else {
            reject(NSError(domain: "AIMediaKit", code: -4, userInfo: [NSLocalizedDescriptionKey: "Failed to load image at index \(index)"]))
            return
          }

          guard let pixelBufferPool = pixelBufferAdaptor.pixelBufferPool,
                let pixelBuffer = createPixelBuffer(from: image, width: Int(width), height: Int(height)) else {
            reject(NSError(domain: "AIMediaKit", code: -5, userInfo: [NSLocalizedDescriptionKey: "Failed to create pixel buffer"]))
            return
          }

          let presentationTime = CMTimeMultiply(frameDuration, multiplier: Int32(index))
          while !writerInput.isReadyForMoreMediaData {
            Thread.sleep(forTimeInterval: 0.01)
          }
          pixelBufferAdaptor.append(pixelBuffer, withPresentationTime: presentationTime)
          frameCount += 1
        }

        writerInput.markAsFinished()
        writer.finishWriting {
          if writer.status == .completed {
            resolve(outputPath)
          } else {
            reject(writer.error ?? NSError(domain: "AIMediaKit", code: -6, userInfo: [NSLocalizedDescriptionKey: "Failed to write video"]))
          }
        }
      } catch {
        reject(error)
      }
    }
  }

  func saveSkiaImage(imageData: margelo.nitro.ArrayBuffer, outputPath: String) throws -> Promise<String> {
    return Promise { resolve, reject in
      guard let outputUrl = URL(string: outputPath) else {
        throw NSError(domain: "AIMediaKit", code: -7, userInfo: [NSLocalizedDescriptionKey: "Invalid output path"])
      }

      // Convert ArrayBuffer to Data
      let data = Data(buffer: imageData)
      guard let image = UIImage(data: data) else {
        reject(NSError(domain: "AIMediaKit", code: -8, userInfo: [NSLocalizedDescriptionKey: "Failed to create image from data"]))
        return
      }

      // Save as PNG
      do {
        try image.pngData()?.write(to: outputUrl)
        resolve(outputPath)
      } catch {
        reject(error)
      }
    }
  }
}

// Helper to convert UIImage to CVPixelBuffer
func createPixelBuffer(from image: UIImage, width: Int, height: Int) -> CVPixelBuffer? {
  var pixelBuffer: CVPixelBuffer?
  let attrs = [
    kCVPixelBufferCGImageCompatibilityKey: kCFBooleanTrue!,
    kCVPixelBufferCGBitmapContextCompatibilityKey: kCFBooleanTrue!
  ] as CFDictionary
  let status = CVPixelBufferCreate(kCFAllocatorDefault, width, height, kCVPixelFormatType_32ARGB, attrs, &pixelBuffer)
  guard status == kCVReturnSuccess, let buffer = pixelBuffer else { return nil }

  CVPixelBufferLockBaseAddress(buffer, [])
  defer { CVPixelBufferUnlockBaseAddress(buffer, []) }

  guard let context = CGContext(
    data: CVPixelBufferGetBaseAddress(buffer),
    width: width,
    height: height,
    bitsPerComponent: 8,
    bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
    space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue
  ) else { return nil }

  context.draw(image.cgImage!, in: CGRect(x: 0, y: 0, width: width, height: height))
  return buffer
}