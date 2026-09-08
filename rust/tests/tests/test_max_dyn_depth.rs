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

use fory_core::fory::Fory;
use fory_core::{Error, ReadContext, RefFlag, RefMode, Serializer, WriteContext};
use fory_derive::{ForyEnum, ForyStruct, ForyUnion};
use std::any::Any;
use std::collections::{BTreeSet, BinaryHeap, HashSet, LinkedList, VecDeque};

#[derive(ForyStruct, Debug)]
#[fory(debug)]
struct Container {
    value: i32,
    nested: Option<Box<dyn Any>>,
}

#[derive(ForyStruct, Debug, PartialEq)]
struct StaticNode {
    value: i32,
    next: Option<Box<StaticNode>>,
}

struct StaticNodeRoot;

// Generated reserved_space follows the recursive schema before writing. These adapters bypass
// only that write-side estimate so the tests can exercise the real generated bodies without
// adding a write-side depth policy.
impl Serializer for StaticNodeRoot {
    type Target = StaticNode;

    fn write_data(value: &Self::Target, context: &mut WriteContext) -> Result<(), Error> {
        <StaticNode as Serializer>::write_data(value, context)
    }

    fn read_data(_context: &mut ReadContext) -> Result<Self::Target, Error> {
        unreachable!("write-only test root")
    }

    fn write(
        value: &Self::Target,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
    ) -> Result<(), Error> {
        <StaticNode as Serializer>::write(value, context, ref_mode, write_type_info)
    }

    fn reserved_space() -> usize {
        0
    }
}

#[derive(ForyStruct, Debug)]
struct RemoteNode {
    value: i32,
    next: Option<Box<RemoteNode>>,
    added: i32,
}

struct RemoteNodeRoot;

impl Serializer for RemoteNodeRoot {
    type Target = RemoteNode;

    fn write_data(value: &Self::Target, context: &mut WriteContext) -> Result<(), Error> {
        <RemoteNode as Serializer>::write_data(value, context)
    }

    fn read_data(_context: &mut ReadContext) -> Result<Self::Target, Error> {
        unreachable!("write-only test root")
    }

    fn write(
        value: &Self::Target,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
    ) -> Result<(), Error> {
        <RemoteNode as Serializer>::write(value, context, ref_mode, write_type_info)
    }

    fn reserved_space() -> usize {
        0
    }
}

#[derive(ForyStruct, Debug, PartialEq)]
struct FlatValue {
    number: i32,
    text: String,
    values: Vec<i32>,
}

#[derive(ForyStruct, Debug)]
struct FlatFuture {
    number: i32,
    text: String,
    values: Vec<i32>,
    added: i32,
}

type Text = std::string::String;

#[derive(ForyStruct, Debug, PartialEq)]
struct FlatAlias {
    text: Text,
}

#[derive(ForyStruct, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
struct SkippedChild {
    value: i32,
}

#[derive(ForyStruct, Debug, Default)]
struct EmptyCollections {
    #[fory(skip)]
    deque: VecDeque<SkippedChild>,
    #[fory(skip)]
    list: LinkedList<SkippedChild>,
    #[fory(skip)]
    heap: BinaryHeap<SkippedChild>,
    #[fory(skip)]
    hash_set: HashSet<SkippedChild>,
    #[fory(skip)]
    tree_set: BTreeSet<SkippedChild>,
}

#[derive(ForyStruct, Debug, PartialEq)]
struct FlatSkipped {
    value: i32,
    #[fory(skip)]
    ignored: Option<Box<SkippedChild>>,
}

#[derive(ForyStruct, Debug, PartialEq)]
struct FlatZeroArray {
    empty: [SkippedChild; 0],
    value: i32,
}

#[derive(ForyEnum, Debug, PartialEq)]
enum FlatKind {
    First,
    Second,
}

mod shadowed_leaf_name {
    use super::*;

