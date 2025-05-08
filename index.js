import { NitroModules } from 'react-native-nitro-modules';
import { AIMediaKit as AIMediaKitType } from './src/AIMediaKit.nitro';

export const AIMediaKit = NitroModules.createHybridObject<AIMediaKitType>('AIMediaKit');