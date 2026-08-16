//! State-machine advancement and invariant enforcement.

use std::collections::{BTreeMap, BTreeSet, VecDeque};

use orion_protocol::{
    ActionId, Effect, EffectResult, ErrorCode, Message, ModelRequest, ProtocolError, Role,
    RunEvent, RunEventKind, RunResult, StartRun, Usage,
};
use serde::{Deserialize, Serialize};
use thiserror::Error;

use crate::state::{RunState, RunStatus};

/// Kernel advancement result. At most one host effect is outstanding.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Step {
    /// Ordered events produced by this transition.
    pub events: Vec<RunEvent>,
    /// Next operation for the host, if the run is not terminal.
    pub effect: Option<Effect>,
    /// Terminal value on successful completion.
    pub result: Option<RunResult>,
}

/// Invalid host/kernel interaction.
#[derive(Debug, Error, Eq, PartialEq)]
pub enum KernelError {
    /// A new command or restored checkpoint violates kernel invariants.
    #[error("invalid kernel state: {0}")]
    InvalidState(String),
    /// A result did not match the outstanding effect.
    #[error("effect result does not match the pending effect")]
    EffectMismatch,
    /// The host attempted to advance a terminal run.
    #[error("run is already terminal")]
    TerminalRun,
    /// The host attempted to advance without an effect.
    #[error("no host effect is pending")]
    NoPendingEffect,
}

/// Deterministic, host-driven execution state machine.
#[derive(Clone, Debug, PartialEq)]
pub struct Kernel {
    state: RunState,
}

impl Kernel {
    /// Creates a run and emits its first model effect.
    ///
    /// # Errors
    ///
    /// Returns [`KernelError::InvalidState`] when the command violates a
    /// required kernel invariant.
    pub fn start(command: StartRun) -> Result<(Self, Step), KernelError> {
        validate_command(&command)?;
        let messages = vec![
            Message {
                role: Role::System,
                content: command.agent.instructions.clone(),
                tool_call_id: None,
                tool_calls: Vec::new(),
            },
            Message {
                role: Role::User,
                content: command.input,
                tool_call_id: None,
                tool_calls: Vec::new(),
            },
        ];
        let state = RunState {
            run_id: command.run_id,
            agent: command.agent,
            messages,
            status: RunStatus::Running,
            turns: 0,
            usage: Usage::default(),
            next_sequence: 0,
            pending_effect: None,
            queued_tool_calls: VecDeque::default(),
            provider_state: BTreeMap::default(),
            output: None,
        };
        let mut kernel = Self { state };
        let started = kernel.event(RunEventKind::RunStarted {
            agent_id: kernel.state.agent.id.clone(),
        });
        let (effect, requested) = kernel.model_effect();
        kernel.state.pending_effect = Some(effect.clone());
        Ok((
            kernel,
            Step {
                events: vec![started, requested],
                effect: Some(effect),
                result: None,
            },
        ))
    }

    /// Returns a snapshot suitable for checkpoint serialization.
    #[must_use]
    pub const fn state(&self) -> &RunState {
        &self.state
    }

    /// Restores a kernel after validating checkpoint invariants.
    ///
    /// # Errors
    ///
    /// Returns [`KernelError::InvalidState`] when a checkpoint is inconsistent.
    pub fn restore(state: RunState) -> Result<Self, KernelError> {
        validate_state(&state)?;
        Ok(Self { state })
    }

    /// Applies the result for the outstanding host effect.
    ///
    /// # Errors
    ///
    /// Returns an error for terminal runs, missing effects, or a mismatched
    /// model/tool result. The pending effect is retained after a mismatch.
    pub fn resume(&mut self, result: EffectResult) -> Result<Step, KernelError> {
        if self.state.status != RunStatus::Running {
            return Err(KernelError::TerminalRun);
        }
        let pending = self
            .state
            .pending_effect
            .take()
            .ok_or(KernelError::NoPendingEffect)?;
        match (pending, result) {
            (Effect::CallModel { .. }, EffectResult::Model(response)) => {
                Ok(self.accept_model(response))
            }
            (Effect::ExecuteTool { action_id, call }, EffectResult::Tool(response)) => {
                Ok(self.accept_tool(action_id, call, response))
            }
            (effect, _) => {
                self.state.pending_effect = Some(effect);
                Err(KernelError::EffectMismatch)
            }
        }
    }

