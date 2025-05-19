import { NitroModules } from "react-native-nitro-modules";
import type { AIMediaKit as AIMediaKitSpec } from "./AIMediaKit.nitro";

export const AIMediaKit =
	NitroModules.createHybridObject<AIMediaKitSpec>("AIMediaKit");