package com.kernelflux.traceharbor.batterycanary.utils

import android.os.Process
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * see [com.android.internal.os.ProcessCpuTracker]
 *
 * @author Kaede
 * @since 2020/11/6
 */
@Suppress("JavadocReference", "SpellCheckingInspection")
object ProcStatUtil {
    private const val TAG = "TraceHarbor.battery.ProcStatUtil"
    private val sBufferRef = ThreadLocal<ByteArray>()
    private var sParseError: OnParseError? = null

    @JvmStatic
    fun getLocalBuffers(): ByteArray {
        if (sBufferRef.get() == null) {
            sBufferRef.set(ByteArray(128))
        }
        return sBufferRef.get()!!
    }

    @JvmStatic
    fun exists(pid: Int): Boolean {
        return File("/proc/$pid/stat").exists()
    }

    @JvmStatic
    fun exists(pid: Int, tid: Int): Boolean {
        return File("/proc/$pid/task/$tid/stat").exists()
    }

    @JvmStatic
    fun currentPid(): ProcStat? {
        return of(Process.myPid())
    }

    @JvmStatic
    fun current(): ProcStat? {
        return of(Process.myPid(), Process.myTid())
    }

    @JvmStatic
    fun of(pid: Int): ProcStat? {
        return parse("/proc/$pid/stat")
    }

    @JvmStatic
    fun of(pid: Int, tid: Int): ProcStat? {
        return parse("/proc/$pid/task/$tid/stat")
    }

    @JvmStatic
    fun parse(path: String): ProcStat? {
        try {
            var procStatInfo: ProcStat? = null
            try {
                // For bettery perf: 30% millis dec
                procStatInfo = BetterProcStatParser.parse(path, getLocalBuffers())
            } catch (e: ParseException) {
                sParseError?.onError(3, e.content)
                try {
                    procStatInfo = parseWithBufferForPath(path, getLocalBuffers())
                } catch (e2: ParseException) {
                    sParseError?.onError(1, e2.content)
                }
            }

            if (procStatInfo == null) {
                TraceHarborLog.w(TAG, "#parseJiffies read with buffer fail, fallback with spilts")
                try {
                    procStatInfo = parseWithSplits(BatteryCanaryUtil.cat(path))
                } catch (e: ParseException) {
                    sParseError?.onError(2, e.content)
                }
                if (procStatInfo == null) {
                    TraceHarborLog.w(TAG, "#parseJiffies read with splits fail")
                    return null
                }
            }
            return procStatInfo
        } catch (e: Throwable) {
            TraceHarborLog.w(TAG, "#parseJiffies fail: " + e.message)
            sParseError?.onError(0, BatteryCanaryUtil.cat(path) + "\n" + e.message)
            return null
        }
    }

    @JvmStatic
    @Throws(ParseException::class)
    fun parseWithBufferForPath(path: String, buffer: ByteArray): ProcStat? {
        val file = File(path)
        if (!file.exists()) {
            return null
        }

        var readBytes: Int
        try {
            FileInputStream(file).use { fis ->
                readBytes = fis.read(buffer)
            }
        } catch (e: IOException) {
            TraceHarborLog.printErrStackTrace(TAG, e, "read buffer from file fail")
            readBytes = -1
        }
        if (readBytes <= 0) {
            return null
        }

        return parseWithBuffer(buffer)
    }

