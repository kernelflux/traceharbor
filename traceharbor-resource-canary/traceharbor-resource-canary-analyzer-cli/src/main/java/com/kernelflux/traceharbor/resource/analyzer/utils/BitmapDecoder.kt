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
package com.kernelflux.traceharbor.resource.analyzer.utils

import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.HashMap

/**
 * Created by tangyinsheng on 2017/7/6.
 *
 * This class is ported from Android Studio tools.
 */
object BitmapDecoder {
    const val BITMAP_FQCN: String = "android.graphics.Bitmap"
    const val BITMAP_DRAWABLE_FQCN: String = "android.graphics.drawable.BitmapDrawable"

    interface BitmapDataProvider {
        fun getBitmapConfigName(): String?
        fun getDimension(): Dimension?

        // Downsizes the bitmap, in-place, to the newSize.
        fun downsizeBitmap(newSize: Dimension): Boolean
        fun getPixelBytes(size: Dimension): ByteArray
    }

    private interface BitmapExtractor {
        fun getImage(w: Int, h: Int, data: ByteArray): BufferedImage
    }

    private val SUPPORTED_FORMATS: MutableMap<String, BitmapExtractor> = HashMap()

    /**
     * Maximum height or width of image beyond which we scale it on the device before retrieving.
     */
    private const val MAX_DIMENSION = 1024

    init {
        SUPPORTED_FORMATS["\"ARGB_8888\""] = ARGB8888BitmapExtractor()
        SUPPORTED_FORMATS["\"RGB_565\""] = RGB565BitmapExtractor()
        SUPPORTED_FORMATS["\"ALPHA_8\""] = ALPHA8BitmapExtractor()
    }

    @JvmStatic
    fun getBitmap(dataProvider: BitmapDataProvider): BufferedImage {
        val config = dataProvider.getBitmapConfigName()
            ?: throw RuntimeException("Unable to determine bitmap configuration")

        val bitmapExtractor = SUPPORTED_FORMATS[config]
            ?: throw RuntimeException("Unsupported bitmap configuration: $config")

        var size = dataProvider.getDimension()
            ?: throw RuntimeException("Unable to determine image dimensions.")

        // if the image is rather large, then scale it down
        if (size.width > MAX_DIMENSION || size.height > MAX_DIMENSION) {
            val couldDownsize = dataProvider.downsizeBitmap(size)
            if (!couldDownsize) {
                throw RuntimeException("Unable to create scaled bitmap")
            }

            size = dataProvider.getDimension()
                ?: throw RuntimeException("Unable to obtained scaled bitmap's dimensions")
        }

        return bitmapExtractor.getImage(size.width, size.height, dataProvider.getPixelBytes(size))
    }

    private class ARGB8888BitmapExtractor : BitmapExtractor {
        override fun getImage(width: Int, height: Int, rgba: ByteArray): BufferedImage {
            @Suppress("UndesirableClassUsage")
            val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

            for (y in 0 until height) {
                val stride = y * width
                for (x in 0 until width) {
                    val i = (stride + x) * 4
                    var rgb = 0L
                    rgb = rgb or ((rgba[i].toLong() and 0xff) shl 16) // r
                    rgb = rgb or ((rgba[i + 1].toLong() and 0xff) shl 8) // g
                    rgb = rgb or (rgba[i + 2].toLong() and 0xff) // b
                    rgb = rgb or ((rgba[i + 3].toLong() and 0xff) shl 24) // a
                    bufferedImage.setRGB(x, y, (rgb and 0xffffffffL).toInt())
                }
            }

            return bufferedImage
        }
    }

    private class RGB565BitmapExtractor : BitmapExtractor {
        override fun getImage(width: Int, height: Int, rgb: ByteArray): BufferedImage {
            val bytesPerPixel = 2

            @Suppress("UndesirableClassUsage")
            val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

            for (y in 0 until height) {
                val stride = y * width
                for (x in 0 until width) {
                    val index = (stride + x) * bytesPerPixel
                    val value =
                        (rgb[index].toInt() and 0x00ff) or ((rgb[index + 1].toInt() shl 8) and 0xff00)
                    // RGB565 to RGB888
                    // Multiply by 255/31 to convert from 5 bits (31 max) to 8 bits (255)
                    val r = ((value ushr 11) and 0x1f) * 255 / 31
                    val g = ((value ushr 5) and 0x3f) * 255 / 63
                    val b = (value and 0x1f) * 255 / 31
                    val a = 0xFF
                    val rgba = (a shl 24) or (r shl 16) or (g shl 8) or b
                    bufferedImage.setRGB(x, y, rgba)
                }
            }

            return bufferedImage
        }
    }

    private class ALPHA8BitmapExtractor : BitmapExtractor {
        override fun getImage(width: Int, height: Int, rgb: ByteArray): BufferedImage {
            @Suppress("UndesirableClassUsage")
            val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

            for (y in 0 until height) {
                val stride = y * width
                for (x in 0 until width) {
                    val index = stride + x
                    val value = rgb[index].toInt()
                    val rgba = (value shl 24) or (0xff shl 16) or (0xff shl 8) or 0xff
                    bufferedImage.setRGB(x, y, rgba)
                }
            }

            return bufferedImage
        }
    }
}
