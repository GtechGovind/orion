package dev.orion.example.weather

import dev.orion.sdk.AgentEvent
import dev.orion.sdk.AgentResult
import kotlinx.coroutines.runBlocking

/** Runs the complete Kotlin weather example through the simplified API. */
fun main(): Unit = runBlocking {

    createWeatherAgent().stream("What is the weather in Delhi?").collect { item ->
        when (item) {
            is AgentEvent -> println("event ${item.sequence}: ${item.kind}")
            is AgentResult -> {
                println("turns: ${item.turns}; output tokens: ${item.usage.outputTokens}")
            }
        }
    }

}
