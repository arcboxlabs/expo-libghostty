Pod::Spec.new do |s|
  s.name           = 'ExpoLibghostty'
  s.version        = '0.1.0'
  s.summary        = 'Ghostty terminal view for Expo / React Native'
  s.description    = 'Ghostty terminal view for Expo / React Native, powered by libghostty'
  s.author         = { 'AprilNEA' => 'github@sku.moe' }
  s.homepage       = 'https://github.com/arcboxlabs/expo-libghostty'
  s.license        = { :type => 'MIT' }
  s.platforms      = {
    :ios => '16.4'
  }
  s.source         = { git: 'https://github.com/arcboxlabs/expo-libghostty.git' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.dependency 'GhosttyTerminal'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  # Module sources only — the vendored pods own everything under vendor/.
  s.source_files = '*.{h,m,mm,swift,hpp,cpp}'
end
