require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "ReactNativeNotifications"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/Obitrain/react-native-notifications.git", :tag => "#{s.version}" }

  s.source_files = [
    "ios/nitro/**/*.{swift}",
    "ios/nitro/**/*.{m,mm}",
    "cpp/**/*.{hpp,cpp}",
  ]

  s.dependency 'React-jsi'
  s.dependency 'React-callinvoker'
  # pure-Swift OS layer; app AppDelegates import this one
  s.dependency 'ObiNotificationsCore'

  load 'nitrogen/generated/ios/ReactNativeNotifications+autolinking.rb'
  add_nitrogen_files(s)

  install_modules_dependencies(s)
end
