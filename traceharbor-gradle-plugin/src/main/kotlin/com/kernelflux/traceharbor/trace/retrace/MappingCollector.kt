/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kernelflux.traceharbor.trace.retrace

import com.kernelflux.traceharbor.javalib.util.Log
import org.objectweb.asm.Type
import java.util.HashMap
import java.util.LinkedHashSet

/**
 * Created by caichongyang on 2017/8/3.
 */
class MappingCollector : MappingProcessor {
    @JvmField
    var mObfuscatedRawClassMap: HashMap<String, String> = HashMap(DEFAULT_CAPACITY)

    @JvmField
    var mRawObfuscatedClassMap: HashMap<String, String> = HashMap(DEFAULT_CAPACITY)

    @JvmField
    var mRawObfuscatedPackageMap: HashMap<String, String> = HashMap(DEFAULT_CAPACITY)

    private val obfuscatedClassMethodMap: MutableMap<String, MutableMap<String, LinkedHashSet<MethodInfo>>> =
        HashMap()
    private val originalClassMethodMap: MutableMap<String, MutableMap<String, LinkedHashSet<MethodInfo>>> =
        HashMap()

    override fun processClassMapping(className: String, newClassName: String): Boolean {
        mObfuscatedRawClassMap[newClassName] = className
        mRawObfuscatedClassMap[className] = newClassName
        val classNameLen = className.lastIndexOf('.')
        val newClassNameLen = newClassName.lastIndexOf('.')
        if (classNameLen > 0 && newClassNameLen > 0) {
            mRawObfuscatedPackageMap[className.substring(0, classNameLen)] =
                newClassName.substring(0, newClassNameLen)
        } else {
            Log.e(TAG, "class without package name: %s -> %s, pls check input mapping", className, newClassName)
        }
        return true
    }

    override fun processMethodMapping(
        className: String,
        methodReturnType: String,
        methodName: String,
        methodArguments: String,
        newClassName: String,
        newMethodName: String
    ) {
        val obfuscatedClassName = mRawObfuscatedClassMap[className] ?: newClassName
        val methodMap = obfuscatedClassMethodMap.getOrPut(obfuscatedClassName) { HashMap() }
        val methodSet = methodMap.getOrPut(newMethodName) { LinkedHashSet() }
        methodSet.add(MethodInfo(className, methodReturnType, methodName, methodArguments))

        val originalMethodMap = originalClassMethodMap.getOrPut(className) { HashMap() }
        val originalMethodSet = originalMethodMap.getOrPut(methodName) { LinkedHashSet() }
        originalMethodSet.add(MethodInfo(obfuscatedClassName, methodReturnType, newMethodName, methodArguments))
    }

    fun originalClassName(proguardClassName: String?, defaultClassName: String): String {
        if (proguardClassName != null && mObfuscatedRawClassMap.containsKey(proguardClassName)) {
            return mObfuscatedRawClassMap[proguardClassName].orEmpty()
        }
        return defaultClassName
    }

    fun proguardClassName(originalClassName: String?, defaultClassName: String): String {
        if (originalClassName != null && mRawObfuscatedClassMap.containsKey(originalClassName)) {
            return mRawObfuscatedClassMap[originalClassName].orEmpty()
        }
        return defaultClassName
    }

    fun proguardPackageName(originalPackage: String?, defaultPackage: String): String {
        if (originalPackage != null && mRawObfuscatedPackageMap.containsKey(originalPackage)) {
            return mRawObfuscatedPackageMap[originalPackage].orEmpty()
        }
        return defaultPackage
    }

    /**
     * get original method info
     */
    fun originalMethodInfo(
        obfuscatedClassName: String?,
        obfuscatedMethodName: String?,
        obfuscatedMethodDesc: String?
    ): MethodInfo {
        val descInfo = parseMethodDesc(obfuscatedMethodDesc, false)

        // obfuscated name -> original method names.
        val methodMap = obfuscatedClassMethodMap[obfuscatedClassName]
        if (methodMap != null && obfuscatedMethodName != null) {
            val methodSet = methodMap[obfuscatedMethodName]
            if (methodSet != null) {
                // Find all matching methods.
                for (methodInfo in methodSet) {
                    if (methodInfo.matches(descInfo.returnType, descInfo.arguments)) {
                        val newMethodInfo = MethodInfo(methodInfo)
                        newMethodInfo.setDesc(descInfo.desc)
                        return newMethodInfo
                    }
                }
            }
        }

        val defaultMethodInfo = MethodInfo.deFault()
        defaultMethodInfo.setDesc(descInfo.desc)
        defaultMethodInfo.setOriginalName(obfuscatedMethodName ?: "")
        return defaultMethodInfo
    }

