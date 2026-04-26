package com.kernelflux.traceharbor.resource.watcher

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

class RetryableTaskExecutor(
    delayMillis: Long,
    handleThread: HandlerThread,
) {
    private val mBackgroundHandler: Handler = Handler(handleThread.looper)
    private val mMainHandler: Handler = Handler(Looper.getMainLooper())
    private var mDelayMillis: Long = delayMillis

    interface RetryableTask {
        enum class Status {
            DONE,
            RETRY,
        }

        fun execute(): Status
    }

    fun setDelayMillis(delayed: Long) {
        mDelayMillis = delayed
    }

    fun executeInMainThread(task: RetryableTask) {
        postToMainThreadWithDelay(task, 0)
    }

    fun executeInBackground(task: RetryableTask) {
        postToBackgroundWithDelay(task, 0)
    }

    fun clearTasks() {
        mBackgroundHandler.removeCallbacksAndMessages(null)
        mMainHandler.removeCallbacksAndMessages(null)
    }

    fun quit() {
        clearTasks()
    }

    private fun postToMainThreadWithDelay(task: RetryableTask, failedAttempts: Int) {
        mMainHandler.postDelayed(
            {
                val status = task.execute()
                if (status == RetryableTask.Status.RETRY) {
                    postToMainThreadWithDelay(task, failedAttempts + 1)
                }
            },
            mDelayMillis,
        )
    }

    private fun postToBackgroundWithDelay(task: RetryableTask, failedAttempts: Int) {
        mBackgroundHandler.postDelayed(
            {
                val status = task.execute()
                if (status == RetryableTask.Status.RETRY) {
                    postToBackgroundWithDelay(task, failedAttempts + 1)
                }
            },
            mDelayMillis,
        )
    }
}

