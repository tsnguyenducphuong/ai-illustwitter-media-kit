import { NitroModules } from "react-native-nitro-modules";
import type { AIMediaKit as AIMediaKitSpec } from "./AIMediaKit.nitro";

export const AIMediaKit =
	NitroModules.createHybridObject<AIMediaKitSpec>("AIMediaKit");

export const createVideoFromImages = async (
    imageUris: string[],
    outputPath: string,
    fps: number,
    bitrate: number,
    width: number,
    height: number
  ): Promise<string> => {

	try{
		return AIMediaKit.createVideoFromImages(imageUris,outputPath,fps,bitrate,width,height);
	}catch (error){
		throw new Error(error instanceof Error ? error.message : String(error));
	}
  }