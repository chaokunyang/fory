pub mod fury;
pub mod protobuf;
pub mod json;

use chrono::{DateTime, Utc, NaiveDateTime};
use prost_types::Timestamp;

pub trait Serializer<T> {
    fn serialize(&self, data: &T) -> Result<Vec<u8>, Box<dyn std::error::Error>>;
    fn deserialize(&self, data: &[u8]) -> Result<T, Box<dyn std::error::Error>>;
}

// Helper functions for protobuf conversion
pub fn naive_datetime_to_timestamp(dt: NaiveDateTime) -> Timestamp {
    Timestamp {
        seconds: dt.and_utc().timestamp(),
        nanos: dt.and_utc().timestamp_subsec_nanos() as i32,
    }
}

pub fn timestamp_to_naive_datetime(ts: Timestamp) -> NaiveDateTime {
    DateTime::from_timestamp(ts.seconds, ts.nanos as u32).unwrap().naive_utc()
}

pub fn datetime_to_timestamp(dt: DateTime<Utc>) -> Timestamp {
    Timestamp {
        seconds: dt.timestamp(),
        nanos: dt.timestamp_subsec_nanos() as i32,
    }
}

pub fn timestamp_to_datetime(ts: Timestamp) -> DateTime<Utc> {
    DateTime::from_timestamp(ts.seconds, ts.nanos as u32).unwrap()
}
