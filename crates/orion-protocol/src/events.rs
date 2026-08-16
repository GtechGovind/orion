//! Immutable lifecycle event envelopes.

use serde::{Deserialize, Serialize};

use crate::{
    errors::ProtocolError,
    identifiers::{ActionId, RunId},
};

/// Ordered, immutable lifecycle event payload.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
#[allow(missing_docs)]
pub enum RunEventKind {
    RunStarted {
        agent_id: String,
    },
    ModelRequested {
        turn: u32,
        provider: String,
        model: String,
    },
    ModelCompleted {
        turn: u32,
        output: String,
        tool_call_count: usize,
    },
    ToolRequested {
        action_id: ActionId,
        call_id: String,
        name: String,
    },
    ToolCompleted {
        action_id: ActionId,
        call_id: String,
        name: String,
    },
    RunCompleted {
        output: String,
    },
    RunFailed {
        error: ProtocolError,
    },
    RunCancelled,
}

/// Event envelope carrying stable run-local ordering.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct RunEvent {
    /// Run identity.
    pub run_id: RunId,
    /// Zero-based monotonic sequence.
    pub sequence: u64,
    /// Event payload.
    pub kind: RunEventKind,
}
