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

from __future__ import annotations

import os
from pathlib import Path

import addressbook
import complex_fbs
import monster
import numpy as np
import pyfory


def build_address_book() -> "addressbook.AddressBook":
    mobile = addressbook.Person.PhoneNumber(
        number="555-0100",
        phone_type=addressbook.Person.PhoneType.MOBILE,
    )
    work = addressbook.Person.PhoneNumber(
        number="555-0111",
        phone_type=addressbook.Person.PhoneType.WORK,
    )

    person = addressbook.Person(
        name="Alice",
        id=123,
        email="alice@example.com",
        tags=["friend", "colleague"],
        scores={"math": 100, "science": 98},
        salary=120000.5,
        phones=[mobile, work],
    )

    return addressbook.AddressBook(
        people=[person],
        people_by_name={person.name: person},
    )


def local_roundtrip(fory: pyfory.Fory, book: "addressbook.AddressBook") -> None:
    data = fory.serialize(book)
    decoded = fory.deserialize(data)
    assert isinstance(decoded, addressbook.AddressBook)
    assert decoded == book


def file_roundtrip(fory: pyfory.Fory, book: "addressbook.AddressBook") -> None:
    data_file = os.environ.get("DATA_FILE")
    if not data_file:
        return
    payload = Path(data_file).read_bytes()
    decoded = fory.deserialize(payload)
    assert isinstance(decoded, addressbook.AddressBook)
    assert decoded == book
    Path(data_file).write_bytes(fory.serialize(decoded))


def build_primitive_types() -> "addressbook.PrimitiveTypes":
    return addressbook.PrimitiveTypes(
        bool_value=True,
        int8_value=12,
        int16_value=1234,
        int32_value=-123456,
        varint32_value=-12345,
        int64_value=-123456789,
        varint64_value=-987654321,
        tagged_int64_value=123456789,
        uint8_value=200,
        uint16_value=60000,
        uint32_value=1234567890,
        var_uint32_value=1234567890,
        uint64_value=9876543210,
        var_uint64_value=12345678901,
        tagged_uint64_value=2222222222,
        float16_value=1.5,
        float32_value=2.5,
        float64_value=3.5,
    )


def build_monster() -> "monster.Monster":
    pos = monster.Vec3(x=1.0, y=2.0, z=3.0)
    return monster.Monster(
        pos=pos,
        mana=200,
        hp=80,
        name="Orc",
        friendly=True,
        inventory=np.array([1, 2, 3], dtype=np.uint8),
        color=monster.Color.Blue,
    )


def assert_monster_equal(decoded: "monster.Monster", expected: "monster.Monster") -> None:
    assert decoded.pos == expected.pos
    assert decoded.mana == expected.mana
    assert decoded.hp == expected.hp
    assert decoded.name == expected.name
    assert decoded.friendly == expected.friendly
    np.testing.assert_array_equal(decoded.inventory, expected.inventory)
    assert decoded.color == expected.color


def local_roundtrip_monster(
    fory: pyfory.Fory, monster_value: "monster.Monster"
) -> None:
    data = fory.serialize(monster_value)
    decoded = fory.deserialize(data)
    assert isinstance(decoded, monster.Monster)
    assert_monster_equal(decoded, monster_value)


def file_roundtrip_monster(
    fory: pyfory.Fory, monster_value: "monster.Monster"
) -> None:
    data_file = os.environ.get("DATA_FILE_FLATBUFFERS_MONSTER")
    if not data_file:
        return
    payload = Path(data_file).read_bytes()
    decoded = fory.deserialize(payload)
    assert isinstance(decoded, monster.Monster)
    assert_monster_equal(decoded, monster_value)
    Path(data_file).write_bytes(fory.serialize(decoded))


def build_container() -> "complex_fbs.Container":
    scalars = complex_fbs.ScalarPack(
        b=-8,
        ub=200,
        s=-1234,
        us=40000,
        i=-123456,
        ui=123456,
        l=-123456789,
        ul=987654321,
        f=1.5,
        d=2.5,
        ok=True,
    )
    return complex_fbs.Container(
        id=9876543210,
        status=complex_fbs.Status.STARTED,
        bytes=np.array([1, 2, 3], dtype=np.int8),
        numbers=np.array([10, 20, 30], dtype=np.int32),
        scalars=scalars,
        names=["alpha", "beta"],
        flags=np.array([True, False], dtype=np.bool_),
    )


def assert_container_equal(
    decoded: "complex_fbs.Container", expected: "complex_fbs.Container"
) -> None:
    assert decoded.id == expected.id
    assert decoded.status == expected.status
    np.testing.assert_array_equal(decoded.bytes, expected.bytes)
    np.testing.assert_array_equal(decoded.numbers, expected.numbers)
    assert decoded.scalars == expected.scalars
    assert decoded.names == expected.names
    np.testing.assert_array_equal(decoded.flags, expected.flags)


def local_roundtrip_container(
    fory: pyfory.Fory, container: "complex_fbs.Container"
) -> None:
    data = fory.serialize(container)
    decoded = fory.deserialize(data)
    assert isinstance(decoded, complex_fbs.Container)
    assert_container_equal(decoded, container)


def file_roundtrip_container(
    fory: pyfory.Fory, container: "complex_fbs.Container"
) -> None:
    data_file = os.environ.get("DATA_FILE_FLATBUFFERS_TEST2")
    if not data_file:
        return
    payload = Path(data_file).read_bytes()
    decoded = fory.deserialize(payload)
    assert isinstance(decoded, complex_fbs.Container)
    assert_container_equal(decoded, container)
    Path(data_file).write_bytes(fory.serialize(decoded))


def local_roundtrip_primitives(
    fory: pyfory.Fory, types: "addressbook.PrimitiveTypes"
) -> None:
    data = fory.serialize(types)
    decoded = fory.deserialize(data)
    assert isinstance(decoded, addressbook.PrimitiveTypes)
    assert decoded == types


def file_roundtrip_primitives(
    fory: pyfory.Fory, types: "addressbook.PrimitiveTypes"
) -> None:
    data_file = os.environ.get("DATA_FILE_PRIMITIVES")
    if not data_file:
        return
    payload = Path(data_file).read_bytes()
    decoded = fory.deserialize(payload)
    assert isinstance(decoded, addressbook.PrimitiveTypes)
    assert decoded == types
    Path(data_file).write_bytes(fory.serialize(decoded))


def main() -> int:
    fory = pyfory.Fory(xlang=True)
    addressbook.register_addressbook_types(fory)
    monster.register_monster_types(fory)
    complex_fbs.register_complex_fbs_types(fory)

    book = build_address_book()
    local_roundtrip(fory, book)
    file_roundtrip(fory, book)

    primitives = build_primitive_types()
    local_roundtrip_primitives(fory, primitives)
    file_roundtrip_primitives(fory, primitives)

    monster_value = build_monster()
    local_roundtrip_monster(fory, monster_value)
    file_roundtrip_monster(fory, monster_value)

    container = build_container()
    local_roundtrip_container(fory, container)
    file_roundtrip_container(fory, container)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
