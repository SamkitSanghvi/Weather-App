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
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.weatherapp.Models.weatherResponse
import com.example.weatherapp.Network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.PermissionRequest
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Converter.Factory.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {


    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog : Dialog? = null
    private lateinit var  mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constant.PREFERENCE_NAME,Context.MODE_PRIVATE)
        setupUI()
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            // This will redirect you to settings from where you need to turn on the location provider.
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)

        }
        else{
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        // check if all permissions are granted
                        if (report.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        // check for permanent denial of any permission
                        if (report.isAnyPermissionPermanentlyDenied) {
                            // permission is denied permanently, navigate user to app settings
                            Toast.makeText(this@MainActivity,
                            "You Have denied Location permission",Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest?>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()

                    }
                })
                .onSameThread()
                .check()
        }


    }
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")
            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }



    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if(Constant.isNetworkAvailable(this)){
           val retrofit : Retrofit =  Retrofit.Builder().baseUrl(Constant.BASE_URL)
               .addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService =
                retrofit.create(WeatherService::class.java)

            val listCall: Call<weatherResponse> = service.getWeather(
                latitude, longitude, Constant.METRIC_UNIT, Constant.APP_ID
            )

                showCustomProgressDialog()

            // Callback methods are executed using the Retrofit callback executor.
            listCall.enqueue(object : Callback<weatherResponse> {
                @SuppressLint("SetTextI18n")


                override fun onResponse(
                    call: Call<weatherResponse>,
                    response: Response<weatherResponse>
                ) {

                    // Check weather the response is success or not.
                    if (response.isSuccessful) {

                        hideProgressDialog()
                        /** The de-serialized response body of a successful response. */
                        val weatherList: weatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constant.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()
                            setupUI()
                        Log.i("Response Result", "$weatherList")
                    } else {
                        // If the response is not success then we check the response code.
                        when (response.code()) {
                            400 -> {
                                Log.e("Error 400", "Bad Request")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<weatherResponse>, t: Throwable) {
                    Log.e("Errors", t.message.toString())
                    hideProgressDialog()
                }
            })


        }
        else{
            Toast.makeText(this@MainActivity,"You Don't Have an Internet Connection"
                ,Toast.LENGTH_SHORT).show()
        }
    }


    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It looks like You have Turned off Permission")
            .setPositiveButton("Go to Setting"){
                _, _  ->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("cancel"){
                dialog,_->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled(): Boolean {
        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        Looper.myLooper()?.let {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                it
            )
        }
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if(mProgressDialog != null)
            mProgressDialog!!.dismiss()
    }


    private fun setupUI(){

        val weatherResponseJSonString = mSharedPreferences.
        getString(Constant.WEATHER_RESPONSE_DATA," ")

        if(!weatherResponseJSonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJSonString,weatherResponse::class.java)


            for(i in weatherList.weather.indices){
                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text = weatherList.main.temp.toString() + getunit(application.resources.configuration.toString())
                tv_min.text =  weatherList.main.temp_min.toString() + " min"
                tv_max.text = weatherList.main.temp_max.toString()  + " max"
                tv_sunrise_time.text = unitxTime(weatherList.sys.sunrise)
                tv_sunset_time.text = unitxTime(weatherList.sys.sunset)
                tv_speed.text = weatherList.wind.speed.toString()
                tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country

                when(weatherList.weather[i].icon){
                    "01d"-> iv_main.setImageResource(R.drawable.sunny)
                    "02d"-> iv_main.setImageResource(R.drawable.cloud)
                    "03d"-> iv_main.setImageResource(R.drawable.cloud)
                    "04d"-> iv_main.setImageResource(R.drawable.cloud)
                    "10d"-> iv_main.setImageResource(R.drawable.rain)
                    "11d"-> iv_main.setImageResource(R.drawable.storm)
                    "13d"-> iv_main.setImageResource(R.drawable.snowflake)
                    "01n"-> iv_main.setImageResource(R.drawable.cloud)
                    "02n"-> iv_main.setImageResource(R.drawable.cloud)
                    "03n"-> iv_main.setImageResource(R.drawable.cloud)
                    "04n"-> iv_main.setImageResource(R.drawable.cloud)
                    "10n"-> iv_main.setImageResource(R.drawable.cloud)
                    "11n"-> iv_main.setImageResource(R.drawable.rain)
                    "13n"-> iv_main.setImageResource(R.drawable.snowflake)

                }


            }

        }


    }

    private fun getunit(value :String):String{
        var value = " °C"
        if("US"==value || "LR"==value || "MM"==value){
            value = " °F"
        }
        return value
    }

    private fun unitxTime(timex:Long) : String{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> {super.onOptionsItemSelected(item)}
        }
    }


}