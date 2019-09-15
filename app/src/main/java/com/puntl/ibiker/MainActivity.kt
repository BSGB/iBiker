package com.puntl.ibiker

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.puntl.ibiker.services.LocationTrackerService

private const val REQUEST_CODE = 0

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
                Intent(applicationContext, LoginRegisterActivity::class.java).also { startActivity(it) }
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

    override fun onBackPressed() {
        finishAffinity()
    }

    fun onRecordRouteClick(view: View) {
        if (!isPermissionGranted()) {
            askForPermission()
        } else {
            startLocationTrackerService()
            startRouteTrackerActivity()
        }
    }

    fun onPublicationsClick(view: View) {
        Intent(this, PublicationCenterActivity::class.java).also { startActivity(it) }
    }

    fun onRoutesClick(view: View) {
        Intent(this, RouteGalleryActivity::class.java).also { startActivity(it) }
    }

    private fun isUserLoggedIn(): Boolean {
        val userToken = sharedPreferences.getString("user_token", null)

        if (!userToken.isNullOrBlank()) return true
        return false
    }

    private fun isPermissionGranted(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun askForPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE)
        }
    }

    private fun startLocationTrackerService() {
        val locationTrackerService = Intent(applicationContext, LocationTrackerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(locationTrackerService)
        } else startService(locationTrackerService)
    }

    private fun startRouteTrackerActivity() {
        Intent(applicationContext, RouteTrackerActivity::class.java).also { startActivity(it) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startLocationTrackerService()
                        startRouteTrackerActivity()
                    } else {
                        askForPermission()
                    }
                }
            }
        }
    }
}
