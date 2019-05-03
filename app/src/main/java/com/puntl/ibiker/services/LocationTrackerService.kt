package com.puntl.ibiker.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import com.puntl.ibiker.R
import android.provider.Settings
import android.support.v4.content.ContextCompat

private const val UPDATE_TIME = 10000L
private const val MIN_DISTANCE = 0F
private const val NOTIFICATION_ID = 1
private const val NOTIFICATION_CHANNEL_ID = "location_service"
private const val NOTIFICATION_CHANNEL_DESC = "location_service_desc"
private const val NOTIFICATION_CHANNEL_NAME = "location_service_name"

class LocationTrackerService: Service() {

    companion object {
        const val BROADCAST_ACTION = "location_update"
        const val BROADCAST_EXTRA_LOCATIONS = "locations"
        var isRunning = false

        val locations = ArrayList<Location>()
    }

    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        isRunning = true

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        //foreground notification stuff
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
                .also {
                    it.description = NOTIFICATION_CHANNEL_DESC
                    notificationManager.createNotificationChannel(it)
                }
        }

        NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.location_service_notification_title))
            .setContentText(applicationContext.getString(R.string.location_service_notification_message))
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
            .also { startForeground(NOTIFICATION_ID, it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val locationBroadcast = Intent(BROADCAST_ACTION)
        var parcelableLocations: Array<Location?>

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location?) {
                if (!isRunning) return

                locations.add(location!!)

                parcelableLocations = arrayOfNulls(locations.size)

                locations.toArray(parcelableLocations)

                locationBroadcast.putExtra(BROADCAST_EXTRA_LOCATIONS, parcelableLocations).also { sendBroadcast(it) }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            }

            override fun onProviderEnabled(provider: String?) {
            }

            override fun onProviderDisabled(provider: String?) {
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(it)
                }
            }
        }

        locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_TIME,
                MIN_DISTANCE,
                locationListener
            )
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
        locations.clear()
        stopForeground(true)
    }
}