/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.dexdeps

import java.util.Arrays

@Suppress("PMD")
class MethodRef(
    private val declClass: String,
    argTypes: Array<String>,
    private val returnType: String,
    private val methodName: String
) {
    private val argTypes: Array<String> = Arrays.copyOf(argTypes, argTypes.size)

    /**
     * Gets the name of the method's declaring class.
     */
    fun getDeclClassName(): String {
        return declClass
    }

    /**
     * Gets the method's descriptor.
     */
    fun getDescriptor(): String {
        return descriptorFromProtoArray(argTypes, returnType)
    }

    /**
     * Gets the method's name.
     */
    fun getName(): String {
        return methodName
    }

    /**
     * Gets an array of method argument types.
     */
    fun getArgumentTypeNames(): List<String> {
        return argTypes.asList()
    }

    /**
     * Gets the method's return type.  Examples: "Ljava/lang/String;", "[I".
     */
    fun getReturnTypeName(): String {
        return returnType
    }

    companion object {
        /**
         * Returns the method descriptor, given the argument and return type
         * prototype strings.
         */
        private fun descriptorFromProtoArray(protos: Array<String>, returnType: String): String {
            val builder = StringBuilder()
            builder.append("(")
            for (proto in protos) {
                builder.append(proto)
            }
            builder.append(")")
            builder.append(returnType)
            return builder.toString()
        }
    }
}

