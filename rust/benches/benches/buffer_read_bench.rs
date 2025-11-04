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

use byteorder::{ByteOrder, LittleEndian};
use criterion::{black_box, criterion_group, criterion_main, Criterion, Throughput};
use fory_core::buffer::{Reader, Writer};
use fory_core::error::Error;

// Alternative implementations for comparison - with proper error handling

#[inline(always)]
fn read_i32_alternative(buf: &[u8], cursor: &mut usize) -> Result<i32, Error> {
    if *cursor + 4 > buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 4, buf.len()));
    }
    let bytes = [
        buf[*cursor],
        buf[*cursor + 1],
        buf[*cursor + 2],
        buf[*cursor + 3],
    ];
    *cursor += 4;
    Ok(i32::from_le_bytes(bytes))
}

#[inline(always)]
fn read_i32_alternative2(buf: &[u8], cursor: &mut usize) -> Result<i32, Error> {
    if *cursor + 4 > buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 4, buf.len()));
    }
    let result = LittleEndian::read_i32(&buf[*cursor..]);
    *cursor += 4;
    Ok(result)
}

#[inline(always)]
fn read_i64_alternative(buf: &[u8], cursor: &mut usize) -> Result<i64, Error> {
    if *cursor + 8 > buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 8, buf.len()));
    }
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
    Ok(i64::from_le_bytes(bytes))
}

#[inline(always)]
fn read_i64_alternative2(buf: &[u8], cursor: &mut usize) -> Result<i64, Error> {
    if *cursor + 8 > buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 8, buf.len()));
    }
    let result = LittleEndian::read_i64(&buf[*cursor..]);
    *cursor += 8;
    Ok(result)
}

#[inline(always)]
fn read_f32_alternative(buf: &[u8], cursor: &mut usize) -> Result<f32, Error> {
    if *cursor + 4 > buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 4, buf.len()));
    }
    let bytes = [
        buf[*cursor],
        buf[*cursor + 1],
        buf[*cursor + 2],
        buf[*cursor + 3],
    ];
    *cursor += 4;
    Ok(f32::from_le_bytes(bytes))
}

#[inline(always)]
fn read_f32_alternative2(buf: &[u8], cursor: &mut usize) -> Result<f32, Error> {
    if *cursor + 4 > buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 4, buf.len()));
    }
    let result = LittleEndian::read_f32(&buf[*cursor..]);
    *cursor += 4;
    Ok(result)
}

#[inline(always)]
fn read_f64_alternative(buf: &[u8], cursor: &mut usize) -> Result<f64, Error> {
    if *cursor + 8 > buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 8, buf.len()));
    }
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
    Ok(f64::from_le_bytes(bytes))
}

#[inline(always)]
fn read_f64_alternative2(buf: &[u8], cursor: &mut usize) -> Result<f64, Error> {
    if *cursor + 8 > buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 8, buf.len()));
    }
    let result = LittleEndian::read_f64(&buf[*cursor..]);
    *cursor += 8;
    Ok(result)
}

#[inline(always)]
fn read_varuint32_alternative(buf: &[u8], cursor: &mut usize) -> Result<u32, Error> {
    let mut result = 0u32;
    let mut shift = 0;
    loop {
        if *cursor >= buf.len() {
            return Err(Error::buffer_out_of_bound(*cursor, 1, buf.len()));
        }
        let b = buf[*cursor];
        *cursor += 1;
        result |= ((b & 0x7F) as u32) << shift;
        if (b & 0x80) == 0 {
            break;
        }
        shift += 7;
    }
    Ok(result)
}

#[inline(always)]
fn read_varuint32_alternative2(buf: &[u8], cursor: &mut usize) -> Result<u32, Error> {
    // Loop unfolding - manually unroll for better performance
    if *cursor >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 1, buf.len()));
    }
    let b0 = buf[*cursor] as u32;
    if b0 < 0x80 {
        *cursor += 1;
        return Ok(b0);
    }

    if *cursor + 1 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 1, 1, buf.len()));
    }
    let b1 = buf[*cursor + 1] as u32;
    let mut encoded = (b0 & 0x7F) | ((b1 & 0x7F) << 7);
    if b1 < 0x80 {
        *cursor += 2;
        return Ok(encoded);
    }

    if *cursor + 2 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 2, 1, buf.len()));
    }
    let b2 = buf[*cursor + 2] as u32;
    encoded |= (b2 & 0x7F) << 14;
    if b2 < 0x80 {
        *cursor += 3;
        return Ok(encoded);
    }

    if *cursor + 3 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 3, 1, buf.len()));
    }
    let b3 = buf[*cursor + 3] as u32;
    encoded |= (b3 & 0x7F) << 21;
    if b3 < 0x80 {
        *cursor += 4;
        return Ok(encoded);
    }

    if *cursor + 4 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 4, 1, buf.len()));
    }
    let b4 = buf[*cursor + 4] as u32;
    encoded |= b4 << 28;
    *cursor += 5;
    Ok(encoded)
}

#[inline(always)]
fn read_varint32_alternative(buf: &[u8], cursor: &mut usize) -> Result<i32, Error> {
    let encoded = read_varuint32_alternative(buf, cursor)?;
    Ok(((encoded >> 1) as i32) ^ -((encoded & 1) as i32))
}

#[inline(always)]
fn read_varint32_alternative2(buf: &[u8], cursor: &mut usize) -> Result<i32, Error> {
    let encoded = read_varuint32_alternative2(buf, cursor)?;
    Ok(((encoded >> 1) as i32) ^ -((encoded & 1) as i32))
}

