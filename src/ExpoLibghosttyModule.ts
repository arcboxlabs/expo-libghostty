import { NativeModule, requireNativeModule } from 'expo';

declare class ExpoLibghosttyModule extends NativeModule<{}> {}

export default requireNativeModule<ExpoLibghosttyModule>('ExpoLibghostty');
