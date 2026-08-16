"""Public agent and host-tool definitions."""

from __future__ import annotations

import inspect
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field, replace
from typing import TypeVar, cast

from ..model import Json, JsonCodec, JsonObject, ModelRef


def _empty_provider_options() -> dict[str, JsonObject]:
    return {}


ToolHandler = Callable[[JsonObject], Json | Awaitable[Json]]
Arguments = TypeVar("Arguments")
Result = TypeVar("Result")


@dataclass(frozen=True, slots=True)
class Tool:
    """Associates model-visible metadata with an application callback.

    The callback may return immediately or return an awaitable. Orion passes
    JSON-compatible arguments and requires a JSON-compatible result.
    """

    name: str

    description: str

    input_schema: JsonObject

    execute: ToolHandler

    def __post_init__(self) -> None:
        """Reject invalid definitions before a run reaches the kernel."""

        if not self.name:
            raise ValueError("tool name must be non-empty")

    @classmethod
    def typed(
        cls,
        name: str,
        description: str,
        *,
        arguments: JsonCodec[Arguments],
        result: JsonCodec[Result],
        execute: Callable[[Arguments], Result | Awaitable[Result]],
    ) -> Tool:
        """Create a tool with typed argument decoding and result encoding.

        The primary constructor remains available for dynamic raw schemas.
        """

        if arguments.schema.get("type") != "object":
            raise ValueError("typed tool arguments must define an object JSON Schema")

        async def execute_typed(raw_arguments: JsonObject) -> Json:
            typed_arguments = arguments.decode(raw_arguments)
            value = execute(typed_arguments)
            if inspect.isawaitable(value):
                typed_result = cast(Result, await value)
            else:
                typed_result = value

            return result.encode(typed_result)

        return cls(name, description, arguments.schema, execute_typed)


@dataclass(frozen=True, slots=True)
class AgentDefinition:
    """Defines immutable behavior and execution limits for one agent."""

    id: str

    name: str

    instructions: str

    model: ModelRef

    tools: tuple[Tool, ...] = ()

    output_schema: JsonObject | None = None

    temperature: float | None = None

    max_output_tokens: int | None = None

    provider_options: dict[str, JsonObject] = field(default_factory=_empty_provider_options)

    max_turns: int = 8

    def __post_init__(self) -> None:
        """Validate limits and identities at the public boundary."""

        if not self.id:
            raise ValueError("agent id must be non-empty")
        if not self.name:
            raise ValueError("agent name must be non-empty")
        if self.max_turns <= 0:
            raise ValueError("max_turns must be positive")
        if self.max_output_tokens is not None and self.max_output_tokens <= 0:
            raise ValueError("max_output_tokens must be positive")

    def with_output(self, output: JsonCodec[Result]) -> AgentDefinition:
        """Return a copy constrained by a typed structured-output schema.

        Set ``output_schema`` directly when the schema is dynamic or supplied by
        another system.
        """

        if output.schema.get("type") != "object":
            raise ValueError("typed agent output must define an object JSON Schema")

        return replace(self, output_schema=output.schema)
