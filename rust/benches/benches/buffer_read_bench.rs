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
use fory_core::buffer::{Reader, Writer};

// Alternative implementations for comparison

#[inline(always)]
fn read_i32_alternative(buf: &[u8], cursor: &mut usize) -> i32 {
    let bytes = [buf[*cursor], buf[*cursor + 1], buf[*cursor + 2], buf[*cursor + 3]];
    *cursor += 4;
    i32::from_le_bytes(bytes)
}

#[inline(always)]
fn read_i64_alternative(buf: &[u8], cursor: &mut usize) -> i64 {
    let bytes = [
        buf[*cursor],
        buf[*cursor + 1],
        buf[*cursor + 2],
        buf[*cursor + 3],
        buf[*cursor + 4],
        buf[*cursor + 5],
        buf[*cursor + 6],
        buf[*cursor + 7],
    ];
    *cursor += 8;
    i64::from_le_bytes(bytes)
}

#[inline(always)]
fn read_f32_alternative(buf: &[u8], cursor: &mut usize) -> f32 {
    let bytes = [buf[*cursor], buf[*cursor + 1], buf[*cursor + 2], buf[*cursor + 3]];
    *cursor += 4;
    f32::from_le_bytes(bytes)
}

#[inline(always)]
fn read_f64_alternative(buf: &[u8], cursor: &mut usize) -> f64 {
    let bytes = [
        buf[*cursor],
        buf[*cursor + 1],
        buf[*cursor + 2],
        buf[*cursor + 3],
        buf[*cursor + 4],
        buf[*cursor + 5],
        buf[*cursor + 6],
        buf[*cursor + 7],
    ];
    *cursor += 8;
    f64::from_le_bytes(bytes)
}

#[inline(always)]
fn read_varuint32_alternative(buf: &[u8], cursor: &mut usize) -> u32 {
    let mut result = 0u32;
    let mut shift = 0;
    loop {
        let b = buf[*cursor];
        *cursor += 1;
        result |= ((b & 0x7F) as u32) << shift;
        if (b & 0x80) == 0 {
            break;
        }
        shift += 7;
    }
    result
}

#[inline(always)]
fn read_varint32_alternative(buf: &[u8], cursor: &mut usize) -> i32 {
    let encoded = read_varuint32_alternative(buf, cursor);
    ((encoded >> 1) as i32) ^ -((encoded & 1) as i32)
}

#[inline(always)]
fn read_varuint64_alternative(buf: &[u8], cursor: &mut usize) -> u64 {
    let mut result = 0u64;
    let mut shift = 0;
    loop {
        let b = buf[*cursor];
        *cursor += 1;
        result |= ((b & 0x7F) as u64) << shift;
        if (b & 0x80) == 0 {
            break;
        }
        shift += 7;
    }
    result
}

#[inline(always)]
fn read_varint64_alternative(buf: &[u8], cursor: &mut usize) -> i64 {
    let encoded = read_varuint64_alternative(buf, cursor);
    ((encoded >> 1) as i64) ^ -((encoded & 1) as i64)
}

fn prepare_i32_buffer() -> Vec<u8> {
    let mut buf = Vec::new();
    let mut writer = Writer::from_buffer(&mut buf);
    for i in 0..1000 {
        writer.write_i32(i * 12345);
    }
    buf
}

fn prepare_i64_buffer() -> Vec<u8> {
    let mut buf = Vec::new();
    let mut writer = Writer::from_buffer(&mut buf);
    for i in 0..1000 {
        writer.write_i64(i * 123456789);
    }
    buf
}

fn prepare_f32_buffer() -> Vec<u8> {
    let mut buf = Vec::new();
    let mut writer = Writer::from_buffer(&mut buf);
    for i in 0..1000 {
        writer.write_f32(i as f32 * 1.23);
    }
    buf
}

fn prepare_f64_buffer() -> Vec<u8> {
    let mut buf = Vec::new();
    let mut writer = Writer::from_buffer(&mut buf);
    for i in 0..1000 {
        writer.write_f64(i as f64 * 1.23456);
    }
    buf
}

