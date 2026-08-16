//! `CPython` module for direct, in-process Orion kernel transitions.

use orion_ffi::session::RunSession;
use orion_protocol::{EffectResult, ProtocolError, StartRun};
use pyo3::{exceptions::PyRuntimeError, prelude::*};
use pythonize::{depythonize, pythonize};

fn map_error(error: impl std::fmt::Display) -> PyErr {
    PyRuntimeError::new_err(error.to_string())
}

/// Rust-owned kernel session exposed as an opaque Python object.
#[pyclass(unsendable)]
struct NativeRun {
    session: RunSession,
}

#[pymethods]
impl NativeRun {
    #[new]
    fn new(command: &Bound<'_, PyAny>) -> PyResult<Self> {
        let command: StartRun = depythonize(command).map_err(map_error)?;
        Ok(Self {
            session: RunSession::start(command).map_err(map_error)?,
        })
    }

    fn take_step(&mut self, py: Python<'_>) -> PyResult<Py<PyAny>> {
        let step = self
            .session
            .take_step()
            .ok_or_else(|| PyRuntimeError::new_err("no unread kernel step"))?;
        pythonize(py, &step).map(Bound::unbind).map_err(map_error)
    }

    fn resume(&mut self, py: Python<'_>, result: &Bound<'_, PyAny>) -> PyResult<Py<PyAny>> {
        let result: EffectResult = depythonize(result).map_err(map_error)?;
        let step = self.session.resume(result).map_err(map_error)?;
        let _ = self.session.take_step();
        pythonize(py, &step).map(Bound::unbind).map_err(map_error)
    }

    fn cancel(&mut self, py: Python<'_>) -> PyResult<Py<PyAny>> {
        let step = self.session.cancel();
        let _ = self.session.take_step();
        pythonize(py, &step).map(Bound::unbind).map_err(map_error)
    }

    fn fail(&mut self, py: Python<'_>, error: &Bound<'_, PyAny>) -> PyResult<Py<PyAny>> {
        let error: ProtocolError = depythonize(error).map_err(map_error)?;
        let step = self.session.fail(error).map_err(map_error)?;
        let _ = self.session.take_step();
        pythonize(py, &step).map(Bound::unbind).map_err(map_error)
    }
}

#[pymodule]
fn _native(module: &Bound<'_, PyModule>) -> PyResult<()> {
    module.add_class::<NativeRun>()?;
    Ok(())
}
