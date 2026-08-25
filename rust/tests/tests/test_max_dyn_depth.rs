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
use fory_core::{ReadContext, RefFlag, RefMode, Serializer, TypeId, WriteContext};
use fory_derive::{ForyStruct, ForyUnion};
use std::any::Any;

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

#[derive(ForyStruct, Debug, PartialEq)]
struct LeafStruct {
    count: i32,
    enabled: bool,
    ratio: f64,
}

#[derive(ForyStruct, Debug, PartialEq)]
struct DepthSibling {
    child: Option<Box<LeafStruct>>,
}

#[derive(ForyUnion, Debug, PartialEq)]
enum LeafEnum {
    #[fory(unknown)]
    Unknown(fory_core::UnknownCase),
    #[fory(default)]
    Empty,
    Count(i32),
}

// The selected root owns only the root envelope. This avoids unrelated recursive registration
// metadata while keeping the generated recursive body inside Fory's TLS and root-reset boundary.
struct StaticNodeRoot;

impl Serializer for StaticNodeRoot {
    type Target = StaticNode;

    fn write_data(value: &StaticNode, context: &mut WriteContext) -> Result<(), fory_core::Error> {
        StaticNode::write_data(value, context)
    }

    fn read_data(context: &mut ReadContext) -> Result<StaticNode, fory_core::Error> {
        StaticNode::read_data(context)
    }

    fn write(
        value: &StaticNode,
        context: &mut WriteContext,
        ref_mode: RefMode,
        _write_type_info: bool,
    ) -> Result<(), fory_core::Error> {
        if ref_mode != RefMode::None {
            context.writer.write_i8(RefFlag::NotNullValue as i8);
        }
        StaticNode::write_data(value, context)
    }

    fn read(
        context: &mut ReadContext,
        ref_mode: RefMode,
        _read_type_info: bool,
    ) -> Result<StaticNode, fory_core::Error> {
        if ref_mode != RefMode::None {
            let flag = context.reader.read_i8()?;
            if flag != RefFlag::NotNullValue as i8 {
                return Err(fory_core::Error::invalid_data(
                    "invalid static node root reference flag",
                ));
            }
        }
        StaticNode::read_data(context)
    }

    fn static_type_id() -> TypeId {
        TypeId::STRUCT
    }
}

#[derive(ForyStruct)]
struct RemoteLevel3 {
    value: i32,
}

#[derive(ForyStruct)]
struct RemoteLevel2 {
    next: Option<Box<RemoteLevel3>>,
}

// The remote-only root field forces the compatible reader onto its generated slow body. The leaf
// RemoteLevel3 body is depth-free, while the two structural bodies still exceed a limit of one.
#[derive(ForyStruct)]
struct RemoteLevel1 {
    extra: i32,
    next: Option<Box<RemoteLevel2>>,
}

#[derive(ForyStruct, Debug, PartialEq)]
struct LocalLevel3 {
    value: i32,
}

#[derive(ForyStruct, Debug, PartialEq)]
struct LocalLevel2 {
    next: Option<Box<LocalLevel3>>,
}

#[derive(ForyStruct, Debug, PartialEq)]
struct LocalLevel1 {
    next: Option<Box<LocalLevel2>>,
}

#[derive(ForyUnion, Debug, PartialEq)]
enum StaticList {
    #[fory(unknown)]
    Unknown(fory_core::UnknownCase),
    #[fory(default)]
    End,
    Next(Box<StaticList>),
}

#[derive(ForyUnion)]
enum CompatibleBranch {
    #[fory(unknown)]
    Unknown(fory_core::UnknownCase),
    #[fory(default)]
    End,
    Next {
        next: Option<Box<LeafStruct>>,
    },
}

#[derive(ForyStruct)]
struct CompatibleSiblings {
    #[fory(id = 0)]
    first: CompatibleBranch,
    #[fory(id = 1)]
    second: DepthSibling,
}

fn static_node(depth: i32) -> StaticNode {
    StaticNode {
        value: depth,
        next: (depth > 1).then(|| Box::new(static_node(depth - 1))),
    }
}

fn static_list(depth: i32) -> StaticList {
    if depth > 1 {
        StaticList::Next(Box::new(static_list(depth - 1)))
    } else {
        StaticList::End
    }
}

