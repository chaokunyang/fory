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

from collections import UserDict, UserList, abc
from dataclasses import dataclass, make_dataclass
from io import BytesIO
from typing import AbstractSet, Any, Dict, List, Mapping, MutableMapping, MutableSequence, MutableSet, Optional, Sequence

import pytest

import pyfory
from pyfory.collection import MappingSerializer, SequenceSerializer, SetCollectionSerializer
from pyfory.error import TypeUnregisteredError
from pyfory.policy import DeserializationPolicy


class ListValue(list):
    pass


class DictValue(dict):
    pass


class SetValue(set):
    pass


class SlottedList(list):
    __slots__ = ("parent", "__label", "missing")

    def __init__(self, required):
        super().__init__([required])
        self.__label = "base"


class ChildList(SlottedList):
    __slots__ = ("child", "__dict__")


@abc.Sequence.register
class VirtualSequence:
    def __init__(self, values):
        self.values = values

    def __len__(self):
        return len(self.values)

    def __getitem__(self, index):
        return self.values[index]

    def __iter__(self):
        return iter(self.values)


class SetView(abc.Set):
    def __init__(self, values):
        self.values = set(values)

    def __len__(self):
        return len(self.values)

    def __iter__(self):
        return iter(self.values)

    def __contains__(self, value):
        return value in self.values


@pytest.mark.parametrize("ref", [False, True])
@pytest.mark.parametrize("compatible", [False, True])
@pytest.mark.parametrize(
    "value,expected",
    [
        (UserDict(), {}),
        (UserDict({"a": 1, "b": None, None: "c"}), {"a": 1, "b": None, None: "c"}),
        (UserDict({str(i): i for i in range(600)}), {str(i): i for i in range(600)}),
        (UserList(), []),
        (UserList([1, None, "a", 2.5]), [1, None, "a", 2.5]),
        (VirtualSequence([1, 2, 3]), [1, 2, 3]),
        (range(4), [0, 1, 2, 3]),
        (SetView([]), set()),
        (SetView([1, 2, 3]), {1, 2, 3}),
        (DictValue(a=1), {"a": 1}),
        (ListValue([1, 2]), [1, 2]),
        (SetValue([1, 2]), {1, 2}),
    ],
)
def test_xlang_containers(value, expected, ref, compatible):
    writer = pyfory.Fory(xlang=True, ref=ref, compatible=compatible)
    reader = pyfory.Fory(xlang=True, ref=ref, compatible=compatible)
    builtin_serializer = writer.type_resolver.get_serializer(type(expected))
    data = writer.dumps(value)
    result = reader.loads(data)
    assert type(result) is type(expected)
    assert result == expected
    assert writer.type_resolver.get_serializer(type(expected)) is builtin_serializer


@pytest.mark.parametrize("cls,items", [(ListValue, [1, None, "a"]), (DictValue, {"a": 1}), (SetValue, {1, 2})])
@pytest.mark.parametrize("ref", [False, True])
@pytest.mark.parametrize("named", [False, True])
def test_native_subclasses(cls, items, ref, named):
    writer = pyfory.Fory(xlang=False, ref=ref)
    reader = pyfory.Fory(xlang=False, ref=ref)
    options = {"name": "test.Container"} if named else {"type_id": 100}
    writer.register(cls, **options)
    reader.register(cls, **options)
    value = cls(items)
    value.label = "example"
    result = reader.loads(writer.dumps(value))
    assert type(result) is cls
    assert result == value
    assert result.label == "example"


@pytest.mark.parametrize("cls", [ListValue, DictValue, SetValue])
def test_subclass_references(cls):
    fory = pyfory.Fory(xlang=False, ref=True)
    fory.register(cls)
    value = cls()
    value.self = value
    if cls is ListValue:
        value.append(value)
    elif cls is DictValue:
        value["self"] = value
    value.state = value.__dict__
    result, again = fory.loads(fory.dumps([value, value]))
    assert result is again
    assert result.self is result
    assert result.state is result.__dict__
    if cls is ListValue:
        assert result[0] is result
    elif cls is DictValue:
        assert result["self"] is result


def test_inherited_slots():
    fory = pyfory.Fory(xlang=False, ref=True)
    fory.register(ChildList)
    value = ChildList(7)
    value.parent = value
    value.child = {"value": value}
    value.label = "child"
    result = fory.loads(fory.dumps(value))
    assert type(result) is ChildList
    assert result == [7]
    assert result.parent is result
    assert result.child["value"] is result
    assert result._SlottedList__label == "base"
    assert result.label == "child"
    assert not hasattr(result, "missing")


@pytest.mark.parametrize("cls", [ListValue, DictValue, SetValue])
def test_native_registration(cls):
    fory = pyfory.Fory(xlang=False)
    with pytest.raises(TypeUnregisteredError):
        fory.dumps(cls())
    fory = pyfory.Fory(xlang=False, strict=False)
    result = fory.loads(fory.dumps(cls()))
    assert type(result) is cls


