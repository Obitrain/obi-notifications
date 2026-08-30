#include <jni.h>
#include "obitrain_reactnativenotificationsOnLoad.hpp"

#include <fbjni/fbjni.h>


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return facebook::jni::initialize(vm, []() {
    margelo::nitro::obitrain_reactnativenotifications::registerAllNatives();
  });
}