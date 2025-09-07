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

use fory_benchmarks::serializers::fury::FurySerializer;
use fory_benchmarks::serializers::Serializer;
use fory_benchmarks::models::simple::SimpleStruct;
use fory_benchmarks::models::TestDataGenerator;
use std::hint::black_box;

fn main() {
    println!("Starting Fury deserialization profiling...");
    
    // Initialize the Fury serializer
    let fury_serializer = FurySerializer::new();
    
    // Generate test data
    let simple_struct = SimpleStruct::generate_small();
    println!("Generated test struct: {:?}", simple_struct);
    
    // Pre-serialize the data once
    let serialized_data = fury_serializer.serialize(&simple_struct).unwrap();
    println!("Serialized data size: {} bytes", serialized_data.len());
    
    // Warm up - run a few iterations to get the code in cache
    for _ in 0..1000 {
        let _: SimpleStruct = black_box(fury_serializer.deserialize(black_box(&serialized_data)).unwrap());
    }
    
    println!("Warmup complete. Starting main profiling loop...");
    
    // Main profiling loop - run many iterations to get good profiling data
    const ITERATIONS: usize = 10_000_000;
    for i in 0..ITERATIONS {
        let _: SimpleStruct = black_box(fury_serializer.deserialize(black_box(&serialized_data)).unwrap());
        
        // Print progress every million iterations
        if i % 1_000_000 == 0 && i > 0 {
            println!("Completed {} iterations", i);
        }
    }
    
    println!("Profiling complete! Ran {} iterations", ITERATIONS);
}
