package com.daboya.pendragon.utils.mapPicker

import android.location.Address
import com.google.android.gms.maps.model.LatLng

interface MapLocationListener {
    fun setPlace(
        fullAddress: String,
        address: Address?,
        locationCoordinates: LatLng?
    )
}