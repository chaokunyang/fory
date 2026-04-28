# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import pytest
from pyfory import Fory, DeserializationPolicy


def policy_global_function():
    return "safe"


class BlockClassPolicy(DeserializationPolicy):
    """Policy that blocks specific class names from deserialization."""

    def __init__(self, blocked_class_names):
        self.blocked_class_names = blocked_class_names

    def validate_class(self, cls, is_local, **kwargs):
        if cls.__name__ in self.blocked_class_names:
            raise ValueError(f"Class {cls.__name__} is blocked")
        return None


class ReplaceObjectPolicy(DeserializationPolicy):
    """Policy that replaces deserialized objects from reduce."""

    def __init__(self, replacement_value):
        self.replacement_value = replacement_value

    def inspect_reduced_object(self, obj, **kwargs):
        if hasattr(obj, "value"):
            return self.replacement_value
        return None


class BlockReduceCallPolicy(DeserializationPolicy):
    """Policy that blocks specific callable invocations during reduce."""

    def __init__(self, blocked_names):
        self.blocked_names = blocked_names

    def intercept_reduce_call(self, callable_obj, args, **kwargs):
        if hasattr(callable_obj, "__name__") and callable_obj.__name__ in self.blocked_names:
            raise ValueError(f"Callable {callable_obj.__name__} is blocked")
        return None


class SanitizeStatePolicy(DeserializationPolicy):
    """Policy that sanitizes object state during setstate."""

    def intercept_setstate(self, obj, state, **kwargs):
        if isinstance(state, dict) and "password" in state:
            state["password"] = "***REDACTED***"
        return None


def test_block_class_type_deserialization():
    """Test blocking class type (not instance) deserialization."""

    class SafeClass:
        pass

    class UnsafeClass:
        pass

    policy = BlockClassPolicy(blocked_class_names=["UnsafeClass"])
    fory = Fory(ref=True, strict=False, policy=policy)

    # Serialize and deserialize the class type itself (not an instance)
    safe_data = fory.serialize(SafeClass)
    result = fory.deserialize(safe_data)
    assert result.__name__ == "SafeClass"

    # Now test blocking
    unsafe_data = fory.serialize(UnsafeClass)
    with pytest.raises(ValueError, match="UnsafeClass is blocked"):
        fory.deserialize(unsafe_data)


def test_block_reduce_call():
    """Test blocking callable invocations during reduce."""

    class ReducibleClass:
        def __init__(self, value):
            self.value = value

        def __reduce__(self):
            return (ReducibleClass, (self.value,))

    policy = BlockReduceCallPolicy(blocked_names=["ReducibleClass"])
    fory = Fory(ref=True, strict=False, policy=policy)
    data = fory.serialize(ReducibleClass(42))

    with pytest.raises(ValueError, match="ReducibleClass is blocked"):
        fory.deserialize(data)


def test_replace_reduced_object():
    """Test replacing objects created via __reduce__."""

    class ReducibleClass:
        def __init__(self, value):
            self.value = value

        def __reduce__(self):
            return (ReducibleClass, (self.value,))

    policy = ReplaceObjectPolicy(replacement_value="REPLACED")
    fory = Fory(ref=True, strict=False, policy=policy)
    data = fory.serialize(ReducibleClass(42))

    result = fory.deserialize(data)
    assert result == "REPLACED"


def test_sanitize_state():
    """Test sanitizing object state during setstate."""

    class SecretHolder:
        def __init__(self, username, password):
            self.username = username
            self.password = password

        def __getstate__(self):
            return {"username": self.username, "password": self.password}

        def __setstate__(self, state):
            self.__dict__.update(state)

    policy = SanitizeStatePolicy()
    fory = Fory(ref=False, strict=False, policy=policy)
    data = fory.serialize(SecretHolder("admin", "secret123"))

    result = fory.deserialize(data)
    assert result.username == "admin"
    assert result.password == "***REDACTED***"


def test_reduce_state_sanitizes_state():
    """Test sanitizing object state restored from __reduce__."""

    class CountingSanitizePolicy(DeserializationPolicy):
        def __init__(self):
            self.intercept_setstate_calls = 0

        def intercept_setstate(self, obj, state, **kwargs):
            self.intercept_setstate_calls += 1
            if isinstance(state, dict) and "password" in state:
                state["password"] = "***REDACTED***"
            return None

    class SecretReduceHolder:
        def __reduce__(self):
            return (SecretReduceHolder, (), {"username": "admin", "password": "secret123"})

        def __setstate__(self, state):
            self.__dict__.update(state)

    policy = CountingSanitizePolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    data = fory.serialize(SecretReduceHolder())

    result = fory.deserialize(data)
    assert policy.intercept_setstate_calls == 1
    assert result.username == "admin"
    assert result.password == "***REDACTED***"


