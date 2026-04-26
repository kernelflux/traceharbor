package com.kernelflux.traceharbor.openglleak.detector

import androidx.annotation.Keep
import com.kernelflux.traceharbor.openglleak.comm.FuncNameString

@Keep
class FuncSeeker {
    companion object {
        @JvmStatic
        fun getFuncIndex(targetFuncName: String): Int {
            return when {
                targetFuncName == FuncNameString.GL_GET_ERROR -> getGlGetErrorIndex()
                targetFuncName.startsWith("glGen") || targetFuncName.startsWith("glDelete") -> getTargetFuncIndex(targetFuncName)
                targetFuncName.startsWith("glBind") -> getBindFuncIndex(targetFuncName)
                targetFuncName == "glTexImage2D" -> getGlTexImage2DIndex()
                targetFuncName == "glTexImage3D" -> getGlTexImage3DIndex()
                targetFuncName == "glBufferData" -> getGlBufferDataIndex()
                targetFuncName == "glRenderbufferStorage" -> getGlRenderbufferStorageIndex()
                else -> 0
            }
        }

        @JvmStatic
        private external fun getGlGetErrorIndex(): Int

        @JvmStatic
        private external fun getTargetFuncIndex(targetFuncName: String): Int

        @JvmStatic
        private external fun getBindFuncIndex(bindFuncName: String): Int

        @JvmStatic
        private external fun getGlTexImage2DIndex(): Int

        @JvmStatic
        private external fun getGlTexImage3DIndex(): Int

        @JvmStatic
        private external fun getGlBufferDataIndex(): Int

        @JvmStatic
        private external fun getGlRenderbufferStorageIndex(): Int
    }
}

