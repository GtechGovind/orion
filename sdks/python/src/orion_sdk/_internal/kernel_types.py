"""Typed values produced after validating native kernel output."""

from __future__ import annotations

from dataclasses import dataclass
from typing import TypeAlias

from ..model import ModelRequest, ToolCall, Usage
from ..runtime.events import RunEvent


@dataclass(frozen=True, slots=True)
class CallModelEffect:
    """Requests execution by the adapter selected in ``request``."""

    request: ModelRequest


@dataclass(frozen=True, slots=True)
class ExecuteToolEffect:
    """Requests execution of one application-owned tool callback."""

    action_id: str

    call: ToolCall


KernelEffect: TypeAlias = CallModelEffect | ExecuteToolEffect


@dataclass(frozen=True, slots=True)
class KernelResult:
    """Carries a successfully completed native run."""

    run_id: str

    output: str

    usage: Usage

    turns: int


@dataclass(frozen=True, slots=True)
class KernelStep:
    """Contains validated events plus one pending effect or result."""

    events: tuple[RunEvent, ...]

    effect: KernelEffect | None

    result: KernelResult | None
