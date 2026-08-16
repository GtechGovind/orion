//! Deterministic Orion execution-kernel boundary.
//!
//! The kernel never performs I/O. It emits one effect at a time and advances
//! only when a host SDK returns the matching result.

pub mod machine;
pub mod state;

pub use machine::{Kernel, KernelError, Step};
pub use state::{RunState, RunStatus};
