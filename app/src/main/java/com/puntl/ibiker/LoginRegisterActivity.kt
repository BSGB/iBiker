package com.puntl.ibiker

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.VolleyError
import com.puntl.ibiker.services.ServiceVolley
import kotlinx.android.synthetic.main.activity_login_register.*
import org.json.JSONObject

/**
 * A login screen that offers login/register via email/password.
 */

private const val REGISTER_URL = "/register"
private const val LOGIN_URL = "/login"
private const val TAG = "LoginRegisterActivity"

class LoginRegisterActivity : AppCompatActivity() {

    enum class LoginRegisterState {
        LOGIN,
        REGISTER
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_register)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        if (isUserLoggedIn()) {
            val mainIntent = Intent(this, MainActivity::class.java)
            startActivity(mainIntent)
        }

    }

    fun onLoginRegisterClick(view: View) {
        val emailString = emailEditText.text.toString()
        val passwordString = passwordEditText.text.toString()

        if (emailString.isBlank() || passwordString.isBlank()) {
            Toast.makeText(this, getString(R.string.fields_unfilled), Toast.LENGTH_LONG).show()
        } else if (!isEmailValid(emailString)) {
            Toast.makeText(this, getString(R.string.email_badly_formatted), Toast.LENGTH_LONG).show()
        } else {
            val state = if (loginRegisterState.isChecked) LoginRegisterState.REGISTER else LoginRegisterState.LOGIN
            val jsonObject = JSONObject()
            val serviceVolley = ServiceVolley()

            jsonObject.put("userEmail", emailString)
            jsonObject.put("userPassword", passwordString)

            when (state) {
                LoginRegisterState.LOGIN -> {
                    serviceVolley.callApi(LOGIN_URL, Request.Method.POST, jsonObject, TAG,
                        { response -> afterLoginSetup(response) },
                        { error -> handleLoginError(error) }
                    )
                }

                LoginRegisterState.REGISTER -> {
                    serviceVolley.callApi(REGISTER_URL, Request.Method.POST, jsonObject, TAG,
                        { response -> afterRegisterSetup(response) },
                        { error -> handleRegisterError(error) }
                    )
                }
            }
        }
    }

    private fun isEmailValid(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isUserLoggedIn(): Boolean {
        val userToken = sharedPreferences.getString("user_token", null)

        if (!userToken.isNullOrBlank()) return true
        return false
    }

    private fun afterRegisterSetup(response: JSONObject?) {
        Toast.makeText(this, getString(R.string.register_successful), Toast.LENGTH_LONG).show()
        loginRegisterState.isChecked = false
        emailEditText.text.clear()
        passwordEditText.text.clear()
        emailEditText.requestFocus()
    }

    private fun handleRegisterError(error: VolleyError) {
        val messageJSON = JSONObject(String(error.networkResponse.data))
        val messageString = messageJSON.get("message").toString()
        Toast.makeText(this, messageString, Toast.LENGTH_LONG).show()
    }

    private fun afterLoginSetup(response: JSONObject?) {
        Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_LONG).show()
        val token = response?.get("token").toString()
        sharedPreferences.edit().putString("user_token", token).apply()
        val mainIntent = Intent(this, MainActivity::class.java)
        startActivity(mainIntent)
    }

    private fun handleLoginError(error: VolleyError) {
        val messageJSON = JSONObject(String(error.networkResponse.data))
        val messageString = messageJSON.get("message").toString()
        Toast.makeText(this, messageString, Toast.LENGTH_LONG).show()
    }
}
