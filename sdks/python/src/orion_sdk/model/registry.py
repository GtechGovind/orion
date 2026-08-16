"""Model-adapter registration and provider resolution."""

from collections.abc import Sequence

from .contracts import ModelAdapter, ModelRef


class ModelRegistry:
    """Resolves unique provider keys to application-owned model adapters."""

    def __init__(self, adapters: Sequence[ModelAdapter]) -> None:
        """Register exactly one adapter for each non-empty provider key."""

        providers = [adapter.provider for adapter in adapters]
        if any(not provider for provider in providers):
            raise ValueError("model adapter providers must be non-empty")
        if len(providers) != len(set(providers)):
            raise ValueError("model adapter providers must be unique")
        self._adapters = {adapter.provider: adapter for adapter in adapters}

    def resolve(self, model: ModelRef) -> ModelAdapter:
        """Resolve the adapter selected by `model`.

        Raises:
            ValueError: If no adapter owns the requested provider key.
        """

        try:
            return self._adapters[model.provider]
        except KeyError as error:
            raise ValueError(
                f"no model adapter registered for provider {model.provider!r}"
            ) from error

    async def close(self) -> None:
        """Close every adapter, then raise the first observed failure."""

        first_error: Exception | None = None
        for adapter in self._adapters.values():
            try:
                await adapter.close()
            except Exception as error:  # noqa: BLE001 -- adapters are application-defined.
                if first_error is None:
                    first_error = error

        if first_error is not None:
            raise first_error
