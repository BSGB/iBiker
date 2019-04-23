package com.puntl.ibiker

import android.app.Application
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

class RequestQueueProvider: Application() {

    companion object {
        private val TAG = RequestQueueProvider::class.java.simpleName
        var instance: RequestQueueProvider? = null
            private set
    }

    val requestQueue: RequestQueue? = null
    get() {
        if (field == null) {
            return Volley.newRequestQueue(applicationContext)
        }
        return field
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    fun <T> addToRequestQueue(request: Request<T>) {
        request.tag = TAG
        requestQueue?.add(request)
    }

    fun <T> addToRequestQueue(request: Request<T>, tag: String) {
        request.tag = if (tag.isEmpty()) TAG else tag
        requestQueue?.add(request)
    }

    fun cancelRequests(tag: Any) {
        if (requestQueue != null) {
            requestQueue!!.cancelAll(tag)
        }
    }
}