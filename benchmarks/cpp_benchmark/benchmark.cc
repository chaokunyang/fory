/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

#include <benchmark/benchmark.h>
#include <cstdint>
#include <string>
#include <vector>

#include "bench.pb.h"
#include "fory/serialization/context.h"
#include "fory/serialization/fory.h"
#include "fory/serialization/struct_serializer.h"

// ============================================================================
// Fory struct definitions (must match proto messages)
// ============================================================================

struct NumericStruct {
  int32_t f1;
  int32_t f2;
  int32_t f3;
  int32_t f4;
  int32_t f5;
  int32_t f6;
  int32_t f7;
  int32_t f8;

  bool operator==(const NumericStruct &other) const {
    return f1 == other.f1 && f2 == other.f2 && f3 == other.f3 &&
           f4 == other.f4 && f5 == other.f5 && f6 == other.f6 &&
           f7 == other.f7 && f8 == other.f8;
  }
};
FORY_STRUCT(NumericStruct, f1, f2, f3, f4, f5, f6, f7, f8);

struct Sample {
  int32_t int_value;
  int64_t long_value;
  float float_value;
  double double_value;
  int32_t short_value;
  int32_t char_value;
  bool boolean_value;
  int32_t int_value_boxed;
  int64_t long_value_boxed;
  float float_value_boxed;
  double double_value_boxed;
  int32_t short_value_boxed;
  int32_t char_value_boxed;
  bool boolean_value_boxed;
  std::vector<int32_t> int_array;
  std::vector<int64_t> long_array;
  std::vector<float> float_array;
  std::vector<double> double_array;
  std::vector<int32_t> short_array;
  std::vector<int32_t> char_array;
  std::vector<bool> boolean_array;
  std::string string;

  bool operator==(const Sample &other) const {
    return int_value == other.int_value && long_value == other.long_value &&
           float_value == other.float_value &&
           double_value == other.double_value &&
           short_value == other.short_value && char_value == other.char_value &&
           boolean_value == other.boolean_value &&
           int_value_boxed == other.int_value_boxed &&
           long_value_boxed == other.long_value_boxed &&
           float_value_boxed == other.float_value_boxed &&
           double_value_boxed == other.double_value_boxed &&
           short_value_boxed == other.short_value_boxed &&
           char_value_boxed == other.char_value_boxed &&
           boolean_value_boxed == other.boolean_value_boxed &&
           int_array == other.int_array && long_array == other.long_array &&
           float_array == other.float_array &&
           double_array == other.double_array &&
           short_array == other.short_array && char_array == other.char_array &&
           boolean_array == other.boolean_array && string == other.string;
  }
};
FORY_STRUCT(Sample, int_value, long_value, float_value, double_value,
            short_value, char_value, boolean_value, int_value_boxed,
            long_value_boxed, float_value_boxed, double_value_boxed,
            short_value_boxed, char_value_boxed, boolean_value_boxed, int_array,
            long_array, float_array, double_array, short_array, char_array,
            boolean_array, string);

// ============================================================================
// Test data creation
// ============================================================================

NumericStruct CreateNumericStruct() {
  // Use mixed positive/negative int32 values for realistic benchmark
  return NumericStruct{
      -12345,     // f1: negative
      987654321,  // f2: large positive
      -31415,     // f3: negative
      27182818,   // f4: positive
      -32000,     // f5: negative (near int16 min)
      1000000,    // f6: medium positive
      -999999999, // f7: large negative
      42          // f8: small positive
  };
}

// ============================================================================
// Protobuf conversion functions (like Java benchmark's
// buildPBStruct/fromPBObject)
// ============================================================================

/// Convert plain C++ struct to protobuf message (for serialization)
inline protobuf::Struct ToPbStruct(const NumericStruct &obj) {
  protobuf::Struct pb;
  pb.set_f1(obj.f1);
  pb.set_f2(obj.f2);
  pb.set_f3(obj.f3);
  pb.set_f4(obj.f4);
  pb.set_f5(obj.f5);
  pb.set_f6(obj.f6);
  pb.set_f7(obj.f7);
  pb.set_f8(obj.f8);
  return pb;
}

