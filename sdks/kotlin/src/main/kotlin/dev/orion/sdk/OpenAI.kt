package dev.orion.sdk

import dev.orion.sdk.model.ModelRef
import dev.orion.sdk.provider.OpenAICompatibleAdapter
import java.time.Duration

/**
 * OpenAI Chat Completions model used directly by [Agent].
 *
 * @param model provider model identifier.
 * @param apiKey bearer credential; reads `OPENAI_API_KEY` by default.
 * @param baseUrl API root containing `/chat/completions`.
 * @param timeout positive connection and request timeout.
 */
public class OpenAI(
    model: String,
    apiKey: String? = System.getenv("OPENAI_API_KEY"),
    baseUrl: String = "https://api.openai.com/v1",
    timeout: Duration = Duration.ofSeconds(60),
) : Model(
    ref = ModelRef(provider = "openai", model = model),
    adapter = OpenAICompatibleAdapter(
        provider = "openai",
        apiKey = apiKey,
        baseUrl = baseUrl,
        timeout = timeout,
    ),
)
