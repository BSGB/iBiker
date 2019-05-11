package com.puntl.ibiker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Point
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.support.design.widget.TabLayout
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.puntl.ibiker.services.LocationTrackerService
import kotlinx.android.synthetic.main.activity_route_tracker.*
import kotlinx.android.synthetic.main.fragment_info.*
import kotlinx.android.synthetic.main.fragment_photos.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

const val MILLIS_IN_SECOND = 1000L
const val SECONDS_IN_MINUTE = 60
const val MINUTES_IN_HOUR = 60

private const val TAG = "RouteTrackerActivity"
private const val BOUNDS_OFFSET = 100
private const val HANDLER_DELAY = 1000L
private const val REQUEST_PHOTO_CAPTURE = 1

class RouteTrackerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private lateinit var recordStatus: RecordStatus
    private var startTime = 0L
    private var pauseTime = 0L
    private var delta = 0L
    private var displayWidth = 0

    private val locations = mutableListOf<Location>()
    private var photoFilePaths = mutableListOf<String>()
    private lateinit var size: Point

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

        windowManager.defaultDisplay.also {
            size = Point()
            it.getSize(size)
            displayWidth = size.x
        }
    }

    override fun onResume() {
        Log.i(TAG, "On resume called...")
        super.onResume()

        locations.clear()
        locations.addAll(LocationTrackerService.locations)
        updateMap()

        if (recordStatus == RecordStatus.PAUSED) return

        Log.i(TAG, "Registering broadcast receiver...")
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                locations.clear()
                intent?.extras?.getParcelableArray(LocationTrackerService.BROADCAST_EXTRA_LOCATIONS)
                    ?.forEach { locations.add(it as Location) }
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
                RecordStatus.RUNNING
            }
            RecordStatus.RUNNING -> {
                pauseTime = System.currentTimeMillis()
                handler.removeCallbacks(runnable)
                unregisterReceiver(broadcastReceiver)
                LocationTrackerService.isRunning = false
                flowButton.text = getString(R.string.resume_recording)
                RecordStatus.PAUSED
            }
        }
    }

    fun onPhotoCaptureClick(view: View) {
        dispatchTakePictureIntent()
    }

    private fun updateMap() {
        if (!::mMap.isInitialized or locations.isEmpty()) return

        mMap.clear()

        val latLngs = getLatLngs()

        mMap.addPolyline(
            PolylineOptions()
                .clickable(false)
                .addAll(latLngs)
        )

        val builder = LatLngBounds.builder()

        latLngs.forEach { builder.include(it) }

        val bounds = builder.build()
        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_OFFSET)

        mMap.addMarker(MarkerOptions().position(latLngs[0]).title(getString(R.string.start_pin_title)))
        mMap.moveCamera(cameraUpdate)
    }

    private fun getLatLngs(): ArrayList<LatLng> {
        val latLngs = arrayListOf<LatLng>()
        locations.forEach { latLngs.add(LatLng(it.latitude, it.longitude)) }
        return latLngs
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

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePhotoIntent ->
            takePhotoIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createPhotoFile()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                    null
                }

                Log.i(TAG, photoFile.toString())
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

    @Throws(IOException::class)
    private fun createPhotoFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            photoFilePaths.add(absolutePath)
        }
    }

    private fun setPic() {
        val targetW: Int = displayWidth
        val lastCapturedPath = photoFilePaths.last()

        val bmOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(lastCapturedPath, this)
            val photoW: Int = outWidth

            val scaleFactor: Int = Math.min(photoW / targetW, 0)

            inJustDecodeBounds = false
            inSampleSize = scaleFactor
        }

        BitmapFactory.decodeFile(lastCapturedPath, bmOptions)?.also { bitmap ->
            photosLinearLayout?.addView(ImageView(this).apply {
                setPadding(0, 0, 0, 10)
                adjustViewBounds = true
                setImageBitmap(bitmap)
            })
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PHOTO_CAPTURE && resultCode == RESULT_OK) {
            setPic()
        }
    }
}