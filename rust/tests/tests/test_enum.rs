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

// RUSTFLAGS="-Awarnings" cargo expand -p tests --test test_enum

use fory_core::Fory;
use fory_derive::ForyObject;
use std::collections::HashMap;

#[test]
fn basic() {
    #[derive(ForyObject, Debug, PartialEq)]
    enum Token {
        Plus,
        Number(i64),
        Ident(String),
        Assign { target: String, value: i32 },
        Other(Option<i64>),
        Child(Box<Token>),
        Map(HashMap<String, Token>),
    }

    let mut fory = Fory::default().xlang(false);
    fory.register::<Token>(1000).unwrap();

    let mut map = HashMap::new();
    map.insert("one".to_string(), Token::Number(1));
    map.insert("plus".to_string(), Token::Plus);
    map.insert(
        "nested".to_string(),
        Token::Child(Box::new(Token::Ident("deep".to_string()))),
    );

    let tokens = vec![
        Token::Plus,
        Token::Number(1),
        Token::Ident("foo".to_string()),
        Token::Assign {
            target: "bar".to_string(),
            value: 42,
        },
        Token::Other(Some(42)),
        Token::Other(None),
        Token::Child(Box::from(Token::Child(Box::from(Token::Other(None))))),
        Token::Map(map),
    ];
    let bin = fory.serialize(&tokens).unwrap();
    let new_tokens = fory.deserialize::<Vec<Token>>(&bin).unwrap();
    assert_eq!(tokens, new_tokens);
}

#[test]
fn named_enum() {
    #[derive(ForyObject, Debug, PartialEq)]
    enum Token1 {
        Assign { target: String, value: i32 },
    }

    #[derive(ForyObject, Debug, PartialEq)]
    enum Token2 {
        Assign { value: i32, target: String },
    }

    let mut fory1 = Fory::default().xlang(false);
    fory1.register::<Token1>(1000).unwrap();

    let mut fory2 = Fory::default().xlang(false);
    fory2.register::<Token2>(1000).unwrap();

    let token = Token1::Assign {
        target: "bar".to_string(),
        value: 42,
    };
    let bin = fory1.serialize(&token).unwrap();
    let new_token = fory2.deserialize::<Token2>(&bin).unwrap();

    let Token1::Assign {
        target: target1,
        value: value1,
    } = token;
    let Token2::Assign {
        target: target2,
        value: value2,
    } = new_token;
    assert_eq!(target1, target2);
    assert_eq!(value1, value2);
}

/// Test schema evolution for unnamed enum variants in compatible mode
#[test]
fn test_unnamed_enum_variant_compatible() {
    // Original enum with 2 fields in variant
    #[derive(ForyObject, Debug, PartialEq)]
    enum EventV1 {
        #[fory(default)]
        Unknown,
        Message(i32, String),
    }

    // Evolved enum with 3 fields in variant (added f64)
    #[derive(ForyObject, Debug, PartialEq)]
    enum EventV2 {
        #[fory(default)]
        Unknown,
        Message(i32, String, f64),
    }

    // Test 1: Serialize v1 (2 fields), deserialize as v2 (3 fields)
    let mut fory_v1 = Fory::default().xlang(false).compatible(true);
    fory_v1.register::<EventV1>(2000).unwrap();

    let mut fory_v2 = Fory::default().xlang(false).compatible(true);
    fory_v2.register::<EventV2>(2000).unwrap();

    let event_v1 = EventV1::Message(42, "hello".to_string());
    let bin = fory_v1.serialize(&event_v1).unwrap();
    let event_v2: EventV2 = fory_v2.deserialize(&bin).expect("deserialize v1 to v2");
    match event_v2 {
        EventV2::Message(a, b, c) => {
            assert_eq!(a, 42);
            assert_eq!(b, "hello");
            assert_eq!(c, 0.0); // Default value for missing field
        }
        _ => panic!("Expected Message variant"),
    }

    // Test 2: Serialize v2 (3 fields), deserialize as v1 (2 fields)
    let event_v2 = EventV2::Message(100, "world".to_string(), 3.14);
    let bin = fory_v2.serialize(&event_v2).unwrap();
    let event_v1: EventV1 = fory_v1.deserialize(&bin).expect("deserialize v2 to v1");
    match event_v1 {
        EventV1::Message(a, b) => {
            assert_eq!(a, 100);
            assert_eq!(b, "world");
            // Extra field (3.14) is skipped during deserialization
        }
        _ => panic!("Expected Message variant"),
    }

    // Test 3: Unknown variant falls back to default
    #[derive(ForyObject, Debug, PartialEq)]
    enum EventV3 {
        #[fory(default)]
        Unknown,
        Message(i32, String),
        NewVariant(bool), // This variant doesn't exist in EventV1
    }

    let mut fory_v3 = Fory::default().xlang(false).compatible(true);
    fory_v3.register::<EventV3>(2000).unwrap();

    let event_v3 = EventV3::NewVariant(true);
    let bin = fory_v3.serialize(&event_v3).unwrap();
    let event_v1: EventV1 = fory_v1
        .deserialize(&bin)
        .expect("deserialize unknown variant");
    assert_eq!(event_v1, EventV1::Unknown); // Falls back to default variant
}

/// Test schema evolution for named enum variants in compatible mode
#[test]
fn test_named_enum_variant_compatible() {
    // Original enum with 2 fields
    #[derive(ForyObject, Debug, PartialEq)]
    enum CommandV1 {
        #[fory(default)]
        Noop,
        Execute {
            args: i32,
            name: String,
        },
    }

    // Evolved enum with 3 fields - added 'env' field
    #[derive(ForyObject, Debug, PartialEq)]
    enum CommandV2 {
        #[fory(default)]
        Noop,
        Execute {
            args: i32,
            env: String,
            name: String,
        },
    }

    let mut fory_v1 = Fory::default().xlang(false).compatible(true);
    fory_v1.register::<CommandV1>(3000).unwrap();

    let mut fory_v2 = Fory::default().xlang(false).compatible(true);
    fory_v2.register::<CommandV2>(3000).unwrap();

    // Test 1: Serialize v1, deserialize as v2 (new field gets default value)
    let cmd_v1 = CommandV1::Execute {
        args: 42,
        name: "run".to_string(),
    };
    let bin = fory_v1.serialize(&cmd_v1).unwrap();
    let cmd_v2: CommandV2 = fory_v2.deserialize(&bin).expect("deserialize v1 to v2");
    match cmd_v2 {
        CommandV2::Execute { args, env, name } => {
            assert_eq!(args, 42);
            assert_eq!(name, "run");
            assert_eq!(env, ""); // Default value for missing field
        }
        _ => panic!("Expected Execute variant"),
    }

    // Test 2: Serialize v2, deserialize as v1 (extra field is skipped)
    let cmd_v2 = CommandV2::Execute {
        args: 100,
        env: "prod".to_string(),
        name: "test".to_string(),
    };
    let bin = fory_v2.serialize(&cmd_v2).unwrap();
    let cmd_v1: CommandV1 = fory_v1.deserialize(&bin).expect("deserialize v2 to v1");
    match cmd_v1 {
        CommandV1::Execute { args, name } => {
            assert_eq!(args, 100);
            assert_eq!(name, "test");
            // 'env' field is skipped
        }
        _ => panic!("Expected Execute variant"),
    }
}
