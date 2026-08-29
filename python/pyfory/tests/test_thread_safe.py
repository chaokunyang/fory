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

import threading
from dataclasses import dataclass

import pytest

import pyfory
from pyfory import ThreadSafeFory


@dataclass
class Person:
    name: str
    age: int


@dataclass
class Address:
    city: str
    country: str


def test_thread_safe_fory_basic_serialization():
    fory = ThreadSafeFory(
        xlang=False,
        compatible=False,
    )
    fory.register(Person)

    person = Person(name="Alice", age=30)
    data = fory.serialize(person)
    result = fory.deserialize(data)

    assert result.name == person.name
    assert result.age == person.age


def test_thread_safe_fory_multiple_threads():
    fory = ThreadSafeFory(
        xlang=False,
        compatible=False,
    )
    fory.register(Person)

    results = []
    errors = []

    def serialize_deserialize(thread_id):
        try:
            person = Person(name=f"Person{thread_id}", age=20 + thread_id)
            data = fory.serialize(person)
            result = fory.deserialize(data)
            results.append((thread_id, result))
        except Exception as e:
            errors.append((thread_id, e))

    threads = []
    for i in range(10):
        t = threading.Thread(target=serialize_deserialize, args=(i,))
        threads.append(t)
        t.start()

    for t in threads:
        t.join()

    assert len(errors) == 0, f"Errors occurred: {errors}"
    assert len(results) == 10

    for thread_id, result in results:
        assert result.name == f"Person{thread_id}"
        assert result.age == 20 + thread_id


def test_thread_safe_fory_registration():
    fory = ThreadSafeFory(
        xlang=False,
        compatible=False,
    )
    fory.register(Person, type_id=100)
    fory.register(Address, name="test.Address")

    person = Person(name="Bob", age=25)
    data = fory.serialize(person)
    result = fory.deserialize(data)
    assert result.name == person.name

    address = Address(city="NYC", country="USA")
    data = fory.serialize(address)
    result = fory.deserialize(data)
    assert result.city == address.city


def test_thread_safe_fory_xlang_mode():
    fory = ThreadSafeFory(xlang=True, compatible=False, ref=True)
    fory.register(Person)

    person = Person(name="Charlie", age=35)
    data = fory.serialize(person)
    result = fory.deserialize(data)

    assert result.name == person.name
    assert result.age == person.age


def test_thread_safe_fory_dumps_loads():
    fory = ThreadSafeFory(
        xlang=False,
        compatible=False,
    )
    fory.register(Person)

    person = Person(name="Dave", age=40)
    data = fory.dumps(person)
    result = fory.loads(data)

    assert result.name == person.name
    assert result.age == person.age


def test_thread_safe_fory_ref_tracking():
    fory = ThreadSafeFory(xlang=False, ref=True, compatible=False)
    fory.register(Person)

    person = Person(name="Eve", age=28)
    data = [person, person]
    serialized = fory.serialize(data)
    result = fory.deserialize(serialized)

    assert len(result) == 2
    assert result[0].name == person.name
    assert result[1].name == person.name


def test_thread_safe_fory_cross_thread_registration():
    fory = ThreadSafeFory(
        xlang=False,
        compatible=False,
    )
    fory.register(Person)
    fory.register(Address)

    results = []
    errors = []

    def serialize_data(thread_id):
        try:
            person = Person(name=f"User{thread_id}", age=25)
            data = fory.serialize(person)
            result = fory.deserialize(data)
            results.append(result)
        except Exception as e:
            errors.append((thread_id, e))

    threads = []
    for i in range(5):
        t = threading.Thread(target=serialize_data, args=(i,))
        threads.append(t)
        t.start()

    for t in threads:
        t.join()

    assert len(errors) == 0
    assert len(results) == 5


def test_thread_safe_fory_register_after_use():
    fory = ThreadSafeFory(
        xlang=False,
        compatible=False,
    )
    fory.register(Person)

    person = Person(name="Alice", age=30)
    fory.serialize(person)

    with pytest.raises(RuntimeError):
        fory.register(Address)


def test_invalid_registration():
    failed_constructions = 0
    valid_constructions = 0

    class BrokenSerializer:
        def __init__(self, *_args):
            nonlocal failed_constructions
            failed_constructions += 1
            raise ValueError("serializer construction failed")

    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    def serializer_factory(type_resolver, cls):
        nonlocal valid_constructions
        valid_constructions += 1
        return AddressSerializer(type_resolver, cls)

    fory = ThreadSafeFory(xlang=False, compatible=False)
    with pytest.raises(ValueError):
        fory.register_type(Address, serializer=BrokenSerializer)
    assert failed_constructions == 1

    fory.register_type(Address, serializer=serializer_factory)
    address = Address(city="Oslo", country="Norway")
    assert fory.deserialize(fory.serialize(address)) == address
    assert failed_constructions == 1
    assert valid_constructions == 1


