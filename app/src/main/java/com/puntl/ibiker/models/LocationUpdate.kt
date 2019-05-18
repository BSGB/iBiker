package com.puntl.ibiker.models

import android.location.Location
import android.os.Parcel
import android.os.Parcelable
import java.io.Serializable

data class LocationUpdate(var location: Location, var timeStamp: Long): Serializable, Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readParcelable(Location::class.java.classLoader),
        parcel.readLong()
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(location, flags)
        parcel.writeLong(timeStamp)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<LocationUpdate> {
        override fun createFromParcel(parcel: Parcel): LocationUpdate {
            return LocationUpdate(parcel)
        }

        override fun newArray(size: Int): Array<LocationUpdate?> {
            return arrayOfNulls(size)
        }
    }
}