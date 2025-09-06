use criterion::{criterion_group, criterion_main};
use fory_benchmarks::run_serialization_benchmarks;

criterion_group!(benches, run_serialization_benchmarks);
criterion_main!(benches);
