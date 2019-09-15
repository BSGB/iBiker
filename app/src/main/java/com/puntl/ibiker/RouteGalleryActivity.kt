package com.puntl.ibiker

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.VolleyError
import com.puntl.ibiker.companions.DateTimeProvider
import com.puntl.ibiker.companions.SessionProvider
import com.puntl.ibiker.interfaces.ItemClickListener
import com.puntl.ibiker.models.Route
import com.puntl.ibiker.models.RouteRecyclerItem
import com.puntl.ibiker.services.ServiceVolley
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.android.synthetic.main.activity_route_gallery.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "RouteGalleryActivity"
private const val ROUTE_URL = "/route"

class RouteGalleryActivity : AppCompatActivity() {

    companion object {
        const val ROUTE_ID = "route_id"
    }

    private lateinit var adapter: RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>
    private val routeRecyclerItems = mutableListOf<RouteRecyclerItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_gallery)

        supportActionBar?.title = "Gallery"

        routesRecyclerView.setHasFixedSize(true)
        routesRecyclerView.layoutManager = LinearLayoutManager(this)

        adapter = RecyclerViewAdapter(routeRecyclerItems, this@RouteGalleryActivity)
        routesRecyclerView.adapter = adapter
    }

    override fun onBackPressed() {
        Intent(this, MainActivity::class.java).also { startActivity(it) }
    }

    override fun onResume() {
        loadRoutes()
        super.onResume()
    }

    inner class RecyclerViewAdapter(var routeItems: MutableList<RouteRecyclerItem>, context: Context) :
        RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.route_entry, parent, false)
            )
        }

        override fun getItemCount(): Int {
            return routeItems.size
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val startDate = routeItems[position].date
            val user = routeItems[position].user
            val comments = routeItems[position].comments

            viewHolder.dateTextView.text = getString(R.string.route_start_date, startDate)
            viewHolder.userTextView.text = getString(R.string.route_user, "@${user.substring(user.length - 5)}")
            viewHolder.commentsTextView.text = getString(R.string.route_comments, comments)

            viewHolder.itemClickListener = object : ItemClickListener {
                override fun onClick(view: View, position: Int, isLongClick: Boolean) {
                    if (!isLongClick) {
                        Intent(applicationContext, RouteViewerActivity::class.java).also { intent ->
                            intent.putExtra(ROUTE_ID, routeRecyclerItems[position].id)
                            startActivity(intent)
                        }
                    }
                }
            }
        }

        inner class ViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!), View.OnClickListener, View.OnLongClickListener {

            init {
                itemView?.setOnClickListener(this)
                itemView?.setOnLongClickListener(this)
            }

            val dateTextView = itemView?.findViewById(R.id.dateTextView) as TextView
            val userTextView = itemView?.findViewById(R.id.userTextView) as TextView
            val commentsTextView = itemView?.findViewById(R.id.commentsTextView) as TextView
            lateinit var itemClickListener: ItemClickListener

            override fun onClick(view: View?) {
                itemClickListener.onClick(view!!, adapterPosition, false)
            }

            override fun onLongClick(view: View?): Boolean {
                itemClickListener.onClick(view!!, adapterPosition, true)
                return true
            }
        }

    }

    private fun loadRoutes() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val volley = ServiceVolley()
        val customHeader = hashMapOf<String, String>()

        customHeader["token"] = SessionProvider.getUserToken(sharedPreferences) ?: return

        volley.callApi(ROUTE_URL, Request.Method.GET, null, TAG, customHeader,
            { response -> afterGetHandler(response) },
            { error -> handleGetError(error) }
        )
    }

    private fun afterGetHandler(response: JSONObject?) {
        val userRoutes = response?.get("routes")?.toString() ?: return
        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(List::class.java, Route::class.java)
        val moshiAdapter: JsonAdapter<List<Route>> = moshi.adapter(type)

        routeRecyclerItems.clear()

        moshiAdapter.fromJson(userRoutes)?.sortedBy { route -> route.startTimeStamp }?.reversed()?.forEach { route ->
            routeRecyclerItems.add(
                RouteRecyclerItem(
                    route.stringifiedId,
                    DateTimeProvider.getDate(route.startTimeStamp, "dd/MM/yyyy HH:mm:ss"),
                    route.userId,
                    getCommentsCount(route)
                )
            )
        } ?: return

        adapter.notifyDataSetChanged()
    }

    private fun handleGetError(error: VolleyError) {
        val messageJSON = JSONObject(String(error.networkResponse.data))
        val messageString = messageJSON.get("message").toString()
        Toast.makeText(this, messageString, Toast.LENGTH_LONG).show()
    }

    private fun getCommentsCount(route: Route): String {
        return route.comments.count().toString()
    }
}