def test_policy_with_local_class():
    """Test policy intercepts local class deserialization."""

    def make_local_class():
        class LocalClass:
            pass

        return LocalClass

    LocalCls = make_local_class()

    policy = BlockClassPolicy(blocked_class_names=["LocalClass"])
    fory = Fory(ref=True, strict=False, policy=policy)

    # Serialize the local class type
    data = fory.serialize(LocalCls)

    with pytest.raises(ValueError, match="LocalClass is blocked"):
        fory.deserialize(data)


def test_policy_with_ref_tracking():
    """Test policy works with reference tracking."""

    class ReducibleClass:
        def __init__(self, value):
            self.value = value

        def __reduce__(self):
            return (ReducibleClass, (self.value,))

    policy = BlockReduceCallPolicy(blocked_names=["ReducibleClass"])
    fory = Fory(ref=True, strict=False, policy=policy)

    data = fory.serialize(ReducibleClass(42))

    with pytest.raises(ValueError, match="ReducibleClass is blocked"):
        fory.deserialize(data)


def test_policy_allows_safe_operations():
    """Test that policy doesn't interfere with safe built-in types."""
    policy = BlockClassPolicy(blocked_class_names=[])
    fory = Fory(ref=False, strict=False, policy=policy)

    assert fory.deserialize(fory.serialize(42)) == 42
    assert fory.deserialize(fory.serialize("test")) == "test"
    assert fory.deserialize(fory.serialize([1, 2, 3])) == [1, 2, 3]


def test_multiple_policy_hooks():
    """Test policy with multiple hooks working together."""

    class MultiHookPolicy(DeserializationPolicy):
        def __init__(self):
            self.hooks_called = []

        def validate_class(self, cls, is_local, **kwargs):
            self.hooks_called.append(("validate_class", cls.__name__))
            return None

        def intercept_reduce_call(self, callable_obj, args, **kwargs):
            if hasattr(callable_obj, "__name__"):
                self.hooks_called.append(("intercept_reduce_call", callable_obj.__name__))
            return None

        def inspect_reduced_object(self, obj, **kwargs):
            self.hooks_called.append(("inspect_reduced_object", type(obj).__name__))
            return None

    class TestClass:
        def __init__(self, value):
            self.value = value

        def __reduce__(self):
            return (TestClass, (self.value,))

    policy = MultiHookPolicy()
    fory = Fory(ref=True, strict=False, policy=policy)

    data = fory.serialize(TestClass(42))
    result = fory.deserialize(data)

    # All hooks should have been called
    assert ("intercept_reduce_call", "TestClass") in policy.hooks_called
    assert ("inspect_reduced_object", "TestClass") in policy.hooks_called
    assert result.value == 42


def test_policy_with_nested_reduce():
    """Test policy handles nested objects with __reduce__."""

    class Inner:
        def __init__(self, value):
            self.value = value

        def __reduce__(self):
            return (Inner, (self.value,))

    class Outer:
        def __init__(self, inner):
            self.inner = inner

        def __reduce__(self):
            return (Outer, (self.inner,))

    policy = BlockReduceCallPolicy(blocked_names=["Inner"])
    fory = Fory(ref=True, strict=False, policy=policy)

    data = fory.serialize(Outer(Inner(42)))

    with pytest.raises(ValueError, match="Inner is blocked"):
        fory.deserialize(data)


def test_stateful_authorizes_instantiation():
    """Test authorize_instantiation policy hook for stateful deserialization."""

    class StatefulPayload:
        def __init__(self):
            self.value = 1

        def __getstate__(self):
            return {"value": self.value}

        def __setstate__(self, state):
            self.__dict__.update(state)

    class BlockInstantiationPolicy(DeserializationPolicy):
        def __init__(self):
            self.authorize_instantiation_calls = 0

        def authorize_instantiation(self, cls, **kwargs):
            self.authorize_instantiation_calls += 1
            if cls is StatefulPayload:
                raise ValueError("StatefulPayload blocked")
            return None

    policy = BlockInstantiationPolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    with pytest.raises(ValueError, match="StatefulPayload blocked"):
        fory.deserialize(fory.serialize(StatefulPayload()))
    assert policy.authorize_instantiation_calls == 1