    #[derive(ForyStruct, Debug)]
    pub(super) struct String {
        next: Option<Box<String>>,
    }

    pub(super) struct Root;

    impl Serializer for Root {
        type Target = String;

        fn write_data(value: &Self::Target, context: &mut WriteContext) -> Result<(), Error> {
            <String as Serializer>::write_data(value, context)
        }

        fn read_data(_context: &mut ReadContext) -> Result<Self::Target, Error> {
            unreachable!("write-only test root")
        }

        fn write(
            value: &Self::Target,
            context: &mut WriteContext,
            ref_mode: RefMode,
            write_type_info: bool,
        ) -> Result<(), Error> {
            <String as Serializer>::write(value, context, ref_mode, write_type_info)
        }

        fn reserved_space() -> usize {
            0
        }
    }

    pub(super) fn chain() -> String {
        String {
            next: Some(Box::new(String { next: None })),
        }
    }
}

fn static_chain(depth: usize) -> StaticNode {
    let mut node = StaticNode {
        value: 0,
        next: None,
    };
    for value in 1..depth {
        node = StaticNode {
            value: value as i32,
            next: Some(Box::new(node)),
        };
    }
    node
}

fn remote_chain(depth: usize) -> RemoteNode {
    let mut node = RemoteNode {
        value: 0,
        next: None,
        added: 1,
    };
    for value in 1..depth {
        node = RemoteNode {
            value: value as i32,
            next: Some(Box::new(node)),
            added: 1,
        };
    }
    node
}

#[derive(ForyUnion, Debug)]
enum SkipUnion {
    #[fory(unknown)]
    Unknown(fory_core::UnknownCase),
    #[fory(id = 0, default)]
    Leaf(String),
    #[fory(id = 1)]
    Next(Box<SkipUnion>),
}

#[derive(ForyStruct, Debug)]
struct SkipWriter {
    kept: i32,
    extra: SkipUnion,
}

#[derive(ForyStruct, Debug, PartialEq)]
struct SkipReader {
    kept: i32,
}

fn skip_union_chain(depth: usize) -> SkipUnion {
    let mut value = SkipUnion::Leaf("leaf".to_string());
    for _ in 0..depth {
        value = SkipUnion::Next(Box::new(value));
    }
    value
}

#[derive(ForyUnion, Debug)]
enum FutureUnion {
    #[fory(unknown)]
    Unknown(fory_core::UnknownCase),
    #[fory(id = 0, default)]
    Leaf(String),
    #[fory(id = 1)]
    Next(Box<FutureUnion>),
}

#[derive(ForyUnion, Debug)]
enum CurrentUnion {
    #[fory(unknown)]
    Unknown(fory_core::UnknownCase),
    #[fory(id = 0, default)]
    Leaf(String),
}

fn future_union_chain(depth: usize) -> FutureUnion {
    let mut value = FutureUnion::Leaf("leaf".to_string());
    for _ in 0..depth {
        value = FutureUnion::Next(Box::new(value));
    }
    value
}

#[derive(ForyUnion, Debug, PartialEq)]
enum RecursiveDefault {
    #[fory(default)]
    Branch(Box<RecursiveDefault>, Box<RecursiveDefault>),
    Leaf(i32),
}

#[derive(ForyUnion, Debug)]
enum SkippedRecursiveDefault {
    #[fory(default)]
    Branch(#[fory(skip)] Box<SkippedRecursiveDefault>, i32),
    Leaf(i32),
}

#[derive(ForyUnion, Debug, PartialEq)]
enum EmptyDefault {
    #[fory(default)]
    Empty(Option<Box<EmptyDefault>>, Vec<EmptyDefault>),
    Leaf(i32),
}

#[derive(ForyUnion, Debug)]
enum EmptySource {
    #[fory(default)]
    Empty,
    Pair(i32, i32),
}

#[derive(ForyUnion, Debug)]
enum MixedDepthEnum {
    #[fory(unknown)]
    Unknown(fory_core::UnknownCase),
    #[fory(id = 0, default)]
    Leaf(i32),
    #[fory(id = 1)]
    Next(Box<MixedDepthEnum>),
}

#[derive(ForyUnion, Debug)]
enum SkippedDefaultVariant {
    #[fory(unknown)]
    Unknown(fory_core::UnknownCase),
    #[fory(id = 0)]
    Keep,
    #[fory(id = 1)]
    #[fory(default)]
    #[fory(skip)]
    Removed(Box<RecursiveDefault>),
}

struct RecursiveDefaultSerializer;

impl Serializer for RecursiveDefaultSerializer {
    type Target = RecursiveDefault;

