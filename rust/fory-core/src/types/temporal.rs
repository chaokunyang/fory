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

use crate::error::Error;

const NANOS_PER_SECOND: i32 = 1_000_000_000;
const MICROS_PER_SECOND: i64 = 1_000_000;

/// Date without timezone, represented as signed days since Unix epoch.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct Date {
    days: i64,
}

impl Date {
    pub const UNIX_EPOCH: Self = Self { days: 0 };

    #[inline(always)]
    pub const fn from_epoch_days(days: i64) -> Self {
        Self { days }
    }

    #[inline(always)]
    pub const fn epoch_days(self) -> i64 {
        self.days
    }
}

/// Point in time, represented as seconds and nanoseconds since Unix epoch.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct Timestamp {
    seconds: i64,
    nanos: u32,
}

impl Timestamp {
    pub const UNIX_EPOCH: Self = Self {
        seconds: 0,
        nanos: 0,
    };

    #[inline(always)]
    pub fn new(seconds: i64, nanos: u32) -> Result<Self, Error> {
        if nanos >= NANOS_PER_SECOND as u32 {
            return Err(Error::invalid_data(format!(
                "timestamp nanoseconds {} out of valid range [0, 999_999_999]",
                nanos
            )));
        }
        Ok(Self { seconds, nanos })
    }

    #[inline(always)]
    pub const fn seconds(self) -> i64 {
        self.seconds
    }

    #[inline(always)]
    pub const fn subsec_nanos(self) -> u32 {
        self.nanos
    }

    #[inline(always)]
    pub fn from_epoch_micros(micros: i64) -> Self {
        let seconds = micros.div_euclid(MICROS_PER_SECOND);
        let micros = micros.rem_euclid(MICROS_PER_SECOND);
        Self {
            seconds,
            nanos: (micros as u32) * 1_000,
        }
    }

    #[inline(always)]
    pub fn to_epoch_micros(self) -> Result<i64, Error> {
        let seconds = self.seconds.checked_mul(MICROS_PER_SECOND).ok_or_else(|| {
            Error::invalid_data(format!(
                "timestamp seconds {} overflow microsecond conversion",
                self.seconds
            ))
        })?;
        seconds
            .checked_add(i64::from(self.nanos / 1_000))
            .ok_or_else(|| {
                Error::invalid_data(format!(
                    "timestamp {:?} overflow microsecond conversion",
                    self
                ))
            })
    }
}

/// Signed duration, represented as seconds and normalized nanoseconds.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct Duration {
    seconds: i64,
    nanos: u32,
}

impl Duration {
    pub const ZERO: Self = Self {
        seconds: 0,
        nanos: 0,
    };

    #[inline(always)]
    pub fn new(seconds: i64, nanos: i32) -> Result<Self, Error> {
        if !(-(NANOS_PER_SECOND - 1)..=(NANOS_PER_SECOND - 1)).contains(&nanos) {
            return Err(Error::invalid_data(format!(
                "duration nanoseconds {} out of valid range [-999_999_999, 999_999_999]",
                nanos
            )));
        }
        if nanos < 0 {
            let seconds = seconds.checked_sub(1).ok_or_else(|| {
                Error::invalid_data(
                    "duration seconds underflow while normalizing negative nanoseconds",
                )
            })?;
            return Ok(Self {
                seconds,
                nanos: (nanos + NANOS_PER_SECOND) as u32,
            });
        }
        Ok(Self {
            seconds,
            nanos: nanos as u32,
        })
    }

    #[inline(always)]
    pub fn from_normalized(seconds: i64, nanos: u32) -> Result<Self, Error> {
        if nanos >= NANOS_PER_SECOND as u32 {
            return Err(Error::invalid_data(format!(
                "duration nanoseconds {} out of valid range [0, 999_999_999]",
                nanos
            )));
        }
        Ok(Self { seconds, nanos })
    }

    #[inline(always)]
    pub const fn seconds(self) -> i64 {
        self.seconds
    }

