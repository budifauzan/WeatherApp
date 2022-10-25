package com.example.weatherapp.network

import com.example.weatherapp.models.APIResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") long: Double,
        @Query("units") units: String?,
        @Query("appid") appid: String?
    ): Call<APIResponse>
}