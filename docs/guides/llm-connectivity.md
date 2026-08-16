# Connecting Orion to an LLM

Register provider adapters on `Runner`; store only `ModelRef` on `Agent`. This
keeps secrets, HTTP clients, vendor objects, and tenant configuration outside
Rust and outside durable definitions.

All SDKs include `OpenAICompatibleAdapter`. It targets Chat Completions-shaped
endpoints and maps messages, function tools, common settings, calls, finish
reasons, and usage. Set `OPENAI_API_KEY` or pass the key to the adapter. Set a
custom base URL for a compatible gateway or local model server.

Python:

```python
runner = Runner(models=ModelRegistry([OpenAICompatibleAdapter()]))
agent = Agent("assistant", "Assistant", "Be concise", "openai:gpt-5-mini")
result = await runner.run(agent, "Hello")
```

TypeScript:

```ts
const runner = new Runner(new ModelRegistry([new OpenAICompatibleAdapter()]));
const agent = new Agent({id: "assistant", name: "Assistant",
  instructions: "Be concise", model: "openai:gpt-5-mini"});
const result = await runner.run(agent, "Hello");
```

Kotlin:

```kotlin
val runner = Runner(ModelRegistry(listOf(OpenAICompatibleAdapter())))
val agent = Agent("assistant", "Assistant", "Be concise", "openai:gpt-5-mini")
val result = runner.run(agent, "Hello")
```

For another API, implement `ModelAdapter`, expose a stable provider key,
translate the provider-neutral request, and return `ModelResponse`. The adapter
owns auth, pooling, translation, cancellation, and error mapping. Rust owns
turns, ordering, limits, tool scheduling, and the terminal outcome.

The protocol defines `ModelProfile` using `native`, `emulated`, `unsupported`,
and `unknown`. Mandatory capability preflight is scheduled after the pilot.
