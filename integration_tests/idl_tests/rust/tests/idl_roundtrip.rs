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
use std::sync::Arc;
use std::{env, fs};

use chrono::NaiveDate;
use fory::{ArcWeak, Fory, RcWeak};
use idl_tests::addressbook::{
    self,
    person::{PhoneNumber, PhoneType},
    AddressBook, Animal, Cat, Dog, Person,
};
use idl_tests::complex_fbs::{self, Container, Note, Payload, ScalarPack, Status};
use idl_tests::monster::{self, Color, Monster, Vec3};
use idl_tests::optional_types::{self, AllOptionalTypes, OptionalHolder, OptionalUnion};
use idl_tests::ref_tests;

fn build_address_book() -> AddressBook {
    let mobile = PhoneNumber {
        number: "555-0100".to_string(),
        phone_type: PhoneType::Mobile,
    };
    let work = PhoneNumber {
        number: "555-0111".to_string(),
        phone_type: PhoneType::Work,
    };

    let mut pet = Animal::Dog(Dog {
        name: "Rex".to_string(),
        bark_volume: 5,
    });
    pet = Animal::Cat(Cat {
        name: "Mimi".to_string(),
        lives: 9,
    });

    let person = Person {
        name: "Alice".to_string(),
        id: 123,
        email: "alice@example.com".to_string(),
        tags: vec!["friend".to_string(), "colleague".to_string()],
        scores: HashMap::from([("math".to_string(), 100), ("science".to_string(), 98)]),
        salary: 120000.5,
        phones: vec![mobile, work],
        pet,
    };

    AddressBook {
        people: vec![person.clone()],
        people_by_name: HashMap::from([(person.name.clone(), person)]),
    }
}

fn build_primitive_types() -> addressbook::PrimitiveTypes {
    let mut contact =
        addressbook::primitive_types::Contact::Email("alice@example.com".to_string());
    contact = addressbook::primitive_types::Contact::Phone(12345);

    addressbook::PrimitiveTypes {
        bool_value: true,
        int8_value: 12,
        int16_value: 1234,
        int32_value: -123456,
        varint32_value: -12345,
        int64_value: -123456789,
        varint64_value: -987654321,
        tagged_int64_value: 123456789,
        uint8_value: 200,
        uint16_value: 60000,
        uint32_value: 1234567890,
        var_uint32_value: 1234567890,
        uint64_value: 9876543210,
        var_uint64_value: 12345678901,
        tagged_uint64_value: 2222222222,
        float32_value: 2.5,
        float64_value: 3.5,
        contact: Some(contact),
    }
}

fn build_monster() -> Monster {
    let pos = Vec3 {
        x: 1.0,
        y: 2.0,
        z: 3.0,
    };
    Monster {
        pos: Some(pos),
        mana: 200,
        hp: 80,
        name: "Orc".to_string(),
        friendly: true,
        inventory: vec![1, 2, 3],
        color: Color::Blue,
    }
}

fn build_container() -> Container {
    let scalars = ScalarPack {
        b: -8,
        ub: 200,
        s: -1234,
        us: 40000,
        i: -123456,
        ui: 123456,
        l: -123456789,
        ul: 987654321,
        f: 1.5,
        d: 2.5,
        ok: true,
    };
    let mut payload = Payload::Note(Note {
        text: "alpha".to_string(),
    });
    payload = Payload::Metric(complex_fbs::Metric { value: 42.0 });

    Container {
        id: 9876543210,
        status: Status::Started,
        bytes: vec![1, 2, 3],
        numbers: vec![10, 20, 30],
        scalars: Some(scalars),
        names: vec!["alpha".to_string(), "beta".to_string()],
        flags: vec![true, false],
        payload,
    }
}

