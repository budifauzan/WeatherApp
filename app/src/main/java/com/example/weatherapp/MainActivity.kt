package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.location.LocationManagerCompat.getCurrentLocation
import com.example.weatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    private var mLatitude: Double? = null
    private var mLongitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Check if location provider is enabled
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Please turn on your GPS!", Toast.LENGTH_SHORT).show()

            // Bring user to location setting if location provider is not enabled
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            // Check if location permission is enabled
            checkLocationPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }


    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun checkLocationPermission() {
        Dexter.withContext(this).withPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(multiplePermissionsReport: MultiplePermissionsReport?) {
                if (multiplePermissionsReport!!.areAllPermissionsGranted()) {
                    getCurrentLocation()
                }
                if (multiplePermissionsReport.isAnyPermissionPermanentlyDenied) {
                    Toast.makeText(
                        this@MainActivity,
                        "You need to enable permission for location in order for this app to work!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onPermissionRationaleShouldBeShown(
                permissionRequests: MutableList<PermissionRequest>?,
                permissionToken: PermissionToken?
            ) {
                showRationaleDialogForPermissions()
            }

        }).onSameThread().check()
    }

    private fun showRationaleDialogForPermissions() {
        AlertDialog.Builder(this).setMessage(
            "It looks like you have turned off permission required for this features. It can be enabled under Applications Setting"
        ).setPositiveButton("GO TO SETTINGS") { _, _ ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }.setNegativeButton("CANCEL") { dialog, _ ->
            dialog.dismiss()
        }.show()
    }

    private val mLocationCallBack = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            mLatitude = mLastLocation?.latitude
            mLongitude = mLastLocation?.longitude
            Log.i("LatLong", "Latitude: $mLatitude Longitude: $mLongitude")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        val locationRequest = com.google.android.gms.location.LocationRequest()
        locationRequest.priority = LocationRequest.QUALITY_HIGH_ACCURACY
        locationRequest.numUpdates = 1

        mFusedLocationProviderClient.requestLocationUpdates(
            locationRequest, mLocationCallBack, Looper.myLooper()
        )
    }

}