    #[inline(always)]
    pub const fn subsec_nanos(self) -> u32 {
        self.nanos
    }

    #[inline(always)]
    pub fn from_micros(micros: i64) -> Self {
        let seconds = micros.div_euclid(MICROS_PER_SECOND);
        let micros = micros.rem_euclid(MICROS_PER_SECOND);
        Self {
            seconds,
            nanos: (micros as u32) * 1_000,
        }
    }

    #[inline(always)]
    pub fn to_micros(self) -> Result<i64, Error> {
        let seconds = self.seconds.checked_mul(MICROS_PER_SECOND).ok_or_else(|| {
            Error::invalid_data(format!(
                "duration seconds {} overflow microsecond conversion",
                self.seconds
            ))
        })?;
        seconds
            .checked_add(i64::from(self.nanos / 1_000))
            .ok_or_else(|| {
                Error::invalid_data(format!("{:?} overflow microsecond conversion", self))
            })
    }
}

#[cfg(feature = "chrono")]
mod chrono_support {
    use super::{Date, Duration, Timestamp};
    use crate::error::Error;
    use chrono::{DateTime, NaiveDate, NaiveDateTime, TimeDelta};

    fn epoch() -> NaiveDate {
        NaiveDate::from_ymd_opt(1970, 1, 1).expect("1970-01-01 is a valid chrono date")
    }

    impl From<NaiveDate> for Date {
        #[inline(always)]
        fn from(value: NaiveDate) -> Self {
            Self::from_epoch_days(value.signed_duration_since(epoch()).num_days())
        }
    }

    impl TryFrom<Date> for NaiveDate {
        type Error = Error;

        #[inline(always)]
        fn try_from(value: Date) -> Result<Self, Self::Error> {
            let duration = TimeDelta::try_days(value.epoch_days()).ok_or_else(|| {
                Error::invalid_data(format!(
                    "date day count {} is out of chrono::TimeDelta range",
                    value.epoch_days()
                ))
            })?;
            epoch().checked_add_signed(duration).ok_or_else(|| {
                Error::invalid_data(format!(
                    "date day count {} is out of chrono::NaiveDate range",
                    value.epoch_days()
                ))
            })
        }
    }

    impl From<NaiveDateTime> for Timestamp {
        #[inline(always)]
        fn from(value: NaiveDateTime) -> Self {
            let value = value.and_utc();
            Self {
                seconds: value.timestamp(),
                nanos: value.timestamp_subsec_nanos(),
            }
        }
    }

    impl TryFrom<Timestamp> for NaiveDateTime {
        type Error = Error;

        #[inline(always)]
        fn try_from(value: Timestamp) -> Result<Self, Self::Error> {
            DateTime::from_timestamp(value.seconds(), value.subsec_nanos())
                .map(|value| value.naive_utc())
                .ok_or_else(|| {
                    Error::invalid_data(format!(
                        "timestamp seconds {} nanoseconds {} is out of chrono::NaiveDateTime range",
                        value.seconds(),
                        value.subsec_nanos()
                    ))
                })
        }
    }

    impl TryFrom<chrono::Duration> for Duration {
        type Error = Error;

        #[inline(always)]
        fn try_from(value: chrono::Duration) -> Result<Self, Self::Error> {
            Self::new(value.num_seconds(), value.subsec_nanos())
        }
    }

    impl TryFrom<Duration> for chrono::Duration {
        type Error = Error;

        #[inline(always)]
        fn try_from(value: Duration) -> Result<Self, Self::Error> {
            chrono::Duration::try_seconds(value.seconds())
                .and_then(|duration| {
                    duration.checked_add(&chrono::Duration::nanoseconds(i64::from(
                        value.subsec_nanos(),
                    )))
                })
                .ok_or_else(|| {
                    Error::invalid_data(format!(
                        "duration seconds {} out of chrono::Duration valid range",
                        value.seconds()
                    ))
                })
        }
    }
}