    /// Cancels a running operation without retrying partial model output.
    #[must_use]
    pub fn cancel(&mut self) -> Step {
        if self.state.status != RunStatus::Running {
            return Step {
                events: Vec::new(),
                effect: None,
                result: None,
            };
        }
        self.state.status = RunStatus::Cancelled;
        self.state.pending_effect = None;
        let event = self.event(RunEventKind::RunCancelled);
        Step {
            events: vec![event],
            effect: None,
            result: None,
        }
    }

    /// Fails the run with a normalized host error.
    ///
    /// # Errors
    ///
    /// Returns [`KernelError::TerminalRun`] when the run is already terminal.
    pub fn fail(&mut self, error: ProtocolError) -> Result<Step, KernelError> {
        if self.state.status != RunStatus::Running {
            return Err(KernelError::TerminalRun);
        }
        self.state.status = RunStatus::Failed;
        self.state.pending_effect = None;
        let event = self.event(RunEventKind::RunFailed { error });
        Ok(Step {
            events: vec![event],
            effect: None,
            result: None,
        })
    }

    fn accept_model(&mut self, response: orion_protocol::ModelResponse) -> Step {
        if !response.tool_calls.is_empty() {
            if let Err(error) = self.validate_tool_calls(&response) {
                return self.fail(error).expect("the run is still active");
            }
        }
        let Some(turns) = self.state.turns.checked_add(1) else {
            return self.internal_failure("model turn counter overflowed");
        };
        let Some(input_tokens) = self
            .state
            .usage
            .input_tokens
            .checked_add(response.usage.input_tokens)
        else {
            return self.internal_failure("input token counter overflowed");
        };
        let Some(output_tokens) = self
            .state
            .usage
            .output_tokens
            .checked_add(response.usage.output_tokens)
        else {
            return self.internal_failure("output token counter overflowed");
        };
        self.state.turns = turns;
        self.state.usage.input_tokens = input_tokens;
        self.state.usage.output_tokens = output_tokens;
        self.state.provider_state = response.provider_state;
        self.state.messages.push(Message {
            role: Role::Assistant,
            content: response.content.clone(),
            tool_call_id: None,
            tool_calls: response.tool_calls.clone(),
        });
        let completed = self.event(RunEventKind::ModelCompleted {
            turn: self.state.turns,
            output: response.content.clone(),
            tool_call_count: response.tool_calls.len(),
        });

        if response.tool_calls.is_empty()
            && response.finish_reason == orion_protocol::FinishReason::Stop
        {
            self.state.status = RunStatus::Completed;
            self.state.output = Some(response.content.clone());
            let event = self.event(RunEventKind::RunCompleted {
                output: response.content.clone(),
            });
            let result = RunResult {
                run_id: self.state.run_id.clone(),
                output: response.content,
                usage: self.state.usage.clone(),
                turns: self.state.turns,
            };
            return Step {
                events: vec![completed, event],
                effect: None,
                result: Some(result),
            };
        }
        if response.tool_calls.is_empty() {
            let (code, message) = match response.finish_reason {
                orion_protocol::FinishReason::ContentFilter => (
                    ErrorCode::ContentSafety,
                    "model response was blocked by a content-safety filter",
                ),
                orion_protocol::FinishReason::Length => (
                    ErrorCode::MalformedResponse,
                    "model response ended at its output limit before completing",
                ),
                _ => (
                    ErrorCode::MalformedResponse,
                    "model returned no tool calls without a successful stop reason",
                ),
            };
            return self.protocol_failure(code, message);
        }
        if self.state.turns >= self.state.agent.max_turns {
            let error = ProtocolError {
                code: ErrorCode::TurnLimitExceeded,
                message: "agent reached its configured model turn limit".to_owned(),
                retryable: false,
                retry_after_ms: None,
            };
            let mut step = self.fail(error).expect("running state was checked");
            step.events.insert(0, completed);
            return step;
        }

        self.state.queued_tool_calls = response.tool_calls.into();
        let (effect, requested) = self.next_tool_effect().expect("non-empty calls");
        self.state.pending_effect = Some(effect.clone());
        Step {
            events: vec![completed, requested],
            effect: Some(effect),
            result: None,
        }
    }

