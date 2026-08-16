"""Typed host models backed by a common JSON Schema and JSON codec."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Generic, TypeVar, cast

from pydantic import TypeAdapter

from .types import Json, JsonObject

Value = TypeVar("Value")


@dataclass(frozen=True, slots=True)
class JsonCodec(Generic[Value]):
    """Connect one Python type to its JSON Schema and validated JSON form."""

    schema: JsonObject

    _adapter: TypeAdapter[Value]

    def decode(self, value: JsonObject) -> Value:
        """Validate and decode a JSON object into the configured Python type."""

        return self._adapter.validate_python(value)

    def encode(self, value: Value) -> Json:
        """Validate and encode a Python value into JSON-compatible data."""

        return cast(Json, self._adapter.dump_python(value, mode="json"))

    def decode_json(self, value: str) -> Value:
        """Validate JSON text and return the configured application type."""

        return self._adapter.validate_json(value)


def json_codec(value_type: type[Value]) -> JsonCodec[Value]:
    """Derive a codec from a Pydantic model or standard typed dataclass."""

    adapter = TypeAdapter(value_type)
    raw_schema = adapter.json_schema(mode="validation")

    return JsonCodec(schema=cast(JsonObject, raw_schema), _adapter=adapter)
