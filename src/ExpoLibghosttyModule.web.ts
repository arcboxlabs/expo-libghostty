import { registerWebModule, NativeModule } from 'expo';

// ExpoLibghosttyModule is not available on the web platform.
class ExpoLibghosttyModule extends NativeModule<{}> {}

export default registerWebModule(ExpoLibghosttyModule, 'ExpoLibghosttyModule');