    fn write_data(value: &Self::Target, context: &mut WriteContext) -> Result<(), Error> {
        RecursiveDefault::write_data(value, context)
    }

    fn read_data(context: &mut ReadContext) -> Result<Self::Target, Error> {
        RecursiveDefault::read_data(context)
    }

    fn default_value(context: &mut ReadContext) -> Result<Self::Target, Error> {
        RecursiveDefault::default_value(context)
    }
}

#[derive(ForyStruct, Debug)]
struct SkippedCustomDefault {
    value: i32,
    #[fory(skip, with = RecursiveDefaultSerializer)]
    ignored: RecursiveDefault,
}

#[test]
fn test_max_dyn_depth_exceeded_box_dyn_any() {
    if fory_core::error::should_panic_on_error() {
        return;
    }
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .max_dyn_depth(2)
            .compatible(compatible)
            .build();
        fory.register::<Container>(100).unwrap();

        let level3 = Container {
            value: 3,
            nested: None,
        };
        let level2 = Container {
            value: 2,
            nested: Some(Box::new(level3)),
        };
        let level1 = Container {
            value: 1,
            nested: Some(Box::new(level2)),
        };

        let outer: Box<dyn Any> = Box::new(level1);
        let bytes = fory.serialize(&outer).unwrap();
        let result: Result<Box<dyn Any>, _> = fory.deserialize(&bytes);
        assert!(
            result.is_err(),
            "Expected deserialization to fail due to max depth"
        );
        let err = result.unwrap_err();
        let err_msg = format!("{:?}", err);
        assert!(err_msg.contains("Maximum dynamic object nesting depth"));

        let shallow: Box<dyn Any> = Box::new(Container {
            value: 4,
            nested: None,
        });
        let shallow_bytes = fory.serialize(&shallow).unwrap();
        let reused: Result<Box<dyn Any>, _> = fory.deserialize(&shallow_bytes);
        assert!(reused.is_ok(), "failed root depth must reset before reuse");
    }
}

#[test]
fn test_max_dyn_depth_within_limit_box_dyn_any() {
    if fory_core::error::should_panic_on_error() {
        return;
    }
    let mut fory = Fory::builder()
        .xlang(false)
        .max_dyn_depth(3)
        .compatible(false)
        .build();
    fory.register::<Container>(100).unwrap();

    let level3 = Container {
        value: 3,
        nested: None,
    };
    let level2 = Container {
        value: 2,
        nested: Some(Box::new(level3)),
    };
    let level1 = Container {
        value: 1,
        nested: Some(Box::new(level2)),
    };

    let outer: Box<dyn Any> = Box::new(level1);
    let bytes = fory.serialize(&outer).unwrap();
    let result: Result<Box<dyn Any>, _> = fory.deserialize(&bytes);
    assert!(result.is_ok());
}

#[test]
fn dynamic_fields_skip_struct_gate() {
    if fory_core::error::should_panic_on_error() {
        return;
    }
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_dyn_depth(3)
            .max_struct_depth(0)
            .build();
        fory.register::<Container>(101).unwrap();

        let value: Box<dyn Any> = Box::new(Container {
            value: 1,
            nested: Some(Box::new(Container {
                value: 2,
                nested: Some(Box::new(Container {
                    value: 3,
                    nested: None,
                })),
            })),
        });
        let bytes = fory.serialize(&value).unwrap();
        let decoded: Result<Box<dyn Any>, _> = fory.deserialize(&bytes);
        assert!(decoded.is_ok());
    }
}

