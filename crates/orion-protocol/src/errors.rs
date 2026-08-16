//! Versioned protocol error categories.

use serde::{Deserialize, Serialize};

/// Stable cross-language error categories.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
#[allow(missing_docs)]
pub enum ErrorCode {
    InvalidCommand,
    InvalidState,
    Configuration,
    Authentication,
    RateLimited,
    Timeout,
    Network,
    UnsupportedCapability,
    ContentSafety,
    MalformedResponse,
    Provider,
    Tool,
    Cancelled,
    TurnLimitExceeded,
}

/// Normalized error safe to cross the language boundary.
#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct ProtocolError {
    /// Stable machine-readable code.
    pub code: ErrorCode,
    /// Human-readable diagnostic without secrets.
    pub message: String,
    /// Whether policy may consider a retry.
    pub retryable: bool,
    /// Provider suggested delay.
    pub retry_after_ms: Option<u64>,
}