    fn accept_tool(
        &mut self,
        action_id: ActionId,
        call: orion_protocol::ToolCall,
        response: orion_protocol::ToolResponse,
    ) -> Step {
        let content = response.content;
        self.state.messages.push(Message {
            role: Role::Tool,
            content: content.to_string(),
            tool_call_id: Some(call.id.clone()),
            tool_calls: Vec::new(),
        });
        let completed = self.event(RunEventKind::ToolCompleted {
            action_id,
            call_id: call.id,
            name: call.name,
        });
        if let Some((effect, requested)) = self.next_tool_effect() {
            self.state.pending_effect = Some(effect.clone());
            return Step {
                events: vec![completed, requested],
                effect: Some(effect),
                result: None,
            };
        }
        let (effect, requested) = self.model_effect();
        self.state.pending_effect = Some(effect.clone());
        Step {
            events: vec![completed, requested],
            effect: Some(effect),
            result: None,
        }
    }

    fn model_effect(&mut self) -> (Effect, RunEvent) {
        let request = ModelRequest {
            model: self.state.agent.model.clone(),
            messages: self.state.messages.clone(),
            tools: self.state.agent.tools.clone(),
            output_schema: self.state.agent.output_schema.clone(),
            settings: self.state.agent.model_settings.clone(),
            provider_state: self.state.provider_state.clone(),
        };
        let event = self.event(RunEventKind::ModelRequested {
            turn: self.state.turns + 1,
            provider: request.model.provider.clone(),
            model: request.model.model.clone(),
        });
        (Effect::CallModel { request }, event)
    }

    fn next_tool_effect(&mut self) -> Option<(Effect, RunEvent)> {
        let call = self.state.queued_tool_calls.pop_front()?;
        let action_id = ActionId(format!(
            "{}:action:{}",
            self.state.run_id.0, self.state.next_sequence
        ));
        let event = self.event(RunEventKind::ToolRequested {
            action_id: action_id.clone(),
            call_id: call.id.clone(),
            name: call.name.clone(),
        });
        Some((Effect::ExecuteTool { action_id, call }, event))
    }

    fn event(&mut self, kind: RunEventKind) -> RunEvent {
        let event = RunEvent {
            run_id: self.state.run_id.clone(),
            sequence: self.state.next_sequence,
            kind,
        };
        self.state.next_sequence = self
            .state
            .next_sequence
            .checked_add(1)
            .expect("validated event sequence capacity");
        event
    }

    fn validate_tool_calls(
        &self,
        response: &orion_protocol::ModelResponse,
    ) -> Result<(), ProtocolError> {
        let malformed = |message: &str| ProtocolError {
            code: ErrorCode::MalformedResponse,
            message: message.to_owned(),
            retryable: false,
            retry_after_ms: None,
        };
        if response.finish_reason != orion_protocol::FinishReason::ToolCalls {
            return Err(malformed(
                "model returned tool calls with an inconsistent finish reason",
            ));
        }
        if response.tool_calls.len() > 128 {
            return Err(malformed(
                "model returned more than 128 tool calls in one turn",
            ));
        }
        let declared_tools: BTreeSet<&str> = self
            .state
            .agent
            .tools
            .iter()
            .map(|tool| tool.name.as_str())
            .collect();
        let mut call_ids = BTreeSet::new();
        for call in &response.tool_calls {
            if call.id.is_empty() || !call_ids.insert(call.id.as_str()) {
                return Err(malformed(
                    "model returned an empty or duplicate tool-call id",
                ));
            }
            if !declared_tools.contains(call.name.as_str()) {
                return Err(malformed(
                    "model requested a tool that is not declared by the agent",
                ));
            }
            if !call.arguments.is_object() {
                return Err(malformed("tool-call arguments must be a JSON object"));
            }
        }
        Ok(())
    }

