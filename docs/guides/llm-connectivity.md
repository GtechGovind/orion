# Connecting Orion to an LLM

Provider configuration belongs directly on the application-facing model. The
SDK internally creates the adapter, registry, and runner; Rust never receives
credentials or performs network I/O.

Python:

```python
agent = Agent(
    model=OpenAI("gpt-5-mini"),
    tools=[get_weather],
    output=WeatherAnswer,
)
result = await agent.run("What is the weather in Delhi?")
print(result.output.summary)
```

TypeScript:

```ts
const agent = new Agent({
  model: new OpenAI("gpt-5-mini"),
  tools: [getWeather],
  output: WeatherAnswer,
});
const result = await agent.run("What is the weather in Delhi?");
console.log(result.output.summary);
```

Kotlin:

```kotlin
val agent = Agent(
    model = OpenAI("gpt-5-mini"),
    tools = listOf(tool(
        name = "weather",
        description = "Get the weather for a city.",
        function = ::getWeather,
    )),
    output = WeatherAnswer.serializer(),
)
val result = agent.run("What is the weather in Delhi?")
println(result.output.summary)
```

`OpenAI` reads `OPENAI_API_KEY` by default and accepts an explicit API key,
compatible base URL, and timeout. The examples provide complete typed tool and
streaming applications in all three languages.
