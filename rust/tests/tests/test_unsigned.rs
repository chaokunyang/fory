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

mod test_helpers;

use fory_core::fory::Fory;
use fory_derive::ForyObject;
use test_helpers::{test_arc_any, test_box_any, test_rc_any, test_roundtrip};

#[test]
fn test_unsigned_numbers() {
    let fory = Fory::default();
    test_roundtrip(&fory, 255u8);
    test_roundtrip(&fory, 65535u16);
    test_roundtrip(&fory, 4294967295u32);
    test_roundtrip(&fory, 18446744073709551615u64);
}

#[test]
fn test_unsigned_arrays() {
    let fory = Fory::default();
    test_roundtrip(&fory, vec![0u8, 1, 2, 255]);
    test_roundtrip(&fory, vec![0u16, 100, 1000, 65535]);
    test_roundtrip(&fory, vec![0u32, 1000, 1000000, 4294967295]);
    test_roundtrip(
        &fory,
        vec![0u64, 1000000, 1000000000000, 18446744073709551615],
    );
}

#[test]
fn test_unsigned_struct_non_compatible() {
    #[derive(ForyObject, Debug, PartialEq)]
    struct UnsignedData {
        a: u8,
        b: u16,
        c: u32,
        d: u64,
        vec_u16: Vec<u16>,
        vec_u32: Vec<u32>,
        vec_u64: Vec<u64>,
    }

    let mut fory = Fory::default();
    fory.register::<UnsignedData>(100).unwrap();

    let data = UnsignedData {
        a: 255,
        b: 65535,
        c: 4294967295,
        d: 18446744073709551615,
        vec_u16: vec![0, 100, 1000, 65535],
        vec_u32: vec![0, 1000, 1000000, 4294967295],
        vec_u64: vec![0, 1000000, 1000000000000, 18446744073709551615],
    };

    let bytes = fory.serialize(&data).unwrap();
    let result: UnsignedData = fory.deserialize(&bytes).unwrap();
    assert_eq!(data, result);
}

#[test]
fn test_unsigned_struct_compatible() {
    #[derive(ForyObject, Debug, PartialEq)]
    struct UnsignedData {
        a: u8,
        b: u16,
        c: u32,
        d: u64,
        vec_u16: Vec<u16>,
        vec_u32: Vec<u32>,
        vec_u64: Vec<u64>,
    }

    let mut fory = Fory::default().compatible(true);
    fory.register::<UnsignedData>(100).unwrap();

    let data = UnsignedData {
        a: 255,
        b: 65535,
        c: 4294967295,
        d: 18446744073709551615,
        vec_u16: vec![0, 100, 1000, 65535],
        vec_u32: vec![0, 1000, 1000000, 4294967295],
        vec_u64: vec![0, 1000000, 1000000000000, 18446744073709551615],
    };

    let bytes = fory.serialize(&data).unwrap();
    let result: UnsignedData = fory.deserialize(&bytes).unwrap();
    assert_eq!(data, result);
}

#[test]
fn test_unsigned_struct_compatible_add_field() {
    #[derive(ForyObject, Debug)]
    struct UnsignedDataV1 {
        a: u8,
        b: u16,
    }

    #[derive(ForyObject, Debug)]
    struct UnsignedDataV2 {
        a: u8,
        b: u16,
        c: u32,
    }

    let mut fory1 = Fory::default().compatible(true);
    let mut fory2 = Fory::default().compatible(true);
    fory1.register::<UnsignedDataV1>(101).unwrap();
    fory2.register::<UnsignedDataV2>(101).unwrap();

    let data_v1 = UnsignedDataV1 { a: 255, b: 65535 };
    let bytes = fory1.serialize(&data_v1).unwrap();
    let result: UnsignedDataV2 = fory2.deserialize(&bytes).unwrap();
    assert_eq!(result.a, 255);
    assert_eq!(result.b, 65535);
    assert_eq!(result.c, 0); // Default value for missing field
}

#[test]
fn test_unsigned_struct_compatible_remove_field() {
    #[derive(ForyObject, Debug)]
    struct UnsignedDataV1 {
        a: u8,
        b: u16,
        c: u32,
    }

    #[derive(ForyObject, Debug)]
    struct UnsignedDataV2 {
        a: u8,
        b: u16,
    }

    let mut fory1 = Fory::default().compatible(true);
    let mut fory2 = Fory::default().compatible(true);
    fory1.register::<UnsignedDataV1>(102).unwrap();
    fory2.register::<UnsignedDataV2>(102).unwrap();

    let data_v1 = UnsignedDataV1 {
        a: 255,
        b: 65535,
        c: 4294967295,
    };
    let bytes = fory1.serialize(&data_v1).unwrap();
    let result: UnsignedDataV2 = fory2.deserialize(&bytes).unwrap();
    assert_eq!(result.a, 255);
    assert_eq!(result.b, 65535);
    // Field c is ignored during deserialization
}

#[test]
fn test_unsigned_edge_cases() {
    let fory = Fory::default();

    // Test minimum values
    test_roundtrip(&fory, 0u8);
    test_roundtrip(&fory, 0u16);
    test_roundtrip(&fory, 0u32);
    test_roundtrip(&fory, 0u64);

    // Test maximum values
    test_roundtrip(&fory, u8::MAX);
    test_roundtrip(&fory, u16::MAX);
    test_roundtrip(&fory, u32::MAX);
    test_roundtrip(&fory, u64::MAX);

    // Test empty arrays
    test_roundtrip(&fory, Vec::<u8>::new());
    test_roundtrip(&fory, Vec::<u16>::new());
    test_roundtrip(&fory, Vec::<u32>::new());
    test_roundtrip(&fory, Vec::<u64>::new());
}

