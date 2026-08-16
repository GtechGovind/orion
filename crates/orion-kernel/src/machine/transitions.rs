use orion_protocol::{
    ActionId, Effect, ErrorCode, Message, ModelRequest, ModelResponse, ProtocolError, Role,
    RunEvent, RunEventKind, RunResult, ToolCall, ToolResponse,
};

use crate::state::RunStatus;

use super::validation::{validate_structured_output, validate_tool_calls};
use super::{Kernel, Step};

impl Kernel {
    pub(super) fn accept_model(&mut self, mut response: ModelResponse) -> Step {
        // Validate and account for the response before choosing the next transition.
        if let Err(error) = validate_tool_calls(&self.state.agent, &response) {
            return self.fail_running(error);
        }

        if let Err(error) = validate_structured_output(&self.state.agent, &response) {
            return self.fail_running(error);
        }

        let completed = match self.record_model_response(&mut response) {
            Ok(event) => event,
            Err(error) => return self.fail_running(error),
        };

        // Terminal responses and exhausted budgets never create another effect.
        if response.tool_calls.is_empty() {
            return self.finish_without_tools(response, completed);
        }

        if self.state.turns >= self.state.agent.max_turns {
            return self.turn_limit_failure(completed);
        }

        // Preserve provider order and expose exactly one outstanding tool effect.
        self.state.queued_tool_calls = response.tool_calls.into();

        let Some((effect, requested)) = self.next_tool_effect() else {
            return self.internal_failure("validated tool calls unexpectedly disappeared");
        };

        self.state.pending_effect = Some(effect.clone());

        Step {
            events: vec![completed, requested],
            effect: Some(effect),
            result: None,
        }
    }

    pub(super) fn accept_tool(
        &mut self,
        action_id: ActionId,
        call: ToolCall,
        response: ToolResponse,
    ) -> Step {
        // Commit the completed call to the transcript before selecting more work.
        let ToolResponse { content } = response;

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

        // Continue the queued calls in provider order, then return to the model.
        let (effect, requested) = self
            .next_tool_effect()
            .unwrap_or_else(|| self.model_effect());

        self.state.pending_effect = Some(effect.clone());

        Step {
            events: vec![completed, requested],
            effect: Some(effect),
            result: None,
        }
    }

    pub(super) fn model_effect(&mut self) -> (Effect, RunEvent) {
        // Snapshot the current provider-neutral state into one immutable request.
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

    fn record_model_response(
        &mut self,
        response: &mut ModelResponse,
    ) -> Result<RunEvent, ProtocolError> {
        // Calculate every fallible counter update before mutating run state.
        let Some(turns) = self.state.turns.checked_add(1) else {
            return Err(internal_error("model turn counter overflowed"));
        };

        let Some(input_tokens) = self
            .state
            .usage
            .input_tokens
            .checked_add(response.usage.input_tokens)
        else {
            return Err(internal_error("input token counter overflowed"));
        };

        let Some(output_tokens) = self
            .state
            .usage
            .output_tokens
            .checked_add(response.usage.output_tokens)
        else {
            return Err(internal_error("output token counter overflowed"));
        };

        // Commit accounting, provider continuation state, and transcript atomically.
        self.state.turns = turns;
        self.state.usage.input_tokens = input_tokens;
        self.state.usage.output_tokens = output_tokens;
        self.state.provider_state = std::mem::take(&mut response.provider_state);
        self.state.messages.push(Message {
            role: Role::Assistant,
            content: response.content.clone(),
            tool_call_id: None,
            tool_calls: response.tool_calls.clone(),
        });

        Ok(self.event(RunEventKind::ModelCompleted {
            turn: self.state.turns,
            output: response.content.clone(),
            tool_call_count: response.tool_calls.len(),
        }))
    }

    fn finish_without_tools(&mut self, response: ModelResponse, completed: RunEvent) -> Step {
        // A response without tools is successful only when the provider reports stop.
        if response.finish_reason != orion_protocol::FinishReason::Stop {
            let (code, message) = finish_reason_failure(&response.finish_reason);
            let mut failure = self.protocol_failure(code, message);

            failure.events.insert(0, completed);

            return failure;
        }

        // Commit terminal state before constructing the matching event and result.
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

        Step {
            events: vec![completed, event],
            effect: None,
            result: Some(result),
        }
    }

    fn turn_limit_failure(&mut self, completed: RunEvent) -> Step {
        let error = ProtocolError {
            code: ErrorCode::TurnLimitExceeded,
            message: "agent reached its configured model turn limit".to_owned(),
            retryable: false,
            retry_after_ms: None,
        };

        let mut step = self.fail_running(error);

        step.events.insert(0, completed);

        step
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

    fn protocol_failure(&mut self, code: ErrorCode, message: &str) -> Step {
        self.fail_running(ProtocolError {
            code,
            message: message.to_owned(),
            retryable: false,
            retry_after_ms: None,
        })
    }

    fn internal_failure(&mut self, message: &str) -> Step {
        self.protocol_failure(ErrorCode::InvalidState, message)
    }
}

fn finish_reason_failure(reason: &orion_protocol::FinishReason) -> (ErrorCode, &'static str) {
    match reason {
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
    }
}

fn internal_error(message: &str) -> ProtocolError {
    ProtocolError {
        code: ErrorCode::InvalidState,
        message: message.to_owned(),
        retryable: false,
        retry_after_ms: None,
    }
}
