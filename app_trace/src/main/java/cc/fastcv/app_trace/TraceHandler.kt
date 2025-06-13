package cc.fastcv.app_trace

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Collections
import java.util.Date
import java.util.HashMap
import java.util.Locale

internal class TraceHandler {

    companion object {
        private const val SDK_VERSION = "1.0.0"

        private const val TAG = "AppTracer"
    }

    private var mIgnoredActivities = ArrayList<String>()
    private var mDeviceId: String? = null
    private var mDeviceInfo: Map<String, Any>? = null

    fun init(app: Application) {
        mDeviceId = getAndroidID(app.applicationContext)
        mDeviceInfo = getDeviceInfo(app.applicationContext)
        registerActivityLifecycleCallbacks(app)
    }

    /**
     * 获取 Android ID
     *
     * @param mContext Context
     * @return String
     */
    @SuppressLint("HardwareIds")
    private fun getAndroidID(mContext: Context): String? {
        var androidID: String? = ""
        try {
            androidID =
                Settings.Secure.getString(mContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return androidID
    }

    private fun getDeviceInfo(context: Context): Map<String, Any>? {
        val deviceInfo: MutableMap<String, Any> = HashMap()
        run {
            deviceInfo["\$lib"] = "Android"
            deviceInfo["\$lib_version"] = SDK_VERSION
            deviceInfo["\$os"] = "Android"
            deviceInfo["\$os_version"] =
                if (Build.VERSION.RELEASE == null) "UNKNOWN" else Build.VERSION.RELEASE
            deviceInfo["\$manufacturer"] =
                if (Build.MANUFACTURER == null) "UNKNOWN" else Build.MANUFACTURER
            if (TextUtils.isEmpty(Build.MODEL)) {
                deviceInfo["\$model"] = "UNKNOWN"
            } else {
                deviceInfo["\$model"] = Build.MODEL.trim()
            }
            try {
                val manager = context.packageManager
                val packageInfo =
                    manager.getPackageInfo(context.packageName, 0)
                deviceInfo["\$app_version"] = packageInfo?.versionName ?: "null"
                val labelRes = packageInfo.applicationInfo?.labelRes ?: 0
                deviceInfo["\$app_name"] = context.resources.getString(labelRes)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            val displayMetrics = context.resources.displayMetrics
            deviceInfo["\$screen_height"] = displayMetrics.heightPixels
            deviceInfo["\$screen_width"] = displayMetrics.widthPixels
            return Collections.unmodifiableMap(deviceInfo)
        }
    }

    private fun registerActivityLifecycleCallbacks(application: Application) {
        application.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                trackAppViewScreen(activity)
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private val mDateFormat = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss"
                + ".SSS", Locale.CHINA
    )

    fun ignoreAutoTrackActivity(activity: Class<*>?) {
        if (activity == null) {
            return
        }
        mIgnoredActivities.add(activity.canonicalName!!)
    }

    fun removeIgnoredActivity(activity: Class<*>?) {
        if (activity == null) {
            return
        }
        if (mIgnoredActivities.contains(activity.canonicalName!!)) {
            mIgnoredActivities.remove(activity.canonicalName!!)
        }
    }


    private fun trackAppViewScreen(activity: Activity?) {
        try {
            if (activity == null) {
                return
            }
            if (mIgnoredActivities.contains(activity::class.java.canonicalName ?: "")) {
                return
            }
            val properties = JSONObject()
            properties.put("\$activity", activity.javaClass.canonicalName)
            properties.put(
                "\$title",
                getActivityTitle(activity)
            )
            track("\$AppViewScreen", properties)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun getActivityTitle(activity: Activity?): String? {
        var activityTitle: String? = null
        if (activity == null) {
            return null
        }
        try {
            activityTitle = activity.title.toString()
            val toolbarTitle: String? = getToolbarTitle(activity)
            if (!TextUtils.isEmpty(toolbarTitle)) {
                activityTitle = toolbarTitle
            }
            if (TextUtils.isEmpty(activityTitle)) {
                val packageManager = activity.packageManager
                if (packageManager != null) {
                    val activityInfo = packageManager.getActivityInfo(activity.componentName, 0)
                    activityTitle = activityInfo.loadLabel(packageManager).toString()
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return activityTitle
    }

    private fun getToolbarTitle(activity: Activity): String? {
        try {
            val actionBar = activity.actionBar
            if (actionBar != null) {
                if (!TextUtils.isEmpty(actionBar.title)) {
                    return actionBar.title.toString()
                }
            } else {
                if (activity is AppCompatActivity) {
                    val appCompatActivity: AppCompatActivity = activity
                    val supportActionBar: ActionBar? =
                        appCompatActivity.supportActionBar
                    if (supportActionBar != null) {
                        if (!TextUtils.isEmpty(supportActionBar.title)) {
                            return supportActionBar.title.toString()
                        }
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return null
    }

    @Throws(JSONException::class)
    fun mergeJSONObject(source: JSONObject, dest: JSONObject) {
        val superPropertiesIterator = source.keys()
        while (superPropertiesIterator.hasNext()) {
            val key = superPropertiesIterator.next()
            val value = source[key]
            if (value is Date) {
                synchronized(mDateFormat) {
                    dest.put(
                        key,
                        mDateFormat.format(
                            value
                        )
                    )
                }
            } else {
                dest.put(key, value)
            }
        }
    }

    private fun formatJson(jsonStr: String?): String {
        return try {
            if (null == jsonStr || "" == jsonStr) {
                return ""
            }
            val sb = StringBuilder()
            var last: Char
            var current = '\u0000'
            var indent = 0
            var isInQuotationMarks = false
            for (element in jsonStr) {
                last = current
                current = element
                when (current) {
                    '"' -> {
                        if (last != '\\') {
                            isInQuotationMarks = !isInQuotationMarks
                        }
                        sb.append(current)
                    }

                    '{', '[' -> {
                        sb.append(current)
                        if (!isInQuotationMarks) {
                            sb.append('\n')
                            indent++
                            addIndentBlank(
                                sb,
                                indent
                            )
                        }
                    }

                    '}', ']' -> {
                        if (!isInQuotationMarks) {
                            sb.append('\n')
                            indent--
                            addIndentBlank(
                                sb,
                                indent
                            )
                        }
                        sb.append(current)
                    }

                    ',' -> {
                        sb.append(current)
                        if (last != '\\' && !isInQuotationMarks) {
                            sb.append('\n')
                            addIndentBlank(
                                sb,
                                indent
                            )
                        }
                    }

                    else -> sb.append(current)
                }
            }
            sb.toString()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun addIndentBlank(sb: java.lang.StringBuilder, indent: Int) {
        try {
            for (i in 0 until indent) {
                sb.append('\t')
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun track(eventName: String, properties: JSONObject?) {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("event", eventName)
            jsonObject.put("device_id", mDeviceId)
            val sendProperties = JSONObject(mDeviceInfo!!)
            if (properties != null) {
                mergeJSONObject(properties, sendProperties)
            }
            jsonObject.put("properties", sendProperties)
            jsonObject.put("time", System.currentTimeMillis())
            Log.i(TAG, formatJson(jsonObject.toString()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


}