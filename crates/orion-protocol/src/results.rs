//! Host effect results and terminal run outcomes.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::{effects::ToolCall, identifiers::RunId};

/// Why a model stopped producing output.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
#[allow(missing_docs)]
pub enum FinishReason {
    Stop,
    ToolCalls,
    Length,
    ContentFilter,
    Other,
}

/// Normalized token accounting.
#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
#[allow(missing_docs)]
pub struct Usage {
    pub input_tokens: u64,
    pub output_tokens: u64,
}

/// Normalized complete model response.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ModelResponse {
    /// Assistant text, which may be empty for tool calls.
    pub content: String,
    /// Proposed calls in provider order.
    #[serde(default)]
    pub tool_calls: Vec<ToolCall>,
    /// Normalized stop reason.
    pub finish_reason: FinishReason,
    /// Token usage when reported.
    #[serde(default)]
    pub usage: Usage,
    /// Opaque continuation state keyed by provider.
    #[serde(default)]
    pub provider_state: BTreeMap<String, Value>,
}

/// Successful tool output. The host is responsible for argument/output validation.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[allow(missing_docs)]
pub struct ToolResponse {
    pub content: Value,
}

/// Result returned by a host for the currently pending effect.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", content = "value", rename_all = "snake_case")]
#[allow(missing_docs)]
pub enum EffectResult {
    Model(ModelResponse),
    Tool(ToolResponse),
}

/// Terminal successful run value.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct RunResult {
    /// Run identity.
    pub run_id: RunId,
    /// Final assistant output.
    pub output: String,
    /// Total normalized usage.
    pub usage: Usage,
    /// Number of completed model turns.
    pub turns: u32,
}
