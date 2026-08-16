//! Effects requested by the kernel and executed by a host SDK.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::{
    commands::{ModelRef, ModelSettings, ToolSpec},
    identifiers::ActionId,
};

/// Provider-neutral conversation role.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
#[allow(missing_docs)]
pub enum Role {
    System,
    User,
    Assistant,
    Tool,
}

/// Provider-neutral message. Rich content parts are a post-pilot extension.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Message {
    /// Message role.
    pub role: Role,
    /// Textual content.
    pub content: String,
    /// Tool call identifier for tool-result messages.
    pub tool_call_id: Option<String>,
    /// Calls proposed by an assistant message.
    #[serde(default)]
    pub tool_calls: Vec<ToolCall>,
}

/// A tool invocation proposed by a model.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ToolCall {
    /// Provider/model supplied call identifier.
    pub id: String,
    /// Registered tool name.
    pub name: String,
    /// Valid JSON arguments.
    pub arguments: Value,
}

/// Model capability support level.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
#[allow(missing_docs)]
pub enum CapabilitySupport {
    Native,
    Emulated,
    Unsupported,
    Unknown,
}

/// Adapter-reported model behavior used for preflight validation.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ModelProfile {
    /// Token streaming support.
    pub streaming: CapabilitySupport,
    /// Model-originated tool calls.
    pub tool_calling: CapabilitySupport,
    /// Schema-constrained output.
    pub structured_output: CapabilitySupport,
    /// Parallel tool-call generation.
    pub parallel_tool_calls: CapabilitySupport,
    /// Optional context limit.
    pub max_context_tokens: Option<u64>,
}

/// Normalized model call requested by the kernel.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ModelRequest {
    /// Selected model.
    pub model: ModelRef,
    /// Canonical transcript.
    pub messages: Vec<Message>,
    /// Model-visible tools.
    pub tools: Vec<ToolSpec>,
    /// Optional output schema.
    pub output_schema: Option<Value>,
    /// Portable and provider-specific settings.
    pub settings: ModelSettings,
    /// Opaque adapter-owned continuation data.
    #[serde(default)]
    pub provider_state: BTreeMap<String, Value>,
}

/// A host operation emitted by the deterministic core.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
#[allow(missing_docs)]
pub enum Effect {
    /// Ask a registered model adapter for the next response.
    CallModel { request: ModelRequest },
    /// Execute one application tool after policy validation.
    ExecuteTool { action_id: ActionId, call: ToolCall },
}
