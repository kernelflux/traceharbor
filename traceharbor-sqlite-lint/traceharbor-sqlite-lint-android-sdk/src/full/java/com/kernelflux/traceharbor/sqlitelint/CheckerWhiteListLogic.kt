package com.kernelflux.traceharbor.sqlitelint

import android.content.Context
import android.content.res.XmlResourceParser
import com.kernelflux.traceharbor.sqlitelint.util.SLog
import com.kernelflux.traceharbor.sqlitelint.util.SQLiteLintUtil
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class CheckerWhiteListLogic private constructor() {
    companion object {
        private const val TAG = "SQLiteLint.CheckerWhiteListLogic"
        private const val TAG_CHECKER = "checker"
        private const val ATTRIBUTE_CHECKER_NAME = "name"
        private const val TAG_WHITE_LIST_ELEMENT = "element"

        @JvmStatic
        fun setWhiteList(context: Context, concernedDbPath: String, xmlResId: Int) {
            val parser: XmlResourceParser = try {
                context.resources.getXml(xmlResId)
            } catch (e: Exception) {
                SLog.w(TAG, "buildWhiteListSet: getResources exp=%s", e.localizedMessage)
                return
            }

            try {
                var protectCnt = 0
                var eventType = parser.eventType
                var enclosedCheckerName: String? = null
                val whiteListMap = HashMap<String, MutableList<String>>()
                while (eventType != XmlResourceParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlResourceParser.START_DOCUMENT -> {
                        }

                        XmlResourceParser.START_TAG -> {
                            val tagName = parser.name
                            if (TAG_CHECKER.equals(tagName, ignoreCase = true)) {
                                enclosedCheckerName = parser.getAttributeValue(null, ATTRIBUTE_CHECKER_NAME)
                            }
                            if (TAG_WHITE_LIST_ELEMENT.equals(tagName, ignoreCase = true)
                                && !SQLiteLintUtil.isNullOrNil(enclosedCheckerName)
                            ) {
                                val text = parser.nextText()
                                val list = whiteListMap.getOrPut(enclosedCheckerName!!) { ArrayList() }
                                list.add(text)
                                SLog.v(
                                    TAG,
                                    "buildWhiteListMap: add to whiteList[%s]: %s",
                                    enclosedCheckerName,
                                    text
                                )
                            }
                        }

                        XmlResourceParser.END_TAG -> {
                        }

                        else -> {
                            SLog.w(TAG, "buildWhiteListMap: default branch , eventType:%d", eventType)
                        }
                    }
                    parser.next()
                    eventType = parser.eventType
                    if (++protectCnt > 10000) {
                        SLog.e(TAG, "buildWhiteListMap:maybe dead loop!!")
                        break
                    }
                }
                addToNative(concernedDbPath, whiteListMap)
            } catch (e: XmlPullParserException) {
                SLog.w(TAG, "buildWhiteListSet: exp=%s", e.localizedMessage)
            } catch (e: IOException) {
                SLog.w(TAG, "buildWhiteListSet: exp=%s", e.localizedMessage)
            }

            parser.close()
        }

        private fun addToNative(concernedDbPath: String, whiteListMap: Map<String, List<String>>?) {
            if (whiteListMap == null) {
                return
            }

            val checkerArr = Array(whiteListMap.size) { "" }
            val whiteListArr = Array(whiteListMap.size) { emptyArray<String>() }
            var index = 0
            for ((key, value) in whiteListMap) {
                checkerArr[index] = key
                whiteListArr[index] = value.toTypedArray()
                index++
            }
            SQLiteLintNativeBridge.nativeAddToWhiteList(concernedDbPath, checkerArr, whiteListArr)
        }
    }
}
