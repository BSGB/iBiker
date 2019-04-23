package com.puntl.ibiker

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.signOutMenuItem -> {
                sharedPreferences.edit().putString("user_token", null).apply()

                val loginRegisterActivity = Intent(applicationContext, LoginRegisterActivity::class.java)
                startActivity(loginRegisterActivity)

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        if (!isUserLoggedIn()) {
            val loginRegisterIntent = Intent(this, LoginRegisterActivity::class.java)
            startActivity(loginRegisterIntent)
        }
    }

    private fun isUserLoggedIn(): Boolean {
        val userToken = sharedPreferences.getString("user_token", null)

        if (!userToken.isNullOrBlank()) return true
        return false
    }
}