#[test]
fn test_unsigned_with_option_non_compatible() {
    #[derive(ForyObject, Debug, PartialEq)]
    struct OptionalUnsigned {
        opt_u8: Option<u8>,
        opt_u16: Option<u16>,
        opt_u32: Option<u32>,
        opt_u64: Option<u64>,
    }

    let mut fory = Fory::default();
    fory.register::<OptionalUnsigned>(103).unwrap();

    // Test with Some values
    let data_some = OptionalUnsigned {
        opt_u8: Some(255),
        opt_u16: Some(65535),
        opt_u32: Some(4294967295),
        opt_u64: Some(18446744073709551615),
    };

    let bytes = fory.serialize(&data_some).unwrap();
    let result: OptionalUnsigned = fory.deserialize(&bytes).unwrap();
    assert_eq!(data_some, result);

    // Test with None values
    let data_none = OptionalUnsigned {
        opt_u8: None,
        opt_u16: None,
        opt_u32: None,
        opt_u64: None,
    };

    let bytes = fory.serialize(&data_none).unwrap();
    let result: OptionalUnsigned = fory.deserialize(&bytes).unwrap();
    assert_eq!(data_none, result);
}

#[test]
fn test_unsigned_with_option_compatible() {
    #[derive(ForyObject, Debug, PartialEq)]
    struct OptionalUnsigned {
        opt_u8: Option<u8>,
        opt_u16: Option<u16>,
        opt_u32: Option<u32>,
        opt_u64: Option<u64>,
    }

    let mut fory = Fory::default().compatible(true);
    fory.register::<OptionalUnsigned>(104).unwrap();

    // Test with Some values
    let data_some = OptionalUnsigned {
        opt_u8: Some(255),
        opt_u16: Some(65535),
        opt_u32: Some(4294967295),
        opt_u64: Some(18446744073709551615),
    };

    let bytes = fory.serialize(&data_some).unwrap();
    let result: OptionalUnsigned = fory.deserialize(&bytes).unwrap();
    assert_eq!(data_some, result);

    // Test with None values
    let data_none = OptionalUnsigned {
        opt_u8: None,
        opt_u16: None,
        opt_u32: None,
        opt_u64: None,
    };

    let bytes = fory.serialize(&data_none).unwrap();
    let result: OptionalUnsigned = fory.deserialize(&bytes).unwrap();
    assert_eq!(data_none, result);
}

#[test]
fn test_unsigned_mixed_fields_compatible() {
    #[derive(ForyObject, Debug)]
    struct MixedDataV1 {
        required_u8: u8,
        optional_u16: Option<u16>,
        vec_u32: Vec<u32>,
    }

    #[derive(ForyObject, Debug)]
    struct MixedDataV2 {
        required_u8: u8,
        optional_u16: Option<u16>,
        vec_u32: Vec<u32>,
        new_u64: u64,
        new_opt_u32: Option<u32>,
    }

    let mut fory1 = Fory::default().compatible(true);
    let mut fory2 = Fory::default().compatible(true);
    fory1.register::<MixedDataV1>(105).unwrap();
    fory2.register::<MixedDataV2>(105).unwrap();

    let data_v1 = MixedDataV1 {
        required_u8: 255,
        optional_u16: Some(65535),
        vec_u32: vec![1000, 2000, 3000],
    };

    let bytes = fory1.serialize(&data_v1).unwrap();
    let result: MixedDataV2 = fory2.deserialize(&bytes).unwrap();
    assert_eq!(result.required_u8, 255);
    assert_eq!(result.optional_u16, Some(65535));
    assert_eq!(result.vec_u32, vec![1000, 2000, 3000]);
    assert_eq!(result.new_u64, 0); // Default value
    assert_eq!(result.new_opt_u32, None); // Default value
}

#[test]
fn test_unsigned_with_smart_pointers() {
    let fory = Fory::default();

    // Test Box<dyn Any> with unsigned types
    test_box_any(&fory, 255u8);
    test_box_any(&fory, 65535u16);
    test_box_any(&fory, 4294967295u32);
    test_box_any(&fory, 18446744073709551615u64);

    // Test Rc<dyn Any> with unsigned types
    test_rc_any(&fory, 255u8);
    test_rc_any(&fory, 65535u16);
    test_rc_any(&fory, 4294967295u32);
    test_rc_any(&fory, 18446744073709551615u64);

    // Test Arc<dyn Any> with unsigned types
    test_arc_any(&fory, 255u8);
    test_arc_any(&fory, 65535u16);
    test_arc_any(&fory, 4294967295u32);
    test_arc_any(&fory, 18446744073709551615u64);

    // Test Box<dyn Any> with unsigned arrays
    test_box_any(&fory, vec![0u8, 127, 255]);
    test_box_any(&fory, vec![0u16, 1000, 65535]);
    test_box_any(&fory, vec![0u32, 1000000, 4294967295]);
    test_box_any(&fory, vec![0u64, 1000000000000, 18446744073709551615]);

    // Test Rc<dyn Any> with unsigned arrays
    test_rc_any(&fory, vec![100u16, 200, 300, 65535]);
    test_rc_any(&fory, vec![1000u32, 2000, 3000, 4294967295]);

    // Test Arc<dyn Any> with unsigned arrays
    test_arc_any(&fory, vec![999u32, 888, 777, 4294967295]);
    test_arc_any(&fory, vec![123u64, 456789, 987654321, 18446744073709551615]);
}
