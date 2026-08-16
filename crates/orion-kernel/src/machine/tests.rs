use std::collections::BTreeMap;

use orion_protocol::{
    AgentSpec, Effect, EffectResult, ErrorCode, FinishReason, ModelRef, ModelResponse,
    ModelSettings, ProtocolError, RunEventKind, RunId, StartRun, ToolCall, ToolResponse, ToolSpec,
    Usage,
};
use serde_json::json;

use crate::state::RunStatus;

use super::{Kernel, KernelError};

fn command() -> StartRun {
    StartRun {
        run_id: RunId("run-1".into()),
        input: "weather?".into(),
        agent: AgentSpec {
            id: "weather".into(),
            name: "Weather".into(),
            instructions: "Be useful".into(),
            model: ModelRef::parse("fake:test").unwrap(),
            tools: vec![ToolSpec {
                name: "weather".into(),
                description: "lookup".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {"city": {"type": "string"}},
                    "required": ["city"],
                    "additionalProperties": false
                }),
            }],
            output_schema: None,
            model_settings: ModelSettings::default(),
            max_turns: 4,
        },
    }
}

#[test]
fn runs_a_deterministic_tool_loop() {
    let (mut kernel, start) = Kernel::start(command()).unwrap();
    assert!(matches!(start.effect, Some(Effect::CallModel { .. })));
    let tool_step = kernel
        .resume(EffectResult::Model(ModelResponse {
            content: String::new(),
            tool_calls: vec![ToolCall {
                id: "c1".into(),
                name: "weather".into(),
                arguments: json!({"city":"Delhi"}),
            }],
            finish_reason: FinishReason::ToolCalls,
            usage: Usage {
                input_tokens: 10,
                output_tokens: 2,
            },
            provider_state: BTreeMap::new(),
        }))
        .unwrap();
    assert!(matches!(tool_step.effect, Some(Effect::ExecuteTool { .. })));
    let next = kernel
        .resume(EffectResult::Tool(ToolResponse {
            content: json!({"c": 31}),
        }))
        .unwrap();
    assert!(matches!(next.effect, Some(Effect::CallModel { .. })));
    let done = kernel
        .resume(EffectResult::Model(ModelResponse {
            content: "It is 31C".into(),
            tool_calls: vec![],
            finish_reason: FinishReason::Stop,
            usage: Usage {
                input_tokens: 15,
                output_tokens: 5,
            },
            provider_state: BTreeMap::new(),
        }))
        .unwrap();
    assert_eq!(
        done.result.unwrap().usage,
        Usage {
            input_tokens: 25,
            output_tokens: 7
        }
    );
    assert_eq!(kernel.state().status, RunStatus::Completed);
}

#[test]
fn rejects_invalid_commands_and_checkpoints() {
    let mut invalid = command();
    invalid.agent.max_turns = 0;
    assert!(matches!(
        Kernel::start(invalid),
        Err(KernelError::InvalidState(_))
    ));

    let (kernel, _) = Kernel::start(command()).unwrap();
    let mut state = kernel.state().clone();
    state.pending_effect = None;
    assert!(matches!(
        Kernel::restore(state),
        Err(KernelError::InvalidState(_))
    ));
}

#[test]
fn rejects_inconsistent_finish_reasons() {
    let (mut kernel, _) = Kernel::start(command()).unwrap();
    let step = kernel
        .resume(EffectResult::Model(ModelResponse {
            content: "truncated".into(),
            tool_calls: Vec::new(),
            finish_reason: FinishReason::Length,
            usage: Usage::default(),
            provider_state: BTreeMap::new(),
        }))
        .unwrap();
    assert!(matches!(
        step.events.last().map(|event| &event.kind),
        Some(RunEventKind::RunFailed {
            error: ProtocolError {
                code: ErrorCode::MalformedResponse,
                ..
            }
        })
    ));
}

#[test]
fn rejects_undeclared_tool_calls() {
    let (mut kernel, _) = Kernel::start(command()).unwrap();
    let step = kernel
        .resume(EffectResult::Model(ModelResponse {
            content: String::new(),
            tool_calls: vec![ToolCall {
                id: "c1".into(),
                name: "undeclared".into(),
                arguments: json!({}),
            }],
            finish_reason: FinishReason::ToolCalls,
            usage: Usage::default(),
            provider_state: BTreeMap::new(),
        }))
        .unwrap();
    assert!(matches!(
        step.events.last().map(|event| &event.kind),
        Some(RunEventKind::RunFailed { .. })
    ));
}

#[test]
fn rejects_invalid_schemas_and_tool_arguments() {
    let mut invalid_schema = command();
    invalid_schema.agent.tools[0].input_schema = json!({"type": "not-a-json-type"});

    assert!(matches!(
        Kernel::start(invalid_schema),
        Err(KernelError::InvalidState(_))
    ));

    let (mut kernel, _) = Kernel::start(command()).unwrap();
    let step = kernel
        .resume(EffectResult::Model(ModelResponse {
            content: String::new(),
            tool_calls: vec![ToolCall {
                id: "c1".into(),
                name: "weather".into(),
                arguments: json!({"city": 31}),
            }],
            finish_reason: FinishReason::ToolCalls,
            usage: Usage::default(),
            provider_state: BTreeMap::new(),
        }))
        .unwrap();

    assert!(matches!(
        step.events.last().map(|event| &event.kind),
        Some(RunEventKind::RunFailed {
            error: ProtocolError {
                code: ErrorCode::MalformedResponse,
                ..
            }
        })
    ));
}

#[test]
fn validates_structured_terminal_output() {
    let mut structured = command();
    structured.agent.output_schema = Some(json!({
        "type": "object",
        "properties": {"summary": {"type": "string"}},
        "required": ["summary"],
        "additionalProperties": false
    }));

    let (mut kernel, _) = Kernel::start(structured).unwrap();
    let step = kernel
        .resume(EffectResult::Model(ModelResponse {
            content: json!({"summary": 31}).to_string(),
            tool_calls: Vec::new(),
            finish_reason: FinishReason::Stop,
            usage: Usage::default(),
            provider_state: BTreeMap::new(),
        }))
        .unwrap();

    assert!(step.result.is_none());
    assert!(matches!(
        step.events.last().map(|event| &event.kind),
        Some(RunEventKind::RunFailed {
            error: ProtocolError {
                code: ErrorCode::MalformedResponse,
                ..
            }
        })
    ));
}

#[test]
fn terminal_runs_cannot_be_failed_again() {
    let (mut kernel, _) = Kernel::start(command()).unwrap();
    let _ = kernel.cancel();
    assert_eq!(
        kernel.fail(ProtocolError {
            code: ErrorCode::Provider,
            message: "late error".into(),
            retryable: false,
            retry_after_ms: None,
        }),
        Err(KernelError::TerminalRun)
    );
}
