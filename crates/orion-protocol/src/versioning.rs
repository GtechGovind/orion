//! Protocol schema versions and compatibility metadata.

use serde::{Deserialize, Serialize};

/// Current major protocol version.
pub const PROTOCOL_VERSION: ProtocolVersion = ProtocolVersion { major: 1, minor: 0 };

/// A wire-compatible protocol version.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct ProtocolVersion {
    /// Breaking-change boundary.
    pub major: u16,
    /// Backwards-compatible feature level.
    pub minor: u16,
}

impl ProtocolVersion {
    /// Returns whether two peers can exchange the core v1 contract.
    #[must_use]
    pub const fn is_compatible_with(self, other: Self) -> bool {
        self.major == other.major && self.minor <= other.minor
    }
}