/// Convert protobuf message to plain C++ struct (for deserialization)
inline NumericStruct FromPbStruct(const protobuf::Struct &pb) {
  NumericStruct obj;
  obj.f1 = pb.f1();
  obj.f2 = pb.f2();
  obj.f3 = pb.f3();
  obj.f4 = pb.f4();
  obj.f5 = pb.f5();
  obj.f6 = pb.f6();
  obj.f7 = pb.f7();
  obj.f8 = pb.f8();
  return obj;
}

protobuf::Struct CreateProtoStruct() {
  return ToPbStruct(CreateNumericStruct());
}

Sample CreateSample() {
  // Consistent with Java Sample.populate() for fair cross-language comparison
  Sample sample;
  sample.int_value = 123;
  sample.long_value = 1230000LL;
  sample.float_value = 12.345f;
  sample.double_value = 1.234567;
  sample.short_value = 12345;
  sample.char_value = '!'; // 33
  sample.boolean_value = true;

  sample.int_value_boxed = 321;
  sample.long_value_boxed = 3210000LL;
  sample.float_value_boxed = 54.321f;
  sample.double_value_boxed = 7.654321;
  sample.short_value_boxed = 32100;
  sample.char_value_boxed = '$'; // 36
  sample.boolean_value_boxed = false;

  // Arrays with mixed positive/negative values (same as Java)
  sample.int_array = {-1234, -123, -12, -1, 0, 1, 12, 123, 1234};
  sample.long_array = {-123400, -12300, -1200, -100,  0,
                       100,     1200,   12300, 123400};
  sample.float_array = {-12.34f, -12.3f, -12.0f, -1.0f, 0.0f,
                        1.0f,    12.0f,  12.3f,  12.34f};
  sample.double_array = {-1.234, -1.23, -12.0, -1.0, 0.0,
                         1.0,    12.0,  1.23,  1.234};
  sample.short_array = {-1234, -123, -12, -1, 0, 1, 12, 123, 1234};
  sample.char_array = {'a', 's', 'd', 'f', 'A', 'S', 'D', 'F'}; // "asdfASDF"
  sample.boolean_array = {true, false, false, true};

  sample.string = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  return sample;
}

