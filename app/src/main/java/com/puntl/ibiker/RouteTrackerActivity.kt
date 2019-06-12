package com.puntl.ibiker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.support.design.widget.TabLayout
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.VolleyError
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.puntl.ibiker.companions.Base64Xcoder
import com.puntl.ibiker.companions.RouteProvider
import com.puntl.ibiker.companions.SessionProvider
import com.puntl.ibiker.models.LocationUpdate
import com.puntl.ibiker.models.Route
import com.puntl.ibiker.models.RouteStop
import com.puntl.ibiker.services.LocationTrackerService
import com.puntl.ibiker.services.ServiceVolley
import com.squareup.moshi.Moshi
import kotlinx.android.synthetic.main.activity_route_tracker.*
import kotlinx.android.synthetic.main.fragment_record_info.*
import kotlinx.android.synthetic.main.fragment_photos.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

const val MILLIS_IN_SECOND = 1000L
const val SECONDS_IN_MINUTE = 60
const val MINUTES_IN_HOUR = 60
const val SECONDS_IN_HOUR = 3600
const val METERS_IN_KILOMETER = 1000

private const val TAG = "RouteTrackerActivity"
private const val BOUNDS_OFFSET = 100
private const val HANDLER_DELAY = 1000L
private const val REQUEST_PHOTO_CAPTURE = 1
private const val ROUTE_URL = "/route"

class RouteTrackerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private lateinit var recordStatus: RecordStatus

    private val locationUpdates = mutableListOf<LocationUpdate>()
    private val routeStops = mutableListOf<RouteStop>()
    private val photoPaths = mutableListOf<String>()

    private var startTime = 0L
    private var pauseTime = 0L
    private var delta = 0L
