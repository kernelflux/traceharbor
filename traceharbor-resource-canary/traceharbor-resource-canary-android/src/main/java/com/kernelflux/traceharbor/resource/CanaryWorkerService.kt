package com.kernelflux.traceharbor.resource

import android.content.Context
import android.content.Intent
import android.os.Build
import com.kernelflux.traceharbor.TraceHarbor
import com.kernelflux.traceharbor.resource.analyzer.model.HeapDump
import com.kernelflux.traceharbor.resource.common.utils.StreamUtil
import com.kernelflux.traceharbor.resource.hproflib.HprofBufferShrinker
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CanaryWorkerService : TraceHarborJobIntentService() {
    override fun onHandleWork(intent: Intent) {
        val action = intent.action
        if (ACTION_SHRINK_HPROF == action) {
            try {
                intent.setExtrasClassLoader(classLoader)
                val heapDump = intent.getSerializableExtra(EXTRA_PARAM_HEAPDUMP) as? HeapDump
                if (heapDump != null) {
                    doShrinkHprofAndReport(heapDump)
                } else {
                    TraceHarborLog.e(TAG, "failed to deserialize heap dump, give up shrinking and reporting.")
                }
            } catch (thr: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, thr, "failed to deserialize heap dump, give up shrinking and reporting.")
            }
        }
    }

    private fun doShrinkHprofAndReport(heapDump: HeapDump) {
        val hprofDir = heapDump.getHprofFile().parentFile ?: return
        val shrinkedHProfFile = File(hprofDir, getShrinkHprofName(heapDump.getHprofFile()))
        val zipResFile = File(hprofDir, getResultZipName("dump_result_${android.os.Process.myPid()}"))
        val hprofFile = heapDump.getHprofFile()
        var zos: ZipOutputStream? = null
        try {
            val startTime = System.currentTimeMillis()
            HprofBufferShrinker().shrink(hprofFile, shrinkedHProfFile)
            TraceHarborLog.i(
                TAG,
                "shrink hprof file %s, size: %dk to %s, size: %dk, use time:%d",
                hprofFile.path,
                hprofFile.length() / 1024,
                shrinkedHProfFile.path,
                shrinkedHProfFile.length() / 1024,
                System.currentTimeMillis() - startTime,
            )

            zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipResFile)))
            val resultInfoEntry = ZipEntry("result.info")
            val shrinkedHProfEntry = ZipEntry(shrinkedHProfFile.name)

            zos.putNextEntry(resultInfoEntry)
            val pw = PrintWriter(OutputStreamWriter(zos, Charset.forName("UTF-8")))
            pw.println("# Resource Canary Result Infomation. THIS FILE IS IMPORTANT FOR THE ANALYZER !!")
            pw.println("sdkVersion=${Build.VERSION.SDK_INT}")
            val manufacturer =
                TraceHarbor.with().getPluginByClass(ResourcePlugin::class.java)?.getConfig()?.getManufacture() ?: ""
            pw.println("manufacturer=$manufacturer")
            pw.println("hprofEntry=${shrinkedHProfEntry.name}")
            pw.println("leakedActivityKey=${heapDump.getReferenceKey()}")
            pw.flush()
            zos.closeEntry()

            zos.putNextEntry(shrinkedHProfEntry)
            StreamUtil.copyFileToStream(shrinkedHProfFile, zos)
            zos.closeEntry()

            shrinkedHProfFile.delete()
            hprofFile.delete()

            TraceHarborLog.i(TAG, "process hprof file use total time:%d", System.currentTimeMillis() - startTime)
            CanaryResultService.reportHprofResult(this, zipResFile.absolutePath, heapDump.getActivityName())
        } catch (e: IOException) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        } finally {
            StreamUtil.closeQuietly(zos)
        }
    }

    private fun getShrinkHprofName(origHprof: File): String {
        val origHprofName = origHprof.name
        val extPos = origHprofName.indexOf(".hprof")
        val namePrefix = origHprofName.substring(0, extPos)
        return "${namePrefix}_shrink.hprof"
    }

    private fun getResultZipName(prefix: String): String {
        return StringBuilder()
            .append(prefix)
            .append('_')
            .append(SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH).format(Date()))
            .append(".zip")
            .toString()
    }

    companion object {
        private const val TAG = "TraceHarbor.CanaryWorkerService"
        private const val JOB_ID = 0xFAFBFCFD.toInt()
        private const val ACTION_SHRINK_HPROF = "com.kernelflux.traceharbor.resource.worker.action.SHRINK_HPROF"
        private const val EXTRA_PARAM_HEAPDUMP = "com.kernelflux.traceharbor.resource.worker.param.HEAPDUMP"

        @JvmStatic
        fun shrinkHprofAndReport(context: Context, heapDump: HeapDump) {
            val intent = Intent(context, CanaryWorkerService::class.java)
            intent.action = ACTION_SHRINK_HPROF
            intent.putExtra(EXTRA_PARAM_HEAPDUMP, heapDump)
            enqueueWork(context, CanaryWorkerService::class.java, JOB_ID, intent)
        }
    }
}

