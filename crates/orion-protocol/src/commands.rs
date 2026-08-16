//! Commands and declarative specifications submitted by a host SDK.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::identifiers::RunId;

/// A serializable provider/model selection. Credentials never belong here.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct ModelRef {
    /// Adapter/provider key, for example `openai`.
    pub provider: String,
    /// Provider model identifier.
    pub model: String,
}

impl ModelRef {
    /// Parses the convenience `provider:model` notation.
    ///
    /// # Errors
    ///
    /// Returns a message when the separator or either component is missing.
    pub fn parse(value: &str) -> Result<Self, String> {
        let Some((provider, model)) = value.split_once(':') else {
            return Err("model reference must use provider:model notation".to_owned());
        };
        if provider.is_empty() || model.is_empty() {
            return Err("provider and model must be non-empty".to_owned());
        }
        Ok(Self {
            provider: provider.to_owned(),
            model: model.to_owned(),
        })
    }
}

/// JSON-schema tool declaration visible to a model.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ToolSpec {
    /// Stable tool name.
    pub name: String,
    /// Human-readable model guidance.
    pub description: String,
    /// JSON Schema for tool arguments.
    pub input_schema: Value,
}

/// Portable model settings plus explicitly namespaced provider extensions.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct ModelSettings {
    /// Sampling temperature when supported.
    pub temperature: Option<f64>,
    /// Maximum generated tokens when supported.
    pub max_output_tokens: Option<u32>,
    /// Provider-keyed opaque configuration.
    #[serde(default)]
    pub provider_options: BTreeMap<String, Value>,
}

/// Immutable, serializable agent definition.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct AgentSpec {
    /// Stable application identifier.
    pub id: String,
    /// Display name.
    pub name: String,
    /// System-level behavior instructions.
    pub instructions: String,
    /// Model selection, resolved by the host.
    pub model: ModelRef,
    /// Tools available to this agent.
    #[serde(default)]
    pub tools: Vec<ToolSpec>,
    /// Optional output JSON Schema.
    pub output_schema: Option<Value>,
    /// Default model request settings.
    #[serde(default)]
    pub model_settings: ModelSettings,
    /// Maximum model turns in one run.
    #[serde(default = "default_max_turns")]
    pub max_turns: u32,
}

const fn default_max_turns() -> u32 {
    8
}

/// Starts a new run.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct StartRun {
    /// Host-generated stable run identifier.
    pub run_id: RunId,
    /// Agent definition.
    pub agent: AgentSpec,
    /// Initial user input.
    pub input: String,
}
