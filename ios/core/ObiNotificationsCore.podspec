require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

# Pure-Swift OS integration layer (no Nitro/C++ types) so app code can
# `import ObiNotificationsCore` from a plain-Swift AppDelegate.
# Consumers add it to their Podfile:
#   pod 'ObiNotificationsCore', :path => '../node_modules/@obitrain/react-native-notifications'
Pod::Spec.new do |s|
  s.name         = "ObiNotificationsCore"
  s.version      = package["version"]
  s.summary      = "OS notification integration for @obitrain/react-native-notifications"
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "15.1" }
  s.source       = { :git => "https://github.com/Obitrain/react-native-notifications.git", :tag => "#{s.version}" }

  s.source_files = ["**/*.swift"]
  s.frameworks   = ["UserNotifications", "UIKit"]
  s.swift_version = "5.9"
  s.pod_target_xcconfig = {
    "DEFINES_MODULE" => "YES",
  }
end
