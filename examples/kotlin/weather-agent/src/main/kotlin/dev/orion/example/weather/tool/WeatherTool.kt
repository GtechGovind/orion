package dev.orion.example.weather.tool

import dev.orion.example.weather.model.WeatherArguments
import dev.orion.example.weather.model.WeatherResult

/** Typed application function exposed directly to the weather agent. */
internal fun getWeather(arguments: WeatherArguments): WeatherResult {
    val city = arguments.city.trim()
    require(city.isNotEmpty()) { "weather city must not be blank" }
    return WeatherResult(city = city, temperatureC = 31)
}
