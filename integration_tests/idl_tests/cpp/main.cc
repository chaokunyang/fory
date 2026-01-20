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

#include <iostream>
#include <map>
#include <string>
#include <vector>

#include "addressbook.h"
#include "fory/serialization/fory.h"

namespace {

fory::Result<void, fory::Error> RunRoundTrip() {
  auto fory =
      fory::serialization::Fory::builder().xlang(true).track_ref(false).build();

  addressbook::RegisterTypes(fory);

  addressbook::Person_PhoneNumber mobile;
  mobile.number = "555-0100";
  mobile.phone_type = addressbook::Person_PhoneType::MOBILE;

  addressbook::Person_PhoneNumber work;
  work.number = "555-0111";
  work.phone_type = addressbook::Person_PhoneType::WORK;

  addressbook::Person person;
  person.name = "Alice";
  person.id = 123;
  person.email = "alice@example.com";
  person.tags = {"friend", "colleague"};
  person.scores = {{"math", 100}, {"science", 98}};
  person.salary = 120000.5;
  person.phones = {mobile, work};

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
