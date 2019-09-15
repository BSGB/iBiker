package com.puntl.ibiker

import android.content.Intent
import android.graphics.BitmapFactory
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.TabLayout
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Switch
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.VolleyError
import com.google.android.gms.maps.CameraUpdateFactory

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.puntl.ibiker.companions.Base64Xcoder
import com.puntl.ibiker.companions.DateTimeProvider
import com.puntl.ibiker.companions.SessionProvider
import com.puntl.ibiker.models.Route
import com.puntl.ibiker.services.ServiceVolley
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.android.synthetic.main.activity_publication_editor.*
import kotlinx.android.synthetic.main.fragment_editor_info.*
import kotlinx.android.synthetic.main.fragment_photos.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "PublicationEditorActivity"
private const val BOUNDS_OFFSET = 100
private const val ROUTE_URL = "/route"

class PublicationEditorActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var routeId: String
    private lateinit var route: Route
    private lateinit var spinnerAdapter: ArrayAdapter<CharSequence>
    private var imageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        setContentView(R.layout.activity_publication_editor)

        saveButton.visibility = View.INVISIBLE

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupTabLayout()

        routeId = intent.getStringExtra(PublicationCenterActivity.PUBLICATION_ID)

        loadRoute()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
    }

    fun onToggleMapClick(view: View) {
        val switch = view as Switch
        val supportFragmentTransaction = supportFragmentManager.beginTransaction()

        if (switch.isChecked) {
            supportFragmentTransaction.hide(map).commit()
        } else {
            supportFragmentTransaction.show(map).commit()
        }
    }

    private fun loadRoute() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val volley = ServiceVolley()
        val customHeader = hashMapOf<String, String>()

        customHeader["token"] = SessionProvider.getUserToken(sharedPreferences)?: return

        volley.callApi("$ROUTE_URL/$routeId", Request.Method.GET, null, TAG, customHeader,
            { response -> afterGetHandler(response) },
            { error -> handleGetError(error) }
        )
    }

    private fun afterGetHandler(response: JSONObject?) {
        val responseRoute = response?.toString()?: return
        val moshi = Moshi.Builder().build()
        val moshiAdapter: JsonAdapter<Route> = moshi.adapter(Route::class.java)

        spinnerAdapter = ArrayAdapter.createFromResource(this, R.array.bike_types, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bikeTypeSpinner.adapter = spinnerAdapter

        moshiAdapter.fromJson(responseRoute)?.also { route ->
            this.route = route

            setInfo()
            displayRoute()
            loadPicture()
        }?: return

        previousActionButton.setOnClickListener {
            imageIndex--
            loadPicture()
        }

        nextActionButton.setOnClickListener {
            imageIndex++
            loadPicture()
        }
    }

    private fun handleGetError(error: VolleyError) {
        val messageJSON = JSONObject(String(error.networkResponse.data))
        val messageString = messageJSON.get("message").toString()
        Toast.makeText(this, messageString, Toast.LENGTH_LONG).show()
    }

    fun onSaveClick(view: View) {
        val description = descriptionEditText?.text?.toString()
        val rating = difficultyRatingBar.rating
        val isPublished = publishSwitch.isChecked
        val bikeType = bikeTypeSpinner.selectedItem.toString()

        route.description = description
        route.difficulty = rating
        route.isPublished = isPublished
        route.bikeType = bikeType

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val customHeader = hashMapOf<String, String>()

        customHeader["token"] = SessionProvider.getUserToken(sharedPreferences)?: return

        val volley = ServiceVolley()
        val moshi = Moshi.Builder().build()
        val moshiAdapter = moshi.adapter(Route::class.java)
        val json = moshiAdapter.toJson(route)

        volley.callApi(ROUTE_URL, Request.Method.PUT, JSONObject(json), TAG, customHeader,
            { afterPutHandler() },
            { error -> handlePutError(error) }
        )
    }

    private fun afterPutHandler() {
        Intent(applicationContext, MainActivity::class.java).also { intent -> startActivity(intent) }
        Toast.makeText(this, getString(R.string.route_updated), Toast.LENGTH_LONG).show()
    }

    private fun handlePutError(error: VolleyError) {
        val messageJSON = JSONObject(String(error.networkResponse.data))
        val messageString = messageJSON.get("message").toString()
        Toast.makeText(this, messageString, Toast.LENGTH_LONG).show()
    }

    private fun setInfo() {
        startDateTextView.text = getString(R.string.start_date, DateTimeProvider.getDate(route.startTimeStamp, "dd/MM/yyyy HH:mm:ss"))
        endDateTextView.text = getString(R.string.end_date, DateTimeProvider.getDate(route.endTimeStamp, "dd/MM/yyyy HH:mm:ss"))

        val position = spinnerAdapter.getPosition(route.bikeType)
        bikeTypeSpinner.setSelection(position)

        DateTimeProvider.getDeltaTime(route.deltaTime).also { triple ->
            deltaTextView.text = getString(R.string.delta_time, triple.first, triple.second, triple.third)
        }

        stopsTextView.text = getString(R.string.stops_count, route.stops.size.toString())

        difficultyRatingBar.rating = route.difficulty ?: 0.0F

        descriptionEditText.setText(route.description ?: "")

        publishSwitch.isChecked = route.isPublished

        saveButton.visibility = View.VISIBLE
    }

    private fun displayRoute() {
        if (!::mMap.isInitialized) return

        mMap.addPolyline(
            PolylineOptions()
                .clickable(false)
                .addAll(route.waypoints)
        )

        val builder = LatLngBounds.builder()

        route.waypoints.forEach { builder.include(it) }

        val bounds = builder.build()
        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_OFFSET)

        mMap.addMarker(MarkerOptions().position(route.waypoints.first()).title(getString(R.string.start_pin_title)))
        mMap.addMarker(MarkerOptions().position(route.waypoints.last()).title(getString(R.string.end_pin_title)))

        route.stops.forEach {
            mMap.addMarker(MarkerOptions().position(it.geoTag))
                .setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
        }

        mMap.moveCamera(cameraUpdate)
    }

    private fun loadPicture() {
        if (imageIndex < 0) imageIndex = 0
        if (imageIndex > route.stops.lastIndex) imageIndex = route.stops.lastIndex

        val options = BitmapFactory.Options().apply {
            inSampleSize = 7
        }

        imageView.apply {
            setPadding(0, 0, 0, 10)
            adjustViewBounds = true
            setImageBitmap(Base64Xcoder.stringToImage(route.stops[imageIndex].imageString, options))
        }
    }

    private fun setupTabLayout() {
        val fragmentAdapter = EditorFragmentPagerAdapter(applicationContext, supportFragmentManager, tabLayout.tabCount)
        editorViewPager.adapter = fragmentAdapter

        editorViewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))

        tabLayout!!.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                editorViewPager!!.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {

            }

            override fun onTabReselected(tab: TabLayout.Tab) {

            }
        })
    }
}
