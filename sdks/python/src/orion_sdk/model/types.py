"""Strongly typed, provider-neutral model values."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import TypeAlias

JsonScalar: TypeAlias = None | bool | int | float | str
Json: TypeAlias = JsonScalar | list["Json"] | dict[str, "Json"]
JsonObject: TypeAlias = dict[str, Json]


def _empty_json_object() -> JsonObject:
    return {}


def _empty_provider_options() -> dict[str, JsonObject]:
    return {}


class CapabilitySupport(str, Enum):
    """Describes how an adapter implements a model capability."""

    NATIVE = "native"

    EMULATED = "emulated"

    UNSUPPORTED = "unsupported"

    UNKNOWN = "unknown"


class FinishReason(str, Enum):
    """Normalizes why a model stopped generating a response."""

    STOP = "stop"

    TOOL_CALLS = "tool_calls"

    LENGTH = "length"

    CONTENT_FILTER = "content_filter"

    OTHER = "other"


class MessageRole(str, Enum):
    """Identifies the author of a provider-neutral transcript message."""

    SYSTEM = "system"

    USER = "user"

    ASSISTANT = "assistant"

    TOOL = "tool"


@dataclass(frozen=True, slots=True)
class ModelRef:
    """Selects a model within a stable provider namespace."""

    provider: str

    model: str

    @classmethod
    def parse(cls, value: str) -> ModelRef:
        """Parse a ``provider:model`` reference.

        Raises:
            ValueError: If the provider, model, or separator is missing.
        """
        provider, separator, model = value.partition(":")
        if not separator or not provider or not model:
            raise ValueError("model reference must use provider:model notation")
        return cls(provider, model)


@dataclass(frozen=True, slots=True)
class ModelProfile:
    """Describes capabilities available for a selected model."""

    streaming: CapabilitySupport = CapabilitySupport.UNKNOWN

    tool_calling: CapabilitySupport = CapabilitySupport.UNKNOWN

    structured_output: CapabilitySupport = CapabilitySupport.UNKNOWN

    parallel_tool_calls: CapabilitySupport = CapabilitySupport.UNKNOWN

    max_context_tokens: int | None = None


@dataclass(frozen=True, slots=True)
class Usage:
    """Records normalized token consumption for one or more model calls."""

    input_tokens: int = 0

    output_tokens: int = 0


@dataclass(frozen=True, slots=True)
class ToolCall:
    """Requests execution of one host-registered tool."""

    id: str

    name: str

    arguments: JsonObject


@dataclass(frozen=True, slots=True)
class Message:
    """Represents one provider-neutral conversation message."""

    role: MessageRole

    content: str

    tool_call_id: str | None = None

    tool_calls: tuple[ToolCall, ...] = ()


@dataclass(frozen=True, slots=True)
class ToolSpec:
    """Describes model-visible tool metadata without its host callback."""

    name: str

    description: str

    input_schema: JsonObject


@dataclass(frozen=True, slots=True)
class ModelSettings:
    """Carries portable settings and provider-namespaced extensions."""

    temperature: float | None = None

    max_output_tokens: int | None = None

    provider_options: dict[str, JsonObject] = field(default_factory=_empty_provider_options)


@dataclass(frozen=True, slots=True)
class ModelRequest:
    """Contains one normalized model operation emitted by the kernel."""

    model: ModelRef

    messages: tuple[Message, ...]

    tools: tuple[ToolSpec, ...]

    output_schema: JsonObject | None

    settings: ModelSettings

    provider_state: JsonObject = field(default_factory=_empty_json_object)


@dataclass(frozen=True, slots=True)
class ModelResponse:
    """Contains a normalized provider response returned to the runtime."""

    content: str = ""

    tool_calls: tuple[ToolCall, ...] = ()

    finish_reason: FinishReason = FinishReason.STOP

    usage: Usage = Usage()

    provider_state: JsonObject = field(default_factory=_empty_json_object)
