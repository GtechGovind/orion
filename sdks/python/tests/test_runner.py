import unittest

from orion_sdk import Agent, ModelProfile, ModelRegistry, ModelResponse, Runner, Tool, ToolCall


class FakeModel:
    provider = "fake"

    def __init__(self):
        self.calls = 0

    def profile(self, model):
        return ModelProfile(tool_calling="native")

    async def close(self):
        pass

    async def complete(self, request):
        self.calls += 1
        if self.calls == 1:
            return ModelResponse(
                tool_calls=(ToolCall("c1", "weather", {"city": "Delhi"}),),
                finish_reason="tool_calls",
            )
        return ModelResponse(content="Delhi is 31 C")


class RunnerTest(unittest.IsolatedAsyncioTestCase):
    async def test_tool_loop_runs_through_rust_kernel(self):
        agent = Agent(
            "weather",
            "Weather",
            "Be concise",
            "fake:test",
            tools=(
                Tool(
                    "weather", "Get weather", {"type": "object"}, lambda args: {"temperature": 31}
                ),
            ),
        )
        result = await Runner(models=ModelRegistry([FakeModel()])).run(agent, "Weather?")
        self.assertEqual(result.output, "Delhi is 31 C")
        self.assertEqual(result.turns, 2)
        self.assertEqual(result.events[-1].type, "run_completed")


if __name__ == "__main__":
    unittest.main()