fn prepare_varint32_buffer(multiplier: i32) -> Vec<u8> {
    let mut buf = Vec::new();
    let mut writer = Writer::from_buffer(&mut buf);
    for i in 0..1000 {
        writer.write_varint32((i % 1000) * multiplier);
    }
    buf
}

fn prepare_varint64_buffer(multiplier: i64) -> Vec<u8> {
    let mut buf = Vec::new();
    let mut writer = Writer::from_buffer(&mut buf);
    for i in 0..1000 {
        writer.write_varint64((i % 1000) * multiplier);
    }
    buf
}

fn bench_read_i32(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_i32");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_i32_buffer();

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += reader.read_i32().unwrap() as i64;
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += read_i32_alternative(&buf, &mut cursor) as i64;
            }
            black_box(sum);
        })
    });

    group.finish();
}

fn bench_read_i64(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_i64");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_i64_buffer();

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(reader.read_i64().unwrap());
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(read_i64_alternative(&buf, &mut cursor));
            }
            black_box(sum);
        })
    });

    group.finish();
}

fn bench_read_f32(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_f32");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_f32_buffer();

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0.0f32;
            for _ in 0..1000 {
                sum += reader.read_f32().unwrap();
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0.0f32;
            for _ in 0..1000 {
                sum += read_f32_alternative(&buf, &mut cursor);
            }
            black_box(sum);
        })
    });

    group.finish();
}

fn bench_read_f64(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_f64");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_f64_buffer();

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0.0f64;
            for _ in 0..1000 {
                sum += reader.read_f64().unwrap();
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0.0f64;
            for _ in 0..1000 {
                sum += read_f64_alternative(&buf, &mut cursor);
            }
            black_box(sum);
        })
    });

    group.finish();
}

fn bench_read_varint32_small(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_varint32_small");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_varint32_buffer(1); // Small values (1 byte)

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += reader.read_varint32().unwrap() as i64;
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += read_varint32_alternative(&buf, &mut cursor) as i64;
            }
            black_box(sum);
        })
    });

    group.finish();
}

fn bench_read_varint32_medium(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_varint32_medium");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_varint32_buffer(1000); // Medium values (2-3 bytes)

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += reader.read_varint32().unwrap() as i64;
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += read_varint32_alternative(&buf, &mut cursor) as i64;
            }
            black_box(sum);
        })
    });

    group.finish();
}

fn bench_read_varint32_large(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_varint32_large");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_varint32_buffer(1000000); // Large values (4-5 bytes)

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += reader.read_varint32().unwrap() as i64;
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += read_varint32_alternative(&buf, &mut cursor) as i64;
            }
            black_box(sum);
        })
    });

    group.finish();
}

fn bench_read_varint64_small(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_varint64_small");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_varint64_buffer(1); // Small values (1 byte)

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(reader.read_varint64().unwrap());
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(read_varint64_alternative(&buf, &mut cursor));
            }
            black_box(sum);
        })
    });

    group.finish();
}

fn bench_read_varint64_medium(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_varint64_medium");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_varint64_buffer(1000000); // Medium values (3-4 bytes)

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(reader.read_varint64().unwrap());
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(read_varint64_alternative(&buf, &mut cursor));
            }
            black_box(sum);
        })
    });

    group.finish();
}

fn bench_read_varint64_large(c: &mut Criterion) {
    let mut group = c.benchmark_group("read_varint64_large");
    group.throughput(Throughput::Elements(1000));

    let buf = prepare_varint64_buffer(1000000000000); // Large values (6-9 bytes)

    group.bench_function("current", |b| {
        b.iter(|| {
            let mut reader = Reader::new(&buf);
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(reader.read_varint64().unwrap());
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(read_varint64_alternative(&buf, &mut cursor));
            }
            black_box(sum);
        })
    });

    group.finish();
}

criterion_group!(
    benches,
    bench_read_i32,
    bench_read_i64,
    bench_read_f32,
    bench_read_f64,
    bench_read_varint32_small,
    bench_read_varint32_medium,
    bench_read_varint32_large,
    bench_read_varint64_small,
    bench_read_varint64_medium,
    bench_read_varint64_large
);
criterion_main!(benches);
