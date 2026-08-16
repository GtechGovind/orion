//! Node-API module for direct, in-process Orion kernel transitions.

use napi_derive::napi;
use orion_ffi::session::RunSession;
use orion_protocol::{EffectResult, ProtocolError, StartRun};
use serde_json::Value;

fn map_error(error: impl std::fmt::Display) -> napi::Error {
    napi::Error::from_reason(error.to_string())
}

/// Rust-owned kernel session exposed as an opaque JavaScript class.
#[napi]
pub struct NativeRun {
    session: RunSession,
}

#[napi]
impl NativeRun {
    /// Starts a run from a plain JavaScript command object.
    ///
    /// # Errors
    ///
    /// Returns an error when the command violates the protocol contract.
    #[napi(constructor)]
    pub fn new(command: Value) -> napi::Result<Self> {
        let command: StartRun = serde_json::from_value(command).map_err(map_error)?;
        Ok(Self {
            session: RunSession::start(command).map_err(map_error)?,
        })
    }

    /// Takes the initial step once.
    ///
    /// # Errors
    ///
    /// Returns an error when the initial step was already consumed.
    #[napi]
    pub fn take_step(&mut self) -> napi::Result<Value> {
        let step = self
            .session
            .take_step()
            .ok_or_else(|| napi::Error::from_reason("no unread kernel step"))?;
        serde_json::to_value(step).map_err(map_error)
    }

    /// Resumes the pending effect with a plain JavaScript result object.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid results or kernel invariant violations.
    #[napi]
    pub fn resume(&mut self, result: Value) -> napi::Result<Value> {
        let result: EffectResult = serde_json::from_value(result).map_err(map_error)?;
        let step = self.session.resume(result).map_err(map_error)?;
        let _ = self.session.take_step();
        serde_json::to_value(step).map_err(map_error)
    }

    /// Cancels the run.
    ///
    /// # Errors
    ///
    /// Returns an error when the step cannot cross the Node-API boundary.
    #[napi]
    pub fn cancel(&mut self) -> napi::Result<Value> {
        let step = self.session.cancel();
        let _ = self.session.take_step();
        serde_json::to_value(step).map_err(map_error)
    }

    /// Fails the run with a normalized error object.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid error objects or terminal sessions.
    #[napi]
    pub fn fail(&mut self, error: Value) -> napi::Result<Value> {
        let error: ProtocolError = serde_json::from_value(error).map_err(map_error)?;
        let step = self.session.fail(error).map_err(map_error)?;
        let _ = self.session.take_step();
        serde_json::to_value(step).map_err(map_error)
    }
}
