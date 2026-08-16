"""Async Python runner backed by an in-process PyO3 kernel session."""

# pyright: reportMissingModuleSource=false

from __future__ import annotations

import asyncio
import inspect
import uuid
from collections.abc import AsyncIterator, Mapping
from typing import Any

from . import _native
from .models import Agent, ModelRef, ModelRegistry, RunEvent, RunResult, Usage


class OrionError(RuntimeError):
    """Normalized SDK failure safe to surface to an application."""


class Runner:
    """Coordinates host effects while Rust owns all run state."""

    def __init__(self, *, models: ModelRegistry) -> None:
        self.models = models

    async def run(self, agent: Agent, input: str, *, run_id: str | None = None) -> RunResult:
        result = None
        async for item in self.run_stream(agent, input, run_id=run_id):
            if isinstance(item, RunResult):
                result = item
        if result is None:
            raise OrionError("run ended without a result")
        return result

    async def run_stream(
        self, agent: Agent, input: str, *, run_id: str | None = None
    ) -> AsyncIterator[RunEvent | RunResult]:
        model = ModelRef.parse(agent.model) if isinstance(agent.model, str) else agent.model
        profile = self.models.resolve(model).profile(model)
        if agent.tools and profile.tool_calling == "unsupported":
            raise OrionError(f"model {model.provider}:{model.model} does not support tool calling")
        if agent.output_schema and profile.structured_output == "unsupported":
            raise OrionError(f"model {model.provider}:{model.model} does not support structured output")

        native = _native.NativeRun(
            {
                "run_id": run_id or f"run-{uuid.uuid4()}",
                "agent": agent.to_wire(),
                "input": input,
            }
        )
        step = native.take_step()
        events: list[RunEvent] = []
        while True:
            for raw in step["events"]:
                kind = raw["kind"]
                event = RunEvent(
                    raw["run_id"],
                    raw["sequence"],
                    kind["type"],
                    {key: value for key, value in kind.items() if key != "type"},
                )
                events.append(event)
                yield event
            terminal = step.get("result")
            if terminal is not None:
                yield RunResult(
                    terminal["run_id"],
                    terminal["output"],
                    Usage(**terminal["usage"]),
                    terminal["turns"],
                    tuple(events),
                )
                return
            effect = step.get("effect")
            if effect is None:
                raise OrionError("run terminated without a successful result")
            try:
                effect_result = await self._execute_effect(agent, effect)
                step = native.resume(effect_result)
            except asyncio.CancelledError:
                native.cancel()
                raise
            except Exception as error:
                failed = native.fail(
                    {
                        "code": "provider" if effect["type"] == "call_model" else "tool",
                        "message": str(error)[:4096],
                        "retryable": False,
                        "retry_after_ms": None,
                    }
                )
                for raw in failed["events"]:
                    kind = raw["kind"]
                    yield RunEvent(
                        raw["run_id"],
                        raw["sequence"],
                        kind["type"],
                        {key: value for key, value in kind.items() if key != "type"},
                    )
                raise OrionError(str(error)) from error

    async def _execute_effect(self, agent: Agent, effect: Mapping[str, Any]) -> dict[str, Any]:
        if effect["type"] == "call_model":
            response = await self.models.resolve(ModelRef(**effect["request"]["model"])).complete(
                effect["request"]
            )
            return {"type": "model", "value": response.to_wire()}
        call = effect["call"]
        tool = next((tool for tool in agent.tools if tool.name == call["name"]), None)
        if tool is None:
            raise OrionError(f"model requested unregistered tool {call['name']!r}")
        value = tool.execute(call["arguments"])
        if inspect.isawaitable(value):
            value = await value
        return {"type": "tool", "value": {"content": value}}
