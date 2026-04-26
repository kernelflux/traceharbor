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

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Data extracted from a DEX file.
 */
@Suppress("PMD")
class DexData(raf: RandomAccessFile) {
    private var dexFile: RandomAccessFile = raf
    private lateinit var headerItem: HeaderItem
    private lateinit var strings: Array<String>
    private lateinit var typeIds: Array<TypeIdItem>
    private lateinit var protoIds: Array<ProtoIdItem>
    private lateinit var fieldIds: Array<FieldIdItem>
    private lateinit var methodIds: Array<MethodIdItem>
    private lateinit var classDefs: Array<ClassDefItem>

    private val tmpBuf = ByteArray(4)
    private var isBigEndian = false

    /**
     * Loads the contents of the DEX file into our data structures.
     *
     * @throws IOException if we encounter a problem while reading
     * @throws DexDataException if the DEX contents look bad
     */
    @Throws(IOException::class)
    fun load() {
        parseHeaderItem()
        loadStrings()
        loadTypeIds()
        loadProtoIds()
        loadFieldIds()
        loadMethodIds()
        loadClassDefs()
        markInternalClasses()
    }

    /**
     * Parses the interesting bits out of the header.
     */
    @Throws(IOException::class)
    fun parseHeaderItem() {
        headerItem = HeaderItem()
        seek(0)

        val magic = ByteArray(8)
        readBytes(magic)
        if (!verifyMagic(magic)) {
            System.err.println("Magic number is wrong -- are you sure this is a DEX file?")
            throw DexDataException()
        }

        seek(8 + 4 + 20 + 4 + 4)
        headerItem.endianTag = readInt()
        if (headerItem.endianTag == HeaderItem.ENDIAN_CONSTANT) {
            // little-endian; expected path
        } else if (headerItem.endianTag == HeaderItem.REVERSE_ENDIAN_CONSTANT) {
            isBigEndian = true
        } else {
            System.err.println(
                "Endian constant has unexpected value " + Integer.toHexString(headerItem.endianTag)
            )
            throw DexDataException()
        }

        seek(8 + 4 + 20)
        headerItem.fileSize = readInt()
        headerItem.headerSize = readInt()
        readInt() // endianTag
        readInt() // linkSize
        readInt() // linkOff
        readInt() // mapOff
        headerItem.stringIdsSize = readInt()
        headerItem.stringIdsOff = readInt()
        headerItem.typeIdsSize = readInt()
        headerItem.typeIdsOff = readInt()
        headerItem.protoIdsSize = readInt()
        headerItem.protoIdsOff = readInt()
        headerItem.fieldIdsSize = readInt()
        headerItem.fieldIdsOff = readInt()
        headerItem.methodIdsSize = readInt()
        headerItem.methodIdsOff = readInt()
        headerItem.classDefsSize = readInt()
        headerItem.classDefsOff = readInt()
        readInt() // dataSize
        readInt() // dataOff
    }

    /**
     * Loads the string table out of the DEX.
     */
    @Throws(IOException::class)
    fun loadStrings() {
        val count = headerItem.stringIdsSize
        val stringOffsets = IntArray(count)

        seek(headerItem.stringIdsOff)
        for (i in 0 until count) {
            stringOffsets[i] = readInt()
        }

        strings = Array(count) { "" }
        for (i in 0 until count) {
            seek(stringOffsets[i])
            strings[i] = readString()
        }
    }

    /**
     * Loads the type ID list.
     */
    @Throws(IOException::class)
    fun loadTypeIds() {
        val count = headerItem.typeIdsSize
        typeIds = Array(count) { TypeIdItem() }
        seek(headerItem.typeIdsOff)
        for (i in 0 until count) {
            typeIds[i].descriptorIdx = readInt()
        }
    }

    /**
     * Loads the proto ID list.
     */
    @Throws(IOException::class)
    fun loadProtoIds() {
        val count = headerItem.protoIdsSize
        protoIds = Array(count) { ProtoIdItem() }
        seek(headerItem.protoIdsOff)

        for (i in 0 until count) {
            val protoId = ProtoIdItem()
            protoId.shortyIdx = readInt()
            protoId.returnTypeIdx = readInt()
            protoId.parametersOff = readInt()
            protoIds[i] = protoId
        }

        for (i in 0 until count) {
            val protoId = protoIds[i]
            val offset = protoId.parametersOff
            if (offset == 0) {
                protoId.types = IntArray(0)
            } else {
                seek(offset)
                val size = readInt()
                val types = IntArray(size)
                for (j in 0 until size) {
                    types[j] = readShort().toInt() and 0xffff
                }
                protoId.types = types
            }
        }
    }

