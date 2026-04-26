package com.kernelflux.traceharbor.trace

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

/**
 * Created by habbyge on 2019/4/25.
 *
 * fix:
 * java.lang.TypeNotPresentException: Type android/content/res/TypedArray not present
 *     at org.objectweb.asm.ClassWriter.getCommonSuperClass(ClassWriter.java:1025)
 *     at org.objectweb.asm.SymbolTable.addMergedType(SymbolTable.java:1202)
 *     at org.objectweb.asm.Frame.merge(Frame.java:1299)
 *     at org.objectweb.asm.Frame.merge(Frame.java:1197)
 *     at org.objectweb.asm.MethodWriter.computeAllFrames(MethodWriter.java:1610)
 *     at org.objectweb.asm.MethodWriter.visitMaxs(MethodWriter.java:1546)
 *     at org.objectweb.asm.tree.MethodNode.accept(MethodNode.java:769)
 *     at org.objectweb.asm.util.CheckMethodAdapter$1.visitEnd(CheckMethodAdapter.java:465)
 *     at org.objectweb.asm.MethodVisitor.visitEnd(MethodVisitor.java:783)
 *     at org.objectweb.asm.util.CheckMethodAdapter.visitEnd(CheckMethodAdapter.java:1036)
 *     at org.objectweb.asm.ClassReader.readMethod(ClassReader.java:1495)
 *     at org.objectweb.asm.ClassReader.accept(ClassReader.java:721)
 */
class TraceClassWriter : ClassWriter {
    private val classLoader: ClassLoader

    constructor(flags: Int, classLoader: ClassLoader) : super(flags) {
        this.classLoader = classLoader
    }

    constructor(classReader: ClassReader, flags: Int, classLoader: ClassLoader) : super(classReader, flags) {
        this.classLoader = classLoader
    }

    override fun getCommonSuperClass(type1: String, type2: String): String {
        return try {
            super.getCommonSuperClass(type1, type2)
        } catch (e: Exception) {
            try {
                getCommonSuperClassV1(type1, type2)
            } catch (e1: Exception) {
                try {
                    getCommonSuperClassV2(type1, type2)
                } catch (e2: Exception) {
                    getCommonSuperClassV3(type1, type2)
                }
            }
        }
    }

    private fun getCommonSuperClassV1(type1: String, type2: String): String {
        val clazz1: Class<*>
        val clazz2: Class<*>
        try {
            clazz1 = Class.forName(type1.replace('/', '.'), false, classLoader)
            clazz2 = Class.forName(type2.replace('/', '.'), false, classLoader)
        } catch (e: Exception) {
            return "java/lang/Object"
        } catch (error: LinkageError) {
            return "java/lang/Object"
        }
        if (clazz1.isAssignableFrom(clazz2)) {
            return type1
        }
        if (clazz2.isAssignableFrom(clazz1)) {
            return type2
        }
        if (clazz1.isInterface || clazz2.isInterface) {
            return "java/lang/Object"
        }
        var parent = clazz1
        while (!parent.isAssignableFrom(clazz2)) {
            parent = parent.superclass
        }
        return parent.name.replace('.', '/')
    }

    private fun getCommonSuperClassV2(type1: String, type2: String): String {
        val curClassLoader = javaClass.classLoader
        val clazz1: Class<*>
        val clazz2: Class<*>
        try {
            clazz1 = Class.forName(type1.replace('/', '.'), false, curClassLoader)
            clazz2 = Class.forName(type2.replace('/', '.'), false, classLoader)
        } catch (e: Exception) {
            return "java/lang/Object"
        } catch (error: LinkageError) {
            return "java/lang/Object"
        }
        if (clazz1.isAssignableFrom(clazz2)) {
            return type1
        }
        if (clazz2.isAssignableFrom(clazz1)) {
            return type2
        }
        if (clazz1.isInterface || clazz2.isInterface) {
            return "java/lang/Object"
        }
        var parent = clazz1
        while (!parent.isAssignableFrom(clazz2)) {
            parent = parent.superclass
        }
        return parent.name.replace('.', '/')
    }

    private fun getCommonSuperClassV3(type1: String, type2: String): String {
        val curClassLoader = javaClass.classLoader
        val clazz1: Class<*>
        val clazz2: Class<*>
        try {
            clazz1 = Class.forName(type1.replace('/', '.'), false, classLoader)
            clazz2 = Class.forName(type2.replace('/', '.'), false, curClassLoader)
        } catch (e: Exception) {
            return "java/lang/Object"
        } catch (error: LinkageError) {
            return "java/lang/Object"
        }
        if (clazz1.isAssignableFrom(clazz2)) {
            return type1
        }
        if (clazz2.isAssignableFrom(clazz1)) {
            return type2
        }
        if (clazz1.isInterface || clazz2.isInterface) {
            return "java/lang/Object"
        }
        var parent = clazz1
        while (!parent.isAssignableFrom(clazz2)) {
            parent = parent.superclass
        }
        return parent.name.replace('.', '/')
    }
}

