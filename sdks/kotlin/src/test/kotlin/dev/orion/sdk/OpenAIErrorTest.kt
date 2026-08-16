package dev.orion.sdk

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class OpenAIErrorTest {

    @Serializable
    private data class ProviderAnswer(val answer: String)

    @Test
    fun rateLimitResponsePreservesProviderRetryDelay(): Unit = runBlocking {

        val server = rateLimitedServer()

        try {
            val agent = Agent(
                model = OpenAI(
                    model = "test",
                    apiKey = null,
                    baseUrl = "http://127.0.0.1:${server.address.port}/v1",
                ),
                output = ProviderAnswer.serializer(),
            )

            val error = assertFailsWith<OrionException> { agent.run("Hello") }

            assertEquals(AgentErrorCode.RATE_LIMITED, error.code)
            assertEquals(true, error.retryable)
            assertEquals(2_000, error.retryAfterMilliseconds)
        } finally {
            server.stop(0)
        }

    }

    private fun rateLimitedServer(): HttpServer {

        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            exchange.requestBody.use { it.readAllBytes() }
            exchange.responseHeaders.add("retry-after", "2")
            exchange.sendResponseHeaders(429, -1)
            exchange.close()
        }
        server.start()

        return server

    }

}
