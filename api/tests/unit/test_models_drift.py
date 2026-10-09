# Fails when the shape of a Pydantic model no longer matches its schema in openapi-v1.yaml.
# Goes further than test_contract.py on types, nullability and extra-field rules.

from pathlib import Path
from typing import Any

import pytest
import yaml
from pydantic import BaseModel, Field

import schemas as s

OPENAPI_PATH = Path(__file__).resolve().parents[3] / "openapi-v1.yaml"

# Pydantic model -> name under components.schemas in the contract
CONTRACT_MODELS: dict[type[BaseModel], str] = {
    s.Me: "Me",
    s.PushTokenRequest: "PushTokenRequest",
}

# Keywords that change what a client may send or receive. Everything else
# (title, description, example, default) is documentation and is ignored.
CONSTRAINT_KEYS = (
    "format",
    "pattern",
    "minLength",
    "maxLength",
    "minimum",
    "maximum",
    "exclusiveMinimum",
    "exclusiveMaximum",
    "multipleOf",
    "minItems",
    "maxItems",
    "uniqueItems",
    "minProperties",
    "maxProperties",
)


def resolve_pointer(root: dict[str, Any], ref: str) -> dict[str, Any]:
    node: Any = root
    for part in ref.removeprefix("#/").split("/"):
        node = node[part.replace("~1", "/").replace("~0", "~")]
    return node


def normalise(schema: dict[str, Any], root: dict[str, Any]) -> dict[str, Any]:
    """Reduce a JSON schema to a comparable form, so the two dialects line up.

    Pydantic writes `str | None` as `anyOf: [string, null]` and enums as `$ref`s into
    `$defs`; the contract writes `type: [string, null]` and refs into `components`.
    """
    while "$ref" in schema:
        schema = resolve_pointer(root, schema["$ref"])

    if "anyOf" in schema:
        branches = [normalise(branch, root) for branch in schema["anyOf"]]
        non_null = [branch for branch in branches if branch.get("type") != ["null"]]
        if len(non_null) == 1 and len(non_null) < len(branches):
            merged = dict(non_null[0])
            merged["type"] = sorted({*merged.get("type", []), "null"})
            return merged
        return {"anyOf": sorted(branches, key=repr)}

    out: dict[str, Any] = {}

    if "type" in schema:
        types = schema["type"]
        out["type"] = sorted(types) if isinstance(types, list) else [types]
    if "const" in schema:
        out["enum"] = [schema["const"]]
    if "enum" in schema:
        out["enum"] = sorted(schema["enum"], key=repr)
    if "items" in schema:
        out["items"] = normalise(schema["items"], root)
    if "properties" in schema:
        out["properties"] = {name: normalise(prop, root) for name, prop in schema["properties"].items()}
        out["required"] = sorted(schema.get("required", []))
        out["additionalProperties"] = schema.get("additionalProperties", True)
    for key in CONSTRAINT_KEYS:
        if key in schema:
            out[key] = schema[key]

    return out


def drop_extra_limits(model: dict[str, Any], contract: dict[str, Any]) -> dict[str, Any]:
    # A model may be stricter than the contract (e.g. a minimum length), never looser.
    out = {key: value for key, value in model.items() if key not in CONSTRAINT_KEYS or key in contract}
    if "items" in out and "items" in contract:
        out["items"] = drop_extra_limits(out["items"], contract["items"])
    if "properties" in out:
        out["properties"] = {
            name: drop_extra_limits(prop, contract.get("properties", {}).get(name, prop))
            for name, prop in out["properties"].items()
        }
    return out


@pytest.fixture(scope="module")
def contract() -> dict[str, Any]:
    assert OPENAPI_PATH.is_file(), f"Contract not found at {OPENAPI_PATH}"
    return yaml.safe_load(OPENAPI_PATH.read_text(encoding="utf-8"))


def contract_schema(contract: dict[str, Any], name: str) -> dict[str, Any]:
    return normalise({"$ref": f"#/components/schemas/{name}"}, contract)


def model_schema(model: type[BaseModel], against: dict[str, Any]) -> dict[str, Any]:
    schema = model.model_json_schema()
    return drop_extra_limits(normalise(schema, schema), against)


def drifted(contract: dict[str, Any], model: type[BaseModel], name: str) -> bool:
    expected = contract_schema(contract, name)
    return model_schema(model, expected) != expected


@pytest.mark.parametrize(("model", "name"), CONTRACT_MODELS.items(), ids=CONTRACT_MODELS.values())
def test_model_matches_contract(contract, model, name):
    assert name in contract["components"]["schemas"], f"{name} is not in openapi-v1.yaml"
    expected = contract_schema(contract, name)
    assert model_schema(model, expected) == expected


def test_role_matches_contract(contract):
    assert sorted(s.Role) == sorted(contract["components"]["schemas"]["Role"]["enum"])


class TestDriftIsDetected:
    # Guards the comparison itself: each kind of drift must make the schemas differ.

    def test_extra_field(self, contract):
        class Drifted(s.Me):
            email: str

        assert drifted(contract, Drifted, "Me")

    def test_optional_field_made_required(self, contract):
        class Drifted(s.Me):
            clinic_id: str | None

        assert drifted(contract, Drifted, "Me")

    def test_changed_type(self, contract):
        class Drifted(s.Me):
            is_anonymous: str

        assert drifted(contract, Drifted, "Me")

    def test_field_no_longer_nullable(self, contract):
        class Drifted(s.Me):
            display_name: str = ""

        assert drifted(contract, Drifted, "Me")

    def test_changed_enum_values(self, contract):
        class Drifted(s.PushTokenRequest):
            platform: str

        assert drifted(contract, Drifted, "PushTokenRequest")

    def test_extra_fields_allowed_again(self, contract):
        class Drifted(s.PushTokenRequest):
            model_config = {"extra": "ignore"}

        assert drifted(contract, Drifted, "PushTokenRequest")

    def test_stricter_model_is_not_drift(self, contract):
        class Stricter(s.PushTokenRequest):
            token: str = Field(min_length=50)

        assert not drifted(contract, Stricter, "PushTokenRequest")
