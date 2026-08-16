"""Public values, extension protocols, and the OpenAI-compatible adapter."""

from __future__ import annotations

import asyncio
import json
import os
import urllib.error
import urllib.request
from collections.abc import Awaitable, Callable, Mapping
from dataclasses import dataclass, field
from typing import Any, Protocol, TypeAlias, cast

Json: TypeAlias = None | bool | int | float | str | list["Json"] | dict[str, "Json"]
WireObject: TypeAlias = Mapping[str, Any]

_PROTECTED_PROVIDER_OPTIONS = {"model", "messages", "tools", "response_format"}


def _empty_json_object() -> dict[str, Json]:
    return {}


def _object(value: object, context: str) -> dict[str, object]:
    if not isinstance(value, dict):
        raise TypeError(f"model provider returned invalid {context}")
    raw = cast(dict[object, object], value)
    if not all(isinstance(key, str) for key in raw):
        raise TypeError(f"model provider returned invalid {context}")
    return cast(dict[str, object], value)


def _array(value: object, context: str) -> list[object]:
    if not isinstance(value, list):
        raise TypeError(f"model provider returned invalid {context}")
    return cast(list[object], value)


def _string(value: object, context: str) -> str:
    if not isinstance(value, str):
        raise TypeError(f"model provider returned invalid {context}")
    return value


def _integer(value: object, context: str) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise RuntimeError(f"model provider returned invalid {context}")
    return value


@dataclass(frozen=True)
class ModelRef:
    provider: str
    model: str

    @classmethod
    def parse(cls, value: str) -> ModelRef:
        provider, separator, model = value.partition(":")
        if not separator or not provider or not model:
            raise ValueError("model reference must use provider:model notation")
        return cls(provider, model)

    def to_wire(self) -> dict[str, str]:
        return {"provider": self.provider, "model": self.model}


@dataclass(frozen=True)
class ModelProfile:
    streaming: str = "unknown"
    tool_calling: str = "unknown"
    structured_output: str = "unknown"
    parallel_tool_calls: str = "unknown"
    max_context_tokens: int | None = None


@dataclass(frozen=True)
class Usage:
    input_tokens: int = 0
    output_tokens: int = 0


@dataclass(frozen=True)
class ToolCall:
    id: str
    name: str
    arguments: Json


@dataclass(frozen=True)
class ModelResponse:
    content: str = ""
    tool_calls: tuple[ToolCall, ...] = ()
    finish_reason: str = "stop"
    usage: Usage = Usage()
    provider_state: Mapping[str, Json] = field(default_factory=_empty_json_object)

    def to_wire(self) -> dict[str, Json]:
        return {
            "content": self.content,
            "tool_calls": [vars(call) for call in self.tool_calls],
            "finish_reason": self.finish_reason,
            "usage": vars(self.usage),
            "provider_state": dict(self.provider_state),
        }


class ModelAdapter(Protocol):
    @property
    def provider(self) -> str: ...
    def profile(self, model: ModelRef) -> ModelProfile: ...
    async def complete(self, request: WireObject) -> ModelResponse: ...
    async def close(self) -> None: ...


ToolHandler = Callable[[Json], Json | Awaitable[Json]]


@dataclass(frozen=True)
class Tool:
    name: str
    description: str
    input_schema: Mapping[str, Json]
    execute: ToolHandler

    def to_wire(self) -> dict[str, Json]:
        return {
            "name": self.name,
            "description": self.description,
            "input_schema": dict(self.input_schema),
        }


@dataclass(frozen=True)
class Agent:
    id: str
    name: str
    instructions: str
    model: ModelRef | str
    tools: tuple[Tool, ...] = ()
    output_schema: Mapping[str, Json] | None = None
    temperature: float | None = None
    max_output_tokens: int | None = None
    provider_options: Mapping[str, Json] = field(default_factory=_empty_json_object)
    max_turns: int = 8

    def to_wire(self) -> dict[str, Json]:
        model = ModelRef.parse(self.model) if isinstance(self.model, str) else self.model
        model_wire: dict[str, Json] = {
            "provider": model.provider,
            "model": model.model,
        }
        return {
            "id": self.id,
            "name": self.name,
            "instructions": self.instructions,
            "model": model_wire,
            "tools": [tool.to_wire() for tool in self.tools],
            "output_schema": dict(self.output_schema) if self.output_schema else None,
            "model_settings": {
                "temperature": self.temperature,
                "max_output_tokens": self.max_output_tokens,
                "provider_options": dict(self.provider_options),
            },
            "max_turns": self.max_turns,
        }


@dataclass(frozen=True)
class RunEvent:
    run_id: str
    sequence: int
    type: str
    data: Mapping[str, Json]


@dataclass(frozen=True)
class RunResult:
    run_id: str
    output: str
    usage: Usage
    turns: int
    events: tuple[RunEvent, ...]


