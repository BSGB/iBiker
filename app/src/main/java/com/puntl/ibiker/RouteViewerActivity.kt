package com.puntl.ibiker

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.TabLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.android.volley.Request
import com.android.volley.VolleyError
import com.google.android.gms.maps.CameraUpdateFactory

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.puntl.ibiker.companions.Base64Xcoder
import com.puntl.ibiker.companions.DateTimeProvider
import com.puntl.ibiker.companions.SessionProvider
import com.puntl.ibiker.interfaces.ItemClickListener
import com.puntl.ibiker.models.CommentRecyclerItem
import com.puntl.ibiker.models.Route
import com.puntl.ibiker.models.UserComment
import com.puntl.ibiker.services.ServiceVolley
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.android.synthetic.main.activity_route_viewer.*
import kotlinx.android.synthetic.main.fragment_photos.*
import kotlinx.android.synthetic.main.fragment_viewer_comments.*
import kotlinx.android.synthetic.main.fragment_viewer_info.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "RouteViewerActivity"
private const val BOUNDS_OFFSET = 100
private const val ROUTE_URL = "/route"

class RouteViewerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var adapter: RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>
    private val commentRecyclerItems = mutableListOf<CommentRecyclerItem>()

    private lateinit var mMap: GoogleMap
    private lateinit var routeId: String
    private lateinit var route: Route
    private lateinit var spinnerAdapter: ArrayAdapter<CharSequence>
    private var imageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_viewer)

        supportActionBar?.hide()

        goButton.visibility = View.INVISIBLE

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        viewPager.offscreenPageLimit = 3

        setupTabLayout()

        routeId = intent.getStringExtra(RouteGalleryActivity.ROUTE_ID)

        loadRoute()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
    }

    fun onGoClick(view: View) {
        val waypoints = route.waypoints.distinct()

        var uriString = "https://www.google.com/maps/dir/?api=1"
        uriString += "&destination=${waypoints.last().latitude},${waypoints.last().longitude}"
        uriString += "&dir_action=navigate"
        if (waypoints.isNotEmpty()) {
            uriString += "&waypoints="
            waypoints.forEach {latLng ->
                uriString += "|${latLng.latitude},${latLng.longitude}"
            }
        }
        uriString += "&travelmode=BICYCLING"

        val uri = Uri.parse(uriString)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        startActivity(intent)
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

    private fun onPostClick() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val userId = SessionProvider.getUser(sharedPreferences).userId

        val commentContent = commentEditText?.text.toString()
        val rating = rateCommentBar.rating

        route.comments.find { userComment -> userComment.userId == userId }?.also { userComment ->
            userComment.commentText = commentContent
            userComment.rating = rating
            userComment.publishTimeStamp = System.currentTimeMillis()
        }?:route.comments.add(UserComment(userId, System.currentTimeMillis(), commentContent, rating))

        val customHeader = hashMapOf<String, String>()

        customHeader["token"] = SessionProvider.getUserToken(sharedPreferences)?: return

        val volley = ServiceVolley()
        val moshi = Moshi.Builder().build()
        val moshiAdapter = moshi.adapter(Route::class.java)
        val json = moshiAdapter.toJson(route)

        volley.callApi(ROUTE_URL, Request.Method.PUT, JSONObject(json), TAG, customHeader,
            { response ->  afterPutHandler(response) },
            { error -> handlePutError(error) }
        )
    }

    inner class RecyclerViewAdapter(var commentItems: MutableList<CommentRecyclerItem>, context: Context) :
        RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.comments_entry, parent, false)
            )
        }

        override fun getItemCount(): Int {
            return commentItems.size
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val startDate = commentItems[position].date
            val user = commentItems[position].user
            val commentContent = commentItems[position].commentContent
            val commentRating = commentItems[position].commentRating

            viewHolder.dateTextView.text = getString(R.string.route_start_date, startDate)
            viewHolder.userTextView.text = getString(R.string.route_user, "@${user.substring(user.length - 5)}")
            viewHolder.commentContent.text = commentContent
            viewHolder.ratingBar.rating = commentRating
        }

        inner class ViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {

            val dateTextView = itemView?.findViewById(R.id.commentDateTextView) as TextView
            val userTextView = itemView?.findViewById(R.id.commentUserTextView) as TextView
            val commentContent = itemView?.findViewById(R.id.commentTextView) as TextView
            val ratingBar = itemView?.findViewById(R.id.commentRatingBar) as RatingBar
        }
    }

    private fun afterPutHandler(response: JSONObject?) {
        val responseRoute = response?.toString()?: return
        val moshi = Moshi.Builder().build()
        val moshiAdapter: JsonAdapter<Route> = moshi.adapter(Route::class.java)

        Toast.makeText(this, getString(R.string.comment_updated), Toast.LENGTH_LONG).show()

        moshiAdapter.fromJson(responseRoute)?.also { route ->
            this.route = route

            commentRecyclerItems.clear()

            loadComments()
        }?: return
    }

    private fun handlePutError(error: VolleyError) {
        val messageJSON = JSONObject(String(error.networkResponse.data))
        val messageString = messageJSON.get("message").toString()
        Toast.makeText(this, messageString, Toast.LENGTH_LONG).show()
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
            loadComments()
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

        goButton.visibility = View.VISIBLE
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

    private fun loadComments() {
        commentsRecyclerView.setHasFixedSize(true)
        commentsRecyclerView.layoutManager = LinearLayoutManager(this)

        adapter = RecyclerViewAdapter(commentRecyclerItems, this@RouteViewerActivity)
        commentsRecyclerView.adapter = adapter

        route.comments.sortedBy { comment -> comment.publishTimeStamp }.reversed().forEach { userComment ->
            val date = DateTimeProvider.getDate(userComment.publishTimeStamp, "dd/MM/yyyy HH:mm:ss")
            val user = userComment.userId
            val text = userComment.commentText
            val rating = userComment.rating
            commentRecyclerItems.add(CommentRecyclerItem(date, user, rating, text))
        }

        adapter.notifyDataSetChanged()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val userId = SessionProvider.getUser(sharedPreferences).userId

        val currentUserComment = route.comments.find { userComment -> userComment.userId == userId }

        rateCommentBar.rating = currentUserComment?.rating?:0.0F
        commentEditText.setText(currentUserComment?.commentText)

        postButton.setOnClickListener { onPostClick() }
    }

    private fun setupTabLayout() {
        val fragmentAdapter = ViewerFragmentPagerAdapter(applicationContext, supportFragmentManager, tabLayout.tabCount)
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
}