#[test]
fn test_max_dyn_depth_default_exceeded() {
    if fory_core::error::should_panic_on_error() {
        return;
    }
    let mut fory = Fory::builder().xlang(false).compatible(false).build();
    fory.register::<Container>(100).unwrap();

    let mut current = Container {
        value: 6,
        nested: None,
    };

    for i in (1..=5).rev() {
        current = Container {
            value: i,
            nested: Some(Box::new(current)),
        };
    }

    let outer: Box<dyn Any> = Box::new(current);
    let bytes = fory.serialize(&outer).unwrap();
    let result: Result<Box<dyn Any>, _> = fory.deserialize(&bytes);

    assert!(result.is_err());
    let err = result.unwrap_err();
    let err_msg = format!("{:?}", err);
    assert!(err_msg.contains("Maximum dynamic object nesting depth"));
    assert!(err_msg.contains("5"));
}

#[test]
fn test_max_dyn_depth_default_within_limit() {
    if fory_core::error::should_panic_on_error() {
        return;
    }
    let mut fory = Fory::builder().xlang(false).compatible(false).build();
    fory.register::<Container>(100).unwrap();

    let mut current = Container {
        value: 5,
        nested: None,
    };

    for i in (1..=4).rev() {
        current = Container {
            value: i,
            nested: Some(Box::new(current)),
        };
    }

    let outer: Box<dyn Any> = Box::new(current);
    let bytes = fory.serialize(&outer).unwrap();
    let result: Result<Box<dyn Any>, _> = fory.deserialize(&bytes);

    assert!(result.is_ok());
}

#[test]
fn static_depth_exceeded() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(32)
            .build();
        fory.register::<StaticNode>(110).unwrap();

        let boundary = static_chain(32);
        let bytes = fory.serialize_with::<StaticNodeRoot>(&boundary).unwrap();
        assert_eq!(fory.deserialize::<StaticNode>(&bytes).unwrap(), boundary);

        let value = static_chain(64);
        let bytes = fory.serialize_with::<StaticNodeRoot>(&value).unwrap();
        let error = fory.deserialize::<StaticNode>(&bytes).unwrap_err();
        assert!(matches!(error, Error::DepthExceed(_)));

        let shallow = static_chain(4);
        let bytes = fory.serialize_with::<StaticNodeRoot>(&shallow).unwrap();
        assert_eq!(fory.deserialize::<StaticNode>(&bytes).unwrap(), shallow);
    }
}

#[test]
fn flat_types_skip_depth_gate() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register::<FlatValue>(140).unwrap();
        fory.register::<FlatKind>(141).unwrap();

        let value = FlatValue {
            number: 7,
            text: "flat".to_string(),
            values: vec![1, 2, 3],
        };
        let bytes = fory.serialize(&value).unwrap();
        assert_eq!(fory.deserialize::<FlatValue>(&bytes).unwrap(), value);

        let bytes = fory.serialize(&FlatKind::Second).unwrap();
        assert_eq!(
            fory.deserialize::<FlatKind>(&bytes).unwrap(),
            FlatKind::Second
        );
    }

    let mut writer = Fory::builder()
        .xlang(false)
        .compatible(true)
        .max_struct_depth(0)
        .build();
    writer.register::<FlatFuture>(142).unwrap();
    let bytes = writer
        .serialize(&FlatFuture {
            number: 8,
            text: "future".to_string(),
            values: vec![4, 5],
            added: 9,
        })
        .unwrap();

    let mut reader = Fory::builder()
        .xlang(false)
        .compatible(true)
        .max_struct_depth(0)
        .build();
    reader.register::<FlatValue>(142).unwrap();
    assert_eq!(
        reader.deserialize::<FlatValue>(&bytes).unwrap(),
        FlatValue {
            number: 8,
            text: "future".to_string(),
            values: vec![4, 5],
        }
    );
}