    /**
     * Loads the field ID list.
     */
    @Throws(IOException::class)
    fun loadFieldIds() {
        val count = headerItem.fieldIdsSize
        fieldIds = Array(count) { FieldIdItem() }
        seek(headerItem.fieldIdsOff)
        for (i in 0 until count) {
            val field = FieldIdItem()
            field.classIdx = readShort().toInt() and 0xffff
            field.typeIdx = readShort().toInt() and 0xffff
            field.nameIdx = readInt()
            fieldIds[i] = field
        }
    }

    /**
     * Loads the method ID list.
     */
    @Throws(IOException::class)
    fun loadMethodIds() {
        val count = headerItem.methodIdsSize
        methodIds = Array(count) { MethodIdItem() }
        seek(headerItem.methodIdsOff)
        for (i in 0 until count) {
            val method = MethodIdItem()
            method.classIdx = readShort().toInt() and 0xffff
            method.protoIdx = readShort().toInt() and 0xffff
            method.nameIdx = readInt()
            methodIds[i] = method
        }
    }

    /**
     * Loads the class defs list.
     */
    @Throws(IOException::class)
    fun loadClassDefs() {
        val count = headerItem.classDefsSize
        classDefs = Array(count) { ClassDefItem() }
        seek(headerItem.classDefsOff)
        for (i in 0 until count) {
            val classDef = ClassDefItem()
            classDef.classIdx = readInt()
            readInt() // access_flags
            readInt() // superclass_idx
            readInt() // interfaces_off
            readInt() // source_file_idx
            readInt() // annotations_off
            readInt() // class_data_off
            readInt() // static_values_off
            classDefs[i] = classDef
        }
    }

    /**
     * Sets the "internal" flag on type IDs which are defined in the
     * DEX file or within the VM (e.g. primitive classes and arrays).
     */
    fun markInternalClasses() {
        for (i in classDefs.indices.reversed()) {
            typeIds[classDefs[i].classIdx].internal = true
        }

        for (i in typeIds.indices) {
            val className = strings[typeIds[i].descriptorIdx]
            if (className.length == 1 || className[0] == '[') {
                typeIds[i].internal = true
            }
        }
    }

    /**
     * Returns the class name, given an index into the type_ids table.
     */
    private fun classNameFromTypeIndex(idx: Int): String {
        return strings[typeIds[idx].descriptorIdx]
    }

    /**
     * Returns an array of method argument type strings.
     */
    private fun argArrayFromProtoIndex(idx: Int): Array<String> {
        val protoId = protoIds[idx]
        val result = Array(protoId.types.size) { "" }
        for (i in protoId.types.indices) {
            result[i] = strings[typeIds[protoId.types[i]].descriptorIdx]
        }
        return result
    }

    /**
     * Returns a string representing the method's return type.
     */
    private fun returnTypeFromProtoIndex(idx: Int): String {
        val protoId = protoIds[idx]
        return strings[typeIds[protoId.returnTypeIdx].descriptorIdx]
    }

    /**
     * Returns all external class references.
     */
    fun getExternalReferences(): Array<ClassRef> {
        val sparseRefs = arrayOfNulls<ClassRef>(typeIds.size)
        var count = 0
        for (i in typeIds.indices) {
            if (!typeIds[i].internal) {
                sparseRefs[i] = ClassRef(strings[typeIds[i].descriptorIdx])
                count++
            }
        }

        addExternalFieldReferences(sparseRefs)
        addExternalMethodReferences(sparseRefs)

        val classRefs = Array(count) { ClassRef("") }
        var idx = 0
        for (i in typeIds.indices) {
            val ref = sparseRefs[i]
            if (ref != null) {
                classRefs[idx++] = ref
            }
        }
        assert(idx == count)
        return classRefs
    }

    /**
     * Runs through the list of field references, inserting external references.
     */
    private fun addExternalFieldReferences(sparseRefs: Array<ClassRef?>) {
        for (fieldId in fieldIds) {
            if (!typeIds[fieldId.classIdx].internal) {
                val newFieldRef = FieldRef(
                    classNameFromTypeIndex(fieldId.classIdx),
                    classNameFromTypeIndex(fieldId.typeIdx),
                    strings[fieldId.nameIdx]
                )
                sparseRefs[fieldId.classIdx]!!.addField(newFieldRef)
            }
        }
    }

