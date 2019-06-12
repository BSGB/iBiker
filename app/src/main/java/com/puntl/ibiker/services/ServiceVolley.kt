package com.puntl.ibiker.services

import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.puntl.ibiker.RequestQueueProvider
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ServiceVolley"
private const val BASE_URL = "http://192.168.0.178:8080/api"
//private const val BASE_URL = "http://10.0.2.2:8080/api"

class ServiceVolley {

    fun callApi(
        path: String,
        method: Int,
        params: JSONObject?,
        tag: String,
        completionHandler: (response: JSONObject?) -> Unit,
        errorHandler: (error: VolleyError) -> Unit
    ) {

        val jsonObjectRequestQueue = JsonObjectRequest(
            method, BASE_URL + path, params,
            Response.Listener<JSONObject?> { response ->
                completionHandler(response)
            },
            Response.ErrorListener { error ->
                errorHandler(error)
            })

        RequestQueueProvider.instance?.addToRequestQueue(jsonObjectRequestQueue, tag)
    }

    fun callApi(
        path: String,
        method: Int,
        params: JSONObject?,
        tag: String,
        customHeaders: HashMap<String, String>,
        completionHandler: (response: JSONObject?) -> Unit,
        errorHandler: (error: VolleyError) -> Unit
    ) {

        val jsonObjectRequestQueue = object : JsonObjectRequest(
            method, BASE_URL + path, params,
            Response.Listener<JSONObject> { response ->
                completionHandler(response)
            },
            Response.ErrorListener { error ->
                errorHandler(error)
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers.putAll(customHeaders)
                return headers
            }
        }

        RequestQueueProvider.instance?.addToRequestQueue(jsonObjectRequestQueue, tag)
    }
}