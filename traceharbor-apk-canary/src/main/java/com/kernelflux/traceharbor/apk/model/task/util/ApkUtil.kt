package com.kernelflux.traceharbor.apk.model.task.util

import com.android.dexdeps.Output
import com.kernelflux.traceharbor.javalib.util.Log
import com.kernelflux.traceharbor.javalib.util.Util
import org.jf.baksmali.Adaptors.ClassDefinition
import org.jf.baksmali.BaksmaliOptions
import org.jf.baksmali.formatter.BaksmaliWriter
import org.jf.dexlib2.iface.ClassDef
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object ApkUtil {
    private const val TAG = "TraceHarbor.ApkUtil"
    private val R_CLASS_PATTERN = Pattern.compile("^R\\$\\w+")

    /*
     *  parse the descriptor of class to normal dot-split class name (XXX.XXX.XXX)
     */
    @JvmStatic
    fun getNormalClassName(name: String?): String {
        if (!Util.isNullOrNil(name)) {
            var className = Output.descriptorToDot(name!!)
            if (className.endsWith("[]")) { // enum or array
                className = className.substring(0, className.indexOf("[]"))
            }
            return className
        }
        return ""
    }

    /*
     *  retrieve the package name from dot-split class name
     */
    @JvmStatic
    fun getPackageName(className: String?): String {
        if (!Util.isNullOrNil(className)) {
            val index = className!!.lastIndexOf('.')
            if (index >= 0) {
                return className.substring(0, index)
            } else {
                Log.d(TAG, "default package class: %s", className)
                return "<default>"
            }
        }
        return ""
    }

    /*
     *  retrieve the class name without package prefix
     */
    @JvmStatic
    fun getPureClassName(classname: String?): String {
        var name = ""
        if (!Util.isNullOrNil(classname)) {
            val index = classname!!.lastIndexOf('.')
            name = if (index != -1) {
                classname.substring(index + 1)
            } else {
                classname
            }
        }
        return name
    }

    /*
     *  determine if the class if R class
     */
    @JvmStatic
    fun isRClassName(className: String?): Boolean {
        return R_CLASS_PATTERN.matcher(className).matches()
    }

    @JvmStatic
    fun disassembleClass(classDef: ClassDef, options: BaksmaliOptions): Array<String>? {
        /**
         * The path for the disassembly file is based on the package name
         * The class descriptor will look something like:
         * Ljava/lang/Object;
         * Where the there is leading 'L' and a trailing ';', and the parts of the
         * package name are separated by '/'
         */
        val classDescriptor = classDef.type

        // validate that the descriptor is formatted like we expect
        if (classDescriptor[0] != 'L' || classDescriptor[classDescriptor.length - 1] != ';') {
            Log.e(TAG, "Unrecognized class descriptor - $classDescriptor - skipping class")
            return null
        }

        // create and initialize the top level string template
        val classDefinition = ClassDefinition(options, classDef)

        // write the disassembly
        var writer: Writer? = null
        try {
            val baos = ByteArrayOutputStream()
            val bufWriter = BufferedWriter(OutputStreamWriter(baos, StandardCharsets.UTF_8))

            writer = BaksmaliWriter(bufWriter, classDef.type)
            classDefinition.writeTo(writer as BaksmaliWriter)
            writer.flush()
            return baos.toString().split("\n").toTypedArray()
        } catch (ex: Exception) {
            Log.e(TAG, "\n\nError occurred while disassembling class " + classDescriptor.replace('/', '.') + " - skipping class")
            ex.printStackTrace()
            return null
        } finally {
            if (writer != null) {
                try {
                    writer.close()
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                }
            }
        }
    }
}

