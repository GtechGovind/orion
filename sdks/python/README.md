# Orion Python SDK

Python 3.10+ SDK backed by an in-process abi3 PyO3 module. The supported API is
intentionally small: `Agent`, provider models such as `OpenAI`, typed results,
lifecycle events, and SDK errors.

```python
import asyncio
from dataclasses import dataclass

from orion_sdk import Agent, OpenAI


@dataclass(frozen=True, slots=True)
class WeatherResult:
    city: str
    temperature_c: int


@dataclass(frozen=True, slots=True)
class WeatherAnswer:
    city: str
    temperature_c: int
    summary: str


async def get_weather(city: str) -> WeatherResult:
    """Get the current weather for a city."""
    return WeatherResult(city, 31)


async def main() -> None:
    agent = Agent(
        model=OpenAI("gpt-5-mini"),
        tools=[get_weather],
        output=WeatherAnswer,
        instructions="Use the weather tool.",
    )

    result = await agent.run("What is the weather in Delhi?")
    print(result.output.summary)


if __name__ == "__main__":
    asyncio.run(main())
```

Function annotations and docstrings define the tool contract. The SDK derives
schemas internally; Rust validates tool arguments and terminal structured
output; `result.output` is already the configured Python type.

Use `agent.stream(...)` for ordered lifecycle events followed by one typed
`AgentResult`. Registry, runner, codec, raw-schema, adapter, model-reference,
protocol, and native-session APIs are internal and unsupported for application
use.

`OrionError` exposes the stable Rust error category and retry metadata without
requiring access to runtime internals:

```python
from orion_sdk import Agent, ErrorCode, OrionError


async def run_weather(agent: Agent[WeatherAnswer]) -> None:
    try:
        result = await agent.run("What is the weather in Delhi?")
        print(result.output.summary)
    except OrionError as error:
        if error.code is ErrorCode.RATE_LIMITED and error.retryable:
            print(f"retry after {error.retry_after_ms} ms")
        raise
```

Cancelling the calling task still raises `asyncio.CancelledError`; Orion does not
replace structured task cancellation with a generic SDK error.

See the [complete weather application][weather-example].

[weather-example]: https://github.com/GtechGovind/orion/blob/main/examples/python/weather_agent/main.py

## Install for local development

```bash
python3.10 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip maturin
cd sdks/python
maturin develop --release
cd ../..
python -m examples.python.weather_agent.main
```

The example calls an OpenAI-compatible endpoint and requires `OPENAI_API_KEY`.
To build a distributable wheel without installing it, run:

```bash
cd sdks/python
uvx maturin build --release --locked
```
