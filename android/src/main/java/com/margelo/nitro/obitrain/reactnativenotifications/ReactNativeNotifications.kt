package com.margelo.nitro.obitrain.reactnativenotifications
  
import com.facebook.proguard.annotations.DoNotStrip

@DoNotStrip
class ReactNativeNotifications : HybridReactNativeNotificationsSpec() {
  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }
}