    fn protocol_failure(&mut self, code: ErrorCode, message: &str) -> Step {
        self.fail(ProtocolError {
            code,
            message: message.to_owned(),
            retryable: false,
            retry_after_ms: None,
        })
        .expect("model results are accepted only for running states")
    }

    fn internal_failure(&mut self, message: &str) -> Step {
        self.protocol_failure(ErrorCode::InvalidState, message)
    }
}

fn validate_command(command: &StartRun) -> Result<(), KernelError> {
    if command.run_id.0.trim().is_empty() {
        return Err(KernelError::InvalidState("run id must not be empty".into()));
    }
    if command.agent.id.trim().is_empty() {
        return Err(KernelError::InvalidState(
            "agent id must not be empty".into(),
        ));
    }
    if command.agent.model.provider.trim().is_empty() || command.agent.model.model.trim().is_empty()
    {
        return Err(KernelError::InvalidState(
            "model provider and model name must not be empty".into(),
        ));
    }
    if command.agent.max_turns == 0 {
        return Err(KernelError::InvalidState(
            "max_turns must be positive".into(),
        ));
    }
    if command.agent.max_turns > 1_024 {
        return Err(KernelError::InvalidState(
            "max_turns must not exceed 1024".into(),
        ));
    }
    let mut names = BTreeSet::new();
    for tool in &command.agent.tools {
        if tool.name.trim().is_empty() || !names.insert(tool.name.as_str()) {
            return Err(KernelError::InvalidState(
                "tool names must be non-empty and unique".into(),
            ));
        }
        if !tool.input_schema.is_object() {
            return Err(KernelError::InvalidState(
                "tool input schemas must be JSON objects".into(),
            ));
        }
    }
    if command
        .agent
        .output_schema
        .as_ref()
        .is_some_and(|schema| !schema.is_object())
    {
        return Err(KernelError::InvalidState(
            "output schema must be a JSON object".into(),
        ));
    }
    Ok(())
}

fn validate_state(state: &RunState) -> Result<(), KernelError> {
    validate_command(&StartRun {
        run_id: state.run_id.clone(),
        agent: state.agent.clone(),
        input: String::new(),
    })?;
    if state.turns > state.agent.max_turns {
        return Err(KernelError::InvalidState(
            "completed turns exceed the configured limit".into(),
        ));
    }
    if state.next_sequence > u64::MAX - 1_024 {
        return Err(KernelError::InvalidState(
            "event sequence has exhausted its safe range".into(),
        ));
    }
    match state.status {
        RunStatus::Running if state.pending_effect.is_none() => Err(KernelError::InvalidState(
            "a running state must contain one pending effect".into(),
        )),
        RunStatus::Completed if state.pending_effect.is_some() || state.output.is_none() => {
            Err(KernelError::InvalidState(
                "a completed state must have output and no pending effect".into(),
            ))
        }
        RunStatus::Failed | RunStatus::Cancelled if state.pending_effect.is_some() => Err(
            KernelError::InvalidState("a terminal state must not contain a pending effect".into()),
        ),
        _ => Ok(()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use orion_protocol::{
        AgentSpec, FinishReason, ModelRef, ModelResponse, ModelSettings, RunId, ToolCall,
        ToolResponse, ToolSpec, Usage,
    };
    use serde_json::json;
    use std::collections::BTreeMap;

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
                    input_schema: json!({"type":"object"}),
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
}
