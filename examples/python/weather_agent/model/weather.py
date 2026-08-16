"""Typed weather contracts shared by the agent and tool modules."""

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class WeatherResult:
    """Structured observation returned by the weather tool."""

    city: str
    temperature_c: int


@dataclass(frozen=True, slots=True)
class WeatherAnswer:
    """Structured terminal output required from the model."""

    city: str
    temperature_c: int
    summary: str
