package com.kernelflux.traceharbor.resource

import android.Manifest
import android.annotation.TargetApi
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobServiceEngine
import android.app.job.JobWorkItem
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.ArrayList
import java.util.HashMap

abstract class TraceHarborJobIntentService : Service() {
    private var mJobImpl: CompatJobEngine? = null
    private var mCompatWorkEnqueuer: WorkEnqueuer? = null
    private var mCurProcessor: CommandProcessor? = null
    private var mInterruptIfStopped = false
    private var mStopped = false
    private var mDestroyed = false
    private val mCompatQueue: ArrayList<CompatWorkItem>? =
        if (Build.VERSION.SDK_INT >= 26) {
            null
        } else {
            ArrayList()
        }

    abstract class WorkEnqueuer(
        val mComponentName: ComponentName,
    ) {
        var mHasJobId = false
        var mJobId = 0

        fun ensureJobId(jobId: Int) {
            if (!mHasJobId) {
                mHasJobId = true
                mJobId = jobId
            } else if (mJobId != jobId) {
                throw IllegalArgumentException("Given job ID $jobId is different than previous $mJobId")
            }
        }

        abstract fun enqueueWork(work: Intent)

        open fun serviceStartReceived() {}

        open fun serviceProcessingStarted() {}

        open fun serviceProcessingFinished() {}
    }

    interface CompatJobEngine {
        fun compatGetBinder(): IBinder?

        fun dequeueWork(): GenericWorkItem?
    }