    /**
     * Do NOT modfiy this method untlil all the test cases within ProcStatUtilsTest is passed.
     */
    @VisibleForTesting
    @JvmStatic
    @Throws(ParseException::class)
    fun parseWithBuffer(statBuffer: ByteArray): ProcStat {
        /*
         * 样本:
         * 10966 (terycanary.test) S 699 699 0 0 -1 1077952832 6187 0 0 0 22 2 0 0 20 0 17 0 9087400 5414273024
         *  24109 18446744073709551615 421814448128 421814472944 549131058960 0 0 0 4612 1 1073775864
         *  1 0 0 17 7 0 0 0 0 0 421814476800 421814478232 422247952384 549131060923 549131061022 549131061022
         *  549131063262 0
         *
         * 字段:
         * - pid:  进程ID.
         * - comm: task_struct结构体的进程名
         * - state: 进程状态, 此处为S
         * - ppid: 父进程ID （父进程是指通过fork方式, 通过clone并非父进程）
         * - pgrp: 进程组ID
         * - session: 进程会话组ID
         * - tty_nr: 当前进程的tty终点设备号
         * - tpgid: 控制进程终端的前台进程号
         * - flags: 进程标识位, 定义在include/linux/sched.h中的PF_*, 此处等于1077952832
         * - minflt:  次要缺页中断的次数, 即无需从磁盘加载内存页. 比如COW和匿名页
         * - cminflt: 当前进程等待子进程的minflt
         * - majflt: 主要缺页中断的次数, 需要从磁盘加载内存页. 比如map文件
         * - majflt: 当前进程等待子进程的majflt
         * - utime: 该进程处于用户态的时间, 单位jiffies, 此处等于166114
         * - stime: 该进程处于内核态的时间, 单位jiffies, 此处等于129684
         * - cutime: 当前进程等待子进程的utime
         * - cstime: 当前进程等待子进程的utime
         * - priority: 进程优先级, 此次等于10.
         * - nice: nice值, 取值范围[19, -20], 此处等于-10
         * - num_threads: 线程个数, 此处等于221
         * - itrealvalue: 该字段已废弃, 恒等于0
         * - starttime: 自系统启动后的进程创建时间, 单位jiffies, 此处等于2284
         * - vsize: 进程的虚拟内存大小, 单位为bytes
         * - rss: 进程独占内存+共享库, 单位pages, 此处等于93087
         * - rsslim: rss大小上限
         *
         * 说明:
         * 第10~17行主要是随着时间而改变的量；
         * 内核时间单位, sysconf(_SC_CLK_TCK)一般地定义为jiffies(一般地等于10ms)
         * starttime: 此值单位为jiffies, 结合/proc/stat的btime, 可知道每一个线程启动的时间点
         * 1500827856 + 2284/100 = 1500827856, 转换成北京时间为2017/7/24 0:37:58
         * 第四行数据很少使用,只说一下该行第7至9个数的含义:
         * signal: 即将要处理的信号, 十进制, 此处等于6660
         * blocked: 阻塞的信号, 十进制
         * sigignore: 被忽略的信号, 十进制, 此处等于36088
         */

        val stat = ProcStat()
        val statBytes = statBuffer.size
        var i = 0
        var spaceIdx = 0
        while (i < statBytes) {
            if (Character.isSpaceChar(statBuffer[i].toInt())) {
                spaceIdx++
                i++
                continue
            }

            when (spaceIdx) {
                1 -> {
                    var readIdx = i
                    var window = 0
                    // seek end symobl of comm: ')'
                    while (i < statBytes && ')'.code != statBuffer[i].toInt()) {
                        i++
                        window++
                    }
                    if ('('.code == statBuffer[readIdx].toInt()) {
                        readIdx++
                        window--
                    }
                    if (')'.code == statBuffer[readIdx + window - 1].toInt()) {
                        window--
                    }
                    if (window > 0) {
                        stat.comm = safeBytesToString(statBuffer, readIdx, window)
                    }
                    spaceIdx = 2
                }

                3 -> {
                    val readIdx = i
                    var window = 0
                    // seek next space
                    while (i < statBytes && !Character.isSpaceChar(statBuffer[i].toInt())) {
                        i++
                        window++
                    }
                    stat.stat = safeBytesToString(statBuffer, readIdx, window)
                }

                14 -> {
                    val readIdx = i
                    var window = 0
                    // seek next space
                    while (i < statBytes && !Character.isSpaceChar(statBuffer[i].toInt())) {
                        i++
                        window++
                    }
                    val num = safeBytesToString(statBuffer, readIdx, window)
                    if (!isNumeric(num)) {
                        throw ParseException(safeBytesToString(statBuffer, 0, statBuffer.size) + "\nutime: " + num)
                    }
                    stat.utime = TraceHarborUtil.parseLong(num, 0)
                }

                15 -> {
                    val readIdx = i
                    var window = 0
                    // seek next space
                    while (i < statBytes && !Character.isSpaceChar(statBuffer[i].toInt())) {
                        i++
                        window++
                    }
                    val num = safeBytesToString(statBuffer, readIdx, window)
                    if (!isNumeric(num)) {
                        throw ParseException(safeBytesToString(statBuffer, 0, statBuffer.size) + "\nstime: " + num)
                    }
                    stat.stime = TraceHarborUtil.parseLong(num, 0)
                }

                16 -> {
                    val readIdx = i
                    var window = 0
                    // seek next space
                    while (i < statBytes && !Character.isSpaceChar(statBuffer[i].toInt())) {
                        i++
                        window++
                    }
                    val num = safeBytesToString(statBuffer, readIdx, window)
                    if (!isNumeric(num)) {
                        throw ParseException(safeBytesToString(statBuffer, 0, statBuffer.size) + "\ncutime: " + num)
                    }
                    stat.cutime = TraceHarborUtil.parseLong(num, 0)
                }

                17 -> {
                    val readIdx = i
                    var window = 0
                    // seek next space
                    while (i < statBytes && !Character.isSpaceChar(statBuffer[i].toInt())) {
                        i++
                        window++
                    }
                    val num = safeBytesToString(statBuffer, readIdx, window)
                    if (!isNumeric(num)) {
                        throw ParseException(safeBytesToString(statBuffer, 0, statBuffer.size) + "\ncstime: " + num)
                    }
                    stat.cstime = TraceHarborUtil.parseLong(num, 0)
                }

                else -> i++
            }
        }
        return stat
    }

