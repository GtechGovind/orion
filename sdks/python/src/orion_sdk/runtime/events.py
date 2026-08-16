"""Strongly typed runtime lifecycle events and terminal values."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import ClassVar, TypeAlias

from ..model import Usage


class ErrorCode(str, Enum):
    """Stable failure categories emitted across the native boundary."""

    INVALID_COMMAND = "invalid_command"

    INVALID_STATE = "invalid_state"

    CONFIGURATION = "configuration"

    AUTHENTICATION = "authentication"

    RATE_LIMITED = "rate_limited"

    TIMEOUT = "timeout"

    NETWORK = "network"

    UNSUPPORTED_CAPABILITY = "unsupported_capability"

    CONTENT_SAFETY = "content_safety"

    MALFORMED_RESPONSE = "malformed_response"

    PROVIDER = "provider"

    TOOL = "tool"

    CANCELLED = "cancelled"

    TURN_LIMIT_EXCEEDED = "turn_limit_exceeded"


class RunEventType(str, Enum):
    """Identifies a lifecycle event without discarding its typed payload."""

    RUN_STARTED = "run_started"

    MODEL_REQUESTED = "model_requested"

    MODEL_COMPLETED = "model_completed"

    TOOL_REQUESTED = "tool_requested"

    TOOL_COMPLETED = "tool_completed"

    RUN_COMPLETED = "run_completed"

    RUN_FAILED = "run_failed"

    RUN_CANCELLED = "run_cancelled"


@dataclass(frozen=True, slots=True)
class RunFailure:
    """Carries a safe, actionable failure across SDK boundaries."""

    code: ErrorCode

    message: str

    retryable: bool

    retry_after_ms: int | None


@dataclass(frozen=True, slots=True)
class RunStarted:
    """Records the agent selected for a newly created run."""

    type: ClassVar[RunEventType] = RunEventType.RUN_STARTED

    agent_id: str


@dataclass(frozen=True, slots=True)
class ModelRequested:
    """Records a model operation before provider execution."""

    type: ClassVar[RunEventType] = RunEventType.MODEL_REQUESTED

    turn: int

    provider: str

    model: str


@dataclass(frozen=True, slots=True)
class ModelCompleted:
    """Records normalized output from one model operation."""

    type: ClassVar[RunEventType] = RunEventType.MODEL_COMPLETED

    turn: int

    output: str

    tool_call_count: int


@dataclass(frozen=True, slots=True)
class ToolRequested:
    """Records a host tool operation before callback execution."""

    type: ClassVar[RunEventType] = RunEventType.TOOL_REQUESTED

    action_id: str

    call_id: str

    name: str


@dataclass(frozen=True, slots=True)
class ToolCompleted:
    """Records successful completion of a host tool callback."""

    type: ClassVar[RunEventType] = RunEventType.TOOL_COMPLETED

    action_id: str

    call_id: str

    name: str


@dataclass(frozen=True, slots=True)
class RunCompleted:
    """Records the successful terminal assistant output."""

    type: ClassVar[RunEventType] = RunEventType.RUN_COMPLETED

    output: str


@dataclass(frozen=True, slots=True)
class RunFailed:
    """Records a terminal normalized runtime failure."""

    type: ClassVar[RunEventType] = RunEventType.RUN_FAILED

    error: RunFailure


@dataclass(frozen=True, slots=True)
class RunCancelled:
    """Records cancellation of a run before successful completion."""

    type: ClassVar[RunEventType] = RunEventType.RUN_CANCELLED


RunEventKind: TypeAlias = (
    RunStarted
    | ModelRequested
    | ModelCompleted
    | ToolRequested
    | ToolCompleted
    | RunCompleted
    | RunFailed
    | RunCancelled
)


@dataclass(frozen=True, slots=True)
class RunEvent:
    """Wraps one ordered, immutable lifecycle observation."""

    run_id: str

    sequence: int

    kind: RunEventKind

    @property
    def type(self) -> RunEventType:
        """Return the stable discriminator for the typed payload."""

        return self.kind.type


@dataclass(frozen=True, slots=True)
class RunResult:
    """Contains successful output and the complete observed event trace."""

    run_id: str

    output: str

    usage: Usage

    turns: int

    events: tuple[RunEvent, ...]
