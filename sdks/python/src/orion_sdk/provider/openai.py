"""OpenAI Chat Completions adapter and compatible endpoint support."""

from __future__ import annotations

import asyncio
import json
import os
import urllib.error
import urllib.request
from typing import cast

from ..model import (
    CapabilitySupport,
    FinishReason,
    Json,
    Model,
    ModelProfile,
    ModelRef,
    ModelRequest,
    ModelResponse,
    ToolCall,
    Usage,
)

_PROTECTED_PROVIDER_OPTIONS = {"model", "messages", "tools", "response_format"}


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
        raise TypeError(f"model provider returned invalid {context}")
    return value


def _json_value(value: object, context: str) -> Json:
    if value is None or isinstance(value, (bool, int, float, str)):
        return value
    if isinstance(value, list):
        return [_json_value(item, context) for item in cast(list[object], value)]
    if isinstance(value, dict):
        raw = _object(cast(object, value), context)
        return {key: _json_value(item, context) for key, item in raw.items()}
    raise TypeError(f"model provider returned invalid {context}")


class OpenAICompatibleAdapter:
    """Executes normalized requests against an OpenAI-compatible endpoint."""

    def __init__(
        self,
        *,
        provider: str = "openai",
        api_key: str | None = None,
        base_url: str = "https://api.openai.com/v1",
        timeout: float = 60.0,
    ) -> None:
        """Configure a provider namespace and Chat Completions endpoint.

        ``api_key`` defaults to ``OPENAI_API_KEY``. A positive timeout is
        required and applies to the complete blocking HTTP operation.
        """

        if not provider:
            raise ValueError("provider must be non-empty")
        if not base_url:
            raise ValueError("base_url must be non-empty")
        if timeout <= 0:
            raise ValueError("timeout must be positive")

        self._provider = provider
        self._api_key = api_key if api_key is not None else os.getenv("OPENAI_API_KEY")
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout

    @property
    def provider(self) -> str:
        """Return the provider namespace owned by this adapter."""
        return self._provider

    def profile(self, model: ModelRef) -> ModelProfile:
        """Return capabilities for a model owned by this adapter."""

        if model.provider != self.provider:
            raise ValueError("model provider does not match adapter")

        return ModelProfile(
            streaming=CapabilitySupport.UNSUPPORTED,
            tool_calling=CapabilitySupport.NATIVE,
            structured_output=CapabilitySupport.NATIVE,
        )

    async def complete(self, request: ModelRequest) -> ModelResponse:
        """Execute a request without blocking the asyncio event loop.

        Raises:
            RuntimeError: If transport fails or the endpoint rejects the call.
            TypeError: If the endpoint returns a malformed response.
        """

        return await asyncio.to_thread(self._complete_sync, request)

    async def close(self) -> None:
        """Release resources; urllib owns no persistent client resources."""

    def _complete_sync(self, request: ModelRequest) -> ModelResponse:
        payload = self._build_payload(request)
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
            raise RuntimeError(f"model provider returned HTTP {error.code}") from error

        return self._parse_response(data)

    def _build_payload(self, request: ModelRequest) -> dict[str, Json]:
        messages: list[Json] = []
        for item in request.messages:
            message: dict[str, Json] = {"role": item.role.value, "content": item.content}
            if item.tool_call_id:
                message["tool_call_id"] = item.tool_call_id
            if item.tool_calls:
                message["tool_calls"] = [
                    {
                        "id": call.id,
                        "type": "function",
                        "function": {
                            "name": call.name,
                            "arguments": json.dumps(call.arguments),
                        },
                    }
                    for call in item.tool_calls
                ]
            messages.append(message)

        payload: dict[str, Json] = {"model": request.model.model, "messages": messages}
        if request.tools:
            payload["tools"] = [
                {
                    "type": "function",
                    "function": {
                        "name": tool.name,
                        "description": tool.description,
                        "parameters": tool.input_schema,
                    },
                }
                for tool in request.tools
            ]

        settings = request.settings
        if settings.temperature is not None:
            payload["temperature"] = settings.temperature
        if settings.max_output_tokens is not None:
            payload["max_tokens"] = settings.max_output_tokens
        if request.output_schema:
            payload["response_format"] = {
                "type": "json_schema",
                "json_schema": {
                    "name": "orion_output",
                    "schema": request.output_schema,
                    "strict": True,
                },
            }
        provider_options = settings.provider_options.get(self.provider, {})
        protected = _PROTECTED_PROVIDER_OPTIONS.intersection(provider_options)
        if protected:
            names = ", ".join(sorted(protected))
            raise ValueError(f"provider options cannot override protected fields: {names}")
        payload.update(provider_options)

        return payload

    def _parse_response(self, data: dict[str, object]) -> ModelResponse:
        choices = _array(data.get("choices"), "choices")
        if not choices:
            raise RuntimeError("model provider returned no choices")
        choice = _object(choices[0], "choice")
        message = _object(choice.get("message"), "message")
        calls = tuple(
            self._parse_tool_call(value)
            for value in _array(message.get("tool_calls", []), "tool calls")
        )
        usage = _object(data.get("usage", {}), "usage")
        finish_reason = choice.get("finish_reason")
        finish = {
            "stop": FinishReason.STOP,
            "length": FinishReason.LENGTH,
            "content_filter": FinishReason.CONTENT_FILTER,
        }.get(
            finish_reason if isinstance(finish_reason, str) else "other",
            FinishReason.OTHER,
        )
        content = message.get("content")

        return ModelResponse(
            content="" if content is None else _string(content, "message content"),
            tool_calls=calls,
            finish_reason=FinishReason.TOOL_CALLS if calls else finish,
            usage=Usage(
                input_tokens=_integer(usage.get("prompt_tokens", 0), "prompt token usage"),
                output_tokens=_integer(usage.get("completion_tokens", 0), "completion token usage"),
            ),
        )

    @staticmethod
    def _parse_tool_call(value: object) -> ToolCall:
        call = _object(value, "tool call")
        function = _object(call.get("function"), "tool function")
        decoded: object = json.loads(_string(function.get("arguments"), "tool arguments"))

        return ToolCall(
            id=_string(call.get("id"), "tool call id"),
            name=_string(function.get("name"), "tool name"),
            arguments={
                key: _json_value(item, "tool arguments")
                for key, item in _object(decoded, "tool arguments").items()
            },
        )


class OpenAI(Model):
    """Selects an OpenAI model and configures its application-owned adapter."""

    def __init__(
        self,
        model: str,
        *,
        api_key: str | None = None,
        base_url: str = "https://api.openai.com/v1",
        timeout: float = 60.0,
    ) -> None:
        """Create a model using OpenAI Chat Completions or a compatible endpoint."""

        super().__init__(
            ref=ModelRef(provider="openai", model=model),
            adapter=OpenAICompatibleAdapter(
                api_key=api_key,
                base_url=base_url,
                timeout=timeout,
            ),
        )
