# ai-illustwitter-media-kit
High performance image and video processing library

# images-video-converter

Converting a sequence of images to an MP4 video with customizable FPS, bitrate, and resolution.  

## Installation

```bash
npm install ai-illustwitter-media-kit react-native-nitro-modules


## API
createVideoFromImages(imageUris: string[], outputPath: string, fps: number, bitrate: number, width: number, height: number): Promise<string>
Converts an array of image URIs to an MP4 video.

imageUris: Array of file URIs (e.g., file:///path/to/image.png).
outputPath: File URI for the output MP4.
fps: Frames per second (e.g., 25).
bitrate: Video bitrate in bps (e.g., 3000000 for 3 Mbps).
width: Video width in pixels.
height: Video height in pixels.
Returns: Promise resolving to the output MP4 URI.
 