    /**
     * get obfuscated method info
     */
    fun obfuscatedMethodInfo(
        originalClassName: String?,
        originalMethodName: String?,
        originalMethodDesc: String?
    ): MethodInfo {
        val descInfo = parseMethodDesc(originalMethodDesc, true)

        // Class name -> obfuscated method names.
        val methodMap = originalClassMethodMap[originalClassName]
        if (methodMap != null && originalMethodName != null) {
            val methodSet = methodMap[originalMethodName]
            if (methodSet != null) {
                // Find all matching methods.
                for (methodInfo in methodSet) {
                    val newMethodInfo = MethodInfo(methodInfo)
                    obfuscatedMethodInfo(newMethodInfo)
                    if (newMethodInfo.matches(descInfo.returnType, descInfo.arguments)) {
                        newMethodInfo.setDesc(descInfo.desc)
                        return newMethodInfo
                    }
                }
            }
        }
        val defaultMethodInfo = MethodInfo.deFault()
        defaultMethodInfo.setDesc(descInfo.desc)
        defaultMethodInfo.setOriginalName(originalMethodName ?: "")
        return defaultMethodInfo
    }

    private fun obfuscatedMethodInfo(methodInfo: MethodInfo) {
        val methodArguments = methodInfo.getOriginalArguments()
        val args = methodArguments.split(",")
        val stringBuffer = StringBuilder()
        for (str in args) {
            val key = str.replace("[", "").replace("]", "")
            if (mRawObfuscatedClassMap.containsKey(key)) {
                stringBuffer.append(str.replace(key, mRawObfuscatedClassMap[key].orEmpty()))
            } else {
                stringBuffer.append(str)
            }
            stringBuffer.append(',')
        }
        if (stringBuffer.isNotEmpty()) {
            stringBuffer.deleteCharAt(stringBuffer.length - 1)
        }
        var methodReturnType = methodInfo.getOriginalType()
        val key = methodReturnType.replace("[", "").replace("]", "")
        if (mRawObfuscatedClassMap.containsKey(key)) {
            methodReturnType = methodReturnType.replace(key, mRawObfuscatedClassMap[key].orEmpty())
        }
        methodInfo.setOriginalArguments(stringBuffer.toString())
        methodInfo.setOriginalType(methodReturnType)
    }

    /**
     * parse method desc
     */
    private fun parseMethodDesc(desc: String?, isRawToObfuscated: Boolean): DescInfo {
        val targetDesc = desc ?: "()V"
        val descInfo = DescInfo()
        val argsObj = Type.getArgumentTypes(targetDesc)
        val argumentsBuffer = StringBuilder()
        val descBuffer = StringBuilder()
        descBuffer.append('(')
        for (type in argsObj) {
            val key = type.className.replace("[", "").replace("]", "")
            if (isRawToObfuscated) {
                if (mRawObfuscatedClassMap.containsKey(key)) {
                    argumentsBuffer.append(type.className.replace(key, mRawObfuscatedClassMap[key].orEmpty()))
                    descBuffer.append(type.toString().replace(key, mRawObfuscatedClassMap[key].orEmpty()))
                } else {
                    argumentsBuffer.append(type.className)
                    descBuffer.append(type.toString())
                }
            } else {
                if (mObfuscatedRawClassMap.containsKey(key)) {
                    argumentsBuffer.append(type.className.replace(key, mObfuscatedRawClassMap[key].orEmpty()))
                    descBuffer.append(type.toString().replace(key, mObfuscatedRawClassMap[key].orEmpty()))
                } else {
                    argumentsBuffer.append(type.className)
                    descBuffer.append(type.toString())
                }
            }
            argumentsBuffer.append(',')
        }
        descBuffer.append(')')

        val returnObj: Type = try {
            Type.getReturnType(targetDesc)
        } catch (e: ArrayIndexOutOfBoundsException) {
            Type.getReturnType("$targetDesc;")
        }
        if (isRawToObfuscated) {
            val key = returnObj.className.replace("[", "").replace("]", "")
            if (mRawObfuscatedClassMap.containsKey(key)) {
                descInfo.returnType = returnObj.className.replace(key, mRawObfuscatedClassMap[key].orEmpty())
                descBuffer.append(returnObj.toString().replace(key, mRawObfuscatedClassMap[key].orEmpty()))
            } else {
                descInfo.returnType = returnObj.className
                descBuffer.append(returnObj.toString())
            }
        } else {
            val key = returnObj.className.replace("[", "").replace("]", "")
            if (mObfuscatedRawClassMap.containsKey(key)) {
                descInfo.returnType = returnObj.className.replace(key, mObfuscatedRawClassMap[key].orEmpty())
                descBuffer.append(returnObj.toString().replace(key, mObfuscatedRawClassMap[key].orEmpty()))
            } else {
                descInfo.returnType = returnObj.className
                descBuffer.append(returnObj.toString())
            }
        }

        // delete last ,
        if (argumentsBuffer.isNotEmpty()) {
            argumentsBuffer.deleteCharAt(argumentsBuffer.length - 1)
        }
        descInfo.arguments = argumentsBuffer.toString()
        descInfo.desc = descBuffer.toString()
        return descInfo
    }

    /**
     * about method desc info
     */
    private class DescInfo {
        var desc: String? = null
        var arguments: String? = null
        var returnType: String? = null
    }

    companion object {
        private const val TAG = "MappingCollector"
        private const val DEFAULT_CAPACITY = 2000
    }
}

