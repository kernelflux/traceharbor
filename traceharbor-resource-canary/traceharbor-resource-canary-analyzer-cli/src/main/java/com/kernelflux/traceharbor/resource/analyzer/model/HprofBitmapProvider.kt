/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kernelflux.traceharbor.resource.analyzer.model

import com.kernelflux.traceharbor.resource.analyzer.utils.BitmapDecoder
import java.awt.Dimension

class HprofBitmapProvider(
    private val mBuffer: ByteArray,
    private val mWidth: Int,
    private val mHeight: Int,
) : BitmapDecoder.BitmapDataProvider {

    override fun getBitmapConfigName(): String? {
        val area = mWidth * mHeight
        val pixelSize = mBuffer.size / area

        if (area > mBuffer.size) {
            return null
        }

        return when (pixelSize) {
            4 -> "\"ARGB_8888\""
            2 -> "\"RGB_565\""
            else -> "\"ALPHA_8\""
        }
    }

    override fun getDimension(): Dimension? {
        return if (mWidth < 0 || mHeight < 0) null else Dimension(mWidth, mHeight)
    }

    override fun downsizeBitmap(newSize: Dimension): Boolean {
        return true
    }

    override fun getPixelBytes(size: Dimension): ByteArray {
        return mBuffer
    }
}
