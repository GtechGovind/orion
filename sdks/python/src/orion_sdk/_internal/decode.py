"""Validate dynamic native values and construct typed SDK contracts."""

from __future__ import annotations

import math
from typing import cast

from ..model import (
    Json,
    JsonObject,
    Message,
    MessageRole,
    ModelRef,
    ModelRequest,
    ModelSettings,
    ToolCall,
    ToolSpec,
    Usage,
)
from ..runtime.events import (
    ErrorCode,
    ModelCompleted,
    ModelRequested,
    RunCancelled,
    RunCompleted,
    RunEvent,
    RunFailed,
    RunFailure,
    RunStarted,
    ToolCompleted,
    ToolRequested,
)
from .kernel_types import CallModelEffect, ExecuteToolEffect, KernelEffect, KernelResult, KernelStep


def _object(value: object, context: str) -> dict[str, object]:
    if not isinstance(value, dict):
        raise TypeError(f"native kernel returned invalid {context}")
    raw = cast(dict[object, object], value)
    if not all(isinstance(key, str) for key in raw):
        raise TypeError(f"native kernel returned invalid {context}")
    return cast(dict[str, object], value)


def _array(value: object, context: str) -> list[object]:
    if not isinstance(value, list):
        raise TypeError(f"native kernel returned invalid {context}")
    return cast(list[object], value)


def _string(value: object, context: str) -> str:
    if not isinstance(value, str):
        raise TypeError(f"native kernel returned invalid {context}")
    return value


def _integer(value: object, context: str) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise TypeError(f"native kernel returned invalid {context}")
    return value


def _boolean(value: object, context: str) -> bool:
    if not isinstance(value, bool):
        raise TypeError(f"native kernel returned invalid {context}")
    return value


def _optional_integer(value: object, context: str) -> int | None:
    return None if value is None else _integer(value, context)


def _json(value: object, context: str) -> Json:
    if value is None or isinstance(value, (bool, int, str)):
        return value
    if isinstance(value, float):
        if not math.isfinite(value):
            raise TypeError(f"native kernel returned non-finite {context}")
        return value
    if isinstance(value, list):
        return [_json(item, context) for item in cast(list[object], value)]
    if isinstance(value, dict):
        raw = _object(cast(object, value), context)
        return {key: _json(item, context) for key, item in raw.items()}
    raise TypeError(f"native kernel returned invalid {context}")


def _json_object(value: object, context: str) -> JsonObject:
    return {key: _json(item, context) for key, item in _object(value, context).items()}


def _optional_json_object(value: object, context: str) -> JsonObject | None:
    return None if value is None else _json_object(value, context)


def _tool_call(value: object) -> ToolCall:
    raw = _object(value, "tool call")

    return ToolCall(
        id=_string(raw.get("id"), "tool call id"),
        name=_string(raw.get("name"), "tool call name"),
        arguments=_json_object(raw.get("arguments"), "tool call arguments"),
    )


def _message(value: object) -> Message:
    raw = _object(value, "model message")
    role_value = _string(raw.get("role"), "model message role")
    try:
        role = MessageRole(role_value)
    except ValueError as error:
        raise TypeError(f"native kernel returned unknown message role {role_value!r}") from error

    tool_call_id = raw.get("tool_call_id")
    return Message(
        role=role,
        content=_string(raw.get("content"), "model message content"),
        tool_call_id=(
            None if tool_call_id is None else _string(tool_call_id, "model message tool call id")
        ),
        tool_calls=tuple(_tool_call(item) for item in _array(raw.get("tool_calls"), "tool calls")),
    )


def _tool_spec(value: object) -> ToolSpec:
    raw = _object(value, "tool specification")

    return ToolSpec(
        name=_string(raw.get("name"), "tool specification name"),
        description=_string(raw.get("description"), "tool specification description"),
        input_schema=_json_object(raw.get("input_schema"), "tool input schema"),
    )


def _settings(value: object) -> ModelSettings:
    raw = _object(value, "model settings")
    options = {
        provider: _json_object(item, f"provider options for {provider!r}")
        for provider, item in _object(raw.get("provider_options"), "provider options").items()
    }
    temperature = raw.get("temperature")
    if temperature is not None and (
        not isinstance(temperature, (int, float)) or isinstance(temperature, bool)
    ):
        raise TypeError("native kernel returned invalid model temperature")

    return ModelSettings(
        temperature=None if temperature is None else float(temperature),
        max_output_tokens=_optional_integer(raw.get("max_output_tokens"), "maximum output tokens"),
        provider_options=options,
    )


