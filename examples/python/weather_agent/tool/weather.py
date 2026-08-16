"""Weather tool construction and application-owned business logic."""

from ..model.weather import WeatherResult


async def get_weather(city: str) -> WeatherResult:
    """Return deterministic demo data after domain-level validation."""

    normalized_city = city.strip()
    if not normalized_city:
        raise ValueError("weather city must not be blank")

    return WeatherResult(city=normalized_city, temperature_c=31)
