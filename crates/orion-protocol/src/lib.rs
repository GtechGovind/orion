//! Language-neutral protocol boundary for Orion.
//!
//! This crate is intentionally empty during the architecture milestone. The
//! modules reserve ownership boundaries; they do not define stable APIs.

pub mod commands;
pub mod effects;
pub mod errors;
pub mod events;
pub mod identifiers;
pub mod results;
pub mod versioning;
