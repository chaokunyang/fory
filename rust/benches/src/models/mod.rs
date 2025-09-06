pub mod simple;
pub mod medium;
pub mod complex;
pub mod realworld;

use rand::Rng;

pub trait TestDataGenerator {
    type Data;
    fn generate_small() -> Self::Data;
    fn generate_medium() -> Self::Data;
    fn generate_large() -> Self::Data;
}

pub fn generate_random_string(len: usize) -> String {
    const CHARSET: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    let mut rng = rand::thread_rng();
    (0..len)
        .map(|_| {
            let idx = rng.gen_range(0..CHARSET.len());
            CHARSET[idx] as char
        })
        .collect()
}

pub fn generate_random_strings(count: usize, len: usize) -> Vec<String> {
    (0..count).map(|_| generate_random_string(len)).collect()
}
