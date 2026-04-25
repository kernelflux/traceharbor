package com.kernelflux.traceharbor.lifecycle.owners

import com.kernelflux.traceharbor.util.TraceHarborLog

internal class ArrayListProxy<T>(
    private val mOrigin: ArrayList<T>,
    private val mListener: OnDataChangedListener,
) : ArrayList<T>() {

    interface OnDataChangedListener {
        fun onAdded(o: Any)
        fun onRemoved(o: Any)
    }

    override val size: Int
        get() = mOrigin.size

    override fun isEmpty(): Boolean = mOrigin.isEmpty()

    override fun contains(element: T): Boolean = mOrigin.contains(element)

    override fun iterator(): MutableIterator<T> = mOrigin.iterator()

    override fun toArray(): Array<Any> = mOrigin.toArray()

    override fun <T1 : Any?> toArray(a: Array<T1>): Array<T1> = mOrigin.toArray(a)

    override fun add(element: T): Boolean {
        val ret = mOrigin.add(element)
        notifyAdded(element)
        return ret
    }

    override fun remove(element: T): Boolean {
        val ret = mOrigin.remove(element)
        notifyRemoved(element)
        return ret
    }

    override fun containsAll(elements: Collection<T>): Boolean = mOrigin.containsAll(elements)

    override fun addAll(elements: Collection<T>): Boolean {
        val ret = mOrigin.addAll(elements)
        for (element in elements) {
            notifyAdded(element)
        }
        return ret
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        val ret = mOrigin.addAll(index, elements)
        for (element in elements) {
            notifyAdded(element)
        }
        return ret
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        val ret = mOrigin.removeAll(elements.toSet())
        for (element in elements) {
            notifyRemoved(element)
        }
        return ret
    }

    override fun retainAll(elements: Collection<T>): Boolean = mOrigin.retainAll(elements.toSet())

    override fun clear() {
        mOrigin.clear()
    }

    override fun get(index: Int): T = mOrigin[index]

    override fun set(index: Int, element: T): T = mOrigin.set(index, element)

    override fun add(index: Int, element: T) {
        mOrigin.add(index, element)
        notifyAdded(element)
    }

    override fun removeAt(index: Int): T {
        val ret = mOrigin.removeAt(index)
        notifyRemoved(ret)
        return ret
    }

    override fun indexOf(element: T): Int = mOrigin.indexOf(element)

    override fun lastIndexOf(element: T): Int = mOrigin.lastIndexOf(element)

    override fun listIterator(): MutableListIterator<T> = mOrigin.listIterator()

    override fun listIterator(index: Int): MutableListIterator<T> = mOrigin.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> =
        mOrigin.subList(fromIndex, toIndex)

    private fun notifyAdded(element: T) {
        try {
            if (element != null) {
                mListener.onAdded(element as Any)
            }
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        }
    }

    private fun notifyRemoved(element: T) {
        try {
            if (element != null) {
                mListener.onRemoved(element as Any)
            }
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        }
    }

    companion object {
        private const val TAG = "TraceHarbor.ArrayListProxy"
    }
}
