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

package com.kernelflux.traceharbor.trace

import com.kernelflux.traceharbor.javalib.util.FileUtil
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import com.kernelflux.traceharbor.plugin.compat.AgpCompat
import com.kernelflux.traceharbor.trace.item.TraceMethod
import com.kernelflux.traceharbor.trace.retrace.MappingCollector
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter
import org.objectweb.asm.util.CheckClassAdapter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import java.util.Enumeration
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Created by caichongyang on 2017/6/4.
 *
 * This class hooks all collected methods in oder to trace method in/out.
 */
class MethodTracer(
    private val executor: ExecutorService,
    private val mappingCollector: MappingCollector,
    private val configuration: Configuration,
    private val collectedMethodMap: ConcurrentHashMap<String, TraceMethod>,
    private val collectedClassExtendMap: ConcurrentHashMap<String, String>
) {
    @Volatile
    private var traceError = false

    @Throws(ExecutionException::class, InterruptedException::class)
    fun trace(
        srcFolderList: Map<File, File>?,
        dependencyJarList: Map<File, File>?,
        classLoader: ClassLoader,
        ignoreCheckClass: Boolean
    ) {
        val futures = LinkedList<Future<*>>()
        traceMethodFromSrc(srcFolderList, futures, classLoader, ignoreCheckClass)
        traceMethodFromJar(dependencyJarList, futures, classLoader, ignoreCheckClass)
        for (future in futures) {
            future.get()
        }
        if (traceError) {
            throw IllegalArgumentException("something wrong with trace, see detail log before")
        }
        futures.clear()
    }

    private fun traceMethodFromSrc(
        srcMap: Map<File, File>?,
        futures: MutableList<Future<*>>,
        classLoader: ClassLoader,
        skipCheckClass: Boolean
    ) {
        if (srcMap != null) {
            for ((key, value) in srcMap) {
                futures.add(
                    executor.submit {
                        innerTraceMethodFromSrc(
                            key,
                            value,
                            classLoader,
                            skipCheckClass
                        )
                    }
                )
            }
        }
    }

    private fun traceMethodFromJar(
        dependencyMap: Map<File, File>?,
        futures: MutableList<Future<*>>,
        classLoader: ClassLoader,
        skipCheckClass: Boolean
    ) {
        if (dependencyMap != null) {
            for ((key, value) in dependencyMap) {
                futures.add(
                    executor.submit {
                        innerTraceMethodFromJar(
                            key,
                            value,
                            classLoader,
                            skipCheckClass
                        )
                    }
                )
            }
        }
    }

    private fun innerTraceMethodFromSrc(input: File, output: File, classLoader: ClassLoader, ignoreCheckClass: Boolean) {
        val classFileList = ArrayList<File>()
        if (input.isDirectory) {
            listClassFiles(classFileList, input)
        } else {
            classFileList.add(input)
        }

        for (classFile in classFileList) {
            var `is`: InputStream? = null
            var os: FileOutputStream? = null
            try {
                val changedFileInputFullPath = classFile.absolutePath
                val changedFileOutput =
                    File(changedFileInputFullPath.replace(input.absolutePath, output.absolutePath))

                if (changedFileOutput.canonicalPath == classFile.canonicalPath) {
                    throw RuntimeException("Input file(" + classFile.canonicalPath + ") should not be same with output!")
                }

                if (!changedFileOutput.exists()) {
                    changedFileOutput.parentFile.mkdirs()
                }
                changedFileOutput.createNewFile()

                if (MethodCollector.isNeedTraceFile(classFile.name)) {
                    `is` = FileInputStream(classFile)
                    val classReader = ClassReader(`is`)
                    val classWriter = TraceClassWriter(ClassWriter.COMPUTE_FRAMES, classLoader)
                    val classVisitor: ClassVisitor = TraceClassAdapter(AgpCompat.asmApi, classWriter)
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                    `is`?.close()

                    val data = classWriter.toByteArray()

                    if (!ignoreCheckClass) {
                        try {
                            val cr = ClassReader(data)
                            val cw = ClassWriter(0)
                            val check: ClassVisitor = CheckClassAdapter(cw)
                            cr.accept(check, ClassReader.EXPAND_FRAMES)
                        } catch (e: Throwable) {
                            System.err.println("trace output ERROR : ${e.message}, $classFile")
                            traceError = true
                        }
                    }

                    os = if (output.isDirectory) {
                        FileOutputStream(changedFileOutput)
                    } else {
                        FileOutputStream(output)
                    }
                    os.write(data)
                    os.close()
                } else {
                    FileUtil.copyFileUsingStream(classFile, changedFileOutput)
                }
            } catch (e: Exception) {
                Log.e(TAG, "[innerTraceMethodFromSrc] input:%s e:%s", input.name, e.message)
                try {
                    Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (e1: Exception) {
                    e1.printStackTrace()
                }
            } finally {
                try {
                    `is`?.close()
                    os?.close()
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun innerTraceMethodFromJar(input: File, output: File, classLoader: ClassLoader, skipCheckClass: Boolean) {
        var zipOutputStream: ZipOutputStream? = null
        var zipFile: ZipFile? = null
        try {
            zipOutputStream = ZipOutputStream(FileOutputStream(output))
            zipFile = ZipFile(input)
            val enumeration: Enumeration<out ZipEntry> = zipFile.entries()
            while (enumeration.hasMoreElements()) {
                val zipEntry = enumeration.nextElement()
                val zipEntryName = zipEntry.name

                if (Util.preventZipSlip(output, zipEntryName)) {
                    Log.e(TAG, "Unzip entry %s failed!", zipEntryName)
                    continue
                }

                if (MethodCollector.isNeedTraceFile(zipEntryName)) {
                    val inputStream = zipFile.getInputStream(zipEntry)
                    val classReader = ClassReader(inputStream)
                    val classWriter = TraceClassWriter(ClassWriter.COMPUTE_FRAMES, classLoader)
                    val classVisitor: ClassVisitor = TraceClassAdapter(AgpCompat.asmApi, classWriter)
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                    val data = classWriter.toByteArray()
                    if (!skipCheckClass) {
                        try {
                            val r = ClassReader(data)
                            val w = ClassWriter(0)
                            val v: ClassVisitor = CheckClassAdapter(w)
                            r.accept(v, ClassReader.EXPAND_FRAMES)
                        } catch (e: Throwable) {
                            System.err.println("trace jar output ERROR: ${e.message}, $zipEntryName")
                            traceError = true
                        }
                    }

                    val byteArrayInputStream = ByteArrayInputStream(data)
                    val newZipEntry = ZipEntry(zipEntryName)
                    FileUtil.addZipEntry(zipOutputStream, newZipEntry, byteArrayInputStream)
                } else {
                    val inputStream = zipFile.getInputStream(zipEntry)
                    val newZipEntry = ZipEntry(zipEntryName)
                    FileUtil.addZipEntry(zipOutputStream, newZipEntry, inputStream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[innerTraceMethodFromJar] input:%s output:%s e:%s", input, output, e.message)
            if (e is ZipException) {
                e.printStackTrace()
            }
            try {
                if (input.length() > 0) {
                    Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } else {
                    Log.e(TAG, "[innerTraceMethodFromJar] input:%s is empty", input)
                }
            } catch (e1: Exception) {
                e1.printStackTrace()
            }
        } finally {
            try {
                zipOutputStream?.finish()
                zipOutputStream?.flush()
                zipOutputStream?.close()
                zipFile?.close()
            } catch (_: Exception) {
                Log.e(TAG, "close stream err!")
            }
        }
    }

    private fun listClassFiles(classFiles: ArrayList<File>, folder: File) {
        val files = folder.listFiles()
        if (files == null) {
            Log.e(TAG, "[listClassFiles] files is null! %s", folder.absolutePath)
            return
        }
        for (file in files) {
            if (file == null) {
                continue
            }
            if (file.isDirectory) {
                listClassFiles(classFiles, file)
            } else if (file.isFile) {
                classFiles.add(file)
            }
        }
    }

    private inner class TraceClassAdapter(i: Int, classVisitor: ClassVisitor) : ClassVisitor(i, classVisitor) {
        private var className: String? = null
        private var superName: String? = null
        private var isABSClass = false
        private var hasWindowFocusMethod = false
        private var isActivityOrSubClass = false
        private var isNeedTrace = false

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            super.visit(version, access, name, signature, superName, interfaces)
            className = name
            this.superName = superName
            isActivityOrSubClass = isActivityOrSubClass(className, collectedClassExtendMap)
            isNeedTrace = MethodCollector.isNeedTrace(configuration, className, mappingCollector)
            if ((access and Opcodes.ACC_ABSTRACT) > 0 || (access and Opcodes.ACC_INTERFACE) > 0) {
                isABSClass = true
            }
        }

        override fun visitMethod(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            if (!hasWindowFocusMethod) {
                hasWindowFocusMethod = MethodCollector.isWindowFocusChangeMethod(name, desc)
            }
            return if (isABSClass) {
                super.visitMethod(access, name, desc, signature, exceptions)
            } else {
                val methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions)
                TraceMethodAdapter(
                    api,
                    methodVisitor,
                    access,
                    name,
                    desc,
                    className.orEmpty(),
                    hasWindowFocusMethod,
                    isActivityOrSubClass,
                    isNeedTrace
                )
            }
        }

        override fun visitEnd() {
            if (!hasWindowFocusMethod && isActivityOrSubClass && isNeedTrace) {
                insertWindowFocusChangeMethod(cv, className.orEmpty(), superName.orEmpty())
            }
            super.visitEnd()
        }
    }

    private inner class TraceMethodAdapter(
        api: Int,
        mv: MethodVisitor,
        access: Int,
        private val name: String,
        desc: String,
        private val className: String,
        private val hasWindowFocusMethod: Boolean,
        private val isActivityOrSubClass: Boolean,
        private val isNeedTrace: Boolean
    ) : AdviceAdapter(api, mv, access, name, desc) {
        private val methodName: String

        init {
            val traceMethod = TraceMethod.create(0, access, className, name, desc)
            methodName = traceMethod.getMethodName()
        }

        override fun onMethodEnter() {
            val traceMethod = collectedMethodMap[methodName]
            if (traceMethod != null) {
                traceMethodCount.incrementAndGet()
                mv.visitLdcInsn(traceMethod.id)
                mv.visitMethodInsn(INVOKESTATIC, TraceBuildConstants.MATRIX_TRACE_CLASS, "i", "(I)V", false)

                if (checkNeedTraceWindowFocusChangeMethod(traceMethod)) {
                    traceWindowFocusChangeMethod(mv, className)
                }
            }
        }

        override fun onMethodExit(opcode: Int) {
            val traceMethod = collectedMethodMap[methodName]
            if (traceMethod != null) {
                traceMethodCount.incrementAndGet()
                mv.visitLdcInsn(traceMethod.id)
                mv.visitMethodInsn(INVOKESTATIC, TraceBuildConstants.MATRIX_TRACE_CLASS, "o", "(I)V", false)
            }
        }

        private fun checkNeedTraceWindowFocusChangeMethod(traceMethod: TraceMethod): Boolean {
            if (hasWindowFocusMethod && isActivityOrSubClass && isNeedTrace) {
                val windowFocusChangeMethod = TraceMethod.create(
                    -1,
                    Opcodes.ACC_PUBLIC,
                    className,
                    TraceBuildConstants.MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD,
                    TraceBuildConstants.MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD_ARGS
                )
                if (windowFocusChangeMethod == traceMethod) {
                    return true
                }
            }
            return false
        }
    }

    private fun isActivityOrSubClass(
        className: String?,
        collectedClassExtendMap: ConcurrentHashMap<String, String>
    ): Boolean {
        val currentName = className.orEmpty().replace(".", "/")
        val isActivity = currentName == TraceBuildConstants.MATRIX_TRACE_ACTIVITY_CLASS ||
            currentName == TraceBuildConstants.MATRIX_TRACE_V4_ACTIVITY_CLASS ||
            currentName == TraceBuildConstants.MATRIX_TRACE_V7_ACTIVITY_CLASS ||
            currentName == TraceBuildConstants.MATRIX_TRACE_ANDROIDX_ACTIVITY_CLASS
        if (isActivity) {
            return true
        }
        return if (!collectedClassExtendMap.containsKey(currentName)) {
            false
        } else {
            isActivityOrSubClass(collectedClassExtendMap[currentName], collectedClassExtendMap)
        }
    }

    private fun traceWindowFocusChangeMethod(mv: MethodVisitor, classname: String) {
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            TraceBuildConstants.MATRIX_TRACE_CLASS,
            "at",
            "(Landroid/app/Activity;Z)V",
            false
        )
    }

    private fun insertWindowFocusChangeMethod(cv: ClassVisitor, classname: String, superClassName: String) {
        val methodVisitor = cv.visitMethod(
            Opcodes.ACC_PUBLIC,
            TraceBuildConstants.MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD,
            TraceBuildConstants.MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD_ARGS,
            null,
            null
        )
        methodVisitor.visitCode()
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0)
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 1)
        methodVisitor.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            superClassName,
            TraceBuildConstants.MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD,
            TraceBuildConstants.MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD_ARGS,
            false
        )
        traceWindowFocusChangeMethod(methodVisitor, classname)
        methodVisitor.visitInsn(Opcodes.RETURN)
        methodVisitor.visitMaxs(2, 2)
        methodVisitor.visitEnd()
    }

    companion object {
        private const val TAG = "TraceHarbor.MethodTracer"
        private val traceMethodCount = AtomicInteger()
    }
}

