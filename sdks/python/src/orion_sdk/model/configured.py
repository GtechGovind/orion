"""Application-facing model selection paired with its provider adapter."""

from dataclasses import dataclass

from .contracts import ModelAdapter
from .types import ModelRef


@dataclass(frozen=True, slots=True)
class Model:
    """Pairs one provider model identifier with the adapter that executes it."""

    ref: ModelRef

    adapter: ModelAdapter
