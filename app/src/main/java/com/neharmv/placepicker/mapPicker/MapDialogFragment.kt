package com.neharmv.placepicker.mapPicker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.*
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.view.animation.Interpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.daboya.pendragon.utils.mapPicker.MapLocationListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.LocationSource.OnLocationChangedListener
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.neharmv.placepicker.R
import com.neharmv.placepicker.mapPicker.permission.OnPermissionInterface
import com.neharmv.placepicker.mapPicker.permission.PermissionManagerUtil
import kotlinx.android.synthetic.main.map_dialog.*
import java.util.*

class MapDialogFragment : DialogFragment(), LocationSource, LocationListener {
    private val TAG = MapDialogFragment::class.java.name
    private val animationDuration: Long = 700
    private var googleMap: GoogleMap? = null
    private var locationManager: LocationManager? = null
    private val criteria = Criteria()
    private var onLocationChangedListener: OnLocationChangedListener? = null
    private var locationReceived = false
    private var currentMarker: Marker? = null
    private var currentLocation: String? = null
    private var currentLatLng: LatLng? = null
    private var mapLocationListener: MapLocationListener? = null
    private var savedInstanceState: Bundle? = null

    private val mapLongClickListener =
        OnMapLongClickListener { point -> addMarker(point, true) }

    private val mapMarkerClickListener =
        OnMarkerClickListener {
            true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setStyle(
            STYLE_NORMAL,
            android.R.style.Theme_Black_NoTitleBar
        )
        try {
            locationManager =
                requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        } catch (e: NullPointerException) {
        }
        criteria.accuracy = Criteria.ACCURACY_FINE
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.map_dialog, container, true)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            dialog.window!!.setLayout(width, height)
        }
    }

    override fun onResume() {
        try {
            PermissionManagerUtil.requestLocationPermission(
                true,
                object : OnPermissionInterface {
                    @SuppressLint("MissingPermission")
                    override fun onPermissionGranted() {
                        if (googleMap == null) {
                            initGoogleMap(savedInstanceState)
                        } else {
                            locationManager!!.requestLocationUpdates(
                                0L,
                                0.0f,
                                criteria,
                                this@MapDialogFragment,
                                null
                            )
                            googleMap!!.setLocationSource(this@MapDialogFragment)
                            mapView!!.onResume()
                        }
                    }

                    override fun onPermissionNotGranted() {
                    }
                })
        } catch (e: Exception) {
            debug("onResume $e")
        }
        if (!PermissionManagerUtil.isGpsEnabled()) {
            buildAlertMessageNoGps()
        }
        super.onResume()

    }

    override fun onPause() {
        try {
            mapView!!.onPause()
            googleMap!!.setLocationSource(null)
            locationManager!!.removeUpdates(this)
        } catch (e: Exception) {
            debug("onPause $e")
        }
        super.onPause()
    }

    override fun onDestroyView() {
        mapLocationListener = null
        super.onDestroyView()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        this.savedInstanceState = savedInstanceState

        ok_button!!.setOnClickListener {
            if (mapLocationListener != null) {
                if (map_selected_location_text!!.tag == null) {
                    mapLocationListener!!.setPlace(
                        map_selected_location_text!!.text.toString(),
                        map_selected_location_text!!.tag as Address,
                        currentLatLng
                    )
                } else {
                    mapLocationListener!!.setPlace(
                        map_selected_location_text!!.text.toString(),
                        map_selected_location_text!!.tag as Address,
                        currentLatLng
                    )
                }
            }
            dismiss()
        }

        ok_button!!.isEnabled = false

        initAutoCompleteFeature()
    }

    /*
     * This will add place search widget provided by Google : https://www.youtube.com/watch?v=nDC-frkH_LA
     */
    private fun initAutoCompleteFeature() {

        val autocompleteSupportFragment =
            AutocompleteSupportFragment()
        autocompleteSupportFragment.setTypeFilter(TypeFilter.ADDRESS)
        autocompleteSupportFragment.setCountries("IN")
        autocompleteSupportFragment.setPlaceFields(
            listOf(
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG
            )
        )
        autocompleteSupportFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                addMarker(place.latLng, true)
                moveAndZoomToLocation(place.latLng, 15)
            }

            override fun onError(status: Status) {}
        })
        childFragmentManager.beginTransaction().replace(
            R.id.place_autocomplete_fragment_container,
            autocompleteSupportFragment,
            "autocompleteSupportFragment"
        ).commit()
    }

    @SuppressLint("MissingPermission")
    private fun initGoogleMap(savedInstanceState: Bundle?) {
        try {
            MapsInitializer.initialize(activity)
        } catch (e: Exception) {
            debug("MapsInitializer.initialize() error$e")
        }
        val googleAPI = GoogleApiAvailability.getInstance()
        when (googleAPI.isGooglePlayServicesAvailable(activity)) {
            ConnectionResult.SUCCESS -> {
                debug("Successfully connected")
                mapView!!.onCreate(savedInstanceState)
                mapView!!.onResume()
                if (mapView != null) {
                    mapView!!.getMapAsync { googleMap ->
                        debug("onMapReady")
                        this@MapDialogFragment.googleMap = googleMap
                        locationManager!!.requestLocationUpdates(
                            0L,
                            0.0f,
                            criteria,
                            this@MapDialogFragment,
                            null
                        )
                        googleMap.setLocationSource(this@MapDialogFragment)
                        googleMap.setOnMyLocationButtonClickListener {
                            if (!PermissionManagerUtil.isGpsEnabled()) {
                                buildAlertMessageNoGps()
                            }
                            false
                        }
                        googleMap.uiSettings.isMyLocationButtonEnabled = true
                        googleMap.uiSettings.isCompassEnabled = true
                        googleMap.uiSettings.isRotateGesturesEnabled = true
                        googleMap.uiSettings.isZoomControlsEnabled = false
                        googleMap.setOnMapLongClickListener(mapLongClickListener)
                        googleMap.setOnMarkerClickListener(mapMarkerClickListener)
                        googleMap.isMyLocationEnabled = true
                        googleMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
                    }
                }
            }
            ConnectionResult.SERVICE_MISSING -> {
                val requestCode = 10
                GooglePlayServicesUtil.showErrorDialogFragment(
                    ConnectionResult.SERVICE_MISSING,
                    activity, null,
                    requestCode, null
                )
                debug("SERVICE_MISSING error")
            }
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> debug("SERVICE_VERSION_UPDATE_REQUIRED error")
        }
    }

    /**
     * Show GPS Alert
     */
    private fun buildAlertMessageNoGps() {
        try {
            val builder =
                AlertDialog.Builder(requireActivity())
            builder.setMessage("GPS ENABLED").setCancelable(false)
                .setPositiveButton("Yes") { dialog, id -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton("No") { dialog, id -> dialog.cancel() }
            val alert = builder.create()
            alert.show()
        } catch (e: RuntimeException) {
            printStackTrace(e)
        } catch (e: Exception) {
            printStackTrace(e)
        }
    }

    /***
     * Adds marker on map
     */
    private fun addMarker(
        point: LatLng?,
        animate: Boolean
    ) {
        if (googleMap != null) {
            val previousMarker = currentMarker
            currentMarker = googleMap!!.addMarker(
                MarkerOptions().position(point!!)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            if (animate) {
                animateMarker(point)
            }

            try {
                val gcd = Geocoder(activity, Locale.getDefault())
                val addresses =
                    gcd.getFromLocation(point.latitude, point.longitude, 1)
                val address = addresses[0]
                var title: String? = ""
                var description: String? = ""
                title += addresses[0].getAddressLine(0)
                for (i in 0 until addresses[0].maxAddressLineIndex) {
                    if (i == 0) {
                        title += addresses[0].getAddressLine(i)
                    } else if (i < addresses[0].maxAddressLineIndex - 1) {
                        description += addresses[0].getAddressLine(i) + " "
                    } else {
                        description += addresses[0].getAddressLine(i)
                    }
                }
                showDetails(point, animate, previousMarker, title, description, address)
            } catch (e: Exception) {
                debug("get location name $e")
                if (activity != null) {
                    Toast.makeText(activity, "Waiting for location", Toast.LENGTH_SHORT)
                        .show()
                }
            }

        }
    }

    /**
     * Will add details to respected views
     */
    private fun showDetails(
        point: LatLng?,
        animate: Boolean,
        previousMarker: Marker?,
        title: String?,
        description: String?,
        address: Address?
    ) {
        if (title!!.isNotEmpty() || description!!.isNotEmpty()) {
            currentLocation = "$title $description"
            map_selected_location_text!!.text = currentLocation
            map_selected_location_text!!.tag = address
            currentLatLng = LatLng(point!!.latitude, point.longitude)
            currentMarker!!.title = title
            currentMarker!!.snippet = description
            previousMarker?.remove()
            if (!animate) {
                currentMarker!!.showInfoWindow()
            }
            ok_button!!.isEnabled = true
        } else {
            Toast.makeText(activity, "Waiting for location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateMarker(point: LatLng?) {
        val start = SystemClock.uptimeMillis()
        val proj = googleMap!!.projection
        val startPoint = proj.toScreenLocation(point)
        startPoint.offset(0, -70)
        val startLatLng = proj.fromScreenLocation(startPoint)
        val interpolator: Interpolator = BounceInterpolator()

        val elapsed = SystemClock.uptimeMillis() - start
        val t =
            interpolator.getInterpolation(elapsed.toFloat() / animationDuration)
        val lng = t * point!!.longitude + (1 - t) * startLatLng.longitude
        val lat = t * point.latitude + (1 - t) * startLatLng.latitude
        if (t < 1.0) {
            currentMarker!!.position = LatLng(lat, lng)
        }
    }

    override fun onLocationChanged(location: Location) {
        if (onLocationChangedListener != null && !locationReceived) {
            locationReceived = true
            onLocationChangedListener!!.onLocationChanged(location)
            val latlng = LatLng(location.latitude, location.longitude)
            moveAndZoomToLocation(latlng, 17)
            currentLatLng = latlng
            addMarker(latlng, false)
        }
    }

    override fun onLowMemory() {
        mapView!!.onLowMemory()
        super.onLowMemory()
    }

    override fun onDestroy() {
        try {
            mapView!!.onDestroy()
        } catch (e: Exception) {
            printStackTrace(e)
        }
        super.onDestroy()
    }

    override fun onProviderDisabled(provider: String) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onStatusChanged(
        provider: String,
        status: Int,
        extras: Bundle
    ) {
    }

    fun setMapLocationListener(_parent: MapLocationListener?) {
        mapLocationListener = _parent
    }

    private fun moveAndZoomToLocation(point: LatLng?, zoom: Int) {
        if (googleMap != null) {
            val cu = CameraUpdateFactory.newLatLngZoom(point, zoom.toFloat())
            googleMap!!.animateCamera(cu)
        }
    }

    private fun printStackTrace(e: Exception?) {
        e?.printStackTrace()
    }

    private fun debug(msg: String) {
        Log.d(TAG, msg)

    }

    /**
     * LocationSource methods
     */
    override fun activate(listener: OnLocationChangedListener) {
        onLocationChangedListener = listener
    }

    override fun deactivate() {
        onLocationChangedListener = null
    }
}