    @VisibleForTesting
    @JvmStatic
    @Throws(ParseException::class)
    fun parseWithSplits(cat: String?): ProcStat {
        val stat = ProcStat()
        if (!TextUtils.isEmpty(cat)) {
            val index = cat!!.indexOf(")")
            if (index <= 0) throw IllegalStateException("$cat has not ')'")
            val prefix = cat.substring(0, index)
            val indexBgn = prefix.indexOf("(") + "(".length
            stat.comm = prefix.substring(indexBgn, index)

            val suffix = cat.substring(index + ")".length)
            val splits = suffix.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            if (!isNumeric(splits[12])) {
                throw ParseException("$cat\nutime: " + splits[12])
            }
            if (!isNumeric(splits[13])) {
                throw ParseException("$cat\nstime: " + splits[13])
            }
            if (!isNumeric(splits[14])) {
                throw ParseException("$cat\ncutime: " + splits[14])
            }
            if (!isNumeric(splits[15])) {
                throw ParseException("$cat\ncstime: " + splits[15])
            }
            stat.stat = splits[1]
            stat.utime = TraceHarborUtil.parseLong(splits[12], 0)
            stat.stime = TraceHarborUtil.parseLong(splits[13], 0)
            stat.cutime = TraceHarborUtil.parseLong(splits[14], 0)
            stat.cstime = TraceHarborUtil.parseLong(splits[15], 0)
        }
        return stat
    }

    @VisibleForTesting
    @JvmStatic
    fun safeBytesToString(buffer: ByteArray, offset: Int, length: Int): String {
        return try {
            val charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(buffer, offset, length))
            String(charBuffer.array(), 0, charBuffer.limit())
        } catch (e: IndexOutOfBoundsException) {
            TraceHarborLog.w(TAG, "#safeBytesToString failed: " + e.message)
            ""
        }
    }

    @JvmStatic
    fun isNumeric(text: String?): Boolean {
        if (TextUtils.isEmpty(text)) return false
        if (text!!.startsWith("-")) {
            // negative number
            return TextUtils.isDigitsOnly(text.substring(1))
        }
        return TextUtils.isDigitsOnly(text)
    }

    @JvmStatic
    fun setParseErrorListener(parseError: OnParseError?) {
        sParseError = parseError
    }

    internal object BetterProcStatParser {
        private const val PROC_USER_TIME_FIELD = 13
        @Suppress("unused")
        private val sLocalReaders: ThreadLocal<ProcStatReader> = InheritableThreadLocal()

        @Throws(ParseException::class)
        fun parse(path: String, buffer: ByteArray): ProcStat {
            val reader = ProcStatReader(path, buffer)
            try {
                reader.reset()
                reader.skipLeftBrace()
                val comm = reader.readToSymbol(')', java.nio.CharBuffer.allocate(16))
                reader.skipSpaces()
                val state = reader.readWord(java.nio.CharBuffer.allocate(1))

                var index = 0
                while (index < PROC_USER_TIME_FIELD - 2) {
                    reader.skipSpaces()
                    index++
                }

                val stat = ProcStat()
                stat.comm = comm.toString()
                stat.stat = state.toString()
                stat.utime = readJiffy(reader)
                stat.stime = readJiffy(reader)
                stat.cutime = readJiffy(reader)
                stat.cstime = readJiffy(reader)
                return stat
            } catch (e: Exception) {
                if (e is ParseException) {
                    throw e
                } else {
                    throw ParseException("ProcStatReader error: " + e.javaClass.name + ", " + e.message)
                }
            } finally {
                try {
                    reader.close()
                } catch (ignored: Exception) {
                }
            }
        }

        private fun readJiffy(reader: ProcStatReader): Long {
            val jiffies = reader.readNumber()
            reader.skipSpaces()
            return jiffies
        }
    }

    @Suppress("SpellCheckingInspection")
    class ProcStat {
        @JvmField
        var comm: String = ""

        @JvmField
        var stat: String = "_"

        @JvmField
        var utime: Long = -1

        @JvmField
        var stime: Long = -1

        @JvmField
        var cutime: Long = -1

        @JvmField
        var cstime: Long = -1

        val jiffies: Long
            get() = utime + stime + cutime + cstime
    }

    fun interface OnParseError {
        fun onError(mode: Int, input: String?)
    }

    class ParseException(@JvmField val content: String?) : Exception()
}
