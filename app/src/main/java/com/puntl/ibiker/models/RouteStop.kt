package com.puntl.ibiker.models

import com.google.android.gms.maps.model.LatLng

data class RouteStop(var imageString: String,
                     var imageName: String?,
                     var geoTag: LatLng)