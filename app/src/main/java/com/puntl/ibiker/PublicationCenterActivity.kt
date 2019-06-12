package com.puntl.ibiker

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.VolleyError
import com.puntl.ibiker.companions.SessionProvider
import com.puntl.ibiker.interfaces.ItemClickListener
import com.puntl.ibiker.models.PublicationRecyclerItem
import com.puntl.ibiker.models.Route
import com.puntl.ibiker.services.ServiceVolley
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.android.synthetic.main.activity_publication_center.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

const val PUBLICATION_ID = "publication_id"

private const val TAG = "PublicationCenterActivity"
private const val ROUTE_URL = "/route"

class PublicationCenterActivity : AppCompatActivity() {

    private lateinit var adapter: RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>
    private val publicationRecyclerItems = mutableListOf<PublicationRecyclerItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_publication_center)

        publicationsRecyclerView.setHasFixedSize(true)
        publicationsRecyclerView.layoutManager = LinearLayoutManager(this)

        adapter = RecyclerViewAdapter(publicationRecyclerItems, this@PublicationCenterActivity)
        publicationsRecyclerView.adapter = adapter

        loadPublications()
    }

    inner class RecyclerViewAdapter(var publicationItems: MutableList<PublicationRecyclerItem>, context: Context) :
        RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.publication_entry, parent, false)
            )
        }

        override fun getItemCount(): Int {
            return publicationItems.size
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val startDate = publicationItems[position].date
            val publishStatus = if (publicationItems[position].isPublished) "published" else "unpublished"

            viewHolder.dateTextView.text = getString(R.string.route_start_date, startDate)
            viewHolder.isPublishedTextView.text = getString(R.string.route_publish_status, publishStatus)

            viewHolder.itemClickListener = object : ItemClickListener {
                override fun onClick(view: View, position: Int, isLongClick: Boolean) {
                    if (isLongClick) {
                        //delete from db
                        Toast.makeText(applicationContext, "LONG: $position", Toast.LENGTH_LONG).show()
                    } else {
                        Intent(applicationContext, PublicationEditorActivity::class.java).also { intent ->
                            intent.putExtra(PUBLICATION_ID, publicationRecyclerItems[position].id)
                            startActivity(intent)
                        }
                    }
                }

            }
        }

        inner class ViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!), View.OnClickListener,
            View.OnLongClickListener {

            init {
                itemView?.setOnClickListener(this)
                itemView?.setOnLongClickListener(this)
            }

            val dateTextView = itemView?.findViewById(R.id.dateTextView) as TextView

            val isPublishedTextView = itemView?.findViewById(R.id.isPublishedTextView) as TextView
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

    private fun loadPublications() {
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

        moshiAdapter.fromJson(userRoutes)?.sortedBy { route -> route.startTimeStamp }?.reversed()?.forEach { route ->
            publicationRecyclerItems.add(
                PublicationRecyclerItem(
                    route.stringifiedId,
                    getDate(route.startTimeStamp, "dd/MM/yyyy HH:mm:ss"),
                    route.isPublished
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

    private fun getDate(millis: Long, format: String): String {
        val formatter = SimpleDateFormat(format, Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return formatter.format(calendar.time)
    }
}
