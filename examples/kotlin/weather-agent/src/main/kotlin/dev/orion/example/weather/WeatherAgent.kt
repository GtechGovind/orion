package dev.orion.example.weather

import dev.orion.example.weather.model.WeatherAnswer
import dev.orion.example.weather.tool.getWeather
import dev.orion.sdk.Agent
import dev.orion.sdk.OpenAI
import dev.orion.sdk.tool

/** Builds the immutable agent and its typed structured-output contract. */
internal fun createWeatherAgent(): Agent<WeatherAnswer> {

    return Agent(
        id = "weather",
        name = "Weather assistant",
        instructions = "Use the weather tool. Return only the requested structured JSON " +
            "with city, temperature_c, and a concise summary.",
        model = OpenAI("gpt-5-mini"),
        tools = listOf(tool(
            name = "weather",
            description = "Get the current temperature for a city.",
            function = ::getWeather,
        )),
        output = WeatherAnswer.serializer(),
        maxTurns = 4,
    )

}