def test_reduce_class_callable_authorizes_instantiation():
    """Test authorize_instantiation policy hook for reduce class callables."""

    class ReduceTarget:
        pass

    class ReducePayload:
        def __reduce__(self):
            return (ReduceTarget, ())

    class BlockInstantiationPolicy(DeserializationPolicy):
        def __init__(self):
            self.authorize_instantiation_calls = 0

        def authorize_instantiation(self, cls, **kwargs):
            self.authorize_instantiation_calls += 1
            if cls.__name__ == "ReduceTarget":
                raise ValueError("ReduceTarget blocked")
            return None

    policy = BlockInstantiationPolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    with pytest.raises(ValueError, match="ReduceTarget blocked"):
        fory.deserialize(fory.serialize(ReducePayload()))
    assert policy.authorize_instantiation_calls == 1


def test_validate_module():
    """Test validate_module policy hook for module deserialization."""
    import json
    import collections

    # Test 1: Return module object directly
    class ReturnModulePolicy(DeserializationPolicy):
        def validate_module(self, module_name, **kwargs):
            return collections

    fory1 = Fory(ref=True, strict=False, policy=ReturnModulePolicy())
    data = fory1.serialize(json)
    assert fory1.deserialize(data) is collections

    # Test 2: Return string to redirect import
    class RedirectPolicy(DeserializationPolicy):
        def validate_module(self, module_name, **kwargs):
            return "collections" if module_name == "json" else None

    fory2 = Fory(ref=True, strict=False, policy=RedirectPolicy())
    assert fory2.deserialize(fory2.serialize(json)).__name__ == "collections"

    # Test 3: Raise to block module
    class BlockPolicy(DeserializationPolicy):
        def validate_module(self, module_name, **kwargs):
            raise ValueError(f"Module {module_name} blocked")

    fory3 = Fory(ref=True, strict=False, policy=BlockPolicy())
    with pytest.raises(ValueError, match="blocked"):
        fory3.deserialize(fory3.serialize(json))


def test_type_deserialization_validates_module():
    """Test validate_module policy hook for global class deserialization."""
    import subprocess

    class BlockModulePolicy(DeserializationPolicy):
        def __init__(self):
            self.validate_module_calls = 0

        def validate_module(self, module_name, **kwargs):
            self.validate_module_calls += 1
            if module_name == "subprocess":
                raise ValueError("subprocess blocked")
            return None

    policy = BlockModulePolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    with pytest.raises(ValueError, match="subprocess blocked"):
        fory.deserialize(fory.serialize(subprocess.Popen))
    assert policy.validate_module_calls == 1


def test_global_function_deserialization_validates_module():
    """Test validate_module policy hook for global function deserialization."""

    class BlockModulePolicy(DeserializationPolicy):
        def __init__(self):
            self.validate_module_calls = 0

        def validate_module(self, module_name, **kwargs):
            self.validate_module_calls += 1
            if module_name == policy_global_function.__module__:
                raise ValueError("function module blocked")
            return None

    policy = BlockModulePolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    with pytest.raises(ValueError, match="function module blocked"):
        fory.deserialize(fory.serialize(policy_global_function))
    assert policy.validate_module_calls == 1


def test_local_function_deserialization_validates_module():
    """Test validate_module policy hook for local function deserialization."""

    def local_function():
        return "safe"

    class BlockModulePolicy(DeserializationPolicy):
        def __init__(self):
            self.validate_module_calls = 0

        def validate_module(self, module_name, **kwargs):
            self.validate_module_calls += 1
            if module_name == local_function.__module__:
                raise ValueError("local function module blocked")
            return None

    policy = BlockModulePolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    with pytest.raises(ValueError, match="local function module blocked"):
        fory.deserialize(fory.serialize(local_function))
    assert policy.validate_module_calls == 1


def test_native_function_deserialization_validates_module():
    """Test validate_module policy hook for native function deserialization."""
    import time

    class BlockModulePolicy(DeserializationPolicy):
        def __init__(self):
            self.validate_module_calls = 0

        def validate_module(self, module_name, **kwargs):
            self.validate_module_calls += 1
            if module_name == "time":
                raise ValueError("time blocked")
            return None

    policy = BlockModulePolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    with pytest.raises(ValueError, match="time blocked"):
        fory.deserialize(fory.serialize(time.time))
    assert policy.validate_module_calls == 1


