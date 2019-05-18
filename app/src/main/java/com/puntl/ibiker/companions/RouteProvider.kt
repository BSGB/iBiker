package com.puntl.ibiker.companions

import com.google.android.gms.maps.model.LatLng
import com.puntl.ibiker.MILLIS_IN_SECOND
import com.puntl.ibiker.models.LocationUpdate

class RouteProvider {
    companion object {
        fun getAverages(locationUpdates: List<LocationUpdate>): List<Float> {
            return locationUpdates.zipWithNext()
                .map {
                    var timeDiff = (it.second.timeStamp - it.first.timeStamp) / MILLIS_IN_SECOND
                    timeDiff = if (timeDiff != 0L) timeDiff else 1
                    val distanceDiff = it.first.location.distanceTo(it.second.location)

                    distanceDiff / timeDiff
                }
        }

        fun getTotalDistance(locationUpdates: List<LocationUpdate>): Float {
            if (locationUpdates.size < 2) return 0F
            return locationUpdates.zipWithNext()
                .map {
                    it.first.location.distanceTo(it.second.location)
                }
                .reduce { acc, it -> acc + it }
        }

        fun getLatLngs(locationUpdates: List<LocationUpdate>): ArrayList<LatLng> {
            return ArrayList(locationUpdates.map {
                LatLng(it.location.latitude, it.location.longitude)
            })
        }
    }
}