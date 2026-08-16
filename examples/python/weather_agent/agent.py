"""Weather agent definition independent of application startup."""

from orion_sdk import Agent, OpenAI

from .model.weather import WeatherAnswer
from .tool.weather import get_weather


def create_weather_agent() -> Agent[WeatherAnswer]:
    """Build the immutable agent and its typed structured-output contract."""

    return Agent(
        id="weather",
        name="Weather assistant",
        instructions=(
            "Use the weather tool. Return only the requested structured JSON "
            "with city, temperature_c, and a concise summary."
        ),
        model=OpenAI("gpt-5-mini"),
        tools=(get_weather,),
        output=WeatherAnswer,
        max_turns=4,
    )