def test_type_metadata_load_validates_module():
    """Test validate_module policy hook for by-name type metadata loading."""

    class BlockModulePolicy(DeserializationPolicy):
        def __init__(self):
            self.validate_module_calls = 0

        def validate_module(self, module_name, **kwargs):
            self.validate_module_calls += 1
            if module_name == "subprocess":
                raise ValueError("subprocess blocked")
            return None

    policy = BlockModulePolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    from pyfory.registry import SharedRegistry, TypeResolver

    resolver = TypeResolver(fory.config, shared_registry=SharedRegistry())
    namespace = resolver.namespace_encoder.encode("subprocess")
    ns_metabytes = resolver.shared_registry.get_encoded_meta_string(namespace)
    typename = resolver.typename_encoder.encode("Popen")
    type_metabytes = resolver.shared_registry.get_encoded_meta_string(typename)

    with pytest.raises(ValueError, match="subprocess blocked"):
        resolver._load_metabytes_to_type_info(ns_metabytes, type_metabytes)
    assert policy.validate_module_calls == 1


def test_type_metadata_load_validates_class():
    """Test validate_class policy hook for by-name type metadata loading."""

    class BlockClassPolicy(DeserializationPolicy):
        def __init__(self):
            self.validate_class_calls = 0

        def validate_class(self, cls, is_local, **kwargs):
            self.validate_class_calls += 1
            if cls.__module__ == "subprocess" and cls.__name__ == "Popen":
                raise ValueError("Popen blocked")
            return None

    policy = BlockClassPolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    from pyfory.registry import SharedRegistry, TypeResolver

    resolver = TypeResolver(fory.config, shared_registry=SharedRegistry())
    namespace = resolver.namespace_encoder.encode("subprocess")
    ns_metabytes = resolver.shared_registry.get_encoded_meta_string(namespace)
    typename = resolver.typename_encoder.encode("Popen")
    type_metabytes = resolver.shared_registry.get_encoded_meta_string(typename)

    with pytest.raises(ValueError, match="Popen blocked"):
        resolver._load_metabytes_to_type_info(ns_metabytes, type_metabytes)
    assert policy.validate_class_calls == 1


def test_reduce_global_name_validates_module():
    """Test validate_module policy hook for reduce global-name deserialization."""

    class GlobalNamePayload:
        def __reduce__(self):
            return "subprocess.Popen"

    class BlockModulePolicy(DeserializationPolicy):
        def __init__(self):
            self.validate_module_calls = 0

        def validate_module(self, module_name, **kwargs):
            self.validate_module_calls += 1
            if module_name == "subprocess":
                raise ValueError(f"Module {module_name} blocked")
            return None

    policy = BlockModulePolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    with pytest.raises(ValueError, match="subprocess blocked"):
        fory.deserialize(fory.serialize(GlobalNamePayload()))
    assert policy.validate_module_calls == 1


def test_reduce_global_name_validates_class():
    """Test validate_class policy hook for reduce global-name deserialization."""

    class GlobalNamePayload:
        def __reduce__(self):
            return "subprocess.Popen"

    class BlockClassPolicy(DeserializationPolicy):
        def __init__(self):
            self.validate_module_calls = 0
            self.validate_class_calls = 0

        def validate_module(self, module_name, **kwargs):
            self.validate_module_calls += 1
            return None

        def validate_class(self, cls, is_local, **kwargs):
            self.validate_class_calls += 1
            if cls.__module__ == "subprocess" and cls.__name__ == "Popen":
                raise ValueError("subprocess.Popen blocked")
            return None

    policy = BlockClassPolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    with pytest.raises(ValueError, match="subprocess.Popen blocked"):
        fory.deserialize(fory.serialize(GlobalNamePayload()))
    assert policy.validate_module_calls == 1
    assert policy.validate_class_calls == 1


def test_reduce_global_name_validates_function():
    """Test validate_function policy hook for reduce builtins-name deserialization."""

    class GlobalNamePayload:
        def __reduce__(self):
            return "eval"

    class BlockFunctionPolicy(DeserializationPolicy):
        def __init__(self):
            self.validate_module_calls = 0
            self.validate_function_calls = 0

        def validate_module(self, module_name, **kwargs):
            self.validate_module_calls += 1
            return None

        def validate_function(self, func, is_local, **kwargs):
            self.validate_function_calls += 1
            if func.__name__ == "eval":
                raise ValueError("eval blocked")
            return None

    policy = BlockFunctionPolicy()
    fory = Fory(ref=True, strict=False, policy=policy)
    with pytest.raises(ValueError, match="eval blocked"):
        fory.deserialize(fory.serialize(GlobalNamePayload()))
    assert policy.validate_module_calls == 1
    assert policy.validate_function_calls == 1
