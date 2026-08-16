"""Behavior tests for the single supported Python application API."""

import asyncio
import unittest
from dataclasses import dataclass

from orion_sdk import Agent, AgentResult, ErrorCode, OrionError
from orion_sdk.model import (
    CapabilitySupport,
    FinishReason,
    Model,
    ModelProfile,
    ModelRef,
    ModelRequest,
    ModelResponse,
    ToolCall,
)


@dataclass(frozen=True, slots=True)
class WeatherResult:
    city: str

    temperature_c: int


@dataclass(frozen=True, slots=True)
class WeatherAnswer:
    city: str

    temperature_c: int

    summary: str


async def weather(city: str) -> WeatherResult:
    """Get the current weather for a city."""

    return WeatherResult(city=city, temperature_c=31)


class FakeModel:
    provider = "fake"

    def __init__(self) -> None:
        self.calls = 0

    def profile(self, model: ModelRef) -> ModelProfile:
        return ModelProfile(
            tool_calling=CapabilitySupport.NATIVE,
            structured_output=CapabilitySupport.NATIVE,
        )

    async def close(self) -> None:
        pass

    async def complete(self, request: ModelRequest) -> ModelResponse:
        self.calls += 1
        if self.calls == 1:
            return ModelResponse(
                tool_calls=(ToolCall("c1", "weather", {"city": "Delhi"}),),
                finish_reason=FinishReason.TOOL_CALLS,
            )

        return ModelResponse(
            content=('{"city":"Delhi","temperature_c":31,"summary":"Delhi is 31 C."}')
        )


class FailingModel(FakeModel):
    async def complete(self, request: ModelRequest) -> ModelResponse:
        raise RuntimeError("provider unavailable")


class BlockingModel(FakeModel):
    def __init__(self, started: asyncio.Event) -> None:
        super().__init__()
        self._started = started

    async def complete(self, request: ModelRequest) -> ModelResponse:
        self._started.set()
        await asyncio.Future[None]()
        raise AssertionError("unreachable")


class AgentTest(unittest.IsolatedAsyncioTestCase):
    async def test_function_tool_and_structured_output_are_automatic(self) -> None:
        adapter = FakeModel()
        agent = Agent(
            model=Model(ModelRef("fake", "test"), adapter),
            tools=[weather],
            output=WeatherAnswer,
            instructions="Use the weather tool.",
        )

        result = await agent.run("Weather?")

        self.assertIsInstance(result.output, WeatherAnswer)
        self.assertEqual(result.output.temperature_c, 31)
        self.assertEqual(result.turns, 2)
        self.assertEqual(adapter.calls, 2)

    async def test_stream_finishes_with_the_same_typed_result(self) -> None:
        agent = Agent(
            model=Model(ModelRef("fake", "test"), FakeModel()),
            tools=[weather],
            output=WeatherAnswer,
        )

        items = [item async for item in agent.stream("Weather?")]

        self.assertIsInstance(items[-1], AgentResult)
        terminal = items[-1]
        if not isinstance(terminal, AgentResult):
            self.fail("stream did not finish with an AgentResult")
        self.assertEqual(terminal.output.city, "Delhi")

    async def test_turn_limit_preserves_rust_error_code(self) -> None:
        agent = Agent(
            model=Model(ModelRef("fake", "test"), FakeModel()),
            tools=[weather],
            output=WeatherAnswer,
            max_turns=1,
        )

        with self.assertRaises(OrionError) as captured:
            await agent.run("Weather?")

        self.assertEqual(captured.exception.code, ErrorCode.TURN_LIMIT_EXCEEDED)
        self.assertFalse(captured.exception.retryable)
        self.assertIsNone(captured.exception.retry_after_ms)

    async def test_provider_failure_preserves_provider_category(self) -> None:
        agent = Agent(
            model=Model(ModelRef("fake", "test"), FailingModel()),
            output=WeatherAnswer,
        )

        with self.assertRaises(OrionError) as captured:
            await agent.run("Weather?")

        self.assertEqual(captured.exception.code, ErrorCode.PROVIDER)
        self.assertEqual(str(captured.exception), "provider unavailable")

    async def test_tool_failure_preserves_tool_category(self) -> None:
        async def weather(city: str) -> WeatherResult:
            raise ValueError(f"weather unavailable for {city}")

        agent = Agent(
            model=Model(ModelRef("fake", "test"), FakeModel()),
            tools=[weather],
            output=WeatherAnswer,
        )

        with self.assertRaises(OrionError) as captured:
            await agent.run("Weather?")

        self.assertEqual(captured.exception.code, ErrorCode.TOOL)
        self.assertEqual(str(captured.exception), "weather unavailable for Delhi")

    async def test_task_cancellation_is_not_wrapped(self) -> None:
        started = asyncio.Event()
        agent = Agent(
            model=Model(ModelRef("fake", "test"), BlockingModel(started)),
            output=WeatherAnswer,
        )
        task = asyncio.create_task(agent.run("Weather?"))
        await started.wait()

        task.cancel()

        with self.assertRaises(asyncio.CancelledError):
            await task


if __name__ == "__main__":
    unittest.main()
