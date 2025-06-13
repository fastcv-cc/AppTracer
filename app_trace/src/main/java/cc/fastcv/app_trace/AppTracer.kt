package cc.fastcv.app_trace

import android.app.Application
import org.json.JSONObject

object AppTracer {

    private val traceHandler = TraceHandler()

    fun attach(app: Application) {
        traceHandler.init(app)
    }

    /**
     * 指定不采集哪个 Activity 的页面浏览事件
     *
     * @param activity Activity
     */
    fun ignoreAutoTrackActivity(activity: Class<*>?) {
        traceHandler.ignoreAutoTrackActivity(activity)
    }

    /**
     * 恢复采集某个 Activity 的页面浏览事件
     *
     * @param activity Activity
     */
    fun removeIgnoredActivity(activity: Class<*>?) {
        traceHandler.removeIgnoredActivity(activity)
    }

    /**
     * track 事件
     *
     * @param eventName  String 事件名称
     * @param properties JSONObject 事件自定义属性
     */
    fun track(eventName: String, properties: JSONObject?) {
        traceHandler.track(eventName,properties)
    }



}