def test_zero_arg_serializer_rejected():
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    children = []
    constructions = 0

    def fory_factory():
        child = pyfory.Fory(xlang=False, compatible=False)
        children.append(child)
        return child

    def serializer_factory():
        nonlocal constructions
        constructions += 1
        child = children[-1]
        return AddressSerializer(child.type_resolver, Address)

    fory = ThreadSafeFory(fory_factory=fory_factory)
    with pytest.raises(TypeError):
        fory.register_type(Address, serializer=serializer_factory)

    assert constructions == 0
    assert len(children) == 1
    assert children[0].type_resolver.get_type_info(Address, create=False) is None

    fory.register_type(Address, serializer=AddressSerializer)
    value = Address(city="Oslo", country="Norway")
    assert fory.deserialize(fory.serialize(value)) == value


@pytest.mark.parametrize("method", ["register", "register_type", "register_union"])
def test_serializer_instance_rejected(method):
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    owner = pyfory.Fory(xlang=False, compatible=False)
    serializer = AddressSerializer(owner.type_resolver, Address)
    builds = 0

    def fory_factory():
        nonlocal builds
        builds += 1
        return pyfory.Fory(xlang=False, compatible=False)

    fory = ThreadSafeFory(fory_factory=fory_factory)

    with pytest.raises(TypeError):
        getattr(fory, method)(Address, serializer=serializer)

    assert fory._registrations == []
    assert fory._registration_fory is None
    assert not fory._root_started
    assert builds == 0


def test_serializer_factory_per_child():
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    serializers = []

    def serializer_factory(type_resolver, cls):
        serializer = AddressSerializer(type_resolver, cls)
        serializers.append(serializer)
        return serializer

    fory = ThreadSafeFory(xlang=False, compatible=False)
    fory.register_type(Address, serializer=serializer_factory)
    first = fory._registration_fory
    second = fory._build_fory()

    assert len(serializers) == 2
    assert serializers[0] is not serializers[1]
    assert serializers[0].type_resolver is first.type_resolver
    assert serializers[1].type_resolver is second.type_resolver
    address = Address(city="Oslo", country="Norway")
    assert second.deserialize(second.serialize(address)) == address
    assert fory.deserialize(fory.serialize(address)) == address


@pytest.mark.parametrize("result", ["value", "resolver", "type"])
def test_serializer_factory_result(result):
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    foreign = pyfory.Fory(xlang=False, compatible=False)

    def serializer_factory(type_resolver, cls):
        if result == "value":
            return object()
        if result == "resolver":
            return AddressSerializer(foreign.type_resolver, cls)
        return AddressSerializer(type_resolver, Person)

    fory = ThreadSafeFory(xlang=False, compatible=False)
    with pytest.raises(TypeError):
        fory.register_type(Address, serializer=serializer_factory)

    assert not fory._registrations
    assert fory._registration_fory is None


def test_singleton_serializer_factory():
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    children = []
    singleton = None

    def child_factory():
        child = pyfory.Fory(xlang=False, compatible=False)
        children.append(child)
        return child

    def serializer_factory(type_resolver, cls):
        nonlocal singleton
        if singleton is None:
            singleton = AddressSerializer(type_resolver, cls)
        return singleton

    fory = ThreadSafeFory(fory_factory=child_factory)
    fory.register_type(Address, serializer=serializer_factory)
    assert singleton.type_resolver is children[0].type_resolver

    with pytest.raises(TypeError):
        fory._build_fory()

    assert len(children) == 2
    assert children[1].type_resolver.get_type_info(Address, create=False) is None


def test_factory_serializer_owner():
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    serializers = []

    def fory_factory():
        fory = pyfory.Fory(xlang=False, compatible=False)
        serializer = AddressSerializer(fory.type_resolver, Address)
        fory.register_type(Address, serializer=serializer)
        serializers.append(serializer)
        return fory

    fory = ThreadSafeFory(fory_factory=fory_factory)
    first = fory._build_fory()
    second = fory._build_fory()

    assert len(serializers) == 2
    assert serializers[0] is not serializers[1]
    assert serializers[0].type_resolver is first.type_resolver
    assert serializers[1].type_resolver is second.type_resolver
    address = Address(city="Oslo", country="Norway")
    assert first.deserialize(first.serialize(address)) == address
    assert second.deserialize(second.serialize(address)) == address


