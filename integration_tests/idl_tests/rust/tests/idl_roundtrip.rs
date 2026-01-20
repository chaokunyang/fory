// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

use std::collections::HashMap;
use std::{env, fs};

use fory::Fory;
use idl_tests::addressbook::{
    self, AddressBook, Person, Person_PhoneNumber, Person_PhoneType,
};

fn build_address_book() -> AddressBook {
    let mobile = Person_PhoneNumber {
        number: "555-0100".to_string(),
        phone_type: Person_PhoneType::Mobile,
    };
    let work = Person_PhoneNumber {
        number: "555-0111".to_string(),
        phone_type: Person_PhoneType::Work,
    };

    let person = Person {
        name: "Alice".to_string(),
        id: 123,
        email: "alice@example.com".to_string(),
        tags: vec!["friend".to_string(), "colleague".to_string()],
        scores: HashMap::from([("math".to_string(), 100), ("science".to_string(), 98)]),
        salary: 120000.5,
        phones: vec![mobile, work],
    };

    AddressBook {
        people: vec![person.clone()],
        people_by_name: HashMap::from([(person.name.clone(), person)]),
    }
}

#[test]
fn test_address_book_roundtrip() {
    let mut fory = Fory::default().xlang(true);
    addressbook::register_types(&mut fory).expect("register types");

    let book = build_address_book();
    let bytes = fory.serialize(&book).expect("serialize");
    let roundtrip: AddressBook = fory.deserialize(&bytes).expect("deserialize");

    assert_eq!(book, roundtrip);

    let data_file = match env::var("DATA_FILE") {
        Ok(path) => path,
        Err(_) => return,
    };
    let payload = fs::read(&data_file).expect("read data file");
    let peer_book: AddressBook = fory.deserialize(&payload).expect("deserialize peer payload");
    assert_eq!(book, peer_book);
    let encoded = fory.serialize(&peer_book).expect("serialize peer payload");
    fs::write(data_file, encoded).expect("write data file");
}