#[test]
fn leaf_reads_skip_depth() {
    let mut fory = Fory::builder()
        .xlang(false)
        .compatible(false)
        .max_dyn_depth(0)
        .build();
    fory.register::<LeafStruct>(103).unwrap();
    fory.register_union::<LeafEnum>(104).unwrap();

    let value = LeafStruct {
        count: 42,
        enabled: true,
        ratio: 1.5,
    };
    let bytes = fory.serialize(&value).unwrap();
    assert_eq!(fory.deserialize::<LeafStruct>(&bytes).unwrap(), value);

    let value = LeafEnum::Count(42);
    let bytes = fory.serialize(&value).unwrap();
    assert_eq!(fory.deserialize::<LeafEnum>(&bytes).unwrap(), value);
}

#[test]
fn static_struct_depth_and_reset() {
    if fory_core::error::should_panic_on_error() {
        return;
    }
    let fory = Fory::builder()
        .xlang(false)
        .compatible(false)
        .max_dyn_depth(1)
        .build();

    let deep_bytes = fory
        .serialize_with::<StaticNodeRoot>(&static_node(3))
        .unwrap();
    let shallow = static_node(1);
    let shallow_bytes = fory.serialize_with::<StaticNodeRoot>(&shallow).unwrap();

    let deep = fory.deserialize_with::<StaticNodeRoot>(&deep_bytes);
    assert!(deep.is_err(), "recursive static struct must respect depth");
    assert_eq!(
        fory.deserialize_with::<StaticNodeRoot>(&shallow_bytes)
            .unwrap(),
        shallow
    );
}

#[test]
fn compatible_struct_depth_and_reset() {
    if fory_core::error::should_panic_on_error() {
        return;
    }
    let mut writer = Fory::builder().xlang(false).compatible(true).build();
    writer.register::<RemoteLevel3>(201).unwrap();
    writer.register::<RemoteLevel2>(202).unwrap();
    writer.register::<RemoteLevel1>(203).unwrap();

    let mut reader = Fory::builder()
        .xlang(false)
        .compatible(true)
        .max_dyn_depth(1)
        .build();
    reader.register::<LocalLevel3>(201).unwrap();
    reader.register::<LocalLevel2>(202).unwrap();
    reader.register::<LocalLevel1>(203).unwrap();

    let deep_bytes = writer
        .serialize(&RemoteLevel1 {
            extra: 1,
            next: Some(Box::new(RemoteLevel2 {
                next: Some(Box::new(RemoteLevel3 { value: 3 })),
            })),
        })
        .unwrap();
    let shallow_bytes = writer
        .serialize(&RemoteLevel1 {
            extra: 1,
            next: None,
        })
        .unwrap();

    let deep: Result<LocalLevel1, _> = reader.deserialize(&deep_bytes);
    assert!(deep.is_err(), "compatible static struct must respect depth");
    assert_eq!(
        reader.deserialize::<LocalLevel1>(&shallow_bytes).unwrap(),
        LocalLevel1 { next: None }
    );
}

#[test]
fn static_enum_depth_and_reset() {
    if fory_core::error::should_panic_on_error() {
        return;
    }
    let mut fory = Fory::builder()
        .xlang(false)
        .compatible(false)
        .max_dyn_depth(2)
        .build();
    fory.register_union::<StaticList>(102).unwrap();

    let deep_bytes = fory.serialize(&static_list(3)).unwrap();
    let shallow = static_list(1);
    let shallow_bytes = fory.serialize(&shallow).unwrap();

    let deep: Result<StaticList, _> = fory.deserialize(&deep_bytes);
    assert!(deep.is_err(), "recursive static enum must respect depth");
    assert_eq!(
        fory.deserialize::<StaticList>(&shallow_bytes).unwrap(),
        shallow
    );
}

#[test]
fn compatible_enum_restores_depth() {
    let mut fory = Fory::builder()
        .xlang(false)
        .compatible(true)
        .max_dyn_depth(2)
        .build();
    fory.register::<LeafStruct>(205).unwrap();
    fory.register_union::<CompatibleBranch>(204).unwrap();
    fory.register::<DepthSibling>(207).unwrap();
    fory.register::<CompatibleSiblings>(206).unwrap();

    let value = CompatibleSiblings {
        first: CompatibleBranch::Next { next: None },
        second: DepthSibling { child: None },
    };
    let bytes = fory.serialize(&value).unwrap();
    let decoded = fory.deserialize::<CompatibleSiblings>(&bytes).unwrap();
    assert!(matches!(
        decoded.first,
        CompatibleBranch::Next { next: None }
    ));
    assert!(decoded.second.child.is_none());
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
