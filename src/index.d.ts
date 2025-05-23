declare module "ai-illustwitter-media-kit" {
    export function createVideoFromImages(
    imageUris: string[],
    outputPath: string,
    fps: number,
    bitrate: number,
    width: number,
    height: number
  ): Promise<string>;

  export function getRandomNumber(start:number,end:number): number;
}