    /**
     * Runs through the list of method references, inserting external references.
     */
    private fun addExternalMethodReferences(sparseRefs: Array<ClassRef?>) {
        for (methodId in methodIds) {
            if (!typeIds[methodId.classIdx].internal) {
                val newMethodRef = MethodRef(
                    classNameFromTypeIndex(methodId.classIdx),
                    argArrayFromProtoIndex(methodId.protoIdx),
                    returnTypeFromProtoIndex(methodId.protoIdx),
                    strings[methodId.nameIdx]
                )
                sparseRefs[methodId.classIdx]!!.addMethod(newMethodRef)
            }
        }
    }

    /**
     * Returns all internal class references.
     */
    fun getInternalReferences(): Array<ClassRef> {
        val sparseRefs = arrayOfNulls<ClassRef>(typeIds.size)
        var count = 0
        for (i in typeIds.indices) {
            if (typeIds[i].internal) {
                sparseRefs[i] = ClassRef(strings[typeIds[i].descriptorIdx])
                count++
            }
        }

        addInternalFieldReferences(sparseRefs)
        addInternalMethodReferences(sparseRefs)

        val classRefs = Array(count) { ClassRef("") }
        var idx = 0
        for (i in typeIds.indices) {
            val ref = sparseRefs[i]
            if (ref != null) {
                classRefs[idx++] = ref
            }
        }
        assert(idx == count)
        return classRefs
    }

    /**
     * Runs through the list of field references, inserting internal references.
     */
    private fun addInternalFieldReferences(sparseRefs: Array<ClassRef?>) {
        for (fieldId in fieldIds) {
            if (typeIds[fieldId.classIdx].internal) {
                val newFieldRef = FieldRef(
                    classNameFromTypeIndex(fieldId.classIdx),
                    classNameFromTypeIndex(fieldId.typeIdx),
                    strings[fieldId.nameIdx]
                )
                sparseRefs[fieldId.classIdx]!!.addField(newFieldRef)
            }
        }
    }

    /**
     * Runs through the list of method references, inserting internal references.
     */
    private fun addInternalMethodReferences(sparseRefs: Array<ClassRef?>) {
        for (methodId in methodIds) {
            if (typeIds[methodId.classIdx].internal) {
                val newMethodRef = MethodRef(
                    classNameFromTypeIndex(methodId.classIdx),
                    argArrayFromProtoIndex(methodId.protoIdx),
                    returnTypeFromProtoIndex(methodId.protoIdx),
                    strings[methodId.nameIdx]
                )
                sparseRefs[methodId.classIdx]!!.addMethod(newMethodRef)
            }
        }
    }

    /**
     * Returns the list of all method references.
     */
    fun getMethodRefs(): Array<MethodRef> {
        val refs = Array(methodIds.size) { MethodRef("", emptyArray(), "", "") }
        for (i in methodIds.indices) {
            val methodId = methodIds[i]
            refs[i] = MethodRef(
                classNameFromTypeIndex(methodId.classIdx),
                argArrayFromProtoIndex(methodId.protoIdx),
                returnTypeFromProtoIndex(methodId.protoIdx),
                strings[methodId.nameIdx]
            )
        }
        return refs
    }

    /**
     * Returns the list of all field references.
     */
    fun getFieldRefs(): Array<FieldRef> {
        val refs = Array(fieldIds.size) { FieldRef("", "", "") }
        for (i in fieldIds.indices) {
            val fieldId = fieldIds[i]
            refs[i] = FieldRef(
                classNameFromTypeIndex(fieldId.classIdx),
                classNameFromTypeIndex(fieldId.typeIdx),
                strings[fieldId.nameIdx]
            )
        }
        return refs
    }

    /**
     * Seeks the DEX file to the specified absolute position.
     */
    @Throws(IOException::class)
    fun seek(position: Int) {
        dexFile.seek(position.toLong())
    }

    /**
     * Fills the buffer by reading bytes from the DEX file.
     */
    @Throws(IOException::class)
    fun readBytes(buffer: ByteArray) {
        dexFile.readFully(buffer)
    }

    /**
     * Reads a single signed byte value.
     */
    @Throws(IOException::class)
    fun readByte(): Byte {
        dexFile.readFully(tmpBuf, 0, 1)
        return tmpBuf[0]
    }

    /**
     * Reads a signed 16-bit integer, byte-swapping if necessary.
     */
    @Throws(IOException::class)
    fun readShort(): Short {
        dexFile.readFully(tmpBuf, 0, 2)
        return if (isBigEndian) {
            ((tmpBuf[1].toInt() and 0xff) or ((tmpBuf[0].toInt() and 0xff) shl 8)).toShort()
        } else {
            ((tmpBuf[0].toInt() and 0xff) or ((tmpBuf[1].toInt() and 0xff) shl 8)).toShort()
        }
    }

