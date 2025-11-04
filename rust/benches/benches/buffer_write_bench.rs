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

use criterion::{black_box, criterion_group, criterion_main, Criterion, Throughput};
use fory_core::buffer::Writer;

// Alternative implementations for comparison

#[inline(always)]
fn write_i32_alternative(buf: &mut Vec<u8>, value: i32) {
    buf.extend_from_slice(&value.to_le_bytes());
}

#[inline(always)]
fn write_i64_alternative(buf: &mut Vec<u8>, value: i64) {
    buf.extend_from_slice(&value.to_le_bytes());
}

#[inline(always)]
fn write_f32_alternative(buf: &mut Vec<u8>, value: f32) {
    buf.extend_from_slice(&value.to_le_bytes());
}

#[inline(always)]
fn write_f64_alternative(buf: &mut Vec<u8>, value: f64) {
    buf.extend_from_slice(&value.to_le_bytes());
}

#[inline(always)]
fn write_varint32_alternative(buf: &mut Vec<u8>, value: i32) {
    let zigzag = ((value as i64) << 1) ^ ((value as i64) >> 31);
    write_varuint32_alternative(buf, zigzag as u32);
}

#[inline(always)]
fn write_varuint32_alternative(buf: &mut Vec<u8>, mut value: u32) {
    while value >= 0x80 {
        buf.push((value as u8) | 0x80);
        value >>= 7;
    }
    buf.push(value as u8);
}

#[inline(always)]
fn write_varint64_alternative(buf: &mut Vec<u8>, value: i64) {
    let zigzag = ((value << 1) ^ (value >> 63)) as u64;
    write_varuint64_alternative(buf, zigzag);
}

#[inline(always)]
fn write_varuint64_alternative(buf: &mut Vec<u8>, mut value: u64) {
    while value >= 0x80 {
        buf.push((value as u8) | 0x80);
        value >>= 7;
    }
    buf.push(value as u8);
}

fn bench_write_i32(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_i32");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<i32> = (0..1000).map(|i| i * 12345).collect();

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(4000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_i32(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(4000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_i32_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

fn bench_write_i64(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_i64");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<i64> = (0..1000).map(|i| i * 123456789).collect();

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(8000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_i64(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(8000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_i64_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

fn bench_write_f32(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_f32");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<f32> = (0..1000).map(|i| i as f32 * 1.23).collect();

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(4000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_f32(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(4000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_f32_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

fn bench_write_f64(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_f64");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<f64> = (0..1000).map(|i| i as f64 * 1.23456).collect();

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(8000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_f64(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(8000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_f64_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

fn bench_write_varint32_small(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_varint32_small");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<i32> = (0..1000).map(|i| i % 128).collect(); // Small values (1 byte)

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(2000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_varint32(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(2000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_varint32_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

fn bench_write_varint32_medium(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_varint32_medium");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<i32> = (0..1000).map(|i| i * 1000).collect(); // Medium values (2-3 bytes)

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(3000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_varint32(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(3000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_varint32_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

fn bench_write_varint32_large(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_varint32_large");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<i32> = (0..1000).map(|i| i * 1000000).collect(); // Large values (4-5 bytes)

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(5000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_varint32(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(5000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_varint32_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

fn bench_write_varint64_small(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_varint64_small");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<i64> = (0..1000).map(|i| i % 128).collect(); // Small values (1 byte)

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(2000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_varint64(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(2000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_varint64_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

fn bench_write_varint64_medium(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_varint64_medium");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<i64> = (0..1000).map(|i| i * 1000000).collect(); // Medium values (3-4 bytes)

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(5000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_varint64(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(5000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_varint64_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

fn bench_write_varint64_large(c: &mut Criterion) {
    let mut group = c.benchmark_group("write_varint64_large");
    group.throughput(Throughput::Elements(1000));

    let values: Vec<i64> = (0..1000).map(|i| i as i64 * 1000000000000).collect(); // Large values (6-9 bytes)

    group.bench_function("current", |b| {
        let mut buf = Vec::with_capacity(10000);
        b.iter(|| {
            buf.clear();
            let mut writer = Writer::from_buffer(&mut buf);
            for &val in &values {
                writer.write_varint64(black_box(val));
            }
            black_box(&buf);
        })
    });

    group.bench_function("alternative", |b| {
        let mut buf = Vec::with_capacity(10000);
        b.iter(|| {
            buf.clear();
            for &val in &values {
                write_varint64_alternative(&mut buf, black_box(val));
            }
            black_box(&buf);
        })
    });

    group.finish();
}

criterion_group!(
    benches,
    bench_write_i32,
    bench_write_i64,
    bench_write_f32,
    bench_write_f64,
    bench_write_varint32_small,
    bench_write_varint32_medium,
    bench_write_varint32_large,
    bench_write_varint64_small,
    bench_write_varint64_medium,
    bench_write_varint64_large
);
criterion_main!(benches);