#[test]
fn flat_alias_skips_depth_gate() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register::<FlatAlias>(144).unwrap();

        let value = FlatAlias {
            text: "flat alias".to_string(),
        };
        let bytes = fory.serialize(&value).unwrap();
        assert_eq!(fory.deserialize::<FlatAlias>(&bytes).unwrap(), value);
    }
}

#[test]
fn skipped_option_skips_depth_gate() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register::<FlatSkipped>(146).unwrap();

        let value = FlatSkipped {
            value: 9,
            ignored: Some(Box::new(SkippedChild { value: 10 })),
        };
        let bytes = fory.serialize(&value).unwrap();
        assert_eq!(
            fory.deserialize::<FlatSkipped>(&bytes).unwrap(),
            FlatSkipped {
                value: 9,
                ignored: None,
            }
        );
    }
}

#[test]
fn zero_array_skips_depth_gate() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register::<SkippedChild>(150).unwrap();
        fory.register::<FlatZeroArray>(151).unwrap();

        let value = FlatZeroArray {
            empty: [],
            value: 10,
        };
        let bytes = fory.serialize(&value).unwrap();
        assert_eq!(fory.deserialize::<FlatZeroArray>(&bytes).unwrap(), value);
    }
}

#[test]
fn skipped_custom_default_keeps_depth() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register::<SkippedCustomDefault>(147).unwrap();

        let value = SkippedCustomDefault {
            value: 11,
            ignored: RecursiveDefault::Leaf(12),
        };
        assert!(matches!(&value.ignored, RecursiveDefault::Leaf(12)));
        let bytes = fory.serialize(&value).unwrap();
        assert!(matches!(
            fory.deserialize::<SkippedCustomDefault>(&bytes),
            Err(Error::DepthExceed(_))
        ));
    }
}

#[test]
fn skipped_default_keeps_depth() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register_union::<SkippedDefaultVariant>(149).unwrap();

        let mut bytes = fory.serialize(&SkippedDefaultVariant::Keep).unwrap();
        assert_eq!(bytes[1], RefFlag::NotNullValue as i8 as u8);
        bytes[1] = RefFlag::Null as i8 as u8;
        assert!(matches!(
            fory.deserialize::<SkippedDefaultVariant>(&bytes),
            Err(Error::DepthExceed(_))
        ));
    }
}

#[test]
fn shadowed_leaf_name_keeps_depth_gate() {
    use shadowed_leaf_name::{Root, String};

    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register::<String>(145).unwrap();

        let bytes = fory
            .serialize_with::<Root>(&shadowed_leaf_name::chain())
            .unwrap();
        assert!(matches!(
            fory.deserialize::<String>(&bytes),
            Err(Error::DepthExceed(_))
        ));
    }
}

#[test]
fn compatible_mismatch_depth() {
    let mut writer = Fory::builder()
        .xlang(false)
        .compatible(true)
        .max_struct_depth(0)
        .build();
    writer.register::<RemoteNode>(143).unwrap();

    let mut reader = Fory::builder()
        .xlang(false)
        .compatible(true)
        .max_struct_depth(32)
        .build();
    reader.register::<StaticNode>(143).unwrap();

    let boundary = remote_chain(32);
    let bytes = writer.serialize_with::<RemoteNodeRoot>(&boundary).unwrap();
    assert!(reader.deserialize::<StaticNode>(&bytes).is_ok());

    let too_deep = remote_chain(64);
    let bytes = writer.serialize_with::<RemoteNodeRoot>(&too_deep).unwrap();
    assert!(matches!(
        reader.deserialize::<StaticNode>(&bytes),
        Err(Error::DepthExceed(_))
    ));
}

