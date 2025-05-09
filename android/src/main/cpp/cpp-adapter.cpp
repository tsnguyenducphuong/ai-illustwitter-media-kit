#include <jni.h>
#include "AIMediaKitOnLoad.hpp"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return margelo::nitro::mediakit::initialize(vm);
}