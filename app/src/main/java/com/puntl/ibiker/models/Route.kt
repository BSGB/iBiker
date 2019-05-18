package com.puntl.ibiker.models

import com.google.android.gms.maps.model.LatLng

data class Route(var userID: String,
                 var isPublished: Boolean,
                 var startTimeStamp: Long,
                 var endTimeStamp: Long,
                 var deltaTime: Long,
                 var totalDistance: Float,
                 var waypoints: List<LatLng>,
                 var averages: List<Float>,
                 var stops: List<RouteStop>,
                 var comments: List<UserComment>?)