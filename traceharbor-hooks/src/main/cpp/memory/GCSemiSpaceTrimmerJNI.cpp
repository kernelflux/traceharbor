//
// Created by YinSheng Tang on 2021/6/19.
//

#include <jni.h>

#include "GCSemiSpaceTrimmer.h"

extern "C" jboolean JNIEXPORT
Java_com_kernelflux_traceharbor_hook_memory_GCSemiSpaceTrimmer_nativeIsCompatible(JNIEnv*, jobject) {
    return traceharbor::gc_ss_trimmer::IsCompatible();
}

extern "C" jboolean JNIEXPORT
Java_com_kernelflux_traceharbor_hook_memory_GCSemiSpaceTrimmer_nativeInstall(JNIEnv* env, jobject) {
    return traceharbor::gc_ss_trimmer::Install(env);
}