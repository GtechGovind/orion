import asyncio
from orion_sdk import Agent, ModelRegistry, OpenAICompatibleAdapter, Runner, Tool

async def main() -> None:
    weather = Tool("weather", "Get temperature", {
        "type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"],
    }, lambda arguments: {"city": arguments["city"], "temperature_c": 31})
    agent = Agent("weather", "Weather", "Use the weather tool and answer briefly.",
                  "openai:gpt-5-mini", tools=(weather,))
    result = await Runner(models=ModelRegistry([OpenAICompatibleAdapter()])).run(agent, "Weather in Delhi?")
    print(result.output)

asyncio.run(main())
