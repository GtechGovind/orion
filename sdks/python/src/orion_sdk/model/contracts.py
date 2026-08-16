"""Protocols implemented by model-provider integrations."""

from typing import Protocol

from .types import ModelProfile, ModelRef, ModelRequest, ModelResponse


class ModelAdapter(Protocol):
    """Connects one provider namespace to normalized Orion contracts."""

    @property
    def provider(self) -> str:
        """Return the stable provider key owned by this adapter."""
        ...

    def profile(self, model: ModelRef) -> ModelProfile:
        """Return capabilities for ``model`` without performing I/O."""
        ...

    async def complete(self, request: ModelRequest) -> ModelResponse:
        """Execute one provider-neutral request and normalize its response."""
        ...

    async def close(self) -> None:
        """Release resources owned by the adapter."""
        ...
