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

use chrono::{Duration, NaiveDate};
use fory::{
    ArcWeak, BFloat16, Decimal, Float16, Fory, ForyDefault, ForyObject, Serializer,
};
use fory_core::StructSerializer;
use idl_tests::generated::addressbook::{
    self,
    person::{PhoneNumber, PhoneType},
    AddressBook, Animal, Cat, Dog, Person,
};
use idl_tests::generated::any_example::{self, AnyHolder, AnyInner, AnyUnion};
use idl_tests::generated::auto_id;
use idl_tests::generated::collection::{
    self, NumericCollectionArrayUnion, NumericCollectionUnion, NumericCollections,
    NumericCollectionsArray,
};
use idl_tests::generated::complex_fbs::{self, Container, Note, Payload, ScalarPack, Status};
use idl_tests::generated::complex_pb::{self, PrimitiveTypes};
use idl_tests::generated::evolving1;
use idl_tests::generated::evolving2;
use idl_tests::generated::example::{self, ExampleMessage, ExampleMessageUnion};
use idl_tests::generated::example_common::{self, ExampleLeaf, ExampleLeafUnion, ExampleState};
use idl_tests::generated::monster::{self, Color, Monster, Vec3};
use idl_tests::generated::optional_types::{self, AllOptionalTypes, OptionalHolder, OptionalUnion};
use idl_tests::generated::root;
use idl_tests::generated::{graph, tree};
use num_bigint::BigInt;

const EXAMPLE_MESSAGE_TYPE_ID: u32 = 1500;

