"""Async orchestration backed by an in-process PyO3 kernel session."""

# pyright: reportMissingModuleSource=false

from __future__ import annotations

import asyncio
import inspect
import uuid
from collections.abc import AsyncGenerator, Sequence

from .._internal.decode import decode_step
from .._internal.encode import encode_failure, encode_model_result, encode_start, encode_tool_result
from .._internal.kernel_types import CallModelEffect, ExecuteToolEffect, KernelEffect
from .._internal.native import NativeRun
from ..model import Json, ModelRegistry
from .agent import AgentDefinition
from .events import ErrorCode, RunCancelled, RunEvent, RunFailed, RunFailure, RunResult


class OrionError(RuntimeError):
    """Reports a stable SDK failure without discarding retry metadata."""

    def __init__(
        self,
        message: str,
        *,
        code: ErrorCode,
        retryable: bool = False,
        retry_after_ms: int | None = None,
    ) -> None:
        """Create an application-safe error from normalized runtime metadata."""

        super().__init__(message)
        self.code = code
        self.retryable = retryable
        self.retry_after_ms = retry_after_ms


class Runner:
    """Coordinates host effects while Rust owns mutable run state."""

    def __init__(self, *, models: ModelRegistry) -> None:
        """Create a runner that resolves adapters through ``models``.

        The application retains ownership of the registry and closes it after
        every runner that uses it has stopped.
        """

        self._models = models

    async def run(
        self,
        agent: AgentDefinition,
        input_text: str,
        *,
        run_id: str | None = None,
    ) -> RunResult:
        """Run ``agent`` until successful completion.

        Raises:
            OrionError: If execution fails or ends without a result.
            asyncio.CancelledError: If the calling task is cancelled.
        """

        result: RunResult | None = None
        async for item in self.run_stream(agent, input_text, run_id=run_id):
            if isinstance(item, RunResult):
                result = item

        if result is None:
            raise OrionError("run ended without a result", code=ErrorCode.INVALID_STATE)

        return result

    async def run_stream(
        self,
        agent: AgentDefinition,
        input_text: str,
        *,
        run_id: str | None = None,
    ) -> AsyncGenerator[RunEvent | RunResult, None]:
        """Yield ordered lifecycle events followed by one successful result.

        Raises:
            OrionError: If preflight validation or a host effect fails.
            asyncio.CancelledError: After requesting native cancellation when
                the consuming task is cancelled.
        """

        adapter = self._models.resolve(agent.model)
        profile = adapter.profile(agent.model)
        if agent.tools and profile.tool_calling.value == "unsupported":
            raise OrionError(
                f"model {agent.model.provider}:{agent.model.model} lacks tool calling",
                code=ErrorCode.UNSUPPORTED_CAPABILITY,
            )
        if agent.output_schema and profile.structured_output.value == "unsupported":
            raise OrionError(
                f"model {agent.model.provider}:{agent.model.model} lacks structured output",
                code=ErrorCode.UNSUPPORTED_CAPABILITY,
            )

        native = NativeRun(encode_start(agent, input_text, run_id or f"run-{uuid.uuid4()}"))
        step = decode_step(native.take_step())
        events: list[RunEvent] = []
        terminal = False

        try:
            while not terminal:
                for event in step.events:
                    events.append(event)
                    yield event

                if step.result is not None:
                    terminal = True
                    yield RunResult(
                        run_id=step.result.run_id,
                        output=step.result.output,
                        usage=step.result.usage,
                        turns=step.result.turns,
                        events=tuple(events),
                    )
                    return
                if step.effect is None:
                    terminal_error = _terminal_error(events)
                    if terminal_error is not None:
                        raise terminal_error
                    raise OrionError(
                        "run stopped without an effect or successful result",
                        code=ErrorCode.INVALID_STATE,
                    )

                try:
                    effect_result = await self._execute_effect(agent, step.effect)
                    step = decode_step(native.resume(effect_result))
                except asyncio.CancelledError:
                    terminal = True
                    try:
                        native.cancel()
                    except RuntimeError as cleanup_error:
                        # Task cancellation remains authoritative if native cleanup fails.
                        del cleanup_error
                    raise
                except Exception as error:
                    fallback_code = (
                        ErrorCode.PROVIDER
                        if isinstance(step.effect, CallModelEffect)
                        else ErrorCode.TOOL
                    )
                    try:
                        failed = decode_step(
                            native.fail(encode_failure(fallback_code.value, str(error)))
                        )
                    except RuntimeError:
                        raise OrionError(str(error), code=fallback_code) from error

                    terminal = True
                    for event in failed.events:
                        yield event
                    normalized = _terminal_error(failed.events)
                    if normalized is not None:
                        raise normalized from error
                    raise OrionError(str(error), code=fallback_code) from error
        finally:
            if not terminal:
                try:
                    native.cancel()
                except RuntimeError as cleanup_error:
                    # Closing a suspended stream must not replace cancellation.
                    del cleanup_error

    async def _execute_effect(
        self,
        agent: AgentDefinition,
        effect: KernelEffect,
    ) -> dict[str, Json]:
        """Execute one already-validated host effect."""

        if isinstance(effect, CallModelEffect):
            adapter = self._models.resolve(effect.request.model)
            response = await adapter.complete(effect.request)
            return encode_model_result(response)

        return await self._execute_tool(agent, effect)

    @staticmethod
    async def _execute_tool(
        agent: AgentDefinition,
        effect: ExecuteToolEffect,
    ) -> dict[str, Json]:
        """Resolve and invoke the callback selected by a tool effect."""

        tool = next(
            (candidate for candidate in agent.tools if candidate.name == effect.call.name),
            None,
        )
        if tool is None:
            raise OrionError(
                f"model requested unregistered tool {effect.call.name!r}",
                code=ErrorCode.TOOL,
            )

        value = tool.execute(effect.call.arguments)
        if inspect.isawaitable(value):
            value = await value

        return encode_tool_result(value)


def _terminal_error(events: Sequence[RunEvent]) -> OrionError | None:
    """Convert a typed terminal event into its public SDK error."""

    for event in reversed(events):
        if isinstance(event.kind, RunFailed):
            return _error_from_failure(event.kind.error)
        if isinstance(event.kind, RunCancelled):
            return OrionError("run cancelled", code=ErrorCode.CANCELLED)

    return None


def _error_from_failure(failure: RunFailure) -> OrionError:
    """Preserve every field from a Rust-owned terminal failure."""

    return OrionError(
        failure.message,
        code=failure.code,
        retryable=failure.retryable,
        retry_after_ms=failure.retry_after_ms,
    )