    /**
     * Reads a signed 32-bit integer, byte-swapping if necessary.
     */
    @Throws(IOException::class)
    fun readInt(): Int {
        dexFile.readFully(tmpBuf, 0, 4)
        return if (isBigEndian) {
            (tmpBuf[3].toInt() and 0xff) or
                ((tmpBuf[2].toInt() and 0xff) shl 8) or
                ((tmpBuf[1].toInt() and 0xff) shl 16) or
                ((tmpBuf[0].toInt() and 0xff) shl 24)
        } else {
            (tmpBuf[0].toInt() and 0xff) or
                ((tmpBuf[1].toInt() and 0xff) shl 8) or
                ((tmpBuf[2].toInt() and 0xff) shl 16) or
                ((tmpBuf[3].toInt() and 0xff) shl 24)
        }
    }

    /**
     * Reads a variable-length unsigned LEB128 value.
     */
    @Throws(IOException::class)
    fun readUnsignedLeb128(): Int {
        var result = 0
        var value: Byte
        do {
            value = readByte()
            result = (result shl 7) or (value.toInt() and 0x7f)
        } while (value.toInt() < 0)
        return result
    }

    /**
     * Reads a UTF-8 string.
     */
    @Throws(IOException::class)
    fun readString(): String {
        val utf16Len = readUnsignedLeb128()
        val inBuf = ByteArray(utf16Len * 3)
        var idx = 0
        while (idx < inBuf.size) {
            val value = readByte()
            if (value.toInt() == 0) {
                break
            }
            inBuf[idx++] = value
        }
        return String(inBuf, 0, idx, StandardCharsets.UTF_8)
    }

    /**
     * Holds the contents of a header_item.
     */
    class HeaderItem {
        var fileSize = 0
        var headerSize = 0
        var endianTag = 0
        var stringIdsSize = 0
        var stringIdsOff = 0
        var typeIdsSize = 0
        var typeIdsOff = 0
        var protoIdsSize = 0
        var protoIdsOff = 0
        var fieldIdsSize = 0
        var fieldIdsOff = 0
        var methodIdsSize = 0
        var methodIdsOff = 0
        var classDefsSize = 0
        var classDefsOff = 0

        companion object {
            val DEX_FILE_MAGIC_v035: ByteArray = "dex\n035\u0000".toByteArray(StandardCharsets.US_ASCII)
            val DEX_FILE_MAGIC_v037: ByteArray = "dex\n037\u0000".toByteArray(StandardCharsets.US_ASCII)
            val DEX_FILE_MAGIC_v038: ByteArray = "dex\n038\u0000".toByteArray(StandardCharsets.US_ASCII)
            val DEX_FILE_MAGIC_v039: ByteArray = "dex\n039\u0000".toByteArray(StandardCharsets.US_ASCII)
            val DEX_FILE_MAGIC_v040: ByteArray = "dex\n040\u0000".toByteArray(StandardCharsets.US_ASCII)

            const val ENDIAN_CONSTANT = 0x12345678
            const val REVERSE_ENDIAN_CONSTANT = 0x78563412
        }
    }

    /**
     * Holds the contents of a type_id_item.
     */
    class TypeIdItem {
        var descriptorIdx = 0
        var internal = false
    }

    /**
     * Holds the contents of a proto_id_item.
     */
    class ProtoIdItem {
        var shortyIdx = 0
        var returnTypeIdx = 0
        var parametersOff = 0
        var types: IntArray = IntArray(0)
    }

    /**
     * Holds the contents of a field_id_item.
     */
    class FieldIdItem {
        var classIdx = 0
        var typeIdx = 0
        var nameIdx = 0
    }

    /**
     * Holds the contents of a method_id_item.
     */
    class MethodIdItem {
        var classIdx = 0
        var protoIdx = 0
        var nameIdx = 0
    }

    /**
     * Holds the contents of a class_def_item.
     */
    class ClassDefItem {
        var classIdx = 0
    }

    companion object {
        /**
         * Verifies the given magic number.
         */
        private fun verifyMagic(magic: ByteArray): Boolean {
            return Arrays.equals(magic, HeaderItem.DEX_FILE_MAGIC_v035) ||
                Arrays.equals(magic, HeaderItem.DEX_FILE_MAGIC_v037) ||
                Arrays.equals(magic, HeaderItem.DEX_FILE_MAGIC_v038) ||
                Arrays.equals(magic, HeaderItem.DEX_FILE_MAGIC_v039) ||
                Arrays.equals(magic, HeaderItem.DEX_FILE_MAGIC_v040)
        }
    }
}

