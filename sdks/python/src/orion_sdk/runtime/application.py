"""Low-ceremony application API built over the Rust-owned run loop."""

from __future__ import annotations

import inspect
from collections.abc import AsyncGenerator, Awaitable, Callable, Sequence
from dataclasses import dataclass
from typing import Any, Generic, TypeVar, cast, get_type_hints

from pydantic import BaseModel, TypeAdapter, create_model

from ..model import Json, JsonObject, Model, ModelRegistry, Usage, json_codec
from .agent import AgentDefinition, Tool
from .events import RunEvent, RunResult
from .runner import Runner

Output = TypeVar("Output")
ToolCallable = Callable[..., object | Awaitable[object]]


@dataclass(frozen=True, slots=True)
class AgentResult(Generic[Output]):
    """Successful typed agent output with usage and lifecycle metadata."""

    output: Output

    run_id: str

    usage: Usage

    turns: int

    events: tuple[RunEvent, ...]


class Agent(Generic[Output]):
    """Runs one model with language-native tools and optional typed output."""

    def __init__(
        self,
        *,
        model: Model,
        tools: Sequence[ToolCallable] = (),
        output: type[Output],
        instructions: str = "You are a helpful assistant.",
        name: str = "Assistant",
        id: str = "assistant",
        max_turns: int = 8,
    ) -> None:
        """Create an agent while hiding registry, runner, and codec plumbing."""

        self._output_codec = json_codec(output)
        self._definition = AgentDefinition(
            id=id,
            name=name,
            instructions=instructions,
            model=model.ref,
            tools=tuple(_normalize_tool(item) for item in tools),
            output_schema=self._output_codec.schema,
            max_turns=max_turns,
        )
        self._runner = Runner(models=ModelRegistry([model.adapter]))

    async def run(self, input: str, *, run_id: str | None = None) -> AgentResult[Output]:
        """Run to completion and return typed output when a type was configured."""

        result = await self._runner.run(self._definition, input, run_id=run_id)

        return self._convert_result(result)

    async def stream(
        self,
        input: str,
        *,
        run_id: str | None = None,
    ) -> AsyncGenerator[RunEvent | AgentResult[Output], None]:
        """Yield lifecycle events followed by one typed terminal result."""

        async for item in self._runner.run_stream(self._definition, input, run_id=run_id):
            yield self._convert_result(item) if isinstance(item, RunResult) else item

    def _convert_result(self, result: RunResult) -> AgentResult[Output]:
        return AgentResult(
            output=self._output_codec.decode_json(result.output),
            run_id=result.run_id,
            usage=result.usage,
            turns=result.turns,
            events=result.events,
        )


def _normalize_tool(value: ToolCallable) -> Tool:
    signature = inspect.signature(value)
    type_hints = get_type_hints(value)
    fields: dict[str, Any] = {}
    for parameter in signature.parameters.values():
        if parameter.kind not in (
            inspect.Parameter.POSITIONAL_OR_KEYWORD,
            inspect.Parameter.KEYWORD_ONLY,
        ):
            raise TypeError("agent tool functions require named parameters")
        annotation = type_hints.get(parameter.name)
        if annotation is None:
            raise TypeError(f"tool parameter {parameter.name!r} requires a type annotation")

        default = ... if parameter.default is inspect.Parameter.empty else parameter.default
        fields[parameter.name] = (annotation, default)

    return_annotation = type_hints.get("return")
    if return_annotation is None:
        raise TypeError("agent tool functions require a return type annotation")

    arguments_type: type[BaseModel] = create_model(
        f"{value.__name__.title()}Arguments",
        **fields,
    )
    arguments_adapter = TypeAdapter[BaseModel](arguments_type)
    result_adapter = TypeAdapter[object](return_annotation)
    schema = cast(JsonObject, arguments_adapter.json_schema())
    description = inspect.getdoc(value) or value.__name__.replace("_", " ")

    async def execute(arguments: JsonObject) -> Json:
        validated: BaseModel = arguments_adapter.validate_python(arguments)
        result = value(**validated.model_dump())
        if inspect.isawaitable(result):
            result = await result

        return cast(Json, result_adapter.dump_python(result, mode="json"))

    return Tool(
        name=value.__name__,
        description=description.splitlines()[0],
        input_schema=schema,
        execute=execute,
    )
