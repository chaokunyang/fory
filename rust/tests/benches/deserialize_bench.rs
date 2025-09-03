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

use criterion::{black_box, criterion_group, criterion_main, Criterion};
use fory_core::fory::Fory;
use fory_derive::Fory;

// Struct with 50 numeric fs
#[derive(Fory, Debug, Clone, Default)]
struct NumericStruct {
    f1: i8,
    f2: i8,
    f3: i16,
    f4: i16,
    f5: i32,
    f6: i32,
    f7: i64,
    f8: i64,
    f9: f32,
    f10: f64,
    f11: i8,
    f12: i8,
    f13: i16,
    f14: i16,
    f15: i32,
    f16: i32,
    f17: i64,
    f18: i64,
    f19: f32,
    f20: f64,
    f21: i8,
    f22: i8,
    f23: i16,
    f24: i16,
    f25: i32,
    f26: i32,
    f27: i64,
    f28: i64,
    f29: f32,
    f30: f64,
    f31: i8,
    f32: i8,
    f33: i16,
    f34: i16,
    f35: i32,
    f36: i32,
    f37: i64,
    f38: i64,
    f39: f32,
    f40: f64,
    f41: i8,
    f42: i8,
    f43: i16,
    f44: i16,
    f45: i32,
    f46: i32,
    f47: i64,
    f48: i64,
    f49: f32,
    f50: f64,
}

// Struct with 20 String fs
#[derive(Fory, Debug, Clone, Default)]
struct StringStruct {
    f1: String,
    f2: String,
    f3: String,
    f4: String,
    f5: String,
    f6: String,
    f7: String,
    f8: String,
    f9: String,
    f10: String,
    f11: String,
    f12: String,
    f13: String,
    f14: String,
    f15: String,
    f16: String,
    f17: String,
    f18: String,
    f19: String,
    f20: String,
}

impl NumericStruct {
    fn new() -> Self {
        Self {
            f1: 1,
            f2: 2,
            f3: 3,
            f4: 4,
            f5: 5,
            f6: 6,
            f7: 7,
            f8: 8,
            f9: 9.0,
            f10: 10.0,
            f11: 11,
            f12: 12,
            f13: 13,
            f14: 14,
            f15: 15,
            f16: 16,
            f17: 17,
            f18: 18,
            f19: 19.0,
            f20: 20.0,
            f21: 21,
            f22: 22,
            f23: 23,
            f24: 24,
            f25: 25,
            f26: 26,
            f27: 27,
            f28: 28,
            f29: 29.0,
            f30: 30.0,
            f31: 31,
            f32: 32,
            f33: 33,
            f34: 34,
            f35: 35,
            f36: 36,
            f37: 37,
            f38: 38,
            f39: 39.0,
            f40: 40.0,
            f41: 41,
            f42: 42,
            f43: 43,
            f44: 44,
            f45: 45,
            f46: 46,
            f47: 47,
            f48: 48,
            f49: 49.0,
            f50: 50.0,
        }
    }
}

impl StringStruct {
    fn new() -> Self {
        Self {
            f1: "f1".to_string(),
            f2: "f2".to_string(),
            f3: "f3".to_string(),
            f4: "f4".to_string(),
            f5: "f5".to_string(),
            f6: "f6".to_string(),
            f7: "f7".to_string(),
            f8: "f8".to_string(),
            f9: "f9".to_string(),
            f10: "f10".to_string(),
            f11: "f11".to_string(),
            f12: "f12".to_string(),
            f13: "f13".to_string(),
            f14: "f14".to_string(),
            f15: "f15".to_string(),
            f16: "f16".to_string(),
            f17: "f17".to_string(),
            f18: "f18".to_string(),
            f19: "f19".to_string(),
            f20: "f20".to_string(),
        }
    }
}

fn bench_numeric_struct_old_method(c: &mut Criterion) {
    let mut fory = Fory::default();
    fory.register::<NumericStruct>(100);

    let original = NumericStruct::new();
    let bin = fory.serialize(&original);

    c.bench_function("numeric_struct_old_deserialize", |b| {
        b.iter(|| {
            let result: NumericStruct = fory.deserialize(black_box(&bin)).unwrap();
            black_box(&result);
        });
    });
}

fn bench_numeric_struct_new_method(c: &mut Criterion) {
    let mut fory = Fory::default();
    fory.register::<NumericStruct>(100);

    let original = NumericStruct::new();
    let bin = fory.serialize(&original);

    c.bench_function("numeric_struct_new_deserialize_into", |b| {
        b.iter(|| {
            let mut output = NumericStruct::default();
            fory.deserialize_into(black_box(&bin), &mut output).unwrap();
            black_box(&output);
        });
    });
}

fn bench_string_struct_old_method(c: &mut Criterion) {
    let mut fory = Fory::default();
    fory.register::<StringStruct>(200);

    let original = StringStruct::new();
    let bin = fory.serialize(&original);

    c.bench_function("string_struct_old_deserialize", |b| {
        b.iter(|| {
            let result: StringStruct = fory.deserialize(black_box(&bin)).unwrap();
            black_box(&result);
        });
    });
}

fn bench_string_struct_new_method(c: &mut Criterion) {
    let mut fory = Fory::default();
    fory.register::<StringStruct>(200);

    let original = StringStruct::new();
    let bin = fory.serialize(&original);

    c.bench_function("string_struct_new_deserialize_into", |b| {
        b.iter(|| {
            let mut output = StringStruct::default();
            fory.deserialize_into(black_box(&bin), &mut output).unwrap();
            black_box(&output);
        });
    });
}

fn bench_reuse_allocation(c: &mut Criterion) {
    let mut fory = Fory::default();
    fory.register::<StringStruct>(200);

    let original1 = StringStruct::new();
    let original2 = StringStruct::new();
    let bin1 = fory.serialize(&original1);
    let bin2 = fory.serialize(&original2);

    c.bench_function("string_struct_reuse_allocation", |b| {
        b.iter(|| {
            let mut output = StringStruct::default();
            fory.deserialize_into(black_box(&bin1), &mut output)
                .unwrap();
            fory.deserialize_into(black_box(&bin2), &mut output)
                .unwrap();
            black_box(&output);
        });
    });
}

criterion_group!(
    benches,
    bench_numeric_struct_old_method,
    bench_numeric_struct_new_method,
    bench_string_struct_old_method,
    bench_string_struct_new_method,
    bench_reuse_allocation
);
criterion_main!(benches);
