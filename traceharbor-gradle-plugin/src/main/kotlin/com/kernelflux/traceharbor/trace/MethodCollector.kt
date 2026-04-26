package com.kernelflux.traceharbor.trace

import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.plugin.compat.AgpCompat
import com.kernelflux.traceharbor.trace.item.TraceMethod
import com.kernelflux.traceharbor.trace.retrace.MappingCollector
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.Writer
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.Enumeration
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class MethodCollector(
    private val executor: ExecutorService,
    private val mappingCollector: MappingCollector,
    private val methodId: AtomicInteger,
    private val configuration: Configuration,
    private val collectedMethodMap: ConcurrentHashMap<String, TraceMethod>
) {
    private val collectedClassExtendMap = ConcurrentHashMap<String, String>()
    private val collectedIgnoreMethodMap = ConcurrentHashMap<String, TraceMethod>()
    private val ignoreCount = AtomicInteger()
    private val incrementCount = AtomicInteger()

    fun getCollectedClassExtendMap(): ConcurrentHashMap<String, String> {
        return collectedClassExtendMap
    }

    fun getCollectedMethodMap(): ConcurrentHashMap<String, TraceMethod> {
        return collectedMethodMap
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    fun collect(srcFolderList: Collection<File>, dependencyJarList: Collection<File>) {
        val futures = LinkedList<Future<*>>()

        for (srcFile in srcFolderList) {
            val classFileList = ArrayList<File>()
            if (srcFile.isDirectory) {
                listClassFiles(classFileList, srcFile)
            } else {
                classFileList.add(srcFile)
            }

            for (classFile in classFileList) {
                futures.add(executor.submit(CollectSrcTask(classFile)))
            }
        }

        for (jarFile in dependencyJarList) {
            futures.add(executor.submit(CollectJarTask(jarFile)))
        }

        for (future in futures) {
            future.get()
        }
        futures.clear()

        futures.add(executor.submit { saveIgnoreCollectedMethod(mappingCollector) })
        futures.add(executor.submit { saveCollectedMethod(mappingCollector) })

        for (future in futures) {
            future.get()
        }
        futures.clear()
    }

    private inner class CollectSrcTask(private val classFile: File) : Runnable {
        override fun run() {
            var `is`: InputStream? = null
            try {
                `is` = FileInputStream(classFile)
                val classReader = ClassReader(`is`)
                val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
                val visitor: ClassVisitor = TraceClassAdapter(AgpCompat.asmApi, classWriter)
                classReader.accept(visitor, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    `is`?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private inner class CollectJarTask(private val fromJar: File) : Runnable {
        override fun run() {
            var zipFile: ZipFile? = null
            try {
                zipFile = ZipFile(fromJar)
                val enumeration: Enumeration<out ZipEntry> = zipFile.entries()
                while (enumeration.hasMoreElements()) {
                    val zipEntry = enumeration.nextElement()
                    val zipEntryName = zipEntry.name
                    if (isNeedTraceFile(zipEntryName)) {
                        val inputStream = zipFile.getInputStream(zipEntry)
                        val classReader = ClassReader(inputStream)
                        val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
                        val visitor: ClassVisitor = TraceClassAdapter(AgpCompat.asmApi, classWriter)
                        classReader.accept(visitor, 0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    zipFile?.close()
                } catch (_: Exception) {
                    Log.e(TAG, "close stream err! fromJar:%s", fromJar.absolutePath)
                }
            }
        }
    }

    private fun saveIgnoreCollectedMethod(mappingCollector: MappingCollector) {
        val methodMapFile = File(configuration.ignoreMethodMapFilePath)
        if (!methodMapFile.parentFile.exists()) {
            methodMapFile.parentFile.mkdirs()
        }
        val ignoreMethodList = ArrayList<TraceMethod>()
        ignoreMethodList.addAll(collectedIgnoreMethodMap.values)
        Log.i(
            TAG,
            "[saveIgnoreCollectedMethod] size:%s path:%s",
            collectedIgnoreMethodMap.size,
            methodMapFile.absolutePath
        )

        Collections.sort(ignoreMethodList, Comparator { o1, o2 ->
            o1.className.orEmpty().compareTo(o2.className.orEmpty())
        })

        var pw: PrintWriter? = null
        try {
            val fileOutputStream = FileOutputStream(methodMapFile, false)
            val w: Writer = OutputStreamWriter(fileOutputStream, "UTF-8")
            pw = PrintWriter(w)
            pw.println("ignore methods:")
            for (traceMethod in ignoreMethodList) {
                traceMethod.revert(mappingCollector)
                pw.println(traceMethod.toIgnoreString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "write method map Exception:%s", e.message)
            e.printStackTrace()
        } finally {
            pw?.flush()
            pw?.close()
        }
    }

    private fun saveCollectedMethod(mappingCollector: MappingCollector) {
        val methodMapFile = File(configuration.methodMapFilePath)
        if (!methodMapFile.parentFile.exists()) {
            methodMapFile.parentFile.mkdirs()
        }
        val methodList = ArrayList<TraceMethod>()

        val extra = TraceMethod.create(
            TraceBuildConstants.METHOD_ID_DISPATCH,
            Opcodes.ACC_PUBLIC,
            "android.os.Handler",
            "dispatchMessage",
            "(Landroid.os.Message;)V"
        )
        collectedMethodMap[extra.getMethodName()] = extra

        methodList.addAll(collectedMethodMap.values)

        Log.i(
            TAG,
            "[saveCollectedMethod] size:%s incrementCount:%s path:%s",
            collectedMethodMap.size,
            incrementCount.get(),
            methodMapFile.absolutePath
        )

        Collections.sort(methodList, Comparator { o1, o2 -> o1.id - o2.id })

        var pw: PrintWriter? = null
        try {
            val fileOutputStream = FileOutputStream(methodMapFile, false)
            val w: Writer = OutputStreamWriter(fileOutputStream, "UTF-8")
            pw = PrintWriter(w)
            for (traceMethod in methodList) {
                traceMethod.revert(mappingCollector)
                pw.println(traceMethod.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "write method map Exception:%s", e.message)
            e.printStackTrace()
        } finally {
            pw?.flush()
            pw?.close()
        }
    }

    private inner class TraceClassAdapter(i: Int, classVisitor: ClassVisitor) : ClassVisitor(i, classVisitor) {
        private var className: String? = null
        private var isABSClass = false
        private var hasWindowFocusMethod = false

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
            if ((access and Opcodes.ACC_ABSTRACT) > 0 || (access and Opcodes.ACC_INTERFACE) > 0) {
                isABSClass = true
            }
            collectedClassExtendMap[className.orEmpty()] = superName.orEmpty()
        }

        override fun visitMethod(
            access: Int,
            name: String,
            desc: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            return if (isABSClass) {
                super.visitMethod(access, name, desc, signature, exceptions)
            } else {
                if (!hasWindowFocusMethod) {
                    hasWindowFocusMethod = isWindowFocusChangeMethod(name, desc)
                }
                CollectMethodNode(className.orEmpty(), access, name, desc, signature, exceptions)
            }
        }
    }

    private inner class CollectMethodNode(
        private val className: String,
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<out String>?
    ) : MethodNode(AgpCompat.asmApi, access, name, desc, signature, exceptions) {
        private var isConstructor = false

        override fun visitEnd() {
            super.visitEnd()
            val traceMethod = TraceMethod.create(0, access, className, name, desc)

            if ("<init>" == name) {
                isConstructor = true
            }

            val isNeedTrace = isNeedTrace(configuration, traceMethod.className, mappingCollector)
            // filter simple methods
            if ((isEmptyMethod() || isGetSetMethod() || isSingleMethod()) && isNeedTrace) {
                ignoreCount.incrementAndGet()
                collectedIgnoreMethodMap[traceMethod.getMethodName()] = traceMethod
                return
            }

            if (isNeedTrace && !collectedMethodMap.containsKey(traceMethod.getMethodName())) {
                traceMethod.id = methodId.incrementAndGet()
                collectedMethodMap[traceMethod.getMethodName()] = traceMethod
                incrementCount.incrementAndGet()
            } else if (!isNeedTrace && !collectedIgnoreMethodMap.containsKey(traceMethod.className)) {
                ignoreCount.incrementAndGet()
                collectedIgnoreMethodMap[traceMethod.getMethodName()] = traceMethod
            }
        }

        private fun isGetSetMethod(): Boolean {
            var ignoreCount = 0
            val iterator = instructions.iterator()
            while (iterator.hasNext()) {
                val insnNode = iterator.next()
                val opcode = insnNode.opcode
                if (-1 == opcode) {
                    continue
                }
                if (opcode != Opcodes.GETFIELD &&
                    opcode != Opcodes.GETSTATIC &&
                    opcode != Opcodes.H_GETFIELD &&
                    opcode != Opcodes.H_GETSTATIC &&
                    opcode != Opcodes.RETURN &&
                    opcode != Opcodes.ARETURN &&
                    opcode != Opcodes.DRETURN &&
                    opcode != Opcodes.FRETURN &&
                    opcode != Opcodes.LRETURN &&
                    opcode != Opcodes.IRETURN &&
                    opcode != Opcodes.PUTFIELD &&
                    opcode != Opcodes.PUTSTATIC &&
                    opcode != Opcodes.H_PUTFIELD &&
                    opcode != Opcodes.H_PUTSTATIC &&
                    opcode > Opcodes.SALOAD
                ) {
                    if (isConstructor && opcode == Opcodes.INVOKESPECIAL) {
                        ignoreCount++
                        if (ignoreCount > 1) {
                            return false
                        }
                        continue
                    }
                    return false
                }
            }
            return true
        }

        private fun isSingleMethod(): Boolean {
            val iterator = instructions.iterator()
            while (iterator.hasNext()) {
                val insnNode = iterator.next()
                val opcode = insnNode.opcode
                if (-1 == opcode) {
                    continue
                } else if (Opcodes.INVOKEVIRTUAL <= opcode && opcode <= Opcodes.INVOKEDYNAMIC) {
                    return false
                }
            }
            return true
        }

        private fun isEmptyMethod(): Boolean {
            val iterator = instructions.iterator()
            while (iterator.hasNext()) {
                val insnNode = iterator.next()
                val opcode = insnNode.opcode
                if (-1 == opcode) {
                    continue
                }
                return false
            }
            return true
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
            } else if (isNeedTraceFile(file.name)) {
                classFiles.add(file)
            }
        }
    }

    companion object {
        private const val TAG = "MethodCollector"

        @JvmStatic
        fun isWindowFocusChangeMethod(name: String?, desc: String?): Boolean {
            return name != null &&
                desc != null &&
                name == TraceBuildConstants.MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD &&
                desc == TraceBuildConstants.MATRIX_TRACE_ON_WINDOW_FOCUS_METHOD_ARGS
        }

        @JvmStatic
        fun isNeedTrace(configuration: Configuration, clsName: String?, mappingCollector: MappingCollector?): Boolean {
            var targetClassName = clsName.orEmpty()
            var isNeed = true
            if (configuration.blockSet.contains(targetClassName)) {
                isNeed = false
            } else {
                if (mappingCollector != null) {
                    targetClassName = mappingCollector.originalClassName(targetClassName, targetClassName)
                }
                targetClassName = targetClassName.replace("/", ".")
                for (packageName in configuration.blockSet) {
                    if (targetClassName.startsWith(packageName.replace("/", "."))) {
                        isNeed = false
                        break
                    }
                }
            }
            return isNeed
        }

        @JvmStatic
        fun isNeedTraceFile(fileName: String): Boolean {
            if (fileName.endsWith(".class")) {
                for (unTraceCls in TraceBuildConstants.UN_TRACE_CLASS) {
                    if (fileName.contains(unTraceCls)) {
                        return false
                    }
                }
            } else {
                return false
            }
            return true
        }
    }
}

