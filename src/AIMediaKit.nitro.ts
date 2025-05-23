import type { HybridObject } from 'react-native-nitro-modules';

export interface AIMediaKit extends HybridObject<{ ios: 'swift', android: 'kotlin' }> {
  createVideoFromImages(
    imageUris: string[],
    outputPath: string,
    fps: number,
    bitrate: number,
    width: number,
    height: number
  ): Promise<string>;
  getRandomNumber(start:number,end:number): number;
  
}