def test_reentrant_registration():
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    fory = ThreadSafeFory(xlang=False, compatible=False)
    constructions = 0
    errors = []

    def serializer_factory(type_resolver, cls):
        nonlocal constructions
        constructions += 1
        fory.serialize(None)
        return AddressSerializer(type_resolver, cls)

    def register():
        try:
            fory.register_type(Address, serializer=serializer_factory)
        except RuntimeError as exc:
            errors.append(exc)

    thread = threading.Thread(target=register, daemon=True)
    thread.start()
    thread.join(timeout=5)

    assert not thread.is_alive()
    assert constructions == 1
    assert len(errors) == 1
    assert isinstance(errors[0], RuntimeError)
    assert not fory._registrations
    assert fory._registration_fory is None
    assert fory.deserialize(fory.serialize(None)) is None
    with pytest.raises(RuntimeError):
        fory.register_type(Person)


def test_nested_registration():
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    fory = ThreadSafeFory(xlang=True, compatible=False)
    constructions = 0
    errors = []

    def serializer_factory(type_resolver, cls):
        nonlocal constructions
        constructions += 1
        fory.register_type(Person)
        return AddressSerializer(type_resolver, cls)

    def register():
        try:
            fory.register_type(Address, serializer=serializer_factory)
        except (RuntimeError, TypeError) as exc:
            errors.append(exc)

    thread = threading.Thread(target=register, daemon=True)
    thread.start()
    thread.join(timeout=5)

    assert not thread.is_alive()
    assert not errors
    first = fory._registration_fory
    second = fory._build_fory()
    for child in (first, second):
        resolver = child.type_resolver
        person_info = resolver.get_type_info(Person, create=False)
        address_info = resolver.get_type_info(Address, create=False)
        assert person_info.user_type_id + 1 == address_info.user_type_id
    address = Address(city="Oslo", country="Norway")
    assert second.deserialize(second.serialize(address)) == address
    assert fory.deserialize(fory.serialize(address)) == address
    assert constructions == 2


@pytest.mark.parametrize("scenario", ["unknown", "different"])
def test_nested_replay_rejected(scenario):
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    class Unknown:
        pass

    children = []
    replay = False

    def child_factory():
        child = pyfory.Fory(xlang=True, compatible=False)
        children.append(child)
        return child

    def serializer_factory(type_resolver, cls):
        if not replay:
            fory.register_type(Person)
        elif scenario == "unknown":
            fory.register_type(Unknown)
        else:
            fory.register_type(Person, type_id=101)
        return AddressSerializer(type_resolver, cls)

    fory = ThreadSafeFory(fory_factory=child_factory)
    fory.register_type(Address, serializer=serializer_factory)
    replay = True

    with pytest.raises(RuntimeError):
        fory._build_fory()

    child = children[1]
    person_info = child.type_resolver.get_type_info(Person, create=False)
    assert person_info is not None
    assert person_info.user_type_id != 101
    assert child.type_resolver.get_type_info(Address, create=False) is None
    assert child.type_resolver.get_type_info(Unknown, create=False) is None
    assert fory._replay_limit == 0
    assert fory._building_thread is None


def test_factory_root_reentry():
    fory = None
    constructions = 0

    def fory_factory():
        nonlocal constructions
        constructions += 1
        fory.serialize(None)
        return pyfory.Fory(xlang=False, compatible=False)

    fory = ThreadSafeFory(fory_factory=fory_factory)
    with pytest.raises(Exception):
        fory.register_type(Person)

    assert constructions == 1
    assert fory._root_started
    assert not fory._registrations
    assert fory._registration_fory is None
    with pytest.raises(RuntimeError):
        fory.register_type(Address)


def test_build_owner_precedes_pool():
    fory = None
    pooled = pyfory.Fory(xlang=False, compatible=False)

    def fory_factory():
        fory._return_fory(pooled)
        fory.serialize(None)
        return pyfory.Fory(xlang=False, compatible=False)

    fory = ThreadSafeFory(fory_factory=fory_factory)

    with pytest.raises(RuntimeError):
        fory.serialize(None)

    assert fory._pool == [pooled]


def test_callback_root_reentry():
    class AddressSerializer(pyfory.Serializer):
        def write(self, write_context, value):
            write_context.write_string(value.city)
            write_context.write_string(value.country)

        def read(self, read_context):
            return Address(read_context.read_string(), read_context.read_string())

    fory = pyfory.ThreadSafeFory(xlang=False, compatible=False)
    constructions = 0
    reenter_root = False

    def serializer_factory(type_resolver, cls):
        nonlocal constructions, reenter_root
        constructions += 1
        if reenter_root:
            fory.serialize(None)
        return AddressSerializer(type_resolver, cls)

    fory.register_type(Address, type_id=100, serializer=serializer_factory)
    initial_constructions = constructions
    with pytest.raises(TypeError):
        fory.register_type(Person, type_id=100)

    reenter_root = True
    with pytest.raises(RuntimeError):
        fory.serialize(None)

    assert constructions == initial_constructions + 1
    assert fory._root_started
