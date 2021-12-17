package com.example.weatherapp.Network

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import com.example.weatherapp.Models.weatherResponse

interface WeatherService {
    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String?,
        @Query("appid") appid: String?
    ): Call<weatherResponse>

}