//    private var lastPhotoFilePath = ""
    private var imageIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        setContentView(R.layout.activity_route_tracker)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupTabLayout()

        startTime = System.currentTimeMillis()

        startTimer()

        recordStatus = RecordStatus.RUNNING
    }

    override fun onResume() {
        Log.i(TAG, "On resume called...")
        super.onResume()

        locationUpdates.clear()
        locationUpdates.addAll(LocationTrackerService.locationUpdates)
        updateMap()

        if (recordStatus == RecordStatus.PAUSED) return

        Log.i(TAG, "Registering broadcast receiver...")
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                locationUpdates.clear()
                intent?.extras?.getParcelableArray(LocationTrackerService.BROADCAST_EXTRA_LOCATIONS)
                    ?.forEach { locationUpdates.add(it as LocationUpdate) }
                updateMap()
            }
        }.also { registerReceiver(it, IntentFilter(LocationTrackerService.BROADCAST_ACTION)) }

        handler.postDelayed(runnable, HANDLER_DELAY)
    }

    override fun onBackPressed() {
        Log.i(TAG, "On back pressed called...")
        AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(getString(R.string.is_user_sure))
            .setMessage(getString(R.string.stop_recording_dialog_message))
            .setPositiveButton("Yes") { _, _ ->
                Intent(applicationContext, MainActivity::class.java).also { startActivity(it) }
                finish()
            }
            .setNegativeButton("No") { _, _ ->
                //pass
            }
            .show()
    }

    override fun onPause() {
        Log.i(TAG, "On pause called...")
        super.onPause()

        try {
            Log.i(TAG, "Unregistering broadcast receiver...")
            unregisterReceiver(broadcastReceiver)
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "Broadcast receiver probably already unregistered, skipping...")
        }

        Log.i(TAG, "Removing callbacks...")
        handler.removeCallbacks(runnable)
    }

    override fun onDestroy() {
        Log.i(TAG, "On destroy called...")
        super.onDestroy()

        Log.i(TAG, "Stopping location tracker service...")
        stopService(Intent(applicationContext, LocationTrackerService::class.java))
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
    }

    fun onFlowClick(view: View) {
        recordStatus = when (recordStatus) {
            RecordStatus.PAUSED -> {
                delta += System.currentTimeMillis() - pauseTime
                handler.postDelayed(runnable, HANDLER_DELAY)
                registerReceiver(broadcastReceiver, IntentFilter(LocationTrackerService.BROADCAST_ACTION))
                LocationTrackerService.isRunning = true
                flowButton.text = getString(R.string.pause_recording)
                saveButton.visibility = View.INVISIBLE
                RecordStatus.RUNNING
            }
            RecordStatus.RUNNING -> {
                pauseTime = System.currentTimeMillis()
                handler.removeCallbacks(runnable)
                unregisterReceiver(broadcastReceiver)
                LocationTrackerService.isRunning = false
                flowButton.text = getString(R.string.resume_recording)
                saveButton.visibility = View.VISIBLE
                RecordStatus.PAUSED
            }
        }
    }

    fun onPhotoCaptureClick(view: View) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePhotoIntent ->
            takePhotoIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createPhotoFile()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                    null
                }

                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        applicationContext,
                        "com.example.android.fileprovider",
                        it
                    )
                    takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePhotoIntent, REQUEST_PHOTO_CAPTURE)
                }
            }
        }
    }

    fun onSaveRouteClick(view: View) {
        AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(getString(R.string.is_user_sure))
            .setMessage(getString(R.string.save_recording_dialog_message))
            .setPositiveButton("Yes") { _, _ ->
                saveRoute()
            }
            .setNegativeButton("No") { _, _ ->
                Intent(applicationContext, MainActivity::class.java).also { startActivity(it) }
                finish()
            }
            .show()
    }

    private fun setupTabLayout() {
        val fragmentAdapter = RecordFragmentPagerAdapter(applicationContext, supportFragmentManager, tabLayout.tabCount)
        viewPager.adapter = fragmentAdapter

        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))

        tabLayout!!.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewPager!!.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {

            }

            override fun onTabReselected(tab: TabLayout.Tab) {

            }
        })
    }

    private fun startTimer() {
        handler = Handler()
        runnable = object : Runnable {
            override fun run() {
                val tripTimeMillis = System.currentTimeMillis() - startTime - delta

                var seconds = tripTimeMillis / MILLIS_IN_SECOND
                var minutes = seconds / SECONDS_IN_MINUTE
                val hours = minutes / MINUTES_IN_HOUR

                seconds %= SECONDS_IN_MINUTE
                minutes %= MINUTES_IN_HOUR

                val secondsString = if (seconds < 10) "0$seconds" else "$seconds"
                val minutesString = if (minutes < 10) "0$minutes" else "$minutes"
                val hoursString = if (hours < 10) "0$hours" else "$hours"

                timeTextView?.text = getString(R.string.record_time, hoursString, minutesString, secondsString)

                handler.postDelayed(this, HANDLER_DELAY)
            }
        }
        runnable.run()
    }

    private fun updateMap() {
        if (!::mMap.isInitialized or locationUpdates.isEmpty()) return

        mMap.clear()

        val latLngs = RouteProvider.getLatLngs(locationUpdates)

        mMap.addPolyline(
            PolylineOptions()
                .clickable(false)
                .addAll(latLngs)
        )

        val builder = LatLngBounds.builder()

        latLngs.forEach { builder.include(it) }

        val bounds = builder.build()
        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_OFFSET)

        mMap.addMarker(MarkerOptions().position(latLngs.first()).title(getString(R.string.start_pin_title)))

        routeStops.forEach {
            mMap.addMarker(MarkerOptions().position(it.geoTag))
                .setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
        }

        mMap.moveCamera(cameraUpdate)

        val lastLocationSpeed = (locationUpdates.last().location.speed) * SECONDS_IN_HOUR / METERS_IN_KILOMETER
        speedTextView.text = getString(R.string.current_speed, String.format("%.2f", lastLocationSpeed))

        val totalDistance = RouteProvider.getTotalDistance(locationUpdates)

        distanceTextView.text =
            getString(R.string.total_distance, String.format("%.3f", totalDistance / METERS_IN_KILOMETER))
    }

    @Throws(IOException::class)
    private fun createPhotoFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "PNG_${timeStamp}_",
            ".png",
            storageDir
        ).apply {
            photoPaths.add(absolutePath)
        }
    }

    private fun saveRoute() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val userId = SessionProvider.getUser(sharedPreferences).userId

        val routeToSave =
            Route(
                null,
                userId,
                false,
                startTime,
                System.currentTimeMillis(),
                delta,
                RouteProvider.getTotalDistance(locationUpdates),
                RouteProvider.getLatLngs(locationUpdates),
                RouteProvider.getAverages(locationUpdates),
                routeStops,
                null,
                null,
                null,
                mutableListOf()
            )

        val volley = ServiceVolley()
        val moshi = Moshi.Builder().build()
        val moshiAdapter = moshi.adapter(Route::class.java)
        val json = moshiAdapter.toJson(routeToSave)

        volley.callApi(ROUTE_URL, Request.Method.POST, JSONObject(json), TAG,
            { response -> afterSaveSetup(response) },
            { error -> handleSaveError(error) }
        )
    }

    private fun handleSaveError(error: VolleyError?) {
        val messageJSON = JSONObject(String(error?.networkResponse!!.data))
        val messageString = messageJSON.get("message").toString()
        Toast.makeText(this, messageString, Toast.LENGTH_LONG).show()
    }

    private fun afterSaveSetup(response: JSONObject?) {
        Intent(applicationContext, MainActivity::class.java).also { startActivity(it) }
        finish()
    }

    private fun loadPicture() {
        if (imageIndex < 0) imageIndex = 0
        if (imageIndex > photoPaths.lastIndex) imageIndex = photoPaths.lastIndex

        val options = BitmapFactory.Options().apply {
            inSampleSize = 7
        }

        BitmapFactory.decodeFile(photoPaths[imageIndex], options)?.also { bitmap ->
            imageView.apply {
                    setPadding(0, 0, 0, 10)
                    adjustViewBounds = true
                    setImageBitmap(bitmap)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        previousActionButton.setOnClickListener {
            imageIndex--
            loadPicture()
        }

        nextActionButton.setOnClickListener {
            imageIndex++
            loadPicture()
        }

        if (requestCode == REQUEST_PHOTO_CAPTURE && resultCode == RESULT_OK) {
            loadPicture()

            val imageString = Base64Xcoder.imagePathToString(photoPaths.last())

            val lastLocationUpdate = LocationTrackerService.locationUpdates.last()
            val lastLatitude = lastLocationUpdate.location.latitude
            val lastLongitude = lastLocationUpdate.location.longitude

            RouteStop(imageString, null, LatLng(lastLatitude, lastLongitude)).let { routeStops.add(it) }
        }
    }
}