fn build_optional_holder() -> OptionalHolder {
    let all_types = AllOptionalTypes {
        bool_value: Some(true),
        int8_value: Some(12),
        int16_value: Some(1234),
        int32_value: Some(-123456),
        fixed_int32_value: Some(-123456),
        varint32_value: Some(-12345),
        int64_value: Some(-123456789),
        fixed_int64_value: Some(-123456789),
        varint64_value: Some(-987654321),
        tagged_int64_value: Some(123456789),
        uint8_value: Some(200),
        uint16_value: Some(60000),
        uint32_value: Some(1234567890),
        fixed_uint32_value: Some(1234567890),
        var_uint32_value: Some(1234567890),
        uint64_value: Some(9876543210),
        fixed_uint64_value: Some(9876543210),
        var_uint64_value: Some(12345678901),
        tagged_uint64_value: Some(2222222222),
        float32_value: Some(2.5),
        float64_value: Some(3.5),
        string_value: Some("optional".to_string()),
        bytes_value: Some(vec![1, 2, 3]),
        date_value: Some(NaiveDate::from_ymd_opt(2024, 1, 2).unwrap()),
        timestamp_value: Some(
            NaiveDate::from_ymd_opt(2024, 1, 2)
                .unwrap()
                .and_hms_opt(3, 4, 5)
                .expect("timestamp"),
        ),
        int32_list: Some(vec![1, 2, 3]),
        string_list: Some(vec!["alpha".to_string(), "beta".to_string()]),
        int64_map: Some(HashMap::from([("alpha".to_string(), 10), ("beta".to_string(), 20)])),
    };

    OptionalHolder {
        all_types: Some(all_types.clone()),
        choice: Some(OptionalUnion::Note("optional".to_string())),
    }
}

fn build_ref_suite() -> ref_tests::RefSuite {
    let item = Arc::new(ref_tests::Item {
        name: "shared".to_string(),
    });
    let shared = ref_tests::SharedHolder {
        first: Some(Arc::clone(&item)),
        second: Some(Arc::clone(&item)),
    };
    let repeated = ref_tests::RepeatedHolder {
        items: vec![Arc::clone(&item), Arc::clone(&item)],
    };

    let owner = Arc::new(ref_tests::Owner {
        name: "owner".to_string(),
    });
    let strong = ref_tests::StrongHolder {
        owner: Some(Arc::clone(&owner)),
    };
    let weak = ref_tests::WeakHolder {
        owner: Some(ArcWeak::from(&owner)),
        cache: Some(RcWeak::new()),
    };

    ref_tests::RefSuite {
        shared: Some(shared),
        repeated_holder: Some(repeated),
        strong: Some(strong),
        weak_holder: Some(weak),
    }
}

fn assert_ref_suite(suite: &ref_tests::RefSuite) {
    let shared = suite.shared.as_ref().expect("shared holder");
    let first = shared.first.as_ref().expect("shared first");
    let second = shared.second.as_ref().expect("shared second");
    assert!(Arc::ptr_eq(first, second));
    assert_eq!(first.name, "shared");

    let repeated = suite
        .repeated_holder
        .as_ref()
        .expect("repeated holder");
    assert_eq!(repeated.items.len(), 2);
    assert!(Arc::ptr_eq(&repeated.items[0], &repeated.items[1]));

    let strong = suite.strong.as_ref().expect("strong holder");
    let weak = suite.weak_holder.as_ref().expect("weak holder");
    let strong_owner = strong.owner.as_ref().expect("strong owner");
    let weak_owner = weak.owner.as_ref().expect("weak owner");
    let upgraded = weak_owner.upgrade().expect("weak owner upgrade");
    assert!(Arc::ptr_eq(&upgraded, strong_owner));
    if let Some(cache) = weak.cache.as_ref() {
        assert!(cache.upgrade().is_none());
    }
}

