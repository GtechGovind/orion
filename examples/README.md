# Complete weather applications

The Python, TypeScript, and Kotlin examples implement the same application with
the single supported Orion workflow:

1. define host-language domain types;
2. define one typed weather function/tool;
3. construct `Agent` directly with `OpenAI`, tools, and required typed output;
4. call `agent.stream(...)`;
5. consume lifecycle events and an already decoded `AgentResult`.

No example constructs a schema codec, model reference, adapter, registry,
runner, protocol value, or native session. Those are SDK internals.

## Structure

```text
python/weather_agent/
├── model/weather.py       domain dataclasses
├── tool/weather.py        ordinary annotated function
├── agent.py               Agent composition
└── main.py                streaming application entry point

javascript/weather-agent/
├── package.json           private kebab-case ESM package
└── src/
    ├── model/weather.ts   Zod domain contracts
    ├── tool/weather.ts    single tool(...) declaration
    ├── agent.ts           Agent composition
    └── main.ts            streaming application entry point

kotlin/weather-agent/src/main/kotlin/dev/orion/example/weather/
├── model/WeatherModels.kt @Serializable domain contracts
├── tool/WeatherTool.kt    typed application function
├── WeatherAgent.kt        Agent composition
└── Main.kt                Flow application entry point
```

Each structure follows its ecosystem: Python `snake_case` packages,
TypeScript’s kebab-case npm package plus `src`, and Kotlin’s standard
`src/main/kotlin` package tree.

## Configuration

All examples read `OPENAI_API_KEY` through `OpenAI`:

```bash
export OPENAI_API_KEY="your-key"
```

Do not put credentials in agents, tools, prompts, provider options, logs, or
checkpoints. `OpenAI` also accepts an explicit key, compatible API base URL, and
timeout when application configuration requires them.

## Python

```bash
python3.10 -m venv .venv
source .venv/bin/activate
python -m pip install maturin
cd sdks/python
maturin develop --release
cd ../..
python -m examples.python.weather_agent.main
```

Python derives the tool schema from `get_weather(city: str) -> WeatherResult`
and its docstring. The final `AgentResult.output` is a `WeatherAnswer` dataclass.

## TypeScript

```bash
cd sdks/javascript
npm ci
npm run example:weather
```

Zod is required because TypeScript interfaces do not exist at runtime. One
`tool(...)` declaration connects the inferred input/result types to runtime
validation. `AgentResult.output` is inferred from `WeatherAnswer`.

## Kotlin

```bash
cd sdks/kotlin
./gradlew runWeatherExample --no-daemon
```

Kotlin serializers derive the schema and codecs. The canonical
`tool(name, description, function)` factory adapts the typed function to the
suspending tool contract and gives it a stable model-visible name.
`AgentResult.output` is a `WeatherAnswer`.

## Offline verification

Example source is compiled by every SDK check. Deterministic fake-provider tests
exercise the same model → tool → model path without credentials or network I/O:

```bash
cargo test --workspace --all-features
(cd sdks/javascript && npm test)
(cd sdks/kotlin && ./gradlew test --no-daemon)
python -m unittest discover -s sdks/python/tests -v
```
