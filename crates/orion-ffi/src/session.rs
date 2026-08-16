//! Rust-owned run sessions shared by native language bindings.

use orion_kernel::{Kernel, KernelError, RunState, Step};
use orion_protocol::{EffectResult, ProtocolError, StartRun};

/// An in-process run whose state never crosses the native boundary.
#[derive(Debug)]
pub struct RunSession {
    kernel: Kernel,
    current: Option<Step>,
}

impl RunSession {
    /// Starts a run and retains its initial transition until [`Self::take_step`].
    ///
    /// # Errors
    ///
    /// Returns a kernel validation error when the command is invalid.
    pub fn start(command: StartRun) -> Result<Self, KernelError> {
        let (kernel, step) = Kernel::start(command)?;

        Ok(Self {
            kernel,
            current: Some(step),
        })
    }

    /// Restores a validated durable state without serializing it again.
    ///
    /// # Errors
    ///
    /// Returns a kernel validation error when the state is inconsistent.
    pub fn restore(state: RunState) -> Result<Self, KernelError> {
        Ok(Self {
            kernel: Kernel::restore(state)?,
            current: None,
        })
    }

    /// Returns the most recently produced transition once.
    pub fn take_step(&mut self) -> Option<Step> {
        self.current.take()
    }

    /// Advances the run with the pending host effect result.
    ///
    /// # Errors
    ///
    /// Returns a kernel error for a terminal run, missing effect, or mismatch.
    pub fn resume(&mut self, result: EffectResult) -> Result<Step, KernelError> {
        let step = self.kernel.resume(result)?;

        self.current = Some(step.clone());

        Ok(step)
    }

    /// Cancels the active run.
    pub fn cancel(&mut self) -> Step {
        let step = self.kernel.cancel();

        self.current = Some(step.clone());

        step
    }

    /// Fails the active run with a normalized host error.
    ///
    /// # Errors
    ///
    /// Returns [`KernelError::TerminalRun`] when the session already ended.
    pub fn fail(&mut self, error: ProtocolError) -> Result<Step, KernelError> {
        let step = self.kernel.fail(error)?;

        self.current = Some(step.clone());

        Ok(step)
    }

    /// Borrows the durable state for explicit checkpoint integrations.
    #[must_use]
    pub const fn state(&self) -> &RunState {
        self.kernel.state()
    }
}