#[test]
fn static_depth_default_limit() {
    let fory = Fory::builder().xlang(false).compatible(false).build();
    assert_eq!(fory.get_max_struct_depth(), 256);
}

#[test]
fn enum_null_default_depth() {
    let mut fory = Fory::builder()
        .xlang(false)
        .compatible(false)
        .max_struct_depth(16)
        .build();
    fory.register::<RecursiveDefault>(112).unwrap();

    let mut bytes = fory.serialize(&RecursiveDefault::Leaf(7)).unwrap();
    assert_eq!(bytes[1], RefFlag::NotNullValue as i8 as u8);
    bytes[1] = RefFlag::Null as i8 as u8;
    assert!(matches!(
        fory.deserialize::<RecursiveDefault>(&bytes),
        Err(Error::DepthExceed(_))
    ));

    let bytes = fory.serialize(&RecursiveDefault::Leaf(8)).unwrap();
    assert_eq!(
        fory.deserialize::<RecursiveDefault>(&bytes).unwrap(),
        RecursiveDefault::Leaf(8)
    );
}

#[test]
fn skipped_field_default_depth() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(1)
            // A missing depth gate still terminates at this small allocation budget.
            .max_graph_memory_bytes(64)
            .build();
        fory.register::<SkippedRecursiveDefault>(155).unwrap();

        let value = SkippedRecursiveDefault::Branch(Box::new(SkippedRecursiveDefault::Leaf(7)), 8);
        let bytes = fory.serialize(&value).unwrap();
        assert!(matches!(
            fory.deserialize::<SkippedRecursiveDefault>(&bytes),
            Err(Error::DepthExceed(_))
        ));

        let mut bytes = fory.serialize(&SkippedRecursiveDefault::Leaf(9)).unwrap();
        assert_eq!(bytes[1], RefFlag::NotNullValue as i8 as u8);
        bytes[1] = RefFlag::Null as i8 as u8;
        assert!(matches!(
            fory.deserialize::<SkippedRecursiveDefault>(&bytes),
            Err(Error::DepthExceed(_))
        ));

        let bytes = fory.serialize(&SkippedRecursiveDefault::Leaf(10)).unwrap();
        assert!(matches!(
            fory.deserialize::<SkippedRecursiveDefault>(&bytes),
            Ok(SkippedRecursiveDefault::Leaf(10))
        ));
    }
}

#[test]
fn empty_collections_skip_depth_gate() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register::<EmptyCollections>(159).unwrap();

        let mut bytes = fory.serialize(&EmptyCollections::default()).unwrap();
        assert_eq!(bytes[1], RefFlag::NotNullValue as i8 as u8);
        for flag in [RefFlag::NotNullValue, RefFlag::Null] {
            bytes[1] = flag as i8 as u8;
            let value = fory.deserialize::<EmptyCollections>(&bytes).unwrap();
            assert!(value.deque.is_empty());
            assert!(value.list.is_empty());
            assert!(value.heap.is_empty());
            assert!(value.hash_set.is_empty());
            assert!(value.tree_set.is_empty());
        }
    }
}

#[test]
fn empty_defaults_skip_depth_gate() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register::<StaticNode>(156).unwrap();
        fory.register::<EmptyDefault>(157).unwrap();

        let mut bytes = fory
            .serialize_with::<StaticNodeRoot>(&static_chain(1))
            .unwrap();
        assert_eq!(bytes[1], RefFlag::NotNullValue as i8 as u8);
        bytes[1] = RefFlag::Null as i8 as u8;
        assert_eq!(
            fory.deserialize::<StaticNode>(&bytes).unwrap(),
            static_chain(1)
        );

        let mut bytes = fory.serialize(&EmptyDefault::Leaf(7)).unwrap();
        assert_eq!(bytes[1], RefFlag::NotNullValue as i8 as u8);
        bytes[1] = RefFlag::Null as i8 as u8;
        assert_eq!(
            fory.deserialize::<EmptyDefault>(&bytes).unwrap(),
            EmptyDefault::Empty(None, Vec::new())
        );
    }

    let mut writer = Fory::builder().xlang(false).compatible(true).build();
    writer.register::<EmptySource>(158).unwrap();
    let bytes = writer.serialize(&EmptySource::Empty).unwrap();
    let mut reader = Fory::builder()
        .xlang(false)
        .compatible(true)
        .max_struct_depth(0)
        .build();
    reader.register::<EmptyDefault>(158).unwrap();
    assert_eq!(
        reader.deserialize::<EmptyDefault>(&bytes).unwrap(),
        EmptyDefault::Empty(None, Vec::new())
    );
}