@pytest.mark.parametrize("xlang,compatible", [(False, False), (True, False), (True, True)])
@pytest.mark.parametrize("ref", [False, True])
@pytest.mark.parametrize(
    "hint,value,expected,serializer_type",
    [
        (Mapping[str, Optional[int]], UserDict(a=1, b=None), {"a": 1, "b": None}, MappingSerializer),
        (MutableMapping[str, Any], UserDict(a=1, b="two"), {"a": 1, "b": "two"}, MappingSerializer),
        (Sequence[Optional[int]], UserList([1, None, 2]), [1, None, 2], SequenceSerializer),
        (MutableSequence[str], UserList(["a", "b"]), ["a", "b"], SequenceSerializer),
        (AbstractSet[int], SetView([1, 2]), {1, 2}, SetCollectionSerializer),
        (MutableSet[int], SetView([1, 2]), {1, 2}, SetCollectionSerializer),
        (abc.Mapping, UserDict(a=1), {"a": 1}, MappingSerializer),
        (abc.Sequence, VirtualSequence([1, "a"]), [1, "a"], SequenceSerializer),
        (abc.Set, SetView([1, 2]), {1, 2}, SetCollectionSerializer),
    ],
)
def test_abc_fields(hint, value, expected, serializer_type, xlang, compatible, ref):
    cls = make_dataclass("Container", [("value", hint)])
    fory = pyfory.Fory(xlang=xlang, compatible=compatible, ref=ref)
    fory.register(cls, name="test.Container")
    from pyfory.struct import StructFieldSerializerVisitor
    from pyfory.type_util import infer_field

    assert isinstance(infer_field("value", hint, StructFieldSerializerVisitor(fory.type_resolver)), serializer_type)
    result = fory.loads(fory.dumps(cls(value)))
    assert type(result.value) is type(expected)
    assert result.value == expected


@dataclass
class NestedContainers:
    values: Mapping[str, Sequence[Mapping[str, Optional[int]]]]


@pytest.mark.parametrize("compatible", [False, True])
def test_nested_abc_fields(compatible):
    fory = pyfory.Fory(xlang=True, compatible=compatible, ref=True)
    fory.register(NestedContainers, name="test.Nested")
    value = NestedContainers(UserDict(values=UserList([UserDict(a=1, b=None)])))
    result = fory.loads(fory.dumps(value))
    assert result == NestedContainers({"values": [{"a": 1, "b": None}]})


def test_subclass_policy():
    class Policy(DeserializationPolicy):
        def authorize_instantiation(self, cls):
            if cls is DictValue:
                raise ValueError("blocked container")

    writer = pyfory.Fory(xlang=False)
    reader = pyfory.Fory(xlang=False, policy=Policy())
    writer.register(DictValue, type_id=100)
    reader.register(DictValue, type_id=100)
    with pytest.raises(ValueError, match="blocked container"):
        reader.loads(writer.dumps(DictValue(a=1)))
    assert reader.loads(writer.dumps({"a": 2})) == {"a": 2}


def test_subclass_setstate_policy():
    class Policy(DeserializationPolicy):
        def intercept_setstate(self, obj, state):
            state["label"] = "sanitized"

    fory = pyfory.Fory(xlang=False, policy=Policy())
    fory.register(DictValue)
    value = DictValue(a=1)
    value.label = "original"
    assert fory.loads(fory.dumps(value)).label == "sanitized"


@pytest.mark.parametrize("xlang", [False, True])
def test_custom_serializer_priority(xlang):
    class CustomSerializer(pyfory.Serializer):
        @classmethod
        def support_subclass(cls):
            return True

        def write(self, context, value):
            context.write_string(value.label)

        def read(self, context):
            result = self.type_()
            result.label = context.read_string()
            return result

    class Child(DictValue):
        pass

    fory = pyfory.Fory(xlang=xlang)
    fory.register(DictValue, serializer=CustomSerializer)
    fory.register(Child)
    value = Child(a=1)
    value.label = "custom"
    result = fory.loads(fory.dumps(value))
    assert type(result) is Child
    assert result == {}
    assert result.label == "custom"


class StatefulList(list):
    def __getstate__(self):
        return {"label": self.label}

    def __setstate__(self, state):
        self.label = state["label"]


def test_state_hook_priority():
    fory = pyfory.Fory(xlang=False)
    fory.register(StatefulList)
    value = StatefulList([1, 2])
    value.label = "hook"
    result = fory.loads(fory.dumps(value))
    assert type(result) is StatefulList
    assert result == value
    assert result.label == "hook"


@pytest.mark.parametrize("value", [UserDict(), UserList()])
def test_xlang_cycles(value):
    if isinstance(value, UserDict):
        value["self"] = value
    else:
        value.append(value)
    fory = pyfory.Fory(xlang=True, ref=True)
    result, again = fory.loads(fory.dumps([value, value]))
    assert result is again
    assert (result["self"] if isinstance(result, dict) else result[0]) is result


