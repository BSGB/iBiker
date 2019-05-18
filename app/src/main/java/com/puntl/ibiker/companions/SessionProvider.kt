package com.puntl.ibiker.companions

import android.content.SharedPreferences
import com.puntl.ibiker.models.LoggedUser
import org.json.JSONObject
import java.nio.charset.Charset

class SessionProvider {
    companion object {
        fun getUser(sharedPreferences: SharedPreferences): LoggedUser {
            val userToken = sharedPreferences.getString("user_token", null)
                val rawData = userToken?.split(".")!![1]
                val body = android.util.Base64.decode(rawData, android.util.Base64.DEFAULT)
                val json = JSONObject(String(body, Charset.defaultCharset()))
                return LoggedUser(json["iss"].toString(), json["sub"].toString(), json["exp"].toString().toLong())
        }

        fun getUserToken(sharedPreferences: SharedPreferences): String? {
            return sharedPreferences.getString("user_token", null)
        }
    }
}