"""Encode typed SDK values for the direct PyO3 boundary."""

from ..model import Json, JsonObject, ModelResponse, ToolCall
from ..runtime.agent import AgentDefinition


def _encode_tool_call(call: ToolCall) -> JsonObject:
    return {"id": call.id, "name": call.name, "arguments": call.arguments}


def encode_start(agent: AgentDefinition, input_text: str, run_id: str) -> JsonObject:
    """Encode one validated run command for ``NativeRun``."""

    tools: list[Json] = [
        {
            "name": tool.name,
            "description": tool.description,
            "input_schema": tool.input_schema,
        }
        for tool in agent.tools
    ]
    provider_options: JsonObject = {
        provider: options for provider, options in agent.provider_options.items()
    }
    model_settings: JsonObject = {
        "temperature": agent.temperature,
        "max_output_tokens": agent.max_output_tokens,
        "provider_options": provider_options,
    }
    command_agent: JsonObject = {
        "id": agent.id,
        "name": agent.name,
        "instructions": agent.instructions,
        "model": {"provider": agent.model.provider, "model": agent.model.model},
        "tools": tools,
        "output_schema": agent.output_schema,
        "model_settings": model_settings,
        "max_turns": agent.max_turns,
    }

    return {"run_id": run_id, "agent": command_agent, "input": input_text}


def encode_model_result(response: ModelResponse) -> JsonObject:
    """Encode a normalized model response for kernel resumption."""

    usage: JsonObject = {
        "input_tokens": response.usage.input_tokens,
        "output_tokens": response.usage.output_tokens,
    }
    value: JsonObject = {
        "content": response.content,
        "tool_calls": [_encode_tool_call(call) for call in response.tool_calls],
        "finish_reason": response.finish_reason.value,
        "usage": usage,
        "provider_state": response.provider_state,
    }

    return {"type": "model", "value": value}


def encode_tool_result(content: Json) -> JsonObject:
    """Encode one host tool result for kernel resumption."""

    return {"type": "tool", "value": {"content": content}}


def encode_failure(code: str, message: str) -> JsonObject:
    """Encode a bounded, non-retryable host failure."""

    return {
        "code": code,
        "message": message[:4096],
        "retryable": False,
        "retry_after_ms": None,
    }
