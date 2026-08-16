//! Native binding boundary for Orion host SDKs.
//!
//! Exported symbols and a `cdylib` target will be added only after the protocol
//! and memory-ownership model are accepted through an ADR.

pub mod handles;
pub mod memory;
pub mod transport;
