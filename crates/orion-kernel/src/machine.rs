//! State-machine advancement and invariant enforcement.

use std::collections::{BTreeMap, VecDeque};

use orion_protocol::{
    Effect, EffectResult, Message, ProtocolError, Role, RunEvent, RunEventKind, RunResult,
    StartRun, Usage,
};
use serde::{Deserialize, Serialize};
use thiserror::Error;

use crate::state::{RunState, RunStatus};

use self::validation::{validate_command, validate_state};

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

        let mut kernel = Self {
            state: initial_state(command),
        };

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

        // A terminal transition clears every source of future work.
        self.state.status = RunStatus::Cancelled;
        self.state.pending_effect = None;
        self.state.queued_tool_calls.clear();

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

        Ok(self.fail_running(error))
    }

    fn fail_running(&mut self, error: ProtocolError) -> Step {
        // Failure owns the pending effect and prevents later host resumption.
        self.state.status = RunStatus::Failed;
        self.state.pending_effect = None;
        self.state.queued_tool_calls.clear();

        let event = self.event(RunEventKind::RunFailed { error });

        Step {
            events: vec![event],
            effect: None,
            result: None,
        }
    }

    fn event(&mut self, kind: RunEventKind) -> RunEvent {
        let event = RunEvent {
            run_id: self.state.run_id.clone(),
            sequence: self.state.next_sequence,
            kind,
        };

        // New runs start at zero and restored runs reserve enough sequence space
        // for every remaining bounded turn and tool call.
        self.state.next_sequence = self
            .state
            .next_sequence
            .checked_add(1)
            .expect("event sequence capacity is validated before execution");

        event
    }
}

fn initial_state(command: StartRun) -> RunState {
    let StartRun {
        run_id,
        agent,
        input,
    } = command;

    let messages = vec![
        Message {
            role: Role::System,
            content: agent.instructions.clone(),
            tool_call_id: None,
            tool_calls: Vec::new(),
        },
        Message {
            role: Role::User,
            content: input,
            tool_call_id: None,
            tool_calls: Vec::new(),
        },
    ];

    RunState {
        run_id,
        agent,
        messages,
        status: RunStatus::Running,
        turns: 0,
        usage: Usage::default(),
        next_sequence: 0,
        pending_effect: None,
        queued_tool_calls: VecDeque::default(),
        provider_state: BTreeMap::default(),
        output: None,
    }
}

mod transitions;
mod validation;

#[cfg(test)]
#[path = "machine/tests.rs"]
mod tests;