    class CompatWorkEnqueuer(
        context: Context,
        cn: ComponentName,
    ) : WorkEnqueuer(cn) {
        private val mContext = context.applicationContext
        private val mLaunchWakeLock: PowerManager.WakeLock?
        private val mRunWakeLock: PowerManager.WakeLock?
        private var mLaunchingService = false
        private var mServiceProcessing = false

        init {
            if (mContext.checkPermission(Manifest.permission.WAKE_LOCK, Process.myPid(), Process.myUid()) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                mLaunchWakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, cn.className + ":launch").apply {
                        setReferenceCounted(false)
                    }
                mRunWakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, cn.className + ":run").apply {
                        setReferenceCounted(false)
                    }
            } else {
                TraceHarborLog.w(
                    TAG,
                    "it would be better to grant WAKE_LOCK permission to your app so that tinker can use WakeLock to keep system awake.",
                )
                mLaunchWakeLock = null
                mRunWakeLock = null
            }
        }

        override fun enqueueWork(work: Intent) {
            val intent = Intent(work)
            intent.component = mComponentName
            try {
                if (mContext.startService(intent) != null) {
                    synchronized(this) {
                        if (!mLaunchingService) {
                            mLaunchingService = true
                            if (!mServiceProcessing && mLaunchWakeLock != null) {
                                mLaunchWakeLock.acquire(60 * 1000L)
                            }
                        }
                    }
                }
            } catch (thr: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, thr, "Exception occurred.")
            }
        }

        override fun serviceStartReceived() {
            synchronized(this) {
                mLaunchingService = false
            }
        }

        override fun serviceProcessingStarted() {
            synchronized(this) {
                if (!mServiceProcessing) {
                    mServiceProcessing = true
                    mRunWakeLock?.acquire(10 * 60 * 1000L)
                    mLaunchWakeLock?.release()
                }
            }
        }

        override fun serviceProcessingFinished() {
            synchronized(this) {
                if (mServiceProcessing) {
                    if (mLaunchingService && mLaunchWakeLock != null) {
                        mLaunchWakeLock.acquire(60 * 1000L)
                    }
                    mServiceProcessing = false
                    mRunWakeLock?.release()
                }
            }
        }
    }

    @RequiresApi(26)
    class JobServiceEngineImpl(
        private val mService: TraceHarborJobIntentService,
    ) : JobServiceEngine(mService), CompatJobEngine {
        private val mLock = Any()
        private var mParams: JobParameters? = null

        inner class WrapperWorkItem(
            private val mJobWork: JobWorkItem,
        ) : GenericWorkItem {
            override fun getIntent(): Intent = mJobWork.intent

            override fun complete() {
                synchronized(mLock) {
                    mParams?.completeWork(mJobWork)
                }
            }
        }

        override fun compatGetBinder(): IBinder = binder

        override fun onStartJob(params: JobParameters): Boolean {
            synchronized(mLock) {
                mParams = params
            }
            mService.ensureProcessorRunningLocked(false)
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean {
            val result = mService.doStopCurrentWork()
            synchronized(mLock) {
                mParams = null
            }
            return result
        }

        override fun dequeueWork(): GenericWorkItem? {
            val work: JobWorkItem
            synchronized(mLock) {
                val params = mParams ?: return null
                try {
                    work = params.dequeueWork() ?: return null
                } catch (thr: Throwable) {
                    TraceHarborLog.printErrStackTrace(TAG_JOB_IMPL, thr, "exception occurred.")
                    return null
                }
            }
            val intent = work.intent ?: return null
            intent.setExtrasClassLoader(mService.classLoader)
            return WrapperWorkItem(work)
        }

        companion object {
            private const val TAG_JOB_IMPL = "JobServiceEngineImpl"
        }
    }

    @RequiresApi(26)
    class JobWorkEnqueuer(
        context: Context,
        cn: ComponentName,
        jobId: Int,
    ) : WorkEnqueuer(cn) {
        private val mJobInfo: JobInfo
        private val mJobScheduler: JobScheduler

        init {
            ensureJobId(jobId)
            mJobInfo = JobInfo.Builder(jobId, mComponentName).setOverrideDeadline(0).build()
            mJobScheduler =
                context.applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        }

        override fun enqueueWork(work: Intent) {
            mJobScheduler.enqueue(mJobInfo, JobWorkItem(work))
        }
    }

    interface GenericWorkItem {
        fun getIntent(): Intent

        fun complete()
    }

    inner class CompatWorkItem(
        private val mIntent: Intent,
        private val mStartId: Int,
    ) : GenericWorkItem {
        override fun getIntent(): Intent = mIntent

        override fun complete() {
            stopSelf(mStartId)
        }
    }

    inner class CommandProcessor : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg params: Void?): Void? {
            var work = dequeueWork()
            while (work != null) {
                onHandleWork(work.getIntent())
                work.complete()
                work = dequeueWork()
            }
            return null
        }

        override fun onCancelled(aVoid: Void?) {
            processorFinished()
        }

        override fun onPostExecute(aVoid: Void?) {
            processorFinished()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            mJobImpl = JobServiceEngineImpl(this)
            mCompatWorkEnqueuer = null
        } else {
            mJobImpl = null
            val cn = ComponentName(this, this::class.java)
            mCompatWorkEnqueuer = getWorkEnqueuer(this, cn, false, 0)
        }
    }

    override fun onStartCommand(@Nullable intent: Intent?, flags: Int, startId: Int): Int {
        val compatQueue = mCompatQueue
        if (compatQueue != null) {
            mCompatWorkEnqueuer?.serviceStartReceived()
            synchronized(compatQueue) {
                compatQueue.add(CompatWorkItem(intent ?: Intent(), startId))
                ensureProcessorRunningLocked(true)
            }
            return START_REDELIVER_INTENT
        }
        return START_NOT_STICKY
    }

    override fun onBind(@NonNull intent: Intent): IBinder? {
        return mJobImpl?.compatGetBinder()
    }

    override fun onDestroy() {
        super.onDestroy()
        val compatQueue = mCompatQueue
        if (compatQueue != null) {
            synchronized(compatQueue) {
                mDestroyed = true
                mCompatWorkEnqueuer?.serviceProcessingFinished()
            }
        }
    }

    protected abstract fun onHandleWork(@NonNull intent: Intent)

    fun setInterruptIfStopped(interruptIfStopped: Boolean) {
        mInterruptIfStopped = interruptIfStopped
    }

    fun isStopped(): Boolean = mStopped

    open fun onStopCurrentWork(): Boolean = true

    fun doStopCurrentWork(): Boolean {
        mCurProcessor?.cancel(mInterruptIfStopped)
        mStopped = true
        return onStopCurrentWork()
    }

    @TargetApi(11)
    fun ensureProcessorRunningLocked(reportStarted: Boolean) {
        if (mCurProcessor == null) {
            mCurProcessor = CommandProcessor()
            if (mCompatWorkEnqueuer != null && reportStarted) {
                mCompatWorkEnqueuer?.serviceProcessingStarted()
            }
            mCurProcessor?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    fun processorFinished() {
        val compatQueue = mCompatQueue
        if (compatQueue != null) {
            synchronized(compatQueue) {
                mCurProcessor = null
                if (compatQueue.isNotEmpty()) {
                    ensureProcessorRunningLocked(false)
                } else if (!mDestroyed) {
                    mCompatWorkEnqueuer?.serviceProcessingFinished()
                }
            }
        }
    }

    fun dequeueWork(): GenericWorkItem? {
        mJobImpl?.let { return it.dequeueWork() }
        val compatQueue = mCompatQueue ?: return null
        synchronized(compatQueue) {
            return if (compatQueue.isNotEmpty()) compatQueue.removeAt(0) else null
        }
    }

    companion object {
        const val TAG = "TraceHarbor.JobIntentService"
        const val DEBUG = false

        private val sLock = Any()
        private val sClassWorkEnqueuer: HashMap<ComponentName, WorkEnqueuer> = HashMap()

        @JvmStatic
        fun enqueueWork(
            @NonNull context: Context,
            @NonNull cls: Class<*>,
            jobId: Int,
            @NonNull work: Intent,
        ) {
            enqueueWork(context, ComponentName(context, cls), jobId, work)
        }

        @JvmStatic
        fun enqueueWork(
            @NonNull context: Context,
            @NonNull component: ComponentName,
            jobId: Int,
            @NonNull work: Intent,
        ) {
            if (work == null) {
                throw IllegalArgumentException("work must not be null")
            }
            synchronized(sLock) {
                val we = getWorkEnqueuer(context, component, true, jobId)
                we.ensureJobId(jobId)
                we.enqueueWork(work)
            }
        }

        fun getWorkEnqueuer(context: Context, cn: ComponentName, hasJobId: Boolean, jobId: Int): WorkEnqueuer {
            var we = sClassWorkEnqueuer[cn]
            if (we == null) {
                we =
                    if (Build.VERSION.SDK_INT >= 26) {
                        if (!hasJobId) {
                            throw IllegalArgumentException("Can't be here without a job id")
                        }
                        JobWorkEnqueuer(context, cn, jobId)
                    } else {
                        CompatWorkEnqueuer(context, cn)
                    }
                sClassWorkEnqueuer[cn] = we
            }
            return we
        }
    }
}

