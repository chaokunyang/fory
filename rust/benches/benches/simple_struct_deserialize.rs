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

use criterion::{criterion_group, criterion_main, BenchmarkId, Criterion, black_box};
use fory_benchmarks::serializers::fury::FurySerializer;
use fory_benchmarks::serializers::Serializer;
use fory_benchmarks::models::simple::SimpleStruct;
use fory_benchmarks::models::TestDataGenerator;

fn bench_simple_struct_deserialize(c: &mut Criterion) {
    let fury_serializer = FurySerializer::new();
    
    // Generate small simple struct data
    let simple_struct_small = SimpleStruct::generate_small();
    
    // Pre-serialize the data
    let serialized_data = fury_serializer.serialize(&simple_struct_small).unwrap();
    
    let mut group = c.benchmark_group("simple_struct_deserialize");
    
    group.bench_with_input(
        BenchmarkId::new("fury_deserialize", "small"),
        &serialized_data,
        |b, data| {
            b.iter(|| {
                let _: SimpleStruct = black_box(fury_serializer.deserialize(black_box(data)).unwrap());
            })
        },
    );
    
    group.finish();
}

criterion_group!(benches, bench_simple_struct_deserialize);
criterion_main!(benches);
