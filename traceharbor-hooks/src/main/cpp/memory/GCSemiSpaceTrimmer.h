//
// Created by YinSheng Tang on 2021/6/19.
//

#ifndef TRACEHARBOR_ANDROID_GCSEMISPACETRIMMER_H
#define TRACEHARBOR_ANDROID_GCSEMISPACETRIMMER_H


#include <jni.h>

namespace traceharbor {
    namespace gc_ss_trimmer {
        extern bool IsCompatible();
        extern bool Install(JNIEnv *env);
    }
}


#endif //TRACEHARBOR_ANDROID_GCSEMISPACETRIMMER_H
