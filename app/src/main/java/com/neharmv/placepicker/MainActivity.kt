package com.neharmv.placepicker

import android.location.Address
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.daboya.pendragon.utils.mapPicker.MapLocationListener
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.neharmv.placepicker.mapPicker.MapDialogFragment
import com.neharmv.placepicker.mapPicker.permission.PermissionManagerUtil
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        search.setOnClickListener {

            val mapDialog = MapDialogFragment()
            mapDialog.setMapLocationListener(object : MapLocationListener {
                override fun setPlace(
                    fullAddress: String,
                    address: Address?,
                    locationCoordinates: LatLng?
                ) {
                    tv_address?.text = fullAddress
                }

            })
            mapDialog.show(supportFragmentManager, "MapDialog");
        }


        /*** Important**/
        PermissionManagerUtil.init(this);
        Places.initialize(
            application.applicationContext,
            "YOUR_API_KEY"
        );
    }

    /*** Important**/
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions!!, grantResults!!)
        PermissionManagerUtil.callPermissionManagerCallBack(
            requestCode,
            grantResults
        )
    }
}