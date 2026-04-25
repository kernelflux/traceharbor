package com.kernelflux.traceharbor.batterycanary.utils

import android.annotation.SuppressLint
import android.os.Build
import android.os.SystemClock
import java.util.Arrays
import java.util.LinkedList
import java.util.concurrent.TimeUnit

/**
 * 林库死大多
 *
 * @author Kaede
 * @since date 2016/10/31
 */
@Suppress("unused")
class LinkStart {
    @JvmField
    val mTicker: Ticker

    @JvmField
    val mRoot: Session

    @JvmField
    var mCurrent: Session

    @JvmField
    var mIsFinished: Boolean = true

    constructor() : this(SystemClockMillisTicker())

    constructor(ticker: Ticker) {
        mTicker = ticker
        mRoot = Session("WatchCat")
        mCurrent = mRoot
    }

    fun getCurrent(): Session = mCurrent

    fun setCurrent(current: Session) {
        mCurrent = current
    }

    fun start(): LinkStart {
        mIsFinished = false
        mRoot.startTime = mTicker.currentTime()
        return this
    }

    fun insert(session: String): Session {
        if (!mIsFinished) {
            val entry = mCurrent.insert(session)
            mCurrent = entry
            return entry
        }

        throw IllegalStateException("this link already is finished.")
    }

    fun enter(session: String): Session {
        if (!mIsFinished) {
            if (mCurrent === mRoot) {
                throw IllegalStateException("root session can not have peer.")
            }

            val entry = mCurrent.enter(session)
            mCurrent = entry
            return entry
        }

        throw IllegalStateException("this link already is finished.")
    }

    fun end(session: Session) {
        session.end()
        mCurrent = session
    }

    fun finish() {
        mRoot.end()
        mIsFinished = true
    }

    override fun toString(): String {
        if (!mIsFinished) {
            return "watch cat is not finished yet."
        }

        val sb = StringBuilder("watch cat, time unit = " + mTicker.getUnit() + "\n")
        buildString(mRoot, sb)
        return sb.toString()
    }

    private fun buildString(session: Session, sb: StringBuilder) {
        sb.append(session.toString()).append("\n")

        session.children?.let { children ->
            for (i in children.indices) {
                buildString(children[i], sb)
            }
        }
    }

    @Suppress("UnusedReturnValue")
    inner class Session constructor(name: String) {
        @JvmField
        var name: String = name

        @JvmField
        var startTime: Long = 0

        @JvmField
        var endTime: Long = 0

        @JvmField
        var path: Int = 0

        @JvmField
        var parent: Session? = null

        @JvmField
        var children: MutableList<Session>? = null

        init {
            path = 0 // root
        }

        fun attach(parent: Session): Session {
            this.parent = parent
            return this
        }

        fun insert(childName: String): Session {
            val child = Session(childName).attach(this)
            child.startTime = mTicker.currentTime()
            child.path = this.path + 1
            addChild(child)
            return child
        }

        fun addChild(child: Session) {
            if (children == null) {
                children = LinkedList()
            }
            children!!.add(child)
        }

        fun enter(peerName: String): Session {
            val parent = parent ?: throw IllegalStateException("this method need a nonnull parent.")

            val next = Session(peerName).attach(parent)
            next.startTime = mTicker.currentTime()
            next.path = this.path
            parent.addChild(next)
            return next
        }

        fun end(): Session {
            this.endTime = mTicker.currentTime()
            return this
        }

        override fun toString(): String {
            var prefix = ""
            if (path > 0) {
                prefix = repeat(PREFIX, path)!!
            }

            val postfix: String = if (startTime == 0L) {
                "losing record of bgn time"
            } else if (endTime == 0L) {
                "losing record of end time"
            } else {
                mTicker.getInterval(startTime, endTime).toString()
            }

            var title = name
            if (name.length > MAX_TITLE_LENGTH) {
                title = name.substring(0, MAX_TITLE_LENGTH - 1)
            } else if (name.length < MAX_TITLE_LENGTH) {
                title = name + repeat(" ", MAX_TITLE_LENGTH - name.length)
            }

            return "|$prefix $title : $postfix"
        }
    }

    interface Ticker {
        fun currentTime(): Long

        fun getInterval(start: Long, end: Long): Long

        fun getUnit(): TimeUnit
    }

    /**
     * Milli Second
     */
    class SystemClockMillisTicker : Ticker {
        override fun currentTime(): Long = SystemClock.elapsedRealtime()

        override fun getInterval(start: Long, end: Long): Long = end - start

        override fun getUnit(): TimeUnit = TimeUnit.MILLISECONDS
    }

    class SystemMillisTicker : Ticker {
        override fun currentTime(): Long = System.currentTimeMillis()

        override fun getInterval(start: Long, end: Long): Long = end - start

        override fun getUnit(): TimeUnit = TimeUnit.MILLISECONDS
    }

    /**
     * Nano Second
     */
    class NanoTicker : Ticker {
        @SuppressLint("ObsoleteSdkInt")
        override fun currentTime(): Long {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                SystemClock.elapsedRealtimeNanos()
            } else {
                System.nanoTime()
            }
        }

        override fun getInterval(start: Long, end: Long): Long = end - start

        override fun getUnit(): TimeUnit = TimeUnit.NANOSECONDS
    }

    companion object {
        private const val PREFIX = "----"
        private const val MAX_TITLE_LENGTH = 30
        private const val PAD_LIMIT = 8192

        @JvmStatic
        private fun repeat(ch: Char, repeat: Int): String {
            if (repeat <= 0) {
                return ""
            }
            val buf = CharArray(repeat)
            Arrays.fill(buf, ch)
            return String(buf)
        }

        @JvmStatic
        fun repeat(str: String?, repeat: Int): String? {
            if (str == null) {
                return null
            }
            if (repeat <= 0) {
                return ""
            }
            val inputLength = str.length
            if (repeat == 1 || inputLength == 0) {
                return str
            }
            if (inputLength == 1 && repeat <= PAD_LIMIT) {
                return repeat(str[0], repeat)
            }

            val outputLength = inputLength * repeat
            when (inputLength) {
                1 -> return repeat(str[0], repeat)
                2 -> {
                    val ch0 = str[0]
                    val ch1 = str[1]
                    val output2 = CharArray(outputLength)
                    var i = repeat * 2 - 2
                    while (i >= 0) {
                        output2[i] = ch0
                        output2[i + 1] = ch1
                        i -= 2
                    }
                    return String(output2)
                }

                else -> {
                    val buf = StringBuilder(outputLength)
                    for (i in 0 until repeat) {
                        buf.append(str)
                    }
                    return buf.toString()
                }
            }
        }
    }
}
