pub mod models;
pub mod serializers;

// Include generated protobuf code
include!(concat!(env!("OUT_DIR"), "/simple.rs"));
include!(concat!(env!("OUT_DIR"), "/medium.rs"));
include!(concat!(env!("OUT_DIR"), "/complex.rs"));
include!(concat!(env!("OUT_DIR"), "/realworld.rs"));

// Benchmark function implementation
use criterion::{black_box, Criterion, BenchmarkId};
use serializers::{Serializer, fury::FurySerializer, protobuf::ProtobufSerializer, json::JsonSerializer};
use models::{TestDataGenerator, simple::FurySimpleStruct, medium::{FuryPerson, FuryCompany}, complex::FuryECommerceData, realworld::FurySystemData};

pub fn run_serialization_benchmarks(c: &mut Criterion) {
    let fury_serializer = FurySerializer::new();
    let protobuf_serializer = ProtobufSerializer::new();
    let json_serializer = JsonSerializer::new();

    // Simple struct benchmarks
    run_benchmark_group::<FurySimpleStruct>(
        c,
        "simple_struct",
        &fury_serializer,
        &protobuf_serializer,
        &json_serializer,
    );

    // Person benchmarks
    run_benchmark_group::<FuryPerson>(
        c,
        "person",
        &fury_serializer,
        &protobuf_serializer,
        &json_serializer,
    );

    // Company benchmarks
    run_benchmark_group::<FuryCompany>(
        c,
        "company",
        &fury_serializer,
        &protobuf_serializer,
        &json_serializer,
    );

    // ECommerce data benchmarks
    run_benchmark_group::<FuryECommerceData>(
        c,
        "ecommerce_data",
        &fury_serializer,
        &protobuf_serializer,
        &json_serializer,
    );

    // System data benchmarks
    run_benchmark_group::<FurySystemData>(
        c,
        "system_data",
        &fury_serializer,
        &protobuf_serializer,
        &json_serializer,
    );
}

fn run_benchmark_group<T>(
    c: &mut Criterion,
    group_name: &str,
    fury_serializer: &FurySerializer,
    protobuf_serializer: &ProtobufSerializer,
    json_serializer: &JsonSerializer,
) where
    T: TestDataGenerator<Data = T> + Clone + PartialEq,
    FurySerializer: Serializer<T>,
    ProtobufSerializer: Serializer<T>,
    JsonSerializer: Serializer<T>,
{
    let mut group = c.benchmark_group(group_name);

    // Test data sizes
    let small_data = T::generate_small();
    let medium_data = T::generate_medium();
    let large_data = T::generate_large();

    // Fury serialization benchmarks
    group.bench_with_input(BenchmarkId::new("fury_serialize", "small"), &small_data, |b, data| {
        b.iter(|| {
            let _ = black_box(fury_serializer.serialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("fury_serialize", "medium"), &medium_data, |b, data| {
        b.iter(|| {
            let _ = black_box(fury_serializer.serialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("fury_serialize", "large"), &large_data, |b, data| {
        b.iter(|| {
            let _ = black_box(fury_serializer.serialize(black_box(data)).unwrap());
        })
    });

    // Fury deserialization benchmarks
    let small_serialized = fury_serializer.serialize(&small_data).unwrap();
    let medium_serialized = fury_serializer.serialize(&medium_data).unwrap();
    let large_serialized = fury_serializer.serialize(&large_data).unwrap();

    group.bench_with_input(BenchmarkId::new("fury_deserialize", "small"), &small_serialized, |b, data| {
        b.iter(|| {
            let _ = black_box(fury_serializer.deserialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("fury_deserialize", "medium"), &medium_serialized, |b, data| {
        b.iter(|| {
            let _ = black_box(fury_serializer.deserialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("fury_deserialize", "large"), &large_serialized, |b, data| {
        b.iter(|| {
            let _ = black_box(fury_serializer.deserialize(black_box(data)).unwrap());
        })
    });

    // Protobuf serialization benchmarks
    group.bench_with_input(BenchmarkId::new("protobuf_serialize", "small"), &small_data, |b, data| {
        b.iter(|| {
            let _ = black_box(protobuf_serializer.serialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("protobuf_serialize", "medium"), &medium_data, |b, data| {
        b.iter(|| {
            let _ = black_box(protobuf_serializer.serialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("protobuf_serialize", "large"), &large_data, |b, data| {
        b.iter(|| {
            let _ = black_box(protobuf_serializer.serialize(black_box(data)).unwrap());
        })
    });

    // Protobuf deserialization benchmarks
    let small_protobuf_serialized = protobuf_serializer.serialize(&small_data).unwrap();
    let medium_protobuf_serialized = protobuf_serializer.serialize(&medium_data).unwrap();
    let large_protobuf_serialized = protobuf_serializer.serialize(&large_data).unwrap();

    group.bench_with_input(BenchmarkId::new("protobuf_deserialize", "small"), &small_protobuf_serialized, |b, data| {
        b.iter(|| {
            let _ = black_box(protobuf_serializer.deserialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("protobuf_deserialize", "medium"), &medium_protobuf_serialized, |b, data| {
        b.iter(|| {
            let _ = black_box(protobuf_serializer.deserialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("protobuf_deserialize", "large"), &large_protobuf_serialized, |b, data| {
        b.iter(|| {
            let _ = black_box(protobuf_serializer.deserialize(black_box(data)).unwrap());
        })
    });

    // JSON serialization benchmarks
    group.bench_with_input(BenchmarkId::new("json_serialize", "small"), &small_data, |b, data| {
        b.iter(|| {
            let _ = black_box(json_serializer.serialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("json_serialize", "medium"), &medium_data, |b, data| {
        b.iter(|| {
            let _ = black_box(json_serializer.serialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("json_serialize", "large"), &large_data, |b, data| {
        b.iter(|| {
            let _ = black_box(json_serializer.serialize(black_box(data)).unwrap());
        })
    });

    // JSON deserialization benchmarks
    let small_json_serialized = json_serializer.serialize(&small_data).unwrap();
    let medium_json_serialized = json_serializer.serialize(&medium_data).unwrap();
    let large_json_serialized = json_serializer.serialize(&large_data).unwrap();

    group.bench_with_input(BenchmarkId::new("json_deserialize", "small"), &small_json_serialized, |b, data| {
        b.iter(|| {
            let _ = black_box(json_serializer.deserialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("json_deserialize", "medium"), &medium_json_serialized, |b, data| {
        b.iter(|| {
            let _ = black_box(json_serializer.deserialize(black_box(data)).unwrap());
        })
    });

    group.bench_with_input(BenchmarkId::new("json_deserialize", "large"), &large_json_serialized, |b, data| {
        b.iter(|| {
            let _ = black_box(json_serializer.deserialize(black_box(data)).unwrap());
        })
    });

    group.finish();
}