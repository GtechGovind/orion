"""Application entry point for the complete Python weather example."""

import asyncio

from orion_sdk import AgentResult

from .agent import create_weather_agent


async def main() -> None:
    """Stream one run and release every application-owned provider resource."""

    async for item in create_weather_agent().stream("What is the weather in Delhi?"):
        if isinstance(item, AgentResult):
            print(f"answer: {item.output.summary}")
            print(f"turns: {item.turns}; output tokens: {item.usage.output_tokens}")
        else:
            print(f"event {item.sequence}: {item.type.value}")


if __name__ == "__main__":
    asyncio.run(main())