macro_rules! define_example_schema_variant_structs {
    ($( $(#[$attr:meta])* $name:ident { $field:ident : $ty:ty }; )*) => {
        $(
            #[derive(ForyObject, Debug, PartialEq, Clone, Default)]
            struct $name {
                $(#[$attr])*
                $field: $ty,
            }
        )*
    };
}

macro_rules! assert_example_schema_variant_structs {
    ($payload:expr, $expected:expr, $( $(#[$attr:meta])* $name:ident { $field:ident : $ty:ty }; )*) => {
        $(
            assert_example_schema_variant_decode(
                $payload,
                $name {
                    $field: $expected.$field.clone(),
                },
            );
        )*
    };
}

macro_rules! for_each_example_schema_variant {
    ($macro:ident) => {
        $macro! {
            #[fory(id = 1)] ExampleFieldBoolValue { bool_value: bool };
            #[fory(id = 2)] ExampleFieldInt8Value { int8_value: i8 };
            #[fory(id = 3)] ExampleFieldInt16Value { int16_value: i16 };
            #[fory(id = 4, encoding = "fixed")] ExampleFieldFixedInt32Value { fixed_int32_value: i32 };
            #[fory(id = 5)] ExampleFieldVarint32Value { varint32_value: i32 };
            #[fory(id = 6, encoding = "fixed")] ExampleFieldFixedInt64Value { fixed_int64_value: i64 };
            #[fory(id = 7)] ExampleFieldVarint64Value { varint64_value: i64 };
            #[fory(id = 8, encoding = "tagged")] ExampleFieldTaggedInt64Value { tagged_int64_value: i64 };
            #[fory(id = 9)] ExampleFieldUint8Value { uint8_value: u8 };
            #[fory(id = 10)] ExampleFieldUint16Value { uint16_value: u16 };
            #[fory(id = 11, encoding = "fixed")] ExampleFieldFixedUint32Value { fixed_uint32_value: u32 };
            #[fory(id = 12)] ExampleFieldVarUint32Value { var_uint32_value: u32 };
            #[fory(id = 13, encoding = "fixed")] ExampleFieldFixedUint64Value { fixed_uint64_value: u64 };
            #[fory(id = 14)] ExampleFieldVarUint64Value { var_uint64_value: u64 };
            #[fory(id = 15, encoding = "tagged")] ExampleFieldTaggedUint64Value { tagged_uint64_value: u64 };
            #[fory(id = 16)] ExampleFieldFloat16Value { float16_value: fory::Float16 };
            #[fory(id = 17)] ExampleFieldBfloat16Value { bfloat16_value: fory::BFloat16 };
            #[fory(id = 18)] ExampleFieldFloat32Value { float32_value: f32 };
            #[fory(id = 19)] ExampleFieldFloat64Value { float64_value: f64 };
            #[fory(id = 20)] ExampleFieldStringValue { string_value: String };
            #[fory(id = 21)] ExampleFieldBytesValue { bytes_value: Vec<u8> };
            #[fory(id = 22)] ExampleFieldDateValue { date_value: chrono::NaiveDate };
            #[fory(id = 23)] ExampleFieldTimestampValue { timestamp_value: chrono::NaiveDateTime };
            #[fory(id = 24)] ExampleFieldDurationValue { duration_value: chrono::Duration };
            #[fory(id = 25)] ExampleFieldDecimalValue { decimal_value: fory::Decimal };
            #[fory(id = 26)] ExampleFieldEnumValue { enum_value: crate::example_common::ExampleState };
            #[fory(id = 27, nullable = true)] ExampleFieldMessageValue { message_value: Option<crate::example_common::ExampleLeaf> };
            #[fory(id = 28, type_id = "union")] ExampleFieldUnionValue { union_value: crate::example_common::ExampleLeafUnion };
            #[fory(id = 101)] ExampleFieldBoolList { bool_list: Vec<bool> };
            #[fory(id = 102, type_id = "int8_array")] ExampleFieldInt8List { int8_list: Vec<i8> };
            #[fory(id = 103)] ExampleFieldInt16List { int16_list: Vec<i16> };
            #[fory(id = 104)] ExampleFieldFixedInt32List { fixed_int32_list: Vec<i32> };
            #[fory(id = 105)] ExampleFieldVarint32List { varint32_list: Vec<i32> };
            #[fory(id = 106)] ExampleFieldFixedInt64List { fixed_int64_list: Vec<i64> };
            #[fory(id = 107)] ExampleFieldVarint64List { varint64_list: Vec<i64> };
            #[fory(id = 108)] ExampleFieldTaggedInt64List { tagged_int64_list: Vec<i64> };
            #[fory(id = 109, type_id = "uint8_array")] ExampleFieldUint8List { uint8_list: Vec<u8> };
            #[fory(id = 110)] ExampleFieldUint16List { uint16_list: Vec<u16> };
            #[fory(id = 111)] ExampleFieldFixedUint32List { fixed_uint32_list: Vec<u32> };
            #[fory(id = 112)] ExampleFieldVarUint32List { var_uint32_list: Vec<u32> };
            #[fory(id = 113)] ExampleFieldFixedUint64List { fixed_uint64_list: Vec<u64> };
            #[fory(id = 114)] ExampleFieldVarUint64List { var_uint64_list: Vec<u64> };
            #[fory(id = 115)] ExampleFieldTaggedUint64List { tagged_uint64_list: Vec<u64> };
            #[fory(id = 116)] ExampleFieldFloat16List { float16_list: Vec<fory::Float16> };
            #[fory(id = 117)] ExampleFieldBfloat16List { bfloat16_list: Vec<fory::BFloat16> };
            #[fory(id = 118)] ExampleFieldMaybeFloat16List { maybe_float16_list: Vec<Option<fory::Float16>> };
            #[fory(id = 119)] ExampleFieldMaybeBfloat16List { maybe_bfloat16_list: Vec<Option<fory::BFloat16>> };
            #[fory(id = 120)] ExampleFieldFloat32List { float32_list: Vec<f32> };
            #[fory(id = 121)] ExampleFieldFloat64List { float64_list: Vec<f64> };
            #[fory(id = 122)] ExampleFieldStringList { string_list: Vec<String> };
            #[fory(id = 123)] ExampleFieldBytesList { bytes_list: Vec<Vec<u8>> };
            #[fory(id = 124)] ExampleFieldDateList { date_list: Vec<chrono::NaiveDate> };
            #[fory(id = 125)] ExampleFieldTimestampList { timestamp_list: Vec<chrono::NaiveDateTime> };
            #[fory(id = 126)] ExampleFieldDurationList { duration_list: Vec<chrono::Duration> };
            #[fory(id = 127)] ExampleFieldDecimalList { decimal_list: Vec<fory::Decimal> };
            #[fory(id = 128)] ExampleFieldEnumList { enum_list: Vec<crate::example_common::ExampleState> };
            #[fory(id = 129)] ExampleFieldMessageList { message_list: Vec<crate::example_common::ExampleLeaf> };
            #[fory(id = 130)] ExampleFieldUnionList { union_list: Vec<crate::example_common::ExampleLeafUnion> };
            #[fory(id = 201)] ExampleFieldStringValuesByBool { string_values_by_bool: HashMap<bool, String> };
            #[fory(id = 202)] ExampleFieldStringValuesByInt8 { string_values_by_int8: HashMap<i8, String> };
            #[fory(id = 203)] ExampleFieldStringValuesByInt16 { string_values_by_int16: HashMap<i16, String> };
            #[fory(id = 204)] ExampleFieldStringValuesByFixedInt32 { string_values_by_fixed_int32: HashMap<i32, String> };
            #[fory(id = 205)] ExampleFieldStringValuesByVarint32 { string_values_by_varint32: HashMap<i32, String> };
            #[fory(id = 206)] ExampleFieldStringValuesByFixedInt64 { string_values_by_fixed_int64: HashMap<i64, String> };
            #[fory(id = 207)] ExampleFieldStringValuesByVarint64 { string_values_by_varint64: HashMap<i64, String> };
            #[fory(id = 208)] ExampleFieldStringValuesByTaggedInt64 { string_values_by_tagged_int64: HashMap<i64, String> };
            #[fory(id = 209)] ExampleFieldStringValuesByUint8 { string_values_by_uint8: HashMap<u8, String> };
            #[fory(id = 210)] ExampleFieldStringValuesByUint16 { string_values_by_uint16: HashMap<u16, String> };
            #[fory(id = 211)] ExampleFieldStringValuesByFixedUint32 { string_values_by_fixed_uint32: HashMap<u32, String> };
            #[fory(id = 212)] ExampleFieldStringValuesByVarUint32 { string_values_by_var_uint32: HashMap<u32, String> };
            #[fory(id = 213)] ExampleFieldStringValuesByFixedUint64 { string_values_by_fixed_uint64: HashMap<u64, String> };
            #[fory(id = 214)] ExampleFieldStringValuesByVarUint64 { string_values_by_var_uint64: HashMap<u64, String> };
            #[fory(id = 215)] ExampleFieldStringValuesByTaggedUint64 { string_values_by_tagged_uint64: HashMap<u64, String> };
            #[fory(id = 218)] ExampleFieldStringValuesByString { string_values_by_string: HashMap<String, String> };
            #[fory(id = 219)] ExampleFieldStringValuesByTimestamp { string_values_by_timestamp: HashMap<chrono::NaiveDateTime, String> };
            #[fory(id = 220)] ExampleFieldStringValuesByDuration { string_values_by_duration: HashMap<chrono::Duration, String> };
            #[fory(id = 221)] ExampleFieldStringValuesByEnum { string_values_by_enum: HashMap<crate::example_common::ExampleState, String> };
            #[fory(id = 222)] ExampleFieldFloat16ValuesByName { float16_values_by_name: HashMap<String, fory::Float16> };
            #[fory(id = 223)] ExampleFieldMaybeFloat16ValuesByName { maybe_float16_values_by_name: HashMap<String, Option<fory::Float16>> };
            #[fory(id = 224)] ExampleFieldBfloat16ValuesByName { bfloat16_values_by_name: HashMap<String, fory::BFloat16> };
            #[fory(id = 225)] ExampleFieldMaybeBfloat16ValuesByName { maybe_bfloat16_values_by_name: HashMap<String, Option<fory::BFloat16>> };
            #[fory(id = 226)] ExampleFieldBytesValuesByName { bytes_values_by_name: HashMap<String, Vec<u8>> };
            #[fory(id = 227)] ExampleFieldDateValuesByName { date_values_by_name: HashMap<String, chrono::NaiveDate> };
            #[fory(id = 228)] ExampleFieldDecimalValuesByName { decimal_values_by_name: HashMap<String, fory::Decimal> };
            #[fory(id = 229)] ExampleFieldMessageValuesByName { message_values_by_name: HashMap<String, crate::example_common::ExampleLeaf> };
            #[fory(id = 230)] ExampleFieldUnionValuesByName { union_values_by_name: HashMap<String, crate::example_common::ExampleLeafUnion> };
        }
    };
    ($macro:ident, $($prefix:tt)*) => {
        $macro! {
            $($prefix)*
            #[fory(id = 1)] ExampleFieldBoolValue { bool_value: bool };
            #[fory(id = 2)] ExampleFieldInt8Value { int8_value: i8 };
            #[fory(id = 3)] ExampleFieldInt16Value { int16_value: i16 };
            #[fory(id = 4, encoding = "fixed")] ExampleFieldFixedInt32Value { fixed_int32_value: i32 };
            #[fory(id = 5)] ExampleFieldVarint32Value { varint32_value: i32 };
            #[fory(id = 6, encoding = "fixed")] ExampleFieldFixedInt64Value { fixed_int64_value: i64 };
            #[fory(id = 7)] ExampleFieldVarint64Value { varint64_value: i64 };
            #[fory(id = 8, encoding = "tagged")] ExampleFieldTaggedInt64Value { tagged_int64_value: i64 };
            #[fory(id = 9)] ExampleFieldUint8Value { uint8_value: u8 };
            #[fory(id = 10)] ExampleFieldUint16Value { uint16_value: u16 };
            #[fory(id = 11, encoding = "fixed")] ExampleFieldFixedUint32Value { fixed_uint32_value: u32 };
            #[fory(id = 12)] ExampleFieldVarUint32Value { var_uint32_value: u32 };
            #[fory(id = 13, encoding = "fixed")] ExampleFieldFixedUint64Value { fixed_uint64_value: u64 };
            #[fory(id = 14)] ExampleFieldVarUint64Value { var_uint64_value: u64 };
            #[fory(id = 15, encoding = "tagged")] ExampleFieldTaggedUint64Value { tagged_uint64_value: u64 };
            #[fory(id = 16)] ExampleFieldFloat16Value { float16_value: fory::Float16 };
            #[fory(id = 17)] ExampleFieldBfloat16Value { bfloat16_value: fory::BFloat16 };
            #[fory(id = 18)] ExampleFieldFloat32Value { float32_value: f32 };
            #[fory(id = 19)] ExampleFieldFloat64Value { float64_value: f64 };
            #[fory(id = 20)] ExampleFieldStringValue { string_value: String };
            #[fory(id = 21)] ExampleFieldBytesValue { bytes_value: Vec<u8> };
            #[fory(id = 22)] ExampleFieldDateValue { date_value: chrono::NaiveDate };
            #[fory(id = 23)] ExampleFieldTimestampValue { timestamp_value: chrono::NaiveDateTime };
            #[fory(id = 24)] ExampleFieldDurationValue { duration_value: chrono::Duration };
            #[fory(id = 25)] ExampleFieldDecimalValue { decimal_value: fory::Decimal };
            #[fory(id = 26)] ExampleFieldEnumValue { enum_value: crate::example_common::ExampleState };
            #[fory(id = 27, nullable = true)] ExampleFieldMessageValue { message_value: Option<crate::example_common::ExampleLeaf> };
            #[fory(id = 28, type_id = "union")] ExampleFieldUnionValue { union_value: crate::example_common::ExampleLeafUnion };
            #[fory(id = 101)] ExampleFieldBoolList { bool_list: Vec<bool> };
            #[fory(id = 102, type_id = "int8_array")] ExampleFieldInt8List { int8_list: Vec<i8> };
            #[fory(id = 103)] ExampleFieldInt16List { int16_list: Vec<i16> };
            #[fory(id = 104)] ExampleFieldFixedInt32List { fixed_int32_list: Vec<i32> };
            #[fory(id = 105)] ExampleFieldVarint32List { varint32_list: Vec<i32> };
            #[fory(id = 106)] ExampleFieldFixedInt64List { fixed_int64_list: Vec<i64> };
            #[fory(id = 107)] ExampleFieldVarint64List { varint64_list: Vec<i64> };
            #[fory(id = 108)] ExampleFieldTaggedInt64List { tagged_int64_list: Vec<i64> };
            #[fory(id = 109, type_id = "uint8_array")] ExampleFieldUint8List { uint8_list: Vec<u8> };
            #[fory(id = 110)] ExampleFieldUint16List { uint16_list: Vec<u16> };
            #[fory(id = 111)] ExampleFieldFixedUint32List { fixed_uint32_list: Vec<u32> };
            #[fory(id = 112)] ExampleFieldVarUint32List { var_uint32_list: Vec<u32> };
            #[fory(id = 113)] ExampleFieldFixedUint64List { fixed_uint64_list: Vec<u64> };
            #[fory(id = 114)] ExampleFieldVarUint64List { var_uint64_list: Vec<u64> };
            #[fory(id = 115)] ExampleFieldTaggedUint64List { tagged_uint64_list: Vec<u64> };
            #[fory(id = 116)] ExampleFieldFloat16List { float16_list: Vec<fory::Float16> };
            #[fory(id = 117)] ExampleFieldBfloat16List { bfloat16_list: Vec<fory::BFloat16> };
            #[fory(id = 118)] ExampleFieldMaybeFloat16List { maybe_float16_list: Vec<Option<fory::Float16>> };
            #[fory(id = 119)] ExampleFieldMaybeBfloat16List { maybe_bfloat16_list: Vec<Option<fory::BFloat16>> };
            #[fory(id = 120)] ExampleFieldFloat32List { float32_list: Vec<f32> };
            #[fory(id = 121)] ExampleFieldFloat64List { float64_list: Vec<f64> };
            #[fory(id = 122)] ExampleFieldStringList { string_list: Vec<String> };
            #[fory(id = 123)] ExampleFieldBytesList { bytes_list: Vec<Vec<u8>> };
            #[fory(id = 124)] ExampleFieldDateList { date_list: Vec<chrono::NaiveDate> };
            #[fory(id = 125)] ExampleFieldTimestampList { timestamp_list: Vec<chrono::NaiveDateTime> };
            #[fory(id = 126)] ExampleFieldDurationList { duration_list: Vec<chrono::Duration> };
            #[fory(id = 127)] ExampleFieldDecimalList { decimal_list: Vec<fory::Decimal> };
            #[fory(id = 128)] ExampleFieldEnumList { enum_list: Vec<crate::example_common::ExampleState> };
            #[fory(id = 129)] ExampleFieldMessageList { message_list: Vec<crate::example_common::ExampleLeaf> };
            #[fory(id = 130)] ExampleFieldUnionList { union_list: Vec<crate::example_common::ExampleLeafUnion> };
            #[fory(id = 201)] ExampleFieldStringValuesByBool { string_values_by_bool: HashMap<bool, String> };
            #[fory(id = 202)] ExampleFieldStringValuesByInt8 { string_values_by_int8: HashMap<i8, String> };
            #[fory(id = 203)] ExampleFieldStringValuesByInt16 { string_values_by_int16: HashMap<i16, String> };
            #[fory(id = 204)] ExampleFieldStringValuesByFixedInt32 { string_values_by_fixed_int32: HashMap<i32, String> };
            #[fory(id = 205)] ExampleFieldStringValuesByVarint32 { string_values_by_varint32: HashMap<i32, String> };
            #[fory(id = 206)] ExampleFieldStringValuesByFixedInt64 { string_values_by_fixed_int64: HashMap<i64, String> };
            #[fory(id = 207)] ExampleFieldStringValuesByVarint64 { string_values_by_varint64: HashMap<i64, String> };
            #[fory(id = 208)] ExampleFieldStringValuesByTaggedInt64 { string_values_by_tagged_int64: HashMap<i64, String> };
            #[fory(id = 209)] ExampleFieldStringValuesByUint8 { string_values_by_uint8: HashMap<u8, String> };
            #[fory(id = 210)] ExampleFieldStringValuesByUint16 { string_values_by_uint16: HashMap<u16, String> };
            #[fory(id = 211)] ExampleFieldStringValuesByFixedUint32 { string_values_by_fixed_uint32: HashMap<u32, String> };
            #[fory(id = 212)] ExampleFieldStringValuesByVarUint32 { string_values_by_var_uint32: HashMap<u32, String> };
            #[fory(id = 213)] ExampleFieldStringValuesByFixedUint64 { string_values_by_fixed_uint64: HashMap<u64, String> };
            #[fory(id = 214)] ExampleFieldStringValuesByVarUint64 { string_values_by_var_uint64: HashMap<u64, String> };
            #[fory(id = 215)] ExampleFieldStringValuesByTaggedUint64 { string_values_by_tagged_uint64: HashMap<u64, String> };
            #[fory(id = 218)] ExampleFieldStringValuesByString { string_values_by_string: HashMap<String, String> };
            #[fory(id = 219)] ExampleFieldStringValuesByTimestamp { string_values_by_timestamp: HashMap<chrono::NaiveDateTime, String> };
            #[fory(id = 220)] ExampleFieldStringValuesByDuration { string_values_by_duration: HashMap<chrono::Duration, String> };
            #[fory(id = 221)] ExampleFieldStringValuesByEnum { string_values_by_enum: HashMap<crate::example_common::ExampleState, String> };
            #[fory(id = 222)] ExampleFieldFloat16ValuesByName { float16_values_by_name: HashMap<String, fory::Float16> };
            #[fory(id = 223)] ExampleFieldMaybeFloat16ValuesByName { maybe_float16_values_by_name: HashMap<String, Option<fory::Float16>> };
            #[fory(id = 224)] ExampleFieldBfloat16ValuesByName { bfloat16_values_by_name: HashMap<String, fory::BFloat16> };
            #[fory(id = 225)] ExampleFieldMaybeBfloat16ValuesByName { maybe_bfloat16_values_by_name: HashMap<String, Option<fory::BFloat16>> };
            #[fory(id = 226)] ExampleFieldBytesValuesByName { bytes_values_by_name: HashMap<String, Vec<u8>> };
            #[fory(id = 227)] ExampleFieldDateValuesByName { date_values_by_name: HashMap<String, chrono::NaiveDate> };
            #[fory(id = 228)] ExampleFieldDecimalValuesByName { decimal_values_by_name: HashMap<String, fory::Decimal> };
            #[fory(id = 229)] ExampleFieldMessageValuesByName { message_values_by_name: HashMap<String, crate::example_common::ExampleLeaf> };
            #[fory(id = 230)] ExampleFieldUnionValuesByName { union_values_by_name: HashMap<String, crate::example_common::ExampleLeafUnion> };
        }
    };
}

for_each_example_schema_variant!(define_example_schema_variant_structs);

#[derive(ForyObject, Debug, PartialEq, Clone, Default)]
struct ExampleMessageEmpty {}

fn build_example_schema_evolution_fory<T>() -> Fory
where
    T: StructSerializer + Serializer + ForyDefault + 'static,
{
    let mut fory = Fory::builder().xlang(true).compatible(true).build();
    example_common::register_types(&mut fory).expect("register example common types for schema evolution");
    fory.register::<T>(EXAMPLE_MESSAGE_TYPE_ID)
        .expect("register schema evolution type");
    fory
}

fn assert_example_schema_variant_decode<T>(payload: &[u8], expected: T)
where
    T: StructSerializer + Serializer + ForyDefault + PartialEq + std::fmt::Debug + Clone + 'static,
{
    let fory = build_example_schema_evolution_fory::<T>();
    let actual: T = fory
        .deserialize(payload)
        .expect("deserialize schema evolution variant");
    assert_eq!(expected, actual);
}

fn assert_example_message_schema_evolution(payload: &[u8], expected: &ExampleMessage) {
    let empty_fory = build_example_schema_evolution_fory::<ExampleMessageEmpty>();
    let _: ExampleMessageEmpty = empty_fory
        .deserialize(payload)
        .expect("deserialize empty schema evolution type");

    for_each_example_schema_variant!(assert_example_schema_variant_structs, payload, expected,);
}

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

fn build_root_holder() -> root::MultiHolder {
    let owner = Person {
        name: "Alice".to_string(),
        id: 123,
        email: String::new(),
        tags: Vec::new(),
        scores: HashMap::new(),
        salary: 0.0,
        phones: Vec::new(),
        pet: Animal::Dog(Dog {
            name: "Rex".to_string(),
            bark_volume: 5,
        }),
    };

    let book = AddressBook {
        people: vec![owner.clone()],
        people_by_name: HashMap::from([(owner.name.clone(), owner.clone())]),
    };

    let root_node = tree::TreeNode {
        id: "root".to_string(),
        name: "root".to_string(),
        children: Vec::new(),
        parent: None,
    };

    root::MultiHolder {
        book: Some(book),
        root: Some(root_node),
        owner: Some(owner),
    }
}

fn build_auto_id_envelope() -> auto_id::Envelope {
    let payload = auto_id::envelope::Payload { value: 42 };
    let detail = auto_id::envelope::Detail::Payload(payload.clone());
    auto_id::Envelope {
        id: "env-1".to_string(),
        payload: Some(payload),
        detail,
        status: auto_id::Status::Ok,
    }
}

fn build_auto_id_wrapper(envelope: auto_id::Envelope) -> auto_id::Wrapper {
    auto_id::Wrapper::Envelope(envelope)
}

fn decimal_value(unscaled: &str, scale: i32) -> Decimal {
    Decimal::new(
        BigInt::parse_bytes(unscaled.as_bytes(), 10).expect("invalid decimal"),
        scale,
    )
}

fn build_example_leafs() -> (ExampleLeaf, ExampleLeaf) {
    (
        ExampleLeaf {
            label: "leaf-a".to_string(),
            count: 7,
        },
        ExampleLeaf {
            label: "leaf-b".to_string(),
            count: -3,
        },
    )
}

fn build_example_message() -> ExampleMessage {
    let (leaf_a, leaf_b) = build_example_leafs();
    let leaf_union = ExampleLeafUnion::Leaf(leaf_b.clone());
    let date_value = NaiveDate::from_ymd_opt(2024, 2, 29).expect("example date");
    let timestamp_value = date_value
        .and_hms_micro_opt(12, 34, 56, 789_123)
        .expect("example timestamp");
    let duration_value = Duration::seconds(3723) + Duration::microseconds(456_789);
    let amount = decimal_value("1234567890123456789", 4);

    ExampleMessage {
        bool_value: true,
        int8_value: -12,
        int16_value: 1234,
        fixed_int32_value: 123_456_789,
        varint32_value: -1_234_567,
        fixed_int64_value: 1_234_567_890_123_456_789,
        varint64_value: -1_234_567_890_123_456_789,
        tagged_int64_value: 1_073_741_824,
        uint8_value: 200,
        uint16_value: 60_000,
        fixed_uint32_value: 2_000_000_000,
        var_uint32_value: 2_100_000_000,
        fixed_uint64_value: 9_000_000_000,
        var_uint64_value: 12_000_000_000,
        tagged_uint64_value: 2_222_222_222,
        float16_value: Float16::from_f32(1.5),
        bfloat16_value: BFloat16::from_f32(-2.75),
        float32_value: 3.25,
        float64_value: -4.5,
        string_value: "example-string".to_string(),
        bytes_value: vec![1, 2, 3, 4],
        date_value,
        timestamp_value,
        duration_value,
        decimal_value: amount.clone(),
        enum_value: ExampleState::Ready,
        message_value: Some(leaf_a.clone()),
        union_value: leaf_union.clone(),
        bool_list: vec![true, false],
        int8_list: vec![-12, 8],
        int16_list: vec![1234, -4321],
        fixed_int32_list: vec![123_456_789, -98_765_432],
        varint32_list: vec![-1_234_567, 7_654_321],
        fixed_int64_list: vec![1_234_567_890_123_456_789, -123_456_789_012_345_678],
        varint64_list: vec![-1_234_567_890_123_456_789, 2_233_445_566_778_899],
        tagged_int64_list: vec![1_073_741_824, -1_073_741_824],
        uint8_list: vec![200, 17],
        uint16_list: vec![60_000, 42],
        fixed_uint32_list: vec![2_000_000_000, 77],
        var_uint32_list: vec![2_100_000_000, 123],
        fixed_uint64_list: vec![9_000_000_000, 456],
        var_uint64_list: vec![12_000_000_000, 789],
        tagged_uint64_list: vec![2_222_222_222, 321],
        float16_list: vec![Float16::from_f32(1.5), Float16::from_f32(-0.5)],
        bfloat16_list: vec![BFloat16::from_f32(-2.75), BFloat16::from_f32(2.25)],
        maybe_float16_list: vec![
            Some(Float16::from_f32(1.5)),
            None,
            Some(Float16::from_f32(-0.5)),
        ],
        maybe_bfloat16_list: vec![
            None,
            Some(BFloat16::from_f32(2.25)),
            Some(BFloat16::from_f32(-1.0)),
        ],
        float32_list: vec![3.25, -1.25],
        float64_list: vec![-4.5, 6.75],
        string_list: vec!["example-string".to_string(), "example-alt".to_string()],
        bytes_list: vec![vec![1, 2, 3, 4], vec![4, 3, 2, 1]],
        date_list: vec![
            date_value,
            NaiveDate::from_ymd_opt(2024, 3, 1).expect("example date list"),
        ],
        timestamp_list: vec![
            timestamp_value,
            NaiveDate::from_ymd_opt(2024, 3, 1)
                .expect("example timestamp date")
                .and_hms_micro_opt(12, 35, 56, 789_123)
                .expect("example timestamp list"),
        ],
        duration_list: vec![duration_value, duration_value + Duration::seconds(1)],
        decimal_list: vec![amount.clone(), decimal_value("-9999", 3)],
        enum_list: vec![ExampleState::Ready, ExampleState::Failed],
        message_list: vec![leaf_a.clone(), leaf_b.clone()],
        union_list: vec![
            ExampleLeafUnion::Note("example-note".to_string()),
            leaf_union.clone(),
        ],
        string_values_by_bool: HashMap::from([
            (true, "bool-true".to_string()),
            (false, "bool-false".to_string()),
        ]),
        string_values_by_int8: HashMap::from([(-12, "int8".to_string())]),
        string_values_by_int16: HashMap::from([(1234, "int16".to_string())]),
        string_values_by_fixed_int32: HashMap::from([(123_456_789, "fixed-int32".to_string())]),
        string_values_by_varint32: HashMap::from([(-1_234_567, "varint32".to_string())]),
        string_values_by_fixed_int64: HashMap::from([(
            1_234_567_890_123_456_789,
            "fixed-int64".to_string(),
        )]),
        string_values_by_varint64: HashMap::from([(
            -1_234_567_890_123_456_789,
            "varint64".to_string(),
        )]),
        string_values_by_tagged_int64: HashMap::from([(1_073_741_824, "tagged-int64".to_string())]),
        string_values_by_uint8: HashMap::from([(200, "uint8".to_string())]),
        string_values_by_uint16: HashMap::from([(60_000, "uint16".to_string())]),
        string_values_by_fixed_uint32: HashMap::from([(2_000_000_000, "fixed-uint32".to_string())]),
        string_values_by_var_uint32: HashMap::from([(2_100_000_000, "var-uint32".to_string())]),
        string_values_by_fixed_uint64: HashMap::from([(9_000_000_000, "fixed-uint64".to_string())]),
        string_values_by_var_uint64: HashMap::from([(12_000_000_000, "var-uint64".to_string())]),
        string_values_by_tagged_uint64: HashMap::from([(
            2_222_222_222,
            "tagged-uint64".to_string(),
        )]),
        string_values_by_string: HashMap::from([(
            "example-string".to_string(),
            "string".to_string(),
        )]),
        string_values_by_timestamp: HashMap::from([(timestamp_value, "timestamp".to_string())]),
        string_values_by_duration: HashMap::from([(duration_value, "duration".to_string())]),
        string_values_by_enum: HashMap::from([(ExampleState::Ready, "ready".to_string())]),
        float16_values_by_name: HashMap::from([
            ("primary".to_string(), Float16::from_f32(1.5)),
            ("secondary".to_string(), Float16::from_f32(-0.5)),
        ]),
        maybe_float16_values_by_name: HashMap::from([
            ("primary".to_string(), Some(Float16::from_f32(1.5))),
            ("missing".to_string(), None),
        ]),
        bfloat16_values_by_name: HashMap::from([
            ("primary".to_string(), BFloat16::from_f32(-2.75)),
            ("secondary".to_string(), BFloat16::from_f32(2.25)),
        ]),
        maybe_bfloat16_values_by_name: HashMap::from([
            ("missing".to_string(), None),
            ("primary".to_string(), Some(BFloat16::from_f32(-2.75))),
        ]),
        bytes_values_by_name: HashMap::from([
            ("primary".to_string(), vec![1, 2, 3, 4]),
            ("secondary".to_string(), vec![4, 3, 2, 1]),
        ]),
        date_values_by_name: HashMap::from([("leap-day".to_string(), date_value)]),
        decimal_values_by_name: HashMap::from([("primary".to_string(), amount)]),
        message_values_by_name: HashMap::from([
            ("leaf-a".to_string(), leaf_a),
            ("leaf-b".to_string(), leaf_b),
        ]),
        union_values_by_name: HashMap::from([("leaf-b".to_string(), leaf_union)]),
    }
}

fn build_example_message_union() -> ExampleMessageUnion {
    let (_, leaf_b) = build_example_leafs();
    ExampleMessageUnion::UnionValue(ExampleLeafUnion::Leaf(leaf_b))
}

#[test]
fn test_to_bytes_from_bytes() {
    let book = build_address_book();
    let bytes = book.to_bytes().expect("serialize addressbook");
    let decoded = AddressBook::from_bytes(&bytes).expect("deserialize addressbook");
    assert_eq!(decoded, book);

    let dog = Dog {
        name: "Rex".to_string(),
        bark_volume: 5,
    };
    let animal = Animal::Dog(dog);
    let animal_bytes = animal.to_bytes().expect("serialize animal");
    let decoded_animal = Animal::from_bytes(&animal_bytes).expect("deserialize animal");
    assert_eq!(decoded_animal, animal);

    let multi = build_root_holder();
    let multi_bytes = multi.to_bytes().expect("serialize root");
    let decoded_multi = root::MultiHolder::from_bytes(&multi_bytes).expect("deserialize root");
    assert_eq!(decoded_multi, multi);

    let example_message = build_example_message();
    let example_bytes = example_message.to_bytes().expect("serialize example");
    let decoded_example = ExampleMessage::from_bytes(&example_bytes).expect("deserialize example");
    assert_eq!(decoded_example, example_message);

    let example_union = build_example_message_union();
    let example_union_bytes = example_union.to_bytes().expect("serialize example union");
    let decoded_example_union =
        ExampleMessageUnion::from_bytes(&example_union_bytes).expect("deserialize example union");
    assert_eq!(decoded_example_union, example_union);
}

fn build_primitive_types() -> PrimitiveTypes {
    let mut contact = complex_pb::primitive_types::Contact::Email("alice@example.com".to_string());
    contact = complex_pb::primitive_types::Contact::Phone(12345);

    PrimitiveTypes {
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

fn build_numeric_collections() -> NumericCollections {
    NumericCollections {
        int8_values: vec![1, -2, 3],
        int16_values: vec![100, -200, 300],
        int32_values: vec![1000, -2000, 3000],
        int64_values: vec![10000, -20000, 30000],
        uint8_values: vec![200, 250],
        uint16_values: vec![50000, 60000],
        uint32_values: vec![2000000000, 2100000000],
        uint64_values: vec![9000000000, 12000000000],
        float32_values: vec![1.5, 2.5],
        float64_values: vec![3.5, 4.5],
    }
}

fn build_numeric_collection_union() -> NumericCollectionUnion {
    NumericCollectionUnion::Int32Values(vec![7, 8, 9])
}

fn build_numeric_collections_array() -> NumericCollectionsArray {
    NumericCollectionsArray {
        int8_values: vec![1, -2, 3],
        int16_values: vec![100, -200, 300],
        int32_values: vec![1000, -2000, 3000],
        int64_values: vec![10000, -20000, 30000],
        uint8_values: vec![200, 250],
        uint16_values: vec![50000, 60000],
        uint32_values: vec![2000000000, 2100000000],
        uint64_values: vec![9000000000, 12000000000],
        float32_values: vec![1.5, 2.5],
        float64_values: vec![3.5, 4.5],
    }
}

fn build_numeric_collection_array_union() -> NumericCollectionArrayUnion {
    NumericCollectionArrayUnion::Uint16Values(vec![1000, 2000, 3000])
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
        int64_map: Some(HashMap::from([
            ("alpha".to_string(), 10),
            ("beta".to_string(), 20),
        ])),
    };

    OptionalHolder {
        all_types: Some(all_types.clone()),
        choice: Some(OptionalUnion::Note("optional".to_string())),
    }
}

fn build_any_holder() -> AnyHolder {
    AnyHolder {
        bool_value: Box::new(true),
        string_value: Box::new("hello".to_string()),
        date_value: Box::new(NaiveDate::from_ymd_opt(2024, 1, 2).unwrap()),
        timestamp_value: Box::new(
            NaiveDate::from_ymd_opt(2024, 1, 2)
                .unwrap()
                .and_hms_opt(3, 4, 5)
                .expect("timestamp"),
        ),
        message_value: Box::new(AnyInner {
            name: "inner".to_string(),
        }),
        union_value: Box::new(AnyUnion::Text("union".to_string())),
        list_value: Box::new("list-placeholder".to_string()),
        map_value: Box::new("map-placeholder".to_string()),
    }
}

fn build_any_holder_with_collections() -> AnyHolder {
    AnyHolder {
        bool_value: Box::new(true),
        string_value: Box::new("hello".to_string()),
        date_value: Box::new(NaiveDate::from_ymd_opt(2024, 1, 2).unwrap()),
        timestamp_value: Box::new(
            NaiveDate::from_ymd_opt(2024, 1, 2)
                .unwrap()
                .and_hms_opt(3, 4, 5)
                .expect("timestamp"),
        ),
        message_value: Box::new(AnyInner {
            name: "inner".to_string(),
        }),
        union_value: Box::new(AnyUnion::Text("union".to_string())),
        list_value: Box::new(vec!["alpha".to_string(), "beta".to_string()]),
        map_value: Box::new(HashMap::from([
            ("k1".to_string(), "v1".to_string()),
            ("k2".to_string(), "v2".to_string()),
        ])),
    }
}

fn assert_any_holder(holder: &AnyHolder) {
    let bool_value = holder.bool_value.downcast_ref::<bool>().expect("bool any");
    assert_eq!(*bool_value, true);
    let string_value = holder
        .string_value
        .downcast_ref::<String>()
        .expect("string any");
    assert_eq!(string_value, "hello");
    let date_value = holder
        .date_value
        .downcast_ref::<NaiveDate>()
        .expect("date any");
    assert_eq!(*date_value, NaiveDate::from_ymd_opt(2024, 1, 2).unwrap());
    let timestamp_value = holder
        .timestamp_value
        .downcast_ref::<chrono::NaiveDateTime>()
        .expect("timestamp any");
    assert_eq!(
        *timestamp_value,
        NaiveDate::from_ymd_opt(2024, 1, 2)
            .unwrap()
            .and_hms_opt(3, 4, 5)
            .expect("timestamp")
    );
    let message_value = holder
        .message_value
        .downcast_ref::<AnyInner>()
        .expect("message any");
    assert_eq!(message_value.name, "inner");
    let union_value = holder
        .union_value
        .downcast_ref::<AnyUnion>()
        .expect("union any");
    assert_eq!(*union_value, AnyUnion::Text("union".to_string()));
}

fn build_tree() -> tree::TreeNode {
    let mut child_a = Arc::new(tree::TreeNode {
        id: "child-a".to_string(),
        name: "child-a".to_string(),
        children: vec![],
        parent: None,
    });
    let mut child_b = Arc::new(tree::TreeNode {
        id: "child-b".to_string(),
        name: "child-b".to_string(),
        children: vec![],
        parent: None,
    });

    let child_a_weak = ArcWeak::from(&child_a);
    let child_b_weak = ArcWeak::from(&child_b);
    Arc::get_mut(&mut child_a).expect("child a unique").parent = Some(child_b_weak);
    Arc::get_mut(&mut child_b).expect("child b unique").parent = Some(child_a_weak);

    tree::TreeNode {
        id: "root".to_string(),
        name: "root".to_string(),
        children: vec![
            Arc::clone(&child_a),
            Arc::clone(&child_a),
            Arc::clone(&child_b),
        ],
        parent: None,
    }
}

fn assert_tree(root: &tree::TreeNode) {
    assert_eq!(root.children.len(), 3);
    assert!(Arc::ptr_eq(&root.children[0], &root.children[1]));
    assert!(!Arc::ptr_eq(&root.children[0], &root.children[2]));
    let parent_a = root.children[0]
        .parent
        .as_ref()
        .expect("child a parent")
        .upgrade()
        .expect("upgrade child a parent");
    let parent_b = root.children[2]
        .parent
        .as_ref()
        .expect("child b parent")
        .upgrade()
        .expect("upgrade child b parent");
    assert!(Arc::ptr_eq(&parent_a, &root.children[2]));
    assert!(Arc::ptr_eq(&parent_b, &root.children[0]));
}

fn build_graph() -> graph::Graph {
    let mut node_a = Arc::new(graph::Node {
        id: "node-a".to_string(),
        out_edges: vec![],
        in_edges: vec![],
    });
    let mut node_b = Arc::new(graph::Node {
        id: "node-b".to_string(),
        out_edges: vec![],
        in_edges: vec![],
    });

    let edge = Arc::new(graph::Edge {
        id: "edge-1".to_string(),
        weight: 1.5_f32,
        from: Some(ArcWeak::from(&node_a)),
        to: Some(ArcWeak::from(&node_b)),
    });

    Arc::get_mut(&mut node_a).expect("node a unique").out_edges = vec![Arc::clone(&edge)];
    Arc::get_mut(&mut node_a).expect("node a unique").in_edges = vec![Arc::clone(&edge)];
    Arc::get_mut(&mut node_b).expect("node b unique").in_edges = vec![Arc::clone(&edge)];

    graph::Graph {
        nodes: vec![Arc::clone(&node_a), Arc::clone(&node_b)],
        edges: vec![Arc::clone(&edge)],
    }
}

fn assert_graph(value: &graph::Graph) {
    assert_eq!(value.nodes.len(), 2);
    assert_eq!(value.edges.len(), 1);
    let node_a = &value.nodes[0];
    let node_b = &value.nodes[1];
    let edge = &value.edges[0];
    assert!(Arc::ptr_eq(&node_a.out_edges[0], &node_a.in_edges[0]));
    assert!(Arc::ptr_eq(&node_a.out_edges[0], edge));
    let from = edge
        .from
        .as_ref()
        .expect("edge from")
        .upgrade()
        .expect("upgrade from");
    let to = edge
        .to
        .as_ref()
        .expect("edge to")
        .upgrade()
        .expect("upgrade to");
    assert!(Arc::ptr_eq(&from, node_a));
    assert!(Arc::ptr_eq(&to, node_b));
}

#[test]
fn test_address_book_roundtrip_compatible() {
    run_address_book_roundtrip(true);
}

#[test]
fn test_address_book_roundtrip_schema_consistent() {
    run_address_book_roundtrip(false);
}

#[test]
fn test_evolving_roundtrip() {
    let mut fory_v1 = Fory::builder().xlang(true).compatible(true).build();
    evolving1::register_types(&mut fory_v1).expect("register evolving1 types");
    let mut fory_v2 = Fory::builder().xlang(true).compatible(true).build();
    evolving2::register_types(&mut fory_v2).expect("register evolving2 types");

    let msg_v1 = evolving1::EvolvingMessage {
        id: 1,
        name: "Alice".to_string(),
        city: "NYC".to_string(),
    };
    let bytes = fory_v1.serialize(&msg_v1).expect("serialize evolving v1");
    let mut msg_v2: evolving2::EvolvingMessage = fory_v2
        .deserialize(&bytes)
        .expect("deserialize evolving v2");
    assert_eq!(msg_v1.id, msg_v2.id);
    assert_eq!(msg_v1.name, msg_v2.name);
    assert_eq!(msg_v1.city, msg_v2.city);

    msg_v2.email = Some("alice@example.com".to_string());
    let round_bytes = fory_v2.serialize(&msg_v2).expect("serialize evolving v2");
    let msg_v1_round: evolving1::EvolvingMessage = fory_v1
        .deserialize(&round_bytes)
        .expect("deserialize evolving v1");
    assert_eq!(msg_v1, msg_v1_round);

    let fixed_v1 = evolving1::FixedMessage {
        id: 10,
        name: "Bob".to_string(),
        score: 90,
        note: "note".to_string(),
    };
    let fixed_bytes = fory_v1.serialize(&fixed_v1).expect("serialize fixed v1");
    let fixed_v2 = fory_v2.deserialize::<evolving2::FixedMessage>(&fixed_bytes);
    match fixed_v2 {
        Err(_) => return,
        Ok(value) => {
            let round = fory_v2.serialize(&value);
            if let Ok(round_bytes) = round {
                let fixed_round = fory_v1.deserialize::<evolving1::FixedMessage>(&round_bytes);
                if let Ok(fixed_round) = fixed_round {
                    assert_ne!(fixed_round, fixed_v1);
                }
            }
        }
    }
}

fn run_address_book_roundtrip(compatible: bool) {
    let mut fory = Fory::builder().xlang(true).compatible(compatible).build();
    complex_pb::register_types(&mut fory).expect("register complex pb types");
    addressbook::register_types(&mut fory).expect("register types");
    auto_id::register_types(&mut fory).expect("register auto_id types");
    monster::register_types(&mut fory).expect("register monster types");
    complex_fbs::register_types(&mut fory).expect("register flatbuffers types");
    collection::register_types(&mut fory).expect("register collection types");
    optional_types::register_types(&mut fory).expect("register optional types");
    any_example::register_types(&mut fory).expect("register any example types");
    example_common::register_types(&mut fory).expect("register example common types");
    example::register_types(&mut fory).expect("register example types");

    let book = build_address_book();
    let bytes = fory.serialize(&book).expect("serialize");
    let roundtrip: AddressBook = fory.deserialize(&bytes).expect("deserialize");

    assert_eq!(book, roundtrip);

    let auto_env = build_auto_id_envelope();
    let auto_bytes = fory.serialize(&auto_env).expect("serialize auto_id");
    let auto_roundtrip: auto_id::Envelope =
        fory.deserialize(&auto_bytes).expect("deserialize auto_id");
    assert_eq!(auto_env, auto_roundtrip);

    let auto_wrapper = build_auto_id_wrapper(auto_env.clone());
    let wrapper_bytes = fory
        .serialize(&auto_wrapper)
        .expect("serialize auto_id wrapper");
    let wrapper_roundtrip: auto_id::Wrapper = fory
        .deserialize(&wrapper_bytes)
        .expect("deserialize auto_id wrapper");
    assert_eq!(auto_wrapper, wrapper_roundtrip);

    let example_message = build_example_message();
    let example_bytes = fory.serialize(&example_message).expect("serialize example");
    let example_roundtrip: ExampleMessage = fory
        .deserialize(&example_bytes)
        .expect("deserialize example");
    assert_eq!(example_message, example_roundtrip);

    let example_union = build_example_message_union();
    let example_union_bytes = fory
        .serialize(&example_union)
        .expect("serialize example union");
    let example_union_roundtrip: ExampleMessageUnion = fory
        .deserialize(&example_union_bytes)
        .expect("deserialize example union");
    assert_eq!(example_union, example_union_roundtrip);

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

    if let Ok(data_file) = env::var("DATA_FILE_AUTO_ID") {
        let payload = fs::read(&data_file).expect("read auto_id data file");
        let peer_env: auto_id::Envelope = fory
            .deserialize(&payload)
            .expect("deserialize auto_id peer payload");
        assert_eq!(auto_env, peer_env);
        let encoded = fory
            .serialize(&peer_env)
            .expect("serialize auto_id payload");
        fs::write(data_file, encoded).expect("write auto_id data file");
    }

    if let Ok(data_file) = env::var("DATA_FILE_EXAMPLE_MESSAGE") {
        let payload = fs::read(&data_file).expect("read example data file");
        let peer_message: ExampleMessage = fory
            .deserialize(&payload)
            .expect("deserialize example peer payload");
        assert_eq!(example_message, peer_message);
        if compatible {
            assert_example_message_schema_evolution(&payload, &example_message);
        }
        let encoded = fory
            .serialize(&peer_message)
            .expect("serialize example payload");
        fs::write(data_file, encoded).expect("write example data file");
    }

    if let Ok(data_file) = env::var("DATA_FILE_EXAMPLE_UNION") {
        let payload = fs::read(&data_file).expect("read example union data file");
        let peer_union: ExampleMessageUnion = fory
            .deserialize(&payload)
            .expect("deserialize example union peer payload");
        assert_eq!(example_union, peer_union);
        let encoded = fory
            .serialize(&peer_union)
            .expect("serialize example union payload");
        fs::write(data_file, encoded).expect("write example union data file");
    }

    let types = build_primitive_types();
    let bytes = fory.serialize(&types).expect("serialize");
    let roundtrip: PrimitiveTypes = fory.deserialize(&bytes).expect("deserialize");
    assert_eq!(types, roundtrip);

    let primitive_file = match env::var("DATA_FILE_PRIMITIVES") {
        Ok(path) => path,
        Err(_) => return,
    };
    let payload = fs::read(&primitive_file).expect("read data file");
    let peer_types: PrimitiveTypes = fory
        .deserialize(&payload)
        .expect("deserialize peer payload");
    assert_eq!(types, peer_types);
    let encoded = fory.serialize(&peer_types).expect("serialize peer payload");
    fs::write(primitive_file, encoded).expect("write data file");

    let collections = build_numeric_collections();
    let bytes = fory.serialize(&collections).expect("serialize collections");
    let roundtrip: NumericCollections = fory.deserialize(&bytes).expect("deserialize");
    assert_eq!(collections, roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_COLLECTION") {
        let payload = fs::read(&data_file).expect("read data file");
        let peer_collections: NumericCollections = fory
            .deserialize(&payload)
            .expect("deserialize peer payload");
        assert_eq!(collections, peer_collections);
        let encoded = fory
            .serialize(&peer_collections)
            .expect("serialize peer payload");
        fs::write(data_file, encoded).expect("write data file");
    }

    let collection_union = build_numeric_collection_union();
    let bytes = fory
        .serialize(&collection_union)
        .expect("serialize collection union");
    let roundtrip: NumericCollectionUnion = fory.deserialize(&bytes).expect("deserialize");
    assert_eq!(collection_union, roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_COLLECTION_UNION") {
        let payload = fs::read(&data_file).expect("read data file");
        let peer_union: NumericCollectionUnion = fory
            .deserialize(&payload)
            .expect("deserialize peer payload");
        assert_eq!(collection_union, peer_union);
        let encoded = fory.serialize(&peer_union).expect("serialize peer payload");
        fs::write(data_file, encoded).expect("write data file");
    }

    let collections_array = build_numeric_collections_array();
    let bytes = fory
        .serialize(&collections_array)
        .expect("serialize collection array");
    let roundtrip: NumericCollectionsArray = fory.deserialize(&bytes).expect("deserialize");
    assert_eq!(collections_array, roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_COLLECTION_ARRAY") {
        let payload = fs::read(&data_file).expect("read data file");
        let peer_array: NumericCollectionsArray = fory
            .deserialize(&payload)
            .expect("deserialize peer payload");
        assert_eq!(collections_array, peer_array);
        let encoded = fory.serialize(&peer_array).expect("serialize peer payload");
        fs::write(data_file, encoded).expect("write data file");
    }

    let collection_array_union = build_numeric_collection_array_union();
    let bytes = fory
        .serialize(&collection_array_union)
        .expect("serialize collection array union");
    let roundtrip: NumericCollectionArrayUnion = fory.deserialize(&bytes).expect("deserialize");
    assert_eq!(collection_array_union, roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_COLLECTION_ARRAY_UNION") {
        let payload = fs::read(&data_file).expect("read data file");
        let peer_union: NumericCollectionArrayUnion = fory
            .deserialize(&payload)
            .expect("deserialize peer payload");
        assert_eq!(collection_array_union, peer_union);
        let encoded = fory.serialize(&peer_union).expect("serialize peer payload");
        fs::write(data_file, encoded).expect("write data file");
    }

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

    let any_holder = build_any_holder();
    let bytes = fory.serialize(&any_holder).expect("serialize any");
    let roundtrip: AnyHolder = fory.deserialize(&bytes).expect("deserialize any");
    assert_any_holder(&roundtrip);

    let any_holder_collections = build_any_holder_with_collections();
    let bytes = fory
        .serialize(&any_holder_collections)
        .expect("serialize any collections");
    let result: Result<AnyHolder, _> = fory.deserialize(&bytes);
    assert!(result.is_err());

    let mut ref_fory = Fory::builder()
        .xlang(true)
        .compatible(compatible)
        .track_ref(true)
        .build();
    tree::register_types(&mut ref_fory).expect("register tree types");
    graph::register_types(&mut ref_fory).expect("register graph types");

    let tree_root = build_tree();
    let bytes = ref_fory.serialize(&tree_root).expect("serialize tree");
    let roundtrip: tree::TreeNode = ref_fory.deserialize(&bytes).expect("deserialize");
    assert_tree(&roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_TREE") {
        let payload = fs::read(&data_file).expect("read tree data file");
        let peer_tree: tree::TreeNode = ref_fory
            .deserialize(&payload)
            .expect("deserialize peer tree payload");
        assert_tree(&peer_tree);
        let encoded = ref_fory
            .serialize(&peer_tree)
            .expect("serialize peer tree payload");
        fs::write(data_file, encoded).expect("write tree data file");
    }

    let graph_value = build_graph();
    let bytes = ref_fory.serialize(&graph_value).expect("serialize graph");
    let roundtrip: graph::Graph = ref_fory.deserialize(&bytes).expect("deserialize");
    assert_graph(&roundtrip);

    if let Ok(data_file) = env::var("DATA_FILE_GRAPH") {
        let payload = fs::read(&data_file).expect("read graph data file");
        let peer_graph: graph::Graph = ref_fory
            .deserialize(&payload)
            .expect("deserialize peer graph payload");
        assert_graph(&peer_graph);
        let encoded = ref_fory
            .serialize(&peer_graph)
            .expect("serialize peer graph payload");
        fs::write(data_file, encoded).expect("write graph data file");
    }
}
