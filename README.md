# ai-illustwitter-media-kit
High performance image and video processing library

# images-video-converter

Converting a sequence of images to an MP4 video with customizable FPS, bitrate, and resolution. Supports saving Skia images for use with `@shopify/react-native-skia`.

## Installation

```bash
npm install ai-illustwitter-media-kit


## API
createVideoFromImages(imageUris: string[], outputPath: string, fps: number, bitrate: number, width: number, height: number): Promise<string>
Converts an array of image URIs to an MP4 video.

imageUris: Array of file URIs (e.g., file:///path/to/image.png).
outputPath: File URI for the output MP4.
fps: Frames per second (e.g., 30).
bitrate: Video bitrate in bps (e.g., 3000000 for 3 Mbps).
width: Video width in pixels.
height: Video height in pixels.
Returns: Promise resolving to the output MP4 URI.
saveSkiaImage(imageData: ArrayBuffer, outputPath: string): Promise<string>
Saves a Skia image (from surface.makeImageSnapshot().encodeToBytes()) as a PNG.

imageData: ArrayBuffer containing PNG-encoded image data.
outputPath: File URI for the output PNG.
Returns: Promise resolving to the output PNG URI.