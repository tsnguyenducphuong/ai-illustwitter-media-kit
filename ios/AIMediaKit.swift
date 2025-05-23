import Foundation
import AVFoundation

import Dispatch
import NitroModules // Import Promise from NitroModules core
 
enum RandomDoubleError: Error {
    case invalidRange
}
class AIMediaKit: HybridAIMediaKitSpec {
    func generateRandomDouble(from start: Double, to end: Double) throws -> Double {
        guard start <= end else {
            throw RandomDoubleError.invalidRange
        }
        return Double.random(in: start...end)
    }

    func createVideoFromImages(
        imageUris: [String],
        outputPath: String,
        fps: Double,
        bitrate: Double,
        width: Double,
        height: Double
    ) throws -> Promise<String> {
        return Promise.async {
            // Validate inputs
            guard !imageUris.isEmpty else {
                throw NSError(domain: "AIMediaKit", code: -1, userInfo: [NSLocalizedDescriptionKey: "No image provided."])
            }
            guard let outputUrl = URL(string: outputPath) ?? URL(fileURLWithPath: outputPath) else {
                throw NSError(domain: "AIMediaKit", code: -2, userInfo: [NSLocalizedDescriptionKey: "Invalid output path."])
            }
            guard fps > 0, bitrate > 0, width > 0, height > 0 else {
                throw  NSError(domain: "AIMediaKit", code: -3, userInfo: [NSLocalizedDescriptionKey: "Invalid parameters."])
            } 
           
                do {
                    let writer = try AVAssetWriter(outputURL: outputUrl, fileType: .mp4)
                    let videoSettings: [String: Any] = [
                        AVVideoCodecKey: AVVideoCodecType.h264,
                        AVVideoWidthKey: NSNumber(value: width),
                        AVVideoHeightKey: NSNumber(value: height),
                        AVVideoCompressionPropertiesKey: [
                            AVVideoAverageBitRateKey: NSNumber(value: Int(bitrate))
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
                        guard let imageUrl = URL(string: imageUri) ?? URL(fileURLWithPath: imageUri) else {
                            throw NSError(domain: "AIMediaKit", code: -4, userInfo: [NSLocalizedDescriptionKey: "Invalid image URL."])
                        }
                        let imageData = try Data(contentsOf: imageUrl)
                        guard let image = UIImage(data: imageData) else {
                            throw NSError(domain: "AIMediaKit", code: -5, userInfo: [NSLocalizedDescriptionKey: "Invalid image."])
                        }

                        // Scale image to target size
                        let scaledImage = image.scaleToSize(CGSize(width: width, height: height))

                        guard let pixelBufferPool = pixelBufferAdaptor.pixelBufferPool,
                              let pixelBuffer = self.createPixelBuffer(from: scaledImage, width: Int(width), height: Int(height)) else {
                           
                            throw NSError(domain: "AIMediaKit", code: -6, userInfo: [NSLocalizedDescriptionKey: "Can't create pixel buffer."])
                        }

                        let presentationTime = CMTimeMultiply(frameDuration, multiplier: Int32(index))
                        while !writerInput.isReadyForMoreMediaData {
                            usleep(10000) // 10ms sleep instead of Thread.sleep
                        }
                        pixelBufferAdaptor.append(pixelBuffer, withPresentationTime: presentationTime)
                        frameCount += 1
                    }

                    writerInput.markAsFinished()
                    try await withCheckedThrowingContinuation { continuation in
                         writer.finishWriting {
                            if writer.status == .completed {
                                continuation.resume()
                            } else {
                                continuation.resume(throwing: writer.error ?? NSError(domain: "AIMediaKit", code: -1000))
                            }
                        }
                    }

                    return outputUrl.path

                } catch {
                     throw NSError(domain: "AIMediaKit", code: -100, userInfo: [NSLocalizedDescriptionKey: error.localizedDescription])
                }
            
        }
    }

     

    // Helper to convert UIImage to CVPixelBuffer
    private func createPixelBuffer(from image: UIImage, width: Int, height: Int) -> CVPixelBuffer? {
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
        ),
              let cgImage = image.cgImage else {
            return nil
        }

        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        return buffer
    }
}

// Extension to scale UIImage
extension UIImage {
    func scaleToSize(_ size: CGSize) -> UIImage {
        UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
        defer { UIGraphicsEndImageContext() }
        draw(in: CGRect(origin: .zero, size: size))
        return UIGraphicsGetImageFromCurrentImageContext() ?? self
    }
}