#[test]
fn test_address_book_roundtrip() {
    let mut fory = Fory::default().xlang(true);
    addressbook::register_types(&mut fory).expect("register types");
    monster::register_types(&mut fory).expect("register monster types");
    complex_fbs::register_types(&mut fory).expect("register flatbuffers types");
    optional_types::register_types(&mut fory).expect("register optional types");

    let book = build_address_book();
    let bytes = fory.serialize(&book).expect("serialize");
    let roundtrip: AddressBook = fory.deserialize(&bytes).expect("deserialize");

    assert_eq!(book, roundtrip);

    let data_file = match env::var("DATA_FILE") {
        Ok(path) => path,
        Err(_) => return,
    };
    let payload = fs::read(&data_file).expect("read data file");
    let peer_book: AddressBook = fory
        .deserialize(&payload)
        .expect("deserialize peer payload");
    assert_eq!(book, peer_book);
    let encoded = fory.serialize(&peer_book).expect("serialize peer payload");
    fs::write(data_file, encoded).expect("write data file");

    let types = build_primitive_types();
    let bytes = fory.serialize(&types).expect("serialize");
    let roundtrip: addressbook::PrimitiveTypes = fory.deserialize(&bytes).expect("deserialize");
    assert_eq!(types, roundtrip);

    let primitive_file = match env::var("DATA_FILE_PRIMITIVES") {
        Ok(path) => path,
        Err(_) => return,
    };
    let payload = fs::read(&primitive_file).expect("read data file");
    let peer_types: addressbook::PrimitiveTypes = fory
        .deserialize(&payload)
        .expect("deserialize peer payload");
    assert_eq!(types, peer_types);
    let encoded = fory.serialize(&peer_types).expect("serialize peer payload");
    fs::write(primitive_file, encoded).expect("write data file");

    let monster = build_monster();
    let bytes = fory.serialize(&monster).expect("serialize");
    let roundtrip: Monster = fory.deserialize(&bytes).expect("deserialize");
    assert_eq!(monster, roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_FLATBUFFERS_MONSTER") {
        let payload = fs::read(&data_file).expect("read data file");
        let peer_monster: Monster = fory
            .deserialize(&payload)
            .expect("deserialize peer payload");
        assert_eq!(monster, peer_monster);
        let encoded = fory
            .serialize(&peer_monster)
            .expect("serialize peer payload");
        fs::write(data_file, encoded).expect("write data file");
    }

    let container = build_container();
    let bytes = fory.serialize(&container).expect("serialize");
    let roundtrip: Container = fory.deserialize(&bytes).expect("deserialize");
    assert_eq!(container, roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_FLATBUFFERS_TEST2") {
        let payload = fs::read(&data_file).expect("read data file");
        let peer_container: Container = fory
            .deserialize(&payload)
            .expect("deserialize peer payload");
        assert_eq!(container, peer_container);
        let encoded = fory
            .serialize(&peer_container)
            .expect("serialize peer payload");
        fs::write(data_file, encoded).expect("write data file");
    }

    let holder = build_optional_holder();
    let bytes = fory.serialize(&holder).expect("serialize");
    let roundtrip: OptionalHolder = fory.deserialize(&bytes).expect("deserialize");
    assert_eq!(holder, roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_OPTIONAL_TYPES") {
        let payload = fs::read(&data_file).expect("read data file");
        let peer_holder: OptionalHolder = fory
            .deserialize(&payload)
            .expect("deserialize peer payload");
        assert_eq!(holder, peer_holder);
        let encoded = fory
            .serialize(&peer_holder)
            .expect("serialize peer payload");
        fs::write(data_file, encoded).expect("write data file");
    }

    let mut ref_fory = Fory::default().xlang(true).track_ref(true);
    ref_tests::register_types(&mut ref_fory).expect("register ref types");
    let suite = build_ref_suite();
    let bytes = ref_fory.serialize(&suite).expect("serialize ref suite");
    let roundtrip: ref_tests::RefSuite = ref_fory.deserialize(&bytes).expect("deserialize");
    assert_ref_suite(&roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_REF") {
        let payload = fs::read(&data_file).expect("read ref data file");
        let peer_suite: ref_tests::RefSuite = ref_fory
            .deserialize(&payload)
            .expect("deserialize peer ref payload");
        assert_ref_suite(&peer_suite);
        let encoded = ref_fory
            .serialize(&peer_suite)
            .expect("serialize peer ref payload");
        fs::write(data_file, encoded).expect("write ref data file");
    }
}