class ModelRegistry:
    def __init__(self, adapters: list[ModelAdapter] | tuple[ModelAdapter, ...]) -> None:
        providers = [adapter.provider for adapter in adapters]
        if len(providers) != len(set(providers)):
            raise ValueError("model adapter providers must be unique")
        self._adapters = {adapter.provider: adapter for adapter in adapters}

    def resolve(self, model: ModelRef) -> ModelAdapter:
        try:
            return self._adapters[model.provider]
        except KeyError as error:
            raise ValueError(
                f"no model adapter registered for provider {model.provider!r}"
            ) from error

    async def close(self) -> None:
        await asyncio.gather(*(adapter.close() for adapter in self._adapters.values()))


class OpenAICompatibleAdapter:
    """Minimal Chat Completions adapter for OpenAI and compatible local servers."""

    def __init__(
        self,
        *,
        provider: str = "openai",
        api_key: str | None = None,
        base_url: str = "https://api.openai.com/v1",
        timeout: float = 60.0,
    ) -> None:
        self._provider = provider
        self._api_key = api_key if api_key is not None else os.getenv("OPENAI_API_KEY")
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout

    @property
    def provider(self) -> str:
        return self._provider

    def profile(self, model: ModelRef) -> ModelProfile:
        del model
        return ModelProfile(
            streaming="unsupported",
            tool_calling="native",
            structured_output="native",
        )

    async def complete(self, request: WireObject) -> ModelResponse:
        return await asyncio.to_thread(self._complete_sync, request)

    async def close(self) -> None:
        return None

    def _complete_sync(self, request: WireObject) -> ModelResponse:
        settings = request["settings"]
        messages: list[Json] = []
        for item in request["messages"]:
            message: dict[str, Json] = {"role": item["role"], "content": item["content"]}
            if item.get("tool_call_id"):
                message["tool_call_id"] = item["tool_call_id"]
            if item.get("tool_calls"):
                message["tool_calls"] = [
                    {
                        "id": call["id"],
                        "type": "function",
                        "function": {
                            "name": call["name"],
                            "arguments": json.dumps(call["arguments"]),
                        },
                    }
                    for call in item["tool_calls"]
                ]
            messages.append(message)
        payload: dict[str, Json] = {"model": request["model"]["model"], "messages": messages}
        if request.get("tools"):
            payload["tools"] = [
                {
                    "type": "function",
                    "function": {
                        "name": tool["name"],
                        "description": tool["description"],
                        "parameters": tool["input_schema"],
                    },
                }
                for tool in request["tools"]
            ]
        if settings.get("temperature") is not None:
            payload["temperature"] = settings["temperature"]
        if settings.get("max_output_tokens") is not None:
            payload["max_tokens"] = settings["max_output_tokens"]
        if request.get("output_schema"):
            payload["response_format"] = {
                "type": "json_schema",
                "json_schema": {
                    "name": "orion_output",
                    "schema": request["output_schema"],
                    "strict": True,
                },
            }
        provider_options = settings.get("provider_options", {}).get(self.provider, {})
        protected = _PROTECTED_PROVIDER_OPTIONS.intersection(provider_options)
        if protected:
            names = ", ".join(sorted(protected))
            raise ValueError(f"provider options cannot override protected fields: {names}")
        payload.update(provider_options)
        headers = {"Content-Type": "application/json"}
        if self._api_key:
            headers["Authorization"] = f"Bearer {self._api_key}"
        raw = urllib.request.Request(
            f"{self._base_url}/chat/completions",
            data=json.dumps(payload).encode(),
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(raw, timeout=self._timeout) as response:
                data = _object(json.load(response), "response object")
        except urllib.error.HTTPError as error:
            detail = error.read(4096).decode(errors="replace")
            raise RuntimeError(f"model provider returned HTTP {error.code}: {detail}") from error
        choices = _array(data.get("choices"), "choices")
        if not choices:
            raise RuntimeError("model provider returned no choices")
        choice = _object(choices[0], "choice")
        provider_message = _object(choice.get("message"), "message")
        calls_list: list[ToolCall] = []
        for raw_call in _array(provider_message.get("tool_calls", []), "tool calls"):
            call = _object(raw_call, "tool call")
            function = _object(call.get("function"), "tool function")
            arguments_text = _string(function.get("arguments"), "tool arguments")
            arguments = cast(Json, json.loads(arguments_text))
            calls_list.append(
                ToolCall(
                    _string(call.get("id"), "tool call id"),
                    _string(function.get("name"), "tool name"),
                    arguments,
                )
            )
        calls = tuple(calls_list)
        usage = _object(data.get("usage", {}), "usage")
        finish_reason = choice.get("finish_reason")
        finish = {"stop": "stop", "length": "length", "content_filter": "content_filter"}.get(
            finish_reason if isinstance(finish_reason, str) else "other", "other"
        )
        content = provider_message.get("content")
        return ModelResponse(
            "" if content is None else _string(content, "message content"),
            calls,
            "tool_calls" if calls else finish,
            Usage(
                _integer(usage.get("prompt_tokens", 0), "prompt token usage"),
                _integer(usage.get("completion_tokens", 0), "completion token usage"),
            ),
        )
