//
// Created by tomystang on 2022/11/17.
//

#include <jni.h>
#include "RuntimeVerifyMute.h"

extern "C" jboolean JNIEXPORT
Java_com_kernelflux_traceharbor_hook_art_RuntimeVerifyMute_nativeInstall(JNIEnv* env, jobject) {
    return traceharbor::art_misc::Install(env) ? JNI_TRUE : JNI_FALSE;
}