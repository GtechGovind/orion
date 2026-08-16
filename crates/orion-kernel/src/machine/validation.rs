use std::collections::{BTreeMap, BTreeSet};

use orion_protocol::{AgentSpec, ErrorCode, ModelResponse, ProtocolError, StartRun};

use crate::state::{RunState, RunStatus};

use super::KernelError;

const MAX_TURNS: u32 = 1_024;
const MAX_TOOL_CALLS_PER_TURN: usize = 128;
const EVENT_SEQUENCE_HEADROOM: u64 = 300_000;

pub(super) fn validate_command(command: &StartRun) -> Result<(), KernelError> {
    if command.run_id.0.trim().is_empty() {
        return Err(invalid("run id must not be empty"));
    }

    if command.agent.id.trim().is_empty() {
        return Err(invalid("agent id must not be empty"));
    }

    if command.agent.model.provider.trim().is_empty() || command.agent.model.model.trim().is_empty()
    {
        return Err(invalid("model provider and model name must not be empty"));
    }

    if !(1..=MAX_TURNS).contains(&command.agent.max_turns) {
        return Err(invalid("max_turns must be between 1 and 1024"));
    }

    validate_tools(&command.agent)?;

    if let Some(schema) = &command.agent.output_schema {
        validate_schema(schema, "output schema")?;
    }

    Ok(())
}

pub(super) fn validate_state(state: &RunState) -> Result<(), KernelError> {
    validate_command(&StartRun {
        run_id: state.run_id.clone(),
        agent: state.agent.clone(),
        input: String::new(),
    })?;

    if state.turns > state.agent.max_turns {
        return Err(invalid("completed turns exceed the configured limit"));
    }

    if state.next_sequence > u64::MAX - EVENT_SEQUENCE_HEADROOM {
        return Err(invalid("event sequence has exhausted its safe range"));
    }

    match state.status {
        RunStatus::Running if state.pending_effect.is_none() => {
            Err(invalid("a running state must contain one pending effect"))
        }
        RunStatus::Completed if state.pending_effect.is_some() || state.output.is_none() => Err(
            invalid("a completed state must have output and no pending effect"),
        ),
        RunStatus::Failed | RunStatus::Cancelled if state.pending_effect.is_some() => Err(invalid(
            "a terminal state must not contain a pending effect",
        )),
        _ => Ok(()),
    }
}

pub(super) fn validate_tool_calls(
    agent: &AgentSpec,
    response: &ModelResponse,
) -> Result<(), ProtocolError> {
    if response.tool_calls.is_empty() {
        return Ok(());
    }

    if response.finish_reason != orion_protocol::FinishReason::ToolCalls {
        return Err(malformed(
            "model returned tool calls with an inconsistent finish reason",
        ));
    }

    if response.tool_calls.len() > MAX_TOOL_CALLS_PER_TURN {
        return Err(malformed(
            "model returned more than 128 tool calls in one turn",
        ));
    }

    let declared_tools: BTreeMap<&str, &serde_json::Value> = agent
        .tools
        .iter()
        .map(|tool| (tool.name.as_str(), &tool.input_schema))
        .collect();

    let mut call_ids = BTreeSet::new();

    for call in &response.tool_calls {
        if call.id.is_empty() || !call_ids.insert(call.id.as_str()) {
            return Err(malformed(
                "model returned an empty or duplicate tool-call id",
            ));
        }

        let Some(schema) = declared_tools.get(call.name.as_str()) else {
            return Err(malformed(
                "model requested a tool that is not declared by the agent",
            ));
        };

        if !call.arguments.is_object() {
            return Err(malformed("tool-call arguments must be a JSON object"));
        }

        if !instance_matches_schema(schema, &call.arguments) {
            return Err(malformed(
                "tool-call arguments do not match the declared input schema",
            ));
        }
    }

    Ok(())
}

fn validate_tools(agent: &AgentSpec) -> Result<(), KernelError> {
    let mut names = BTreeSet::new();

    for tool in &agent.tools {
        if tool.name.trim().is_empty() || !names.insert(tool.name.as_str()) {
            return Err(invalid("tool names must be non-empty and unique"));
        }

        validate_schema(&tool.input_schema, "tool input schema")?;
    }

    Ok(())
}

pub(super) fn validate_structured_output(
    agent: &AgentSpec,
    response: &ModelResponse,
) -> Result<(), ProtocolError> {
    let Some(schema) = &agent.output_schema else {
        return Ok(());
    };

    if !response.tool_calls.is_empty() {
        return Ok(());
    }

    let output: serde_json::Value = serde_json::from_str(&response.content)
        .map_err(|_| malformed("structured model output is not valid JSON"))?;

    if !instance_matches_schema(schema, &output) {
        return Err(malformed(
            "structured model output does not match the declared output schema",
        ));
    }

    Ok(())
}

fn validate_schema(schema: &serde_json::Value, context: &str) -> Result<(), KernelError> {
    if !schema.is_object() {
        return Err(invalid(&format!("{context} must be a JSON object")));
    }

    jsonschema::draft202012::meta::validate(schema).map_err(|_| {
        invalid(&format!(
            "{context} must be valid JSON Schema Draft 2020-12"
        ))
    })?;

    jsonschema::draft202012::new(schema).map_err(|_| {
        invalid(&format!(
            "{context} contains unsupported or unresolved references"
        ))
    })?;

    Ok(())
}

fn instance_matches_schema(schema: &serde_json::Value, instance: &serde_json::Value) -> bool {
    jsonschema::draft202012::new(schema).is_ok_and(|validator| validator.is_valid(instance))
}

fn invalid(message: &str) -> KernelError {
    KernelError::InvalidState(message.to_owned())
}

fn malformed(message: &str) -> ProtocolError {
    ProtocolError {
        code: ErrorCode::MalformedResponse,
        message: message.to_owned(),
        retryable: false,
        retry_after_ms: None,
    }
}