def _model_request(value: object) -> ModelRequest:
    raw = _object(value, "model request")
    model = _object(raw.get("model"), "model reference")

    return ModelRequest(
        model=ModelRef(
            provider=_string(model.get("provider"), "model provider"),
            model=_string(model.get("model"), "model identifier"),
        ),
        messages=tuple(_message(item) for item in _array(raw.get("messages"), "messages")),
        tools=tuple(_tool_spec(item) for item in _array(raw.get("tools"), "tools")),
        output_schema=_optional_json_object(raw.get("output_schema"), "output schema"),
        settings=_settings(raw.get("settings")),
        provider_state=_json_object(raw.get("provider_state"), "provider state"),
    )


def _usage(value: object) -> Usage:
    raw = _object(value, "usage")

    return Usage(
        input_tokens=_integer(raw.get("input_tokens"), "input token usage"),
        output_tokens=_integer(raw.get("output_tokens"), "output token usage"),
    )


def _failure(value: object) -> RunFailure:
    raw = _object(value, "run failure")
    code_value = _string(raw.get("code"), "run failure code")
    try:
        code = ErrorCode(code_value)
    except ValueError as error:
        raise TypeError(f"native kernel returned unknown error code {code_value!r}") from error

    return RunFailure(
        code=code,
        message=_string(raw.get("message"), "run failure message"),
        retryable=_boolean(raw.get("retryable"), "run failure retryable flag"),
        retry_after_ms=_optional_integer(raw.get("retry_after_ms"), "retry delay"),
    )


def _event(value: object) -> RunEvent:
    raw = _object(value, "run event")
    kind = _object(raw.get("kind"), "run event kind")
    event_type = _string(kind.get("type"), "run event type")

    if event_type == "run_started":
        payload = RunStarted(_string(kind.get("agent_id"), "agent id"))
    elif event_type == "model_requested":
        payload = ModelRequested(
            _integer(kind.get("turn"), "model turn"),
            _string(kind.get("provider"), "model provider"),
            _string(kind.get("model"), "model identifier"),
        )
    elif event_type == "model_completed":
        payload = ModelCompleted(
            _integer(kind.get("turn"), "model turn"),
            _string(kind.get("output"), "model output"),
            _integer(kind.get("tool_call_count"), "tool call count"),
        )
    elif event_type in {"tool_requested", "tool_completed"}:
        event_class = ToolRequested if event_type == "tool_requested" else ToolCompleted
        payload = event_class(
            _string(kind.get("action_id"), "tool action id"),
            _string(kind.get("call_id"), "tool call id"),
            _string(kind.get("name"), "tool name"),
        )
    elif event_type == "run_completed":
        payload = RunCompleted(_string(kind.get("output"), "run output"))
    elif event_type == "run_failed":
        payload = RunFailed(_failure(kind.get("error")))
    elif event_type == "run_cancelled":
        payload = RunCancelled()
    else:
        raise TypeError(f"native kernel returned unknown run event {event_type!r}")

    return RunEvent(
        run_id=_string(raw.get("run_id"), "run id"),
        sequence=_integer(raw.get("sequence"), "run event sequence"),
        kind=payload,
    )


def _effect(value: object) -> KernelEffect:
    raw = _object(value, "effect")
    effect_type = _string(raw.get("type"), "effect type")

    if effect_type == "call_model":
        return CallModelEffect(_model_request(raw.get("request")))
    if effect_type == "execute_tool":
        return ExecuteToolEffect(
            action_id=_string(raw.get("action_id"), "tool action id"),
            call=_tool_call(raw.get("call")),
        )
    raise TypeError(f"native kernel returned unknown effect {effect_type!r}")


def _result(value: object) -> KernelResult:
    raw = _object(value, "run result")

    return KernelResult(
        run_id=_string(raw.get("run_id"), "run id"),
        output=_string(raw.get("output"), "run output"),
        usage=_usage(raw.get("usage")),
        turns=_integer(raw.get("turns"), "run turn count"),
    )


def decode_step(value: object) -> KernelStep:
    """Validate one native transition before orchestration uses it."""

    raw = _object(value, "kernel step")
    effect = raw.get("effect")
    result = raw.get("result")

    return KernelStep(
        events=tuple(_event(item) for item in _array(raw.get("events"), "run events")),
        effect=None if effect is None else _effect(effect),
        result=None if result is None else _result(result),
    )