#[inline(always)]
fn read_varuint64_alternative(buf: &[u8], cursor: &mut usize) -> Result<u64, Error> {
    let mut result = 0u64;
    let mut shift = 0;
    loop {
        if *cursor >= buf.len() {
            return Err(Error::buffer_out_of_bound(*cursor, 1, buf.len()));
        }
        let b = buf[*cursor];
        *cursor += 1;
        result |= ((b & 0x7F) as u64) << shift;
        if (b & 0x80) == 0 {
            break;
        }
        shift += 7;
    }
    Ok(result)
}

#[inline(always)]
fn read_varuint64_alternative2(buf: &[u8], cursor: &mut usize) -> Result<u64, Error> {
    // Loop unfolding - manually unroll for better performance
    if *cursor >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor, 1, buf.len()));
    }
    let b0 = buf[*cursor] as u64;
    if b0 < 0x80 {
        *cursor += 1;
        return Ok(b0);
    }

    if *cursor + 1 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 1, 1, buf.len()));
    }
    let b1 = buf[*cursor + 1] as u64;
    let mut var64 = (b0 & 0x7F) | ((b1 & 0x7F) << 7);
    if b1 < 0x80 {
        *cursor += 2;
        return Ok(var64);
    }

    if *cursor + 2 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 2, 1, buf.len()));
    }
    let b2 = buf[*cursor + 2] as u64;
    var64 |= (b2 & 0x7F) << 14;
    if b2 < 0x80 {
        *cursor += 3;
        return Ok(var64);
    }

    if *cursor + 3 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 3, 1, buf.len()));
    }
    let b3 = buf[*cursor + 3] as u64;
    var64 |= (b3 & 0x7F) << 21;
    if b3 < 0x80 {
        *cursor += 4;
        return Ok(var64);
    }

    if *cursor + 4 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 4, 1, buf.len()));
    }
    let b4 = buf[*cursor + 4] as u64;
    var64 |= (b4 & 0x7F) << 28;
    if b4 < 0x80 {
        *cursor += 5;
        return Ok(var64);
    }

    if *cursor + 5 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 5, 1, buf.len()));
    }
    let b5 = buf[*cursor + 5] as u64;
    var64 |= (b5 & 0x7F) << 35;
    if b5 < 0x80 {
        *cursor += 6;
        return Ok(var64);
    }

    if *cursor + 6 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 6, 1, buf.len()));
    }
    let b6 = buf[*cursor + 6] as u64;
    var64 |= (b6 & 0x7F) << 42;
    if b6 < 0x80 {
        *cursor += 7;
        return Ok(var64);
    }

    if *cursor + 7 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 7, 1, buf.len()));
    }
    let b7 = buf[*cursor + 7] as u64;
    var64 |= (b7 & 0x7F) << 49;
    if b7 < 0x80 {
        *cursor += 8;
        return Ok(var64);
    }

    if *cursor + 8 >= buf.len() {
        return Err(Error::buffer_out_of_bound(*cursor + 8, 1, buf.len()));
    }
    let b8 = buf[*cursor + 8] as u64;
    var64 |= (b8 & 0xFF) << 56;
    *cursor += 9;
    Ok(var64)
}

#[inline(always)]
fn read_varint64_alternative(buf: &[u8], cursor: &mut usize) -> Result<i64, Error> {
    let encoded = read_varuint64_alternative(buf, cursor)?;
    Ok(((encoded >> 1) as i64) ^ -((encoded & 1) as i64))
}

#[inline(always)]
fn read_varint64_alternative2(buf: &[u8], cursor: &mut usize) -> Result<i64, Error> {
    let encoded = read_varuint64_alternative2(buf, cursor)?;
    Ok(((encoded >> 1) as i64) ^ -((encoded & 1) as i64))
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
                sum += read_i32_alternative(&buf, &mut cursor).unwrap() as i64;
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += read_i32_alternative2(&buf, &mut cursor).unwrap() as i64;
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
                sum = sum.wrapping_add(read_i64_alternative(&buf, &mut cursor).unwrap());
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(read_i64_alternative2(&buf, &mut cursor).unwrap());
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
                sum += read_f32_alternative(&buf, &mut cursor).unwrap();
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0.0f32;
            for _ in 0..1000 {
                sum += read_f32_alternative2(&buf, &mut cursor).unwrap();
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
                sum += read_f64_alternative(&buf, &mut cursor).unwrap();
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0.0f64;
            for _ in 0..1000 {
                sum += read_f64_alternative2(&buf, &mut cursor).unwrap();
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
                sum += read_varint32_alternative(&buf, &mut cursor).unwrap() as i64;
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += read_varint32_alternative2(&buf, &mut cursor).unwrap() as i64;
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
                sum += read_varint32_alternative(&buf, &mut cursor).unwrap() as i64;
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += read_varint32_alternative2(&buf, &mut cursor).unwrap() as i64;
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
                sum += read_varint32_alternative(&buf, &mut cursor).unwrap() as i64;
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum += read_varint32_alternative2(&buf, &mut cursor).unwrap() as i64;
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
                sum = sum.wrapping_add(read_varint64_alternative(&buf, &mut cursor).unwrap());
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(read_varint64_alternative2(&buf, &mut cursor).unwrap());
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
                sum = sum.wrapping_add(read_varint64_alternative(&buf, &mut cursor).unwrap());
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(read_varint64_alternative2(&buf, &mut cursor).unwrap());
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
                sum = sum.wrapping_add(read_varint64_alternative(&buf, &mut cursor).unwrap());
            }
            black_box(sum);
        })
    });

    group.bench_function("alternative2", |b| {
        b.iter(|| {
            let mut cursor = 0;
            let mut sum = 0i64;
            for _ in 0..1000 {
                sum = sum.wrapping_add(read_varint64_alternative2(&buf, &mut cursor).unwrap());
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
