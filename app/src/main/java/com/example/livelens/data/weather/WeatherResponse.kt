package com.example.livelens.data.weather

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    val main: Main,
    val weather: List<Weather>
) {
    data class Main(val temp: Double)
    data class Weather(val icon: String, val description: String)
}