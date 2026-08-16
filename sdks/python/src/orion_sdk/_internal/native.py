"""Private access to the in-process PyO3 session type."""

# pyright: reportMissingModuleSource=false

from .._native import NativeRun

__all__ = ["NativeRun"]
