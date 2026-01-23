/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <map>
#include <string>
#include <vector>

#include "addressbook.h"
#include "complex_fbs.h"
#include "fory/serialization/fory.h"
#include "monster.h"

namespace {

fory::Result<std::vector<uint8_t>, fory::Error>
ReadFile(const std::string &path) {
  std::ifstream input(path, std::ios::binary);
  if (FORY_PREDICT_FALSE(!input)) {
    return fory::Unexpected(
        fory::Error::invalid("failed to open data file for reading"));
  }
  std::vector<uint8_t> data((std::istreambuf_iterator<char>(input)),
                            std::istreambuf_iterator<char>());
  return data;
}

fory::Result<void, fory::Error> WriteFile(const std::string &path,
                                          const std::vector<uint8_t> &data) {
  std::ofstream output(path, std::ios::binary | std::ios::trunc);
  if (FORY_PREDICT_FALSE(!output)) {
    return fory::Unexpected(
        fory::Error::invalid("failed to open data file for writing"));
  }
  output.write(reinterpret_cast<const char *>(data.data()),
               static_cast<std::streamsize>(data.size()));
  if (FORY_PREDICT_FALSE(!output)) {
    return fory::Unexpected(fory::Error::invalid("failed to write data file"));
  }
  return fory::Result<void, fory::Error>();
}

fory::Result<void, fory::Error> RunRoundTrip() {
  auto fory = fory::serialization::Fory::builder()
                  .xlang(true)
                  .check_struct_version(true)
                  .track_ref(false)
                  .build();

  addressbook::RegisterTypes(fory);
  monster::RegisterTypes(fory);
  complex_fbs::RegisterTypes(fory);

  addressbook::Person::PhoneNumber mobile;
  mobile.number = "555-0100";
  mobile.phone_type = addressbook::Person::PhoneType::MOBILE;

  addressbook::Person::PhoneNumber work;
  work.number = "555-0111";
  work.phone_type = addressbook::Person::PhoneType::WORK;

  addressbook::Person person;
  person.name = "Alice";
  person.id = 123;
  person.email = "alice@example.com";
  person.tags = {"friend", "colleague"};
  person.scores = {{"math", 100}, {"science", 98}};
  person.salary = 120000.5;
  person.phones = {mobile, work};
  addressbook::Dog dog;
  dog.name = "Rex";
  dog.bark_volume = 5;
  person.pet = addressbook::Animal::dog(dog);
  addressbook::Cat cat;
  cat.name = "Mimi";
  cat.lives = 9;
  person.pet = addressbook::Animal::cat(cat);

  addressbook::AddressBook book;
  book.people = {person};
  book.people_by_name = {{person.name, person}};

  FORY_TRY(bytes, fory.serialize(book));
  FORY_TRY(roundtrip, fory.deserialize<addressbook::AddressBook>(bytes.data(),
                                                                 bytes.size()));

  if (!(roundtrip == book)) {
    return fory::Unexpected(
        fory::Error::invalid("addressbook roundtrip mismatch"));
  }

  addressbook::PrimitiveTypes types;
  types.bool_value = true;
  types.int8_value = 12;
  types.int16_value = 1234;
  types.int32_value = -123456;
  types.varint32_value = -12345;
  types.int64_value = -123456789;
  types.varint64_value = -987654321;
  types.tagged_int64_value = 123456789;
  types.uint8_value = 200;
  types.uint16_value = 60000;
  types.uint32_value = 1234567890;
  types.var_uint32_value = 1234567890;
  types.uint64_value = 9876543210ULL;
  types.var_uint64_value = 12345678901ULL;
  types.tagged_uint64_value = 2222222222ULL;
  types.float16_value = 1.5F;
  types.float32_value = 2.5F;
  types.float64_value = 3.5;
  types.contact = addressbook::PrimitiveTypes::Contact::email(
      std::string("alice@example.com"));
  types.contact = addressbook::PrimitiveTypes::Contact::phone(12345);

  FORY_TRY(primitive_bytes, fory.serialize(types));
  FORY_TRY(primitive_roundtrip,
           fory.deserialize<addressbook::PrimitiveTypes>(
               primitive_bytes.data(), primitive_bytes.size()));

  if (!(primitive_roundtrip == types)) {
    return fory::Unexpected(
        fory::Error::invalid("primitive types roundtrip mismatch"));
  }

  monster::Vec3 pos;
  pos.x = 1.0F;
  pos.y = 2.0F;
  pos.z = 3.0F;

  monster::Monster monster_value;
  monster_value.pos = pos;
  monster_value.mana = 200;
  monster_value.hp = 80;
  monster_value.name = "Orc";
  monster_value.friendly = true;
  monster_value.inventory = {static_cast<uint8_t>(1), static_cast<uint8_t>(2),
                             static_cast<uint8_t>(3)};
  monster_value.color = monster::Color::Blue;

  FORY_TRY(monster_bytes, fory.serialize(monster_value));
  FORY_TRY(monster_roundtrip, fory.deserialize<monster::Monster>(
                                  monster_bytes.data(), monster_bytes.size()));

  if (!(monster_roundtrip == monster_value)) {
    return fory::Unexpected(
        fory::Error::invalid("flatbuffers monster roundtrip mismatch"));
  }

  complex_fbs::ScalarPack scalars;
  scalars.b = -8;
  scalars.ub = 200;
  scalars.s = -1234;
  scalars.us = 40000;
  scalars.i = -123456;
  scalars.ui = 123456;
  scalars.l = -123456789;
  scalars.ul = 987654321;
  scalars.f = 1.5F;
  scalars.d = 2.5;
  scalars.ok = true;

  complex_fbs::Container container;
  container.id = 9876543210ULL;
  container.status = complex_fbs::Status::STARTED;
  container.bytes = {static_cast<int8_t>(1), static_cast<int8_t>(2),
                     static_cast<int8_t>(3)};
  container.numbers = {10, 20, 30};
  container.scalars = scalars;
  container.names = {"alpha", "beta"};
  container.flags = {true, false};
  complex_fbs::Note note;
  note.text = "alpha";
  container.payload = complex_fbs::Payload::note(note);
  complex_fbs::Metric metric;
  metric.value = 42.0;
  container.payload = complex_fbs::Payload::metric(metric);

  FORY_TRY(container_bytes, fory.serialize(container));
  FORY_TRY(container_roundtrip,
           fory.deserialize<complex_fbs::Container>(container_bytes.data(),
                                                    container_bytes.size()));

  if (!(container_roundtrip == container)) {
    return fory::Unexpected(
        fory::Error::invalid("flatbuffers container roundtrip mismatch"));
  }

  const char *data_file = std::getenv("DATA_FILE");
  if (data_file != nullptr && data_file[0] != '\0') {
    FORY_TRY(payload, ReadFile(data_file));
    FORY_TRY(peer_book, fory.deserialize<addressbook::AddressBook>(
                            payload.data(), payload.size()));
    if (!(peer_book == book)) {
      return fory::Unexpected(fory::Error::invalid("peer payload mismatch"));
    }
    FORY_TRY(peer_bytes, fory.serialize(peer_book));
    FORY_RETURN_IF_ERROR(WriteFile(data_file, peer_bytes));
  }

  const char *primitive_file = std::getenv("DATA_FILE_PRIMITIVES");
  if (primitive_file != nullptr && primitive_file[0] != '\0') {
    FORY_TRY(payload, ReadFile(primitive_file));
    FORY_TRY(peer_types, fory.deserialize<addressbook::PrimitiveTypes>(
                             payload.data(), payload.size()));
    if (!(peer_types == types)) {
      return fory::Unexpected(
          fory::Error::invalid("peer primitive payload mismatch"));
    }
    FORY_TRY(peer_bytes, fory.serialize(peer_types));
    FORY_RETURN_IF_ERROR(WriteFile(primitive_file, peer_bytes));
  }

  const char *monster_file = std::getenv("DATA_FILE_FLATBUFFERS_MONSTER");
  if (monster_file != nullptr && monster_file[0] != '\0') {
    FORY_TRY(payload, ReadFile(monster_file));
    FORY_TRY(peer_monster, fory.deserialize<monster::Monster>(payload.data(),
                                                              payload.size()));
    if (!(peer_monster == monster_value)) {
      return fory::Unexpected(
          fory::Error::invalid("peer monster payload mismatch"));
    }
    FORY_TRY(peer_bytes, fory.serialize(peer_monster));
    FORY_RETURN_IF_ERROR(WriteFile(monster_file, peer_bytes));
  }

  const char *container_file = std::getenv("DATA_FILE_FLATBUFFERS_TEST2");
  if (container_file != nullptr && container_file[0] != '\0') {
    FORY_TRY(payload, ReadFile(container_file));
    FORY_TRY(peer_container, fory.deserialize<complex_fbs::Container>(
                                 payload.data(), payload.size()));
    if (!(peer_container == container)) {
      return fory::Unexpected(
          fory::Error::invalid("peer container payload mismatch"));
    }
    FORY_TRY(peer_bytes, fory.serialize(peer_container));
    FORY_RETURN_IF_ERROR(WriteFile(container_file, peer_bytes));
  }

  return fory::Result<void, fory::Error>();
}

} // namespace

int main() {
  auto result = RunRoundTrip();
  if (!result.ok()) {
    std::cerr << "IDL roundtrip failed: " << result.error().message()
              << std::endl;
    return 1;
  }
  return 0;
}
