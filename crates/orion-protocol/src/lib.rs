//! Language-neutral protocol boundary for Orion.
//!
//! Version 1 defines only owned, serializable values so every host language can
//! implement the same behavior without sharing runtime objects across FFI.

pub mod commands;
pub mod effects;
pub mod errors;
pub mod events;
pub mod identifiers;
pub mod results;
pub mod versioning;

pub use commands::{AgentSpec, ModelRef, ModelSettings, StartRun, ToolSpec};
pub use effects::{CapabilitySupport, Effect, Message, ModelProfile, ModelRequest, Role, ToolCall};
pub use errors::{ErrorCode, ProtocolError};
pub use events::{RunEvent, RunEventKind};
pub use identifiers::{ActionId, RunId, TurnId};
pub use results::{EffectResult, FinishReason, ModelResponse, RunResult, ToolResponse, Usage};
