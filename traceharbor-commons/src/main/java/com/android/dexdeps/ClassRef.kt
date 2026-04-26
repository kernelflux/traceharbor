/*
 * Copyright (C) 2010 The Android Open Source Project
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

@Suppress("PMD")
class ClassRef(private val className: String) {
    private val fieldRefs = ArrayList<FieldRef>()
    private val methodRefs = ArrayList<MethodRef>()

    /**
     * Adds the field to the field list.
     */
    fun addField(fref: FieldRef) {
        fieldRefs.add(fref)
    }

    /**
     * Returns the field list as an array.
     */
    fun getFieldArray(): Array<FieldRef> {
        return fieldRefs.toTypedArray()
    }

    /**
     * Adds the method to the method list.
     */
    fun addMethod(mref: MethodRef) {
        methodRefs.add(mref)
    }

    /**
     * Returns the method list as an array.
     */
    fun getMethodArray(): Array<MethodRef> {
        return methodRefs.toTypedArray()
    }

    /**
     * Gets the class name.
     */
    fun getName(): String {
        return className
    }
}