protobuf::Sample CreateProtoSample() {
  // Consistent with Java Sample.populate() for fair cross-language comparison
  protobuf::Sample sample;
  sample.set_int_value(123);
  sample.set_long_value(1230000LL);
  sample.set_float_value(12.345f);
  sample.set_double_value(1.234567);
  sample.set_short_value(12345);
  sample.set_char_value('!'); // 33
  sample.set_boolean_value(true);

  sample.set_int_value_boxed(321);
  sample.set_long_value_boxed(3210000LL);
  sample.set_float_value_boxed(54.321f);
  sample.set_double_value_boxed(7.654321);
  sample.set_short_value_boxed(32100);
  sample.set_char_value_boxed('$'); // 36
  sample.set_boolean_value_boxed(false);

  // Arrays with mixed positive/negative values (same as Java)
  for (int v : {-1234, -123, -12, -1, 0, 1, 12, 123, 1234}) {
    sample.add_int_array(v);
  }
  for (int64_t v : {-123400LL, -12300LL, -1200LL, -100LL, 0LL, 100LL, 1200LL,
                    12300LL, 123400LL}) {
    sample.add_long_array(v);
  }
  for (float v :
       {-12.34f, -12.3f, -12.0f, -1.0f, 0.0f, 1.0f, 12.0f, 12.3f, 12.34f}) {
    sample.add_float_array(v);
  }
  for (double v : {-1.234, -1.23, -12.0, -1.0, 0.0, 1.0, 12.0, 1.23, 1.234}) {
    sample.add_double_array(v);
  }
  for (int v : {-1234, -123, -12, -1, 0, 1, 12, 123, 1234}) {
    sample.add_short_array(v);
  }
  for (int v : {'a', 's', 'd', 'f', 'A', 'S', 'D', 'F'}) { // "asdfASDF"
    sample.add_char_array(v);
  }
  for (bool v : {true, false, false, true}) {
    sample.add_boolean_array(v);
  }
  sample.set_string("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
  return sample;
}

// ============================================================================
// Helper to configure Fory instance
// ============================================================================

void RegisterForyTypes(fory::serialization::Fory &fory) {
  fory.register_struct<NumericStruct>(1);
  fory.register_struct<Sample>(2);
}

// ============================================================================
// Struct benchmarks (simple object with 8 int32 fields)
// ============================================================================

static void BM_Fory_Struct_Serialize(benchmark::State &state) {
  auto fory = fory::serialization::Fory::builder()
                  .xlang(true)
                  .track_ref(false)
                  .check_struct_version(false)
                  .build();
  RegisterForyTypes(fory);
  NumericStruct obj = CreateNumericStruct();

  // Reuse internal buffer
  fory::Buffer buffer;
  buffer.Reserve(64);

  for (auto _ : state) {
    buffer.WriterIndex(0);
    fory.serialize_to(obj, buffer);
    benchmark::DoNotOptimize(buffer.data());
  }
}
BENCHMARK(BM_Fory_Struct_Serialize);

// Fair comparison: convert plain C++ struct to protobuf, then serialize
// (Same pattern as Java benchmark's buildPBStruct().toByteArray())
static void BM_Protobuf_Struct_Serialize(benchmark::State &state) {
  NumericStruct obj = CreateNumericStruct();
  protobuf::Struct pb = ToPbStruct(obj);
  std::vector<uint8_t> output;
  output.resize(pb.ByteSizeLong());

  for (auto _ : state) {
    pb = ToPbStruct(obj);
    pb.SerializeToArray(output.data(), static_cast<int>(output.size()));
    benchmark::DoNotOptimize(output);
  }
}
BENCHMARK(BM_Protobuf_Struct_Serialize);

static void BM_Fory_Struct_Deserialize(benchmark::State &state) {
  auto fory = fory::serialization::Fory::builder()
                  .xlang(true)
                  .track_ref(false)
                  .check_struct_version(false)
                  .build();
  RegisterForyTypes(fory);
  NumericStruct obj = CreateNumericStruct();
  auto serialized = fory.serialize(obj);
  if (!serialized.ok()) {
    state.SkipWithError("Serialization failed");
    return;
  }
  auto &bytes = serialized.value();

  // Verify deserialization works first
  auto test_result =
      fory.deserialize<NumericStruct>(bytes.data(), bytes.size());
  if (!test_result.ok()) {
    state.SkipWithError("Deserialization test failed");
    return;
  }

  for (auto _ : state) {
    auto result = fory.deserialize<NumericStruct>(bytes.data(), bytes.size());
    benchmark::DoNotOptimize(result);
  }
}
BENCHMARK(BM_Fory_Struct_Deserialize);

// Fair comparison: deserialize and convert protobuf to plain C++ struct
// (Same pattern as Java benchmark's fromPBObject())
static void BM_Protobuf_Struct_Deserialize(benchmark::State &state) {
  protobuf::Struct obj = CreateProtoStruct();
  std::string serialized;
  obj.SerializeToString(&serialized);

  for (auto _ : state) {
    protobuf::Struct pb_result;
    pb_result.ParseFromString(serialized);
    NumericStruct result = FromPbStruct(pb_result);
    benchmark::DoNotOptimize(result);
  }
}
BENCHMARK(BM_Protobuf_Struct_Deserialize);

// ============================================================================
// Sample benchmarks (complex object with various types and arrays)
// ============================================================================

static void BM_Fory_Sample_Serialize(benchmark::State &state) {
  auto fory = fory::serialization::Fory::builder()
                  .xlang(true)
                  .track_ref(false)
                  .check_struct_version(false)
                  .build();
  RegisterForyTypes(fory);
  Sample obj = CreateSample();

  // Pre-allocate buffer (like Protobuf benchmark does)
  fory::Buffer buffer;
  buffer.Reserve(4096);

  for (auto _ : state) {
    buffer.WriterIndex(0);
    auto result = fory.serialize_to(obj, buffer);
    benchmark::DoNotOptimize(result);
    benchmark::DoNotOptimize(buffer.data());
  }
}
BENCHMARK(BM_Fory_Sample_Serialize);

static void BM_Protobuf_Sample_Serialize(benchmark::State &state) {
  protobuf::Sample obj = CreateProtoSample();
  std::vector<uint8_t> output;
  output.resize(obj.ByteSizeLong());

  for (auto _ : state) {
    obj.SerializeToArray(output.data(), static_cast<int>(output.size()));
    benchmark::DoNotOptimize(output);
  }
}
BENCHMARK(BM_Protobuf_Sample_Serialize);

static void BM_Fory_Sample_Deserialize(benchmark::State &state) {
  auto fory = fory::serialization::Fory::builder()
                  .xlang(true)
                  .track_ref(false)
                  .check_struct_version(false)
                  .build();
  RegisterForyTypes(fory);
  Sample obj = CreateSample();
  auto serialized = fory.serialize(obj);
  if (!serialized.ok()) {
    state.SkipWithError("Serialization failed");
    return;
  }
  auto &bytes = serialized.value();

  // Verify deserialization works first
  auto test_result = fory.deserialize<Sample>(bytes.data(), bytes.size());
  if (!test_result.ok()) {
    state.SkipWithError("Deserialization test failed");
    return;
  }

  for (auto _ : state) {
    auto result = fory.deserialize<Sample>(bytes.data(), bytes.size());
    benchmark::DoNotOptimize(result);
  }
}
BENCHMARK(BM_Fory_Sample_Deserialize);

static void BM_Protobuf_Sample_Deserialize(benchmark::State &state) {
  protobuf::Sample obj = CreateProtoSample();
  std::string serialized;
  obj.SerializeToString(&serialized);

  for (auto _ : state) {
    protobuf::Sample result;
    result.ParseFromString(serialized);
    benchmark::DoNotOptimize(result);
  }
}
BENCHMARK(BM_Protobuf_Sample_Deserialize);

// ============================================================================
// Serialized size comparison (printed once at the end)
// ============================================================================

static void BM_PrintSerializedSizes(benchmark::State &state) {
  // Fory
  auto fory = fory::serialization::Fory::builder()
                  .xlang(true)
                  .track_ref(false)
                  .check_struct_version(false)
                  .build();
  RegisterForyTypes(fory);
  NumericStruct fory_struct = CreateNumericStruct();
  Sample fory_sample = CreateSample();
  auto fory_struct_bytes = fory.serialize(fory_struct).value();
  auto fory_sample_bytes = fory.serialize(fory_sample).value();

  // Protobuf
  protobuf::Struct proto_struct = CreateProtoStruct();
  protobuf::Sample proto_sample = CreateProtoSample();
  std::string proto_struct_bytes, proto_sample_bytes;
  proto_struct.SerializeToString(&proto_struct_bytes);
  proto_sample.SerializeToString(&proto_sample_bytes);

  for (auto _ : state) {
    // Just run once to print sizes
  }

  state.counters["fory_struct_size"] = fory_struct_bytes.size();
  state.counters["proto_struct_size"] = proto_struct_bytes.size();
  state.counters["fory_sample_size"] = fory_sample_bytes.size();
  state.counters["proto_sample_size"] = proto_sample_bytes.size();
}
BENCHMARK(BM_PrintSerializedSizes)->Iterations(1);
