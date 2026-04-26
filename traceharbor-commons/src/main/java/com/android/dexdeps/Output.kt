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

import java.io.PrintStream

/**
 * Generate fancy output.
 */
@Suppress("PMD")
class Output private constructor() {
    companion object {
        private const val IN0 = ""
        private const val IN1 = "  "
        private const val IN2 = "    "
        private const val IN3 = "      "
        private const val IN4 = "        "

        private val out: PrintStream = System.out

        private fun generateHeader0(fileName: String?, format: String) {
            when (format) {
                "brief" -> if (fileName != null) out.println("File: $fileName")
                "xml" -> {
                    if (fileName != null) {
                        out.println("$IN0<external file=\"$fileName\">")
                    } else {
                        out.println("${IN0}<external>")
                    }
                }
                else -> throw RuntimeException("unknown output format")
            }
        }

        @JvmStatic
        fun generateFirstHeader(fileName: String?, format: String) {
            generateHeader0(fileName, format)
        }

        @JvmStatic
        fun generateHeader(fileName: String?, format: String) {
            out.println()
            generateHeader0(fileName, format)
        }

        @JvmStatic
        fun generateFooter(format: String) {
            when (format) {
                "brief" -> {}
                "xml" -> out.println("</external>")
                else -> throw RuntimeException("unknown output format")
            }
        }

        @JvmStatic
        fun generate(dexData: DexData, format: String, justClasses: Boolean) {
            when (format) {
                "brief" -> printBrief(dexData, justClasses)
                "xml" -> printXml(dexData, justClasses)
                else -> throw RuntimeException("unknown output format")
            }
        }

        /**
         * Prints the data in a simple human-readable format.
         */
        @JvmStatic
        fun printBrief(dexData: DexData, justClasses: Boolean) {
            val externClassRefs = dexData.getExternalReferences()
            printClassRefs(externClassRefs, justClasses)
            if (!justClasses) {
                printFieldRefs(externClassRefs)
                printMethodRefs(externClassRefs)
            }
        }

        /**
         * Prints the list of classes in a simple human-readable format.
         */
        @JvmStatic
        fun printClassRefs(classes: Array<ClassRef>, justClasses: Boolean) {
            if (!justClasses) {
                out.println("Classes:")
            }

            for (ref in classes) {
                out.println(descriptorToDot(ref.getName()))
            }
        }

        /**
         * Prints the list of fields in a simple human-readable format.
         */
        @JvmStatic
        fun printFieldRefs(classes: Array<ClassRef>) {
            out.println("\nFields:")
            for (clazz in classes) {
                for (ref in clazz.getFieldArray()) {
                    out.println(
                        descriptorToDot(ref.getDeclClassName()) + "." + ref.getName() + " : " + ref.getTypeName()
                    )
                }
            }
        }

        /**
         * Prints the list of methods in a simple human-readable format.
         */
        @JvmStatic
        fun printMethodRefs(classes: Array<ClassRef>) {
            out.println("\nMethods:")
            for (clazz in classes) {
                for (ref in clazz.getMethodArray()) {
                    out.println(
                        descriptorToDot(ref.getDeclClassName()) + "." + ref.getName() + " : " + ref.getDescriptor()
                    )
                }
            }
        }

        /**
         * Prints the output in XML format.
         *
         * We shouldn't need to XML-escape the field/method info.
         */
        @JvmStatic
        fun printXml(dexData: DexData, justClasses: Boolean) {
            val externClassRefs = dexData.getExternalReferences()
            var prevPackage: String? = null
            for (cref in externClassRefs) {
                val declClassName = cref.getName()
                val className = classNameOnly(declClassName)
                val packageName = packageNameOnly(declClassName)

                if (packageName != prevPackage) {
                    if (prevPackage != null) {
                        out.println("$IN1</package>")
                    }
                    out.println("$IN1<package name=\"$packageName\">")
                    prevPackage = packageName
                }

                out.println("$IN2<class name=\"$className\">")
                if (!justClasses) {
                    printXmlFields(cref)
                    printXmlMethods(cref)
                }
                out.println("$IN2</class>")
            }

            if (prevPackage != null) {
                out.println("$IN1</package>")
            }
        }

        /**
         * Prints the externally-visible fields in XML format.
         */
        private fun printXmlFields(cref: ClassRef) {
            for (fref in cref.getFieldArray()) {
                out.println(
                    "$IN3<field name=\"" + fref.getName() + "\" type=\"" + descriptorToDot(fref.getTypeName()) + "\"/>"
                )
            }
        }

        /**
         * Prints the externally-visible methods in XML format.
         */
        private fun printXmlMethods(cref: ClassRef) {
            for (mref in cref.getMethodArray()) {
                val declClassName = mref.getDeclClassName()
                val constructor = mref.getName() == "<init>"
                if (constructor) {
                    out.println("$IN3<constructor name=\"" + classNameOnly(declClassName) + "\">")
                } else {
                    out.println(
                        "$IN3<method name=\"" + mref.getName() + "\" return=\"" + descriptorToDot(mref.getReturnTypeName()) + "\">"
                    )
                }
                val args = mref.getArgumentTypeNames()
                for (arg in args) {
                    out.println("$IN4<parameter type=\"" + descriptorToDot(arg) + "\"/>")
                }
                if (constructor) {
                    out.println("$IN3</constructor>")
                } else {
                    out.println("$IN3</method>")
                }
            }
        }

        /**
         * Converts a single-character primitive type into its human-readable
         * equivalent.
         */
        @JvmStatic
        fun primitiveTypeLabel(typeChar: Char): String {
            return when (typeChar) {
                'B' -> "byte"
                'C' -> "char"
                'D' -> "double"
                'F' -> "float"
                'I' -> "int"
                'J' -> "long"
                'S' -> "short"
                'V' -> "void"
                'Z' -> "boolean"
                else -> {
                    System.err.println("Unexpected class char $typeChar")
                    assert(false)
                    "UNKNOWN"
                }
            }
        }

        /**
         * Converts a type descriptor to human-readable "dotted" form.
         */
        @JvmStatic
        fun descriptorToDot(descr: String): String {
            var targetLen = descr.length
            var offset = 0

            while (targetLen > 1 && descr[offset] == '[') {
                offset++
                targetLen--
            }
            var arrayDepth = offset

            val adjustedDescr: String
            if (targetLen == 1) {
                adjustedDescr = primitiveTypeLabel(descr[offset])
                offset = 0
                targetLen = adjustedDescr.length
            } else {
                adjustedDescr = descr
                if (targetLen >= 2 && descr[offset] == 'L' && descr[offset + targetLen - 1] == ';') {
                    targetLen -= 2
                    offset++
                }
            }

            val buf = CharArray(targetLen + arrayDepth * 2)
            var i = 0
            while (i < targetLen) {
                val ch = adjustedDescr[offset + i]
                buf[i] = if (ch == '/') '.' else ch
                i++
            }

            while (arrayDepth-- > 0) {
                buf[i++] = '['
                buf[i++] = ']'
            }
            assert(i == buf.size)
            return String(buf)
        }

        /**
         * Extracts the class name from a type descriptor.
         */
        @JvmStatic
        fun classNameOnly(typeName: String): String {
            val dotted = descriptorToDot(typeName)
            val start = dotted.lastIndexOf(".")
            return if (start < 0) dotted else dotted.substring(start + 1)
        }

        /**
         * Extracts the package name from a type descriptor, and returns it in
         * dotted form.
         */
        @JvmStatic
        fun packageNameOnly(typeName: String): String {
            val dotted = descriptorToDot(typeName)
            val end = dotted.lastIndexOf(".")
            return if (end < 0) "" else dotted.substring(0, end)
        }
    }
}

