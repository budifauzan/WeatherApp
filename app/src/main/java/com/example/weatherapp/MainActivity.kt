package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.APIResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private var mDialog: Dialog? = null

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var mSharedPreferences: SharedPreferences

    private var mLatitude: Double? = null
    private var mLongitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

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
            getWeatherInCurrentLocation()
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

    private fun getWeatherInCurrentLocation() {
        if (Constants.isNetworkAvailable(this)) {
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val weatherService: WeatherService = retrofit.create(WeatherService::class.java)

            val call: Call<APIResponse> = weatherService.getWeather(
                mLatitude!!, mLongitude!!, Constants.METRIC_UNIT, Constants.API_KEY
            )

            showCustomDialog()
            call.enqueue(object : Callback<APIResponse> {
                override fun onResponse(call: Call<APIResponse>, response: Response<APIResponse>) {
                    if (response.isSuccessful) {
                        val weather: APIResponse? = response.body()
                        Log.i("Response", weather.toString())
                        val apiResponse = Gson().toJson(weather)
                        mSharedPreferences.edit()
                            .putString(Constants.API_RESPONSE_DATA, apiResponse).apply()
                        setupUI()
                        hideProgressDialog()
                    } else {
                        when (response.code()) {
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                        hideProgressDialog()
                    }
                }

                override fun onFailure(call: Call<APIResponse>, t: Throwable) {
                    Log.e("Retrofit failure", t.message.toString())
                    hideProgressDialog()
                }

            })

        } else {
            Toast.makeText(this, "No internet connection available!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomDialog() {
        mDialog = Dialog(this)
        mDialog!!.setContentView(R.layout.dialog_custom_progress)
        mDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mDialog != null) {
            mDialog!!.dismiss()
        }
    }

    private fun setupUI() {
        val apiResponse = mSharedPreferences.getString(Constants.API_RESPONSE_DATA, "")
        if (!apiResponse.isNullOrEmpty()) {
            val weather = Gson().fromJson(apiResponse, APIResponse::class.java)
            for (i in weather.weather.indices) {
                binding?.tvWeather?.text = weather.weather[i].main
                binding?.tvCondition?.text = weather.weather[i].description
                val temp = getTemp(weather.main.temp)
                binding?.tvTemp?.text = temp
                val humidity = "${weather.main.humidity}%"
                binding?.tvHumidity?.text = humidity
                val tempMin = getTemp(weather.main.temp_min) + " min"
                binding?.tvMinimum?.text = tempMin
                val tempMax = getTemp(weather.main.temp_max) + " max"
                binding?.tvMaximum?.text = tempMax
                binding?.tvWind?.text = "${weather.wind.speed}"
                binding?.tvName?.text = weather.name
                binding?.tvCountry?.text = weather.sys.country
                binding?.tvSunrise?.text = unixTime(weather.sys.sunrise)
                binding?.tvSunset?.text = unixTime(weather.sys.sunset)

                when (weather.weather[i].icon) {
                    "01d" -> binding?.ivWeather?.setImageResource(R.drawable.sunny)
                    "02d" -> binding?.ivWeather?.setImageResource(R.drawable.cloud)
                    "03d" -> binding?.ivWeather?.setImageResource(R.drawable.cloud)
                    "04d" -> binding?.ivWeather?.setImageResource(R.drawable.cloud)
                    "04n" -> binding?.ivWeather?.setImageResource(R.drawable.cloud)
                    "10d" -> binding?.ivWeather?.setImageResource(R.drawable.rain)
                    "11d" -> binding?.ivWeather?.setImageResource(R.drawable.storm)
                    "13d" -> binding?.ivWeather?.setImageResource(R.drawable.snowflake)
                    "01n" -> binding?.ivWeather?.setImageResource(R.drawable.cloud)
                    "02n" -> binding?.ivWeather?.setImageResource(R.drawable.cloud)
                    "03n" -> binding?.ivWeather?.setImageResource(R.drawable.cloud)
                    "10n" -> binding?.ivWeather?.setImageResource(R.drawable.cloud)
                    "11n" -> binding?.ivWeather?.setImageResource(R.drawable.rain)
                    "13n" -> binding?.ivWeather?.setImageResource(R.drawable.snowflake)
                }
            }
        }
    }

    private fun getTemp(value: Double): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return "$value" + getUnit(application.resources.configuration.locales.toString())
        } else {
            return "$value" + getUnit(application.resources.configuration.locale.toString())
        }
    }

    private fun getUnit(value: String): String {
        var symbol = "°C"
        if (value == "US" || value == "LR" || value == "MM") {
            symbol = "°F"
        }
        return symbol
    }

    private fun unixTime(time: Long): String {
        val date = Date(time * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                getCurrentLocation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}