#[test]
fn mixed_enum_gates_selected_path() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register_union::<MixedDepthEnum>(153).unwrap();

        let bytes = fory.serialize(&MixedDepthEnum::Leaf(7)).unwrap();
        assert!(matches!(
            fory.deserialize::<MixedDepthEnum>(&bytes),
            Ok(MixedDepthEnum::Leaf(7))
        ));

        let bytes = fory
            .serialize(&MixedDepthEnum::Next(Box::new(MixedDepthEnum::Leaf(8))))
            .unwrap();
        assert!(matches!(
            fory.deserialize::<MixedDepthEnum>(&bytes),
            Err(Error::DepthExceed(_))
        ));
    }
}

#[test]
fn flat_enum_default_skips_gate() {
    for compatible in [false, true] {
        let mut fory = Fory::builder()
            .xlang(false)
            .compatible(compatible)
            .max_struct_depth(0)
            .build();
        fory.register_union::<MixedDepthEnum>(154).unwrap();

        let mut bytes = fory.serialize(&MixedDepthEnum::Leaf(9)).unwrap();
        assert_eq!(bytes[1], RefFlag::NotNullValue as i8 as u8);
        bytes[1] = RefFlag::Null as i8 as u8;
        assert!(matches!(
            fory.deserialize::<MixedDepthEnum>(&bytes),
            Ok(MixedDepthEnum::Leaf(0))
        ));
    }
}

#[test]
fn union_skip_depth() {
    let mut writer = Fory::builder()
        .xlang(true)
        .compatible(true)
        .max_dyn_depth(1)
        .max_struct_depth(1)
        .build();
    writer.register::<SkipWriter>(120).unwrap();
    writer.register_union::<SkipUnion>(121).unwrap();
    let bytes = writer
        .serialize(&SkipWriter {
            kept: 7,
            extra: skip_union_chain(12),
        })
        .unwrap();

    let mut reader = Fory::builder()
        .xlang(true)
        .compatible(true)
        .max_dyn_depth(4)
        .build();
    reader.register::<SkipReader>(120).unwrap();
    assert!(matches!(
        reader.deserialize::<SkipReader>(&bytes),
        Err(Error::DepthExceed(_))
    ));
}

#[test]
fn unknown_union_depth() {
    let mut writer = Fory::builder()
        .xlang(true)
        .compatible(false)
        .max_dyn_depth(1)
        .max_struct_depth(1)
        .build();
    writer.register_union::<FutureUnion>(130).unwrap();
    let bytes = writer.serialize(&future_union_chain(12)).unwrap();

    let mut reader = Fory::builder()
        .xlang(true)
        .compatible(false)
        .max_dyn_depth(4)
        .build();
    reader.register_union::<CurrentUnion>(130).unwrap();
    assert!(matches!(
        reader.deserialize::<CurrentUnion>(&bytes),
        Err(Error::DepthExceed(_))
    ));

    let bytes = writer.serialize(&future_union_chain(1)).unwrap();
    let decoded = reader.deserialize::<CurrentUnion>(&bytes).unwrap();
    let CurrentUnion::Unknown(unknown) = decoded else {
        panic!("expected unknown union case");
    };
    assert_eq!(unknown.case_id(), 1);
}
