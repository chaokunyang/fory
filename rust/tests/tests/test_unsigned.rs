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
use fory_derive::ForyObject;
use std::any::Any;
use std::rc::Rc;
use std::sync::Arc;

#[test]
fn test_unsigned_numbers() {
    let fory = Fory::default();

    // Test u8
    let val_u8: u8 = 255;
    let bytes = fory.serialize(&val_u8).unwrap();
    let result: u8 = fory.deserialize(&bytes).unwrap();
    assert_eq!(val_u8, result);

    // Test u16
    let val_u16: u16 = 65535;
    let bytes = fory.serialize(&val_u16).unwrap();
    let result: u16 = fory.deserialize(&bytes).unwrap();
    assert_eq!(val_u16, result);

    // Test u32
    let val_u32: u32 = 4294967295;
    let bytes = fory.serialize(&val_u32).unwrap();
    let result: u32 = fory.deserialize(&bytes).unwrap();
    assert_eq!(val_u32, result);

    // Test u64
    let val_u64: u64 = 18446744073709551615;
    let bytes = fory.serialize(&val_u64).unwrap();
    let result: u64 = fory.deserialize(&bytes).unwrap();
    assert_eq!(val_u64, result);
}

#[test]
fn test_unsigned_arrays() {
    let fory = Fory::default();

    // Test Vec<u8>
    let vec_u8 = vec![0u8, 1, 2, 255];
    let bytes = fory.serialize(&vec_u8).unwrap();
    let result: Vec<u8> = fory.deserialize(&bytes).unwrap();
    assert_eq!(vec_u8, result);

    // Test Vec<u16>
    let vec_u16 = vec![0u16, 100, 1000, 65535];
    let bytes = fory.serialize(&vec_u16).unwrap();
    let result: Vec<u16> = fory.deserialize(&bytes).unwrap();
    assert_eq!(vec_u16, result);

    // Test Vec<u32>
    let vec_u32 = vec![0u32, 1000, 1000000, 4294967295];
    let bytes = fory.serialize(&vec_u32).unwrap();
    let result: Vec<u32> = fory.deserialize(&bytes).unwrap();
    assert_eq!(vec_u32, result);

    // Test Vec<u64>
    let vec_u64 = vec![0u64, 1000000, 1000000000000, 18446744073709551615];
    let bytes = fory.serialize(&vec_u64).unwrap();
    let result: Vec<u64> = fory.deserialize(&bytes).unwrap();
    assert_eq!(vec_u64, result);
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
    assert_eq!(
        0u8,
        fory.deserialize(&fory.serialize(&0u8).unwrap()).unwrap()
    );
    assert_eq!(
        0u16,
        fory.deserialize(&fory.serialize(&0u16).unwrap()).unwrap()
    );
    assert_eq!(
        0u32,
        fory.deserialize(&fory.serialize(&0u32).unwrap()).unwrap()
    );
    assert_eq!(
        0u64,
        fory.deserialize(&fory.serialize(&0u64).unwrap()).unwrap()
    );

    // Test maximum values
    assert_eq!(
        u8::MAX,
        fory.deserialize(&fory.serialize(&u8::MAX).unwrap())
            .unwrap()
    );
    assert_eq!(
        u16::MAX,
        fory.deserialize(&fory.serialize(&u16::MAX).unwrap())
            .unwrap()
    );
    assert_eq!(
        u32::MAX,
        fory.deserialize(&fory.serialize(&u32::MAX).unwrap())
            .unwrap()
    );
    assert_eq!(
        u64::MAX,
        fory.deserialize(&fory.serialize(&u64::MAX).unwrap())
            .unwrap()
    );

    // Test empty arrays
    let empty_u8: Vec<u8> = vec![];
    let empty_u16: Vec<u16> = vec![];
    let empty_u32: Vec<u32> = vec![];
    let empty_u64: Vec<u64> = vec![];

    assert_eq!(
        empty_u8,
        fory.deserialize::<Vec<u8>>(&fory.serialize(&empty_u8).unwrap())
            .unwrap()
    );
    assert_eq!(
        empty_u16,
        fory.deserialize::<Vec<u16>>(&fory.serialize(&empty_u16).unwrap())
            .unwrap()
    );
    assert_eq!(
        empty_u32,
        fory.deserialize::<Vec<u32>>(&fory.serialize(&empty_u32).unwrap())
            .unwrap()
    );
    assert_eq!(
        empty_u64,
        fory.deserialize::<Vec<u64>>(&fory.serialize(&empty_u64).unwrap())
            .unwrap()
    );
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
    let box_u8: Box<dyn Any> = Box::new(255u8);
    let bytes = fory.serialize(&box_u8).unwrap();
    let result: Box<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(result.downcast_ref::<u8>().unwrap(), &255u8);

    let box_u16: Box<dyn Any> = Box::new(65535u16);
    let bytes = fory.serialize(&box_u16).unwrap();
    let result: Box<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(result.downcast_ref::<u16>().unwrap(), &65535u16);

    let box_u32: Box<dyn Any> = Box::new(4294967295u32);
    let bytes = fory.serialize(&box_u32).unwrap();
    let result: Box<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(result.downcast_ref::<u32>().unwrap(), &4294967295u32);

    let box_u64: Box<dyn Any> = Box::new(18446744073709551615u64);
    let bytes = fory.serialize(&box_u64).unwrap();
    let result: Box<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<u64>().unwrap(),
        &18446744073709551615u64
    );

    // Test Rc<dyn Any> with unsigned types
    let rc_u8: Rc<dyn Any> = Rc::new(255u8);
    let bytes = fory.serialize(&rc_u8).unwrap();
    let result: Rc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(result.downcast_ref::<u8>().unwrap(), &255u8);

    let rc_u16: Rc<dyn Any> = Rc::new(65535u16);
    let bytes = fory.serialize(&rc_u16).unwrap();
    let result: Rc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(result.downcast_ref::<u16>().unwrap(), &65535u16);

    let rc_u32: Rc<dyn Any> = Rc::new(4294967295u32);
    let bytes = fory.serialize(&rc_u32).unwrap();
    let result: Rc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(result.downcast_ref::<u32>().unwrap(), &4294967295u32);

    let rc_u64: Rc<dyn Any> = Rc::new(18446744073709551615u64);
    let bytes = fory.serialize(&rc_u64).unwrap();
    let result: Rc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<u64>().unwrap(),
        &18446744073709551615u64
    );

    // Test Arc<dyn Any> with unsigned types
    let arc_u8: Arc<dyn Any> = Arc::new(255u8);
    let bytes = fory.serialize(&arc_u8).unwrap();
    let result: Arc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(result.downcast_ref::<u8>().unwrap(), &255u8);

    let arc_u16: Arc<dyn Any> = Arc::new(65535u16);
    let bytes = fory.serialize(&arc_u16).unwrap();
    let result: Arc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(result.downcast_ref::<u16>().unwrap(), &65535u16);

    let arc_u32: Arc<dyn Any> = Arc::new(4294967295u32);
    let bytes = fory.serialize(&arc_u32).unwrap();
    let result: Arc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(result.downcast_ref::<u32>().unwrap(), &4294967295u32);

    let arc_u64: Arc<dyn Any> = Arc::new(18446744073709551615u64);
    let bytes = fory.serialize(&arc_u64).unwrap();
    let result: Arc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<u64>().unwrap(),
        &18446744073709551615u64
    );

    // Test Box<dyn Any> with unsigned arrays
    let box_vec_u8: Box<dyn Any> = Box::new(vec![0u8, 127, 255]);
    let bytes = fory.serialize(&box_vec_u8).unwrap();
    let result: Box<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<Vec<u8>>().unwrap(),
        &vec![0u8, 127, 255]
    );

    let box_vec_u16: Box<dyn Any> = Box::new(vec![0u16, 1000, 65535]);
    let bytes = fory.serialize(&box_vec_u16).unwrap();
    let result: Box<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<Vec<u16>>().unwrap(),
        &vec![0u16, 1000, 65535]
    );

    let box_vec_u32: Box<dyn Any> = Box::new(vec![0u32, 1000000, 4294967295]);
    let bytes = fory.serialize(&box_vec_u32).unwrap();
    let result: Box<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<Vec<u32>>().unwrap(),
        &vec![0u32, 1000000, 4294967295]
    );

    let box_vec_u64: Box<dyn Any> = Box::new(vec![0u64, 1000000000000, 18446744073709551615]);
    let bytes = fory.serialize(&box_vec_u64).unwrap();
    let result: Box<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<Vec<u64>>().unwrap(),
        &vec![0u64, 1000000000000, 18446744073709551615]
    );

    // Test Rc<dyn Any> with unsigned arrays
    let rc_vec_u16: Rc<dyn Any> = Rc::new(vec![100u16, 200, 300, 65535]);
    let bytes = fory.serialize(&rc_vec_u16).unwrap();
    let result: Rc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<Vec<u16>>().unwrap(),
        &vec![100u16, 200, 300, 65535]
    );

    let rc_vec_u32: Rc<dyn Any> = Rc::new(vec![1000u32, 2000, 3000, 4294967295]);
    let bytes = fory.serialize(&rc_vec_u32).unwrap();
    let result: Rc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<Vec<u32>>().unwrap(),
        &vec![1000u32, 2000, 3000, 4294967295]
    );

    // Test Arc<dyn Any> with unsigned arrays
    let arc_vec_u32: Arc<dyn Any> = Arc::new(vec![999u32, 888, 777, 4294967295]);
    let bytes = fory.serialize(&arc_vec_u32).unwrap();
    let result: Arc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<Vec<u32>>().unwrap(),
        &vec![999u32, 888, 777, 4294967295]
    );

    let arc_vec_u64: Arc<dyn Any> = Arc::new(vec![123u64, 456789, 987654321, 18446744073709551615]);
    let bytes = fory.serialize(&arc_vec_u64).unwrap();
    let result: Arc<dyn Any> = fory.deserialize(&bytes).unwrap();
    assert_eq!(
        result.downcast_ref::<Vec<u64>>().unwrap(),
        &vec![123u64, 456789, 987654321, 18446744073709551615]
    );
}
