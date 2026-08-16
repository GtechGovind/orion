//! Serializable durable run state.

use std::collections::{BTreeMap, VecDeque};

use orion_protocol::{AgentSpec, Effect, Message, RunId, ToolCall, Usage};
use serde::{Deserialize, Serialize};
use serde_json::Value;

/// Observable lifecycle of a run.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
#[allow(missing_docs)]
pub enum RunStatus {
    Running,
    Completed,
    Failed,
    Cancelled,
}

/// Serializable state owned entirely by the Rust kernel.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct RunState {
    /// Run identity.
    pub run_id: RunId,
    /// Immutable agent definition used by this run.
    pub agent: AgentSpec,
    /// Canonical provider-neutral transcript.
    pub messages: Vec<Message>,
    /// Current lifecycle state.
    pub status: RunStatus,
    /// Completed model turns.
    pub turns: u32,
    /// Cumulative usage.
    pub usage: Usage,
    /// Next event sequence number.
    pub next_sequence: u64,
    /// Currently outstanding host effect.
    pub pending_effect: Option<Effect>,
    /// Tool calls waiting behind the outstanding call.
    pub queued_tool_calls: VecDeque<ToolCall>,
    /// Opaque state returned by the selected provider adapter.
    pub provider_state: BTreeMap<String, Value>,
    /// Final output when completed.
    pub output: Option<String>,
}
