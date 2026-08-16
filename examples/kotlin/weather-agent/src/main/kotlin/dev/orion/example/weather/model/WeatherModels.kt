package dev.orion.example.weather.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Typed arguments whose serializer defines the model-visible tool schema. */
@Serializable
internal data class WeatherArguments(
    val city: String,
)

/** Typed observation returned by the application tool. */
@Serializable
internal data class WeatherResult(
    val city: String,
    @SerialName("temperature_c") val temperatureC: Int,
)

/** Typed structured terminal output required from the model. */
@Serializable
internal data class WeatherAnswer(
    val city: String,
    @SerialName("temperature_c") val temperatureC: Int,
    val summary: String,
)