@pytest.mark.parametrize("compatible", [False, True])
def test_abc_builtin_schema(compatible):
    abc_type = make_dataclass("Container", [("values", Mapping[str, Sequence[Optional[int]]])])
    builtin_type = make_dataclass("Container", [("values", Dict[str, List[Optional[int]]])])
    writer = pyfory.Fory(xlang=True, compatible=compatible)
    reader = pyfory.Fory(xlang=True, compatible=compatible)
    writer.register(abc_type, name="test.Container")
    reader.register(builtin_type, name="test.Container")
    data = writer.dumps(abc_type(UserDict(a=UserList([1, None, 2]))))
    result = reader.loads(data)
    assert result.values == {"a": [1, None, 2]}
    assert writer.loads(reader.dumps(result)).values == result.values


@pytest.mark.parametrize("base", [list, set, dict])
def test_overridden_container_methods(base):
    def blocked(*args):
        raise AssertionError("native storage must bypass overridden methods")

    cls = type("Overridden", (base,), dict(__len__=blocked, __iter__=blocked, append=blocked, add=blocked, __setitem__=blocked, items=blocked))
    value = cls()
    if base is dict:
        dict.__setitem__(value, "key", 42)
        dict.__setitem__(value, None, None)
    elif base is list:
        list.append(value, 42)
    else:
        set.add(value, 42)
    value.label = "state"
    fory = pyfory.Fory(xlang=False)
    fory.register(cls)
    result = fory.loads(fory.dumps(value))
    assert type(result) is cls
    assert base.__eq__(result, value)
    assert result.label == "state"


@pytest.mark.parametrize("base", [list, set, dict])
@pytest.mark.parametrize("setter", [False, True])
def test_container_state_hooks(base, setter):
    class Container(base):
        def __getstate__(self):
            return {"label": self.label, "self": self}

    if setter:

        def setstate(self, state):
            assert base.__len__(self) > 0
            self.__dict__.update(state)

        Container.__setstate__ = setstate
    value = Container({"a": 1} if base is dict else [1, 2])
    value.label = "hook"
    fory = pyfory.Fory(xlang=False, ref=True)
    fory.register(Container)
    result = fory.loads(fory.dumps(value))
    assert type(result) is Container
    assert result == value
    assert result.label == "hook"
    assert result.self is result


@pytest.mark.parametrize("base", [list, set, dict])
@pytest.mark.parametrize("compatible", [False, True])
def test_replace_collection_serializer(base, compatible):
    class Container(base):
        pass

    class CustomSerializer(pyfory.Serializer):
        def write(self, context, value):
            context.write_string(value.label)

        def read(self, context):
            value = self.type_()
            value.label = context.read_string() + " restored"
            return value

    fory = pyfory.Fory(xlang=True, compatible=compatible)
    fory.register(Container)
    serializer = CustomSerializer(fory.type_resolver, Container)
    fory.register_serializer(Container, serializer)
    fory.register_serializer(Container, serializer)
    value = Container()
    value.label = "extension"
    builtin = {"a": 1} if base is dict else base([1, 2])
    result = fory.loads(fory.dumps([value, builtin]))
    assert type(result[0]) is Container
    assert result[0].label == "extension restored"
    assert type(result[1]) is base
    assert result[1] == builtin


@pytest.mark.parametrize("method", ["__new__", "__getnewargs__", "__getnewargs_ex__"])
def test_custom_construction_requires_serializer(method):
    cls = type("CustomConstruction", (list,), {method: lambda *args: ()})
    with pytest.raises(TypeError, match="explicit serializer or reduce hook"):
        pyfory.Fory(xlang=False).register(cls)


@pytest.mark.parametrize("hint,values", [(Sequence, [1, 2]), (AbstractSet, {1, 2}), (Mapping, {"a": 1})])
def test_abc_stream_and_graph_budget(hint, values):
    container = make_dataclass("Container", [("values", hint)])
    writer = pyfory.Fory(xlang=True)
    reader = pyfory.Fory(xlang=True, max_graph_memory_bytes=64)
    writer.register(container, type_id=101)
    reader.register(container, type_id=101)
    value = container(values)
    stream = BytesIO()
    writer.dump(value, stream)
    assert writer.loads(pyfory.Buffer.from_stream(BytesIO(stream.getvalue()))) == value
    with pytest.raises(ValueError, match="graph memory"):
        reader.loads(stream.getvalue())
    assert reader.loads(writer.dumps(1)) == 1


class EmptyValue:
    pass


class EmptySerializer(pyfory.Serializer):
    def write(self, context, value):
        pass

    def read(self, context):
        return EmptyValue()


@pytest.mark.parametrize("hint", [Sequence[EmptyValue], AbstractSet[EmptyValue], Mapping[EmptyValue, EmptyValue]])
def test_abc_unbacked_items(hint):
    container = make_dataclass("Container", [("values", hint)])
    values = [EmptyValue() for _ in range(1025)]
    if hint.__origin__ is abc.Mapping:
        values = dict(zip(values, values))
    elif hint.__origin__ is abc.Set:
        values = set(values)
    fory = pyfory.Fory(xlang=True, ref=False, max_unbacked_container_items=0)
    fory.register(EmptyValue, type_id=100, serializer=EmptySerializer)
    fory.register(container, type_id=101)
    with pytest.raises(Exception):
        fory.loads(fory.dumps(container(values)))
    assert fory.loads(fory.dumps(1)) == 1
