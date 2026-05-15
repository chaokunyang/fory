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
const NANOS_PER_SECOND_I128: i128 = NANOS_PER_SECOND as i128;
const NANOS_PER_MILLI: i128 = 1_000_000;
const NANOS_PER_MICRO: i128 = 1_000;
const MILLIS_PER_SECOND: i64 = 1_000;
const MICROS_PER_SECOND: i64 = 1_000_000;
const SECONDS_PER_MINUTE: i64 = 60;
const SECONDS_PER_HOUR: i64 = 60 * SECONDS_PER_MINUTE;
const SECONDS_PER_DAY: i64 = 24 * SECONDS_PER_HOUR;

/// Date without timezone, represented as signed days since Unix epoch.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct Date {
    days: i64,
}

impl Date {
    pub const UNIX_EPOCH: Self = Self { days: 0 };

    /// Creates a date from signed days since 1970-01-01.
    #[inline(always)]
    pub const fn from_epoch_days(days: i64) -> Self {
        Self { days }
    }

    /// Returns the signed day count from 1970-01-01.
    #[inline(always)]
    pub const fn epoch_days(self) -> i64 {
        self.days
    }

    /// Returns a date offset by `days`.
    #[inline(always)]
    pub fn checked_add_days(self, days: i64) -> Result<Self, Error> {
        self.days
            .checked_add(days)
            .map(Self::from_epoch_days)
            .ok_or_else(|| {
                Error::invalid_data(format!(
                    "date day count {} overflow adding {} days",
                    self.days, days
                ))
            })
    }

    /// Returns a date offset backward by `days`.
    #[inline(always)]
    pub fn checked_sub_days(self, days: i64) -> Result<Self, Error> {
        self.days
            .checked_sub(days)
            .map(Self::from_epoch_days)
            .ok_or_else(|| {
                Error::invalid_data(format!(
                    "date day count {} overflow subtracting {} days",
                    self.days, days
                ))
            })
    }

    /// Returns the signed day distance from `earlier` to this date.
    #[inline(always)]
    pub fn days_since(self, earlier: Self) -> Result<i64, Error> {
        self.days.checked_sub(earlier.days).ok_or_else(|| {
            Error::invalid_data(format!(
                "date day count {} overflow subtracting {}",
                self.days, earlier.days
            ))
        })
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

    /// Creates a timestamp from epoch seconds and a nanosecond component.
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

    /// Creates a timestamp from whole seconds since Unix epoch.
    #[inline(always)]
    pub const fn from_epoch_seconds(seconds: i64) -> Self {
        Self { seconds, nanos: 0 }
    }

    /// Creates a timestamp from milliseconds since Unix epoch.
    #[inline(always)]
    pub fn from_epoch_millis(millis: i64) -> Self {
        let seconds = millis.div_euclid(MILLIS_PER_SECOND);
        let millis = millis.rem_euclid(MILLIS_PER_SECOND);
        Self {
            seconds,
            nanos: (millis as u32) * 1_000_000,
        }
    }

    /// Creates a timestamp from microseconds since Unix epoch.
    #[inline(always)]
    pub fn from_epoch_micros(micros: i64) -> Self {
        let seconds = micros.div_euclid(MICROS_PER_SECOND);
        let micros = micros.rem_euclid(MICROS_PER_SECOND);
        Self {
            seconds,
            nanos: (micros as u32) * 1_000,
        }
    }

    /// Creates a timestamp from nanoseconds since Unix epoch.
    #[inline(always)]
    pub fn from_epoch_nanos(nanos: i128) -> Result<Self, Error> {
        let (seconds, nanos) = split_total_nanos(nanos, "timestamp")?;
        Ok(Self { seconds, nanos })
    }

    /// Returns the whole seconds component.
    #[inline(always)]
    pub const fn seconds(self) -> i64 {
        self.seconds
    }

    /// Returns the normalized nanosecond component.
    #[inline(always)]
    pub const fn subsec_nanos(self) -> u32 {
        self.nanos
    }

    /// Returns milliseconds since Unix epoch, rounded down for sub-millisecond values.
    #[inline(always)]
    pub fn to_epoch_millis(self) -> Result<i64, Error> {
        total_unit(self.to_epoch_nanos(), NANOS_PER_MILLI, "timestamp")
    }

    /// Returns microseconds since Unix epoch, rounded down for sub-microsecond values.
    #[inline(always)]
    pub fn to_epoch_micros(self) -> Result<i64, Error> {
        total_unit(self.to_epoch_nanos(), NANOS_PER_MICRO, "timestamp")
    }

    /// Returns nanoseconds since Unix epoch.
    #[inline(always)]
    pub fn to_epoch_nanos(self) -> i128 {
        i128::from(self.seconds) * NANOS_PER_SECOND_I128 + i128::from(self.nanos)
    }

    /// Adds a signed duration to this timestamp.
    #[inline(always)]
    pub fn checked_add_duration(self, duration: Duration) -> Result<Self, Error> {
        Self::from_epoch_nanos(self.to_epoch_nanos() + duration.to_nanos())
    }

    /// Subtracts a signed duration from this timestamp.
    #[inline(always)]
    pub fn checked_sub_duration(self, duration: Duration) -> Result<Self, Error> {
        Self::from_epoch_nanos(self.to_epoch_nanos() - duration.to_nanos())
    }

    /// Returns the signed duration from `earlier` to this timestamp.
    #[inline(always)]
    pub fn duration_since(self, earlier: Self) -> Result<Duration, Error> {
        Duration::from_nanos(self.to_epoch_nanos() - earlier.to_epoch_nanos())
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

    /// Creates a duration from seconds and a nanosecond adjustment.
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

    /// Creates a duration from normalized seconds and nanoseconds.
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

    /// Creates a duration from seconds and an arbitrary nanosecond adjustment.
    #[inline(always)]
    pub fn from_parts(seconds: i64, nanos: i64) -> Result<Self, Error> {
        Self::from_nanos(i128::from(seconds) * NANOS_PER_SECOND_I128 + i128::from(nanos))
    }

    /// Creates a duration from whole seconds.
    #[inline(always)]
    pub const fn from_secs(seconds: i64) -> Self {
        Self { seconds, nanos: 0 }
    }

    /// Creates a duration from minutes.
    #[inline(always)]
    pub fn from_minutes(minutes: i64) -> Result<Self, Error> {
        checked_duration_seconds(minutes, SECONDS_PER_MINUTE, "minutes")
    }

    /// Creates a duration from hours.
    #[inline(always)]
    pub fn from_hours(hours: i64) -> Result<Self, Error> {
        checked_duration_seconds(hours, SECONDS_PER_HOUR, "hours")
    }

    /// Creates a duration from days.
    #[inline(always)]
    pub fn from_days(days: i64) -> Result<Self, Error> {
        checked_duration_seconds(days, SECONDS_PER_DAY, "days")
    }

    /// Creates a duration from milliseconds.
    #[inline(always)]
    pub fn from_millis(millis: i64) -> Self {
        let seconds = millis.div_euclid(MILLIS_PER_SECOND);
        let millis = millis.rem_euclid(MILLIS_PER_SECOND);
        Self {
            seconds,
            nanos: (millis as u32) * 1_000_000,
        }
    }

    /// Creates a duration from microseconds.
    #[inline(always)]
    pub fn from_micros(micros: i64) -> Self {
        let seconds = micros.div_euclid(MICROS_PER_SECOND);
        let micros = micros.rem_euclid(MICROS_PER_SECOND);
        Self {
            seconds,
            nanos: (micros as u32) * 1_000,
        }
    }

    /// Creates a duration from nanoseconds.
    #[inline(always)]
    pub fn from_nanos(nanos: i128) -> Result<Self, Error> {
        let (seconds, nanos) = split_total_nanos(nanos, "duration")?;
        Ok(Self { seconds, nanos })
    }

    /// Returns the normalized seconds component.
    #[inline(always)]
    pub const fn seconds(self) -> i64 {
        self.seconds
    }

    /// Returns the normalized nanosecond component.
    #[inline(always)]
    pub const fn subsec_nanos(self) -> u32 {
        self.nanos
    }

    /// Returns whether this duration is zero.
    #[inline(always)]
    pub const fn is_zero(self) -> bool {
        self.seconds == 0 && self.nanos == 0
    }

    /// Returns whether this duration is greater than zero.
    #[inline(always)]
    pub const fn is_positive(self) -> bool {
        self.seconds > 0 || (self.seconds == 0 && self.nanos > 0)
    }

    /// Returns whether this duration is less than zero.
    #[inline(always)]
    pub const fn is_negative(self) -> bool {
        self.seconds < 0
    }

    /// Returns whole milliseconds, rounded down for sub-millisecond values.
    #[inline(always)]
    pub fn to_millis(self) -> Result<i64, Error> {
        total_unit(self.to_nanos(), NANOS_PER_MILLI, "duration")
    }

    /// Returns whole microseconds, rounded down for sub-microsecond values.
    #[inline(always)]
    pub fn to_micros(self) -> Result<i64, Error> {
        total_unit(self.to_nanos(), NANOS_PER_MICRO, "duration")
    }

    /// Returns nanoseconds.
    #[inline(always)]
    pub fn to_nanos(self) -> i128 {
        i128::from(self.seconds) * NANOS_PER_SECOND_I128 + i128::from(self.nanos)
    }

    /// Adds two durations.
    #[inline(always)]
    pub fn checked_add(self, other: Self) -> Result<Self, Error> {
        Self::from_nanos(self.to_nanos() + other.to_nanos())
    }

    /// Subtracts `other` from this duration.
    #[inline(always)]
    pub fn checked_sub(self, other: Self) -> Result<Self, Error> {
        Self::from_nanos(self.to_nanos() - other.to_nanos())
    }

    /// Negates this duration.
    #[inline(always)]
    pub fn checked_neg(self) -> Result<Self, Error> {
        Self::from_nanos(-self.to_nanos())
    }

    /// Returns the absolute duration.
    #[inline(always)]
    pub fn abs(self) -> Result<Self, Error> {
        if self.is_negative() {
            self.checked_neg()
        } else {
            Ok(self)
        }
    }
}

#[inline(always)]
fn split_total_nanos(nanos: i128, value_name: &str) -> Result<(i64, u32), Error> {
    let total_nanos = nanos;
    let seconds = total_nanos.div_euclid(NANOS_PER_SECOND_I128);
    let nanos = total_nanos.rem_euclid(NANOS_PER_SECOND_I128) as u32;
    let seconds = i64::try_from(seconds).map_err(|_| {
        Error::invalid_data(format!(
            "{} nanoseconds {} out of supported seconds range",
            value_name, total_nanos
        ))
    })?;
    Ok((seconds, nanos))
}

#[inline(always)]
fn total_unit(nanos: i128, nanos_per_unit: i128, value_name: &str) -> Result<i64, Error> {
    let units = nanos.div_euclid(nanos_per_unit);
    i64::try_from(units).map_err(|_| {
        Error::invalid_data(format!(
            "{} nanoseconds {} overflow conversion to requested unit",
            value_name, nanos
        ))
    })
}

#[inline(always)]
fn checked_duration_seconds(value: i64, multiplier: i64, unit: &str) -> Result<Duration, Error> {
    value
        .checked_mul(multiplier)
        .map(Duration::from_secs)
        .ok_or_else(|| {
            Error::invalid_data(format!(
                "duration {} {} overflow second conversion",
                value, unit
            ))
        })
}

impl TryFrom<std::time::Duration> for Duration {
    type Error = Error;

    #[inline(always)]
    fn try_from(value: std::time::Duration) -> Result<Self, Self::Error> {
        let seconds = i64::try_from(value.as_secs()).map_err(|_| {
            Error::invalid_data(format!(
                "std::time::Duration seconds {} exceed Fory duration range",
                value.as_secs()
            ))
        })?;
        Self::from_normalized(seconds, value.subsec_nanos())
    }
}

impl TryFrom<Duration> for std::time::Duration {
    type Error = Error;

    #[inline(always)]
    fn try_from(value: Duration) -> Result<Self, Self::Error> {
        if value.is_negative() {
            return Err(Error::invalid_data(format!(
                "negative Fory duration {:?} cannot convert to std::time::Duration",
                value
            )));
        }
        Ok(std::time::Duration::new(
            u64::try_from(value.seconds()).map_err(|_| {
                Error::invalid_data(format!(
                    "duration seconds {} exceed std::time::Duration range",
                    value.seconds()
                ))
            })?,
            value.subsec_nanos(),
        ))
    }
}

impl TryFrom<std::time::SystemTime> for Timestamp {
    type Error = Error;

    #[inline(always)]
    fn try_from(value: std::time::SystemTime) -> Result<Self, Self::Error> {
        match value.duration_since(std::time::UNIX_EPOCH) {
            Ok(duration) => {
                let seconds = i64::try_from(duration.as_secs()).map_err(|_| {
                    Error::invalid_data(format!(
                        "SystemTime seconds {} exceed Fory timestamp range",
                        duration.as_secs()
                    ))
                })?;
                Self::new(seconds, duration.subsec_nanos())
            }
            Err(error) => {
                let duration = error.duration();
                let nanos = i128::from(duration.as_secs()) * NANOS_PER_SECOND_I128
                    + i128::from(duration.subsec_nanos());
                Self::from_epoch_nanos(-nanos)
            }
        }
    }
}

impl TryFrom<Timestamp> for std::time::SystemTime {
    type Error = Error;

    #[inline(always)]
    fn try_from(value: Timestamp) -> Result<Self, Self::Error> {
        let nanos = value.to_epoch_nanos();
        if nanos >= 0 {
            std::time::UNIX_EPOCH
                .checked_add(std_duration_from_nanos(nanos)?)
                .ok_or_else(|| {
                    Error::invalid_data(format!("timestamp {:?} exceeds SystemTime range", value))
                })
        } else {
            std::time::UNIX_EPOCH
                .checked_sub(std_duration_from_nanos(-nanos)?)
                .ok_or_else(|| {
                    Error::invalid_data(format!("timestamp {:?} exceeds SystemTime range", value))
                })
        }
    }
}

#[inline(always)]
fn std_duration_from_nanos(nanos: i128) -> Result<std::time::Duration, Error> {
    debug_assert!(nanos >= 0);
    let total_nanos = nanos;
    let seconds = total_nanos / NANOS_PER_SECOND_I128;
    let nanos = (total_nanos % NANOS_PER_SECOND_I128) as u32;
    let seconds = u64::try_from(seconds).map_err(|_| {
        Error::invalid_data(format!(
            "nanoseconds {} exceed std::time::Duration range",
            total_nanos
        ))
    })?;
    Ok(std::time::Duration::new(seconds, nanos))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn date_day_api() {
        let date = Date::from_epoch_days(19_782);
        assert_eq!(date.epoch_days(), 19_782);
        assert_eq!(
            date.checked_add_days(1).unwrap(),
            Date::from_epoch_days(19_783)
        );
        assert_eq!(
            date.checked_sub_days(1).unwrap(),
            Date::from_epoch_days(19_781)
        );
        assert_eq!(
            date.days_since(Date::UNIX_EPOCH).unwrap(),
            date.epoch_days()
        );
        assert!(Date::from_epoch_days(i64::MAX).checked_add_days(1).is_err());
    }

    #[test]
    fn timestamp_epoch_api() {
        let millis = Timestamp::from_epoch_millis(-1);
        assert_eq!(millis.seconds(), -1);
        assert_eq!(millis.subsec_nanos(), 999_000_000);
        assert_eq!(millis.to_epoch_millis().unwrap(), -1);

        let nanos = Timestamp::from_epoch_nanos(-1).unwrap();
        assert_eq!(nanos.seconds(), -1);
        assert_eq!(nanos.subsec_nanos(), 999_999_999);
        assert_eq!(nanos.to_epoch_nanos(), -1);
        assert_eq!(nanos.to_epoch_micros().unwrap(), -1);

        let duration = Duration::from_micros(1);
        let timestamp = Timestamp::UNIX_EPOCH
            .checked_add_duration(duration)
            .unwrap();
        assert_eq!(timestamp.to_epoch_micros().unwrap(), 1);
        assert_eq!(
            timestamp.duration_since(Timestamp::UNIX_EPOCH).unwrap(),
            duration
        );
        assert_eq!(
            Timestamp::UNIX_EPOCH
                .checked_sub_duration(Duration::from_nanos(1).unwrap())
                .unwrap(),
            nanos
        );

        assert_eq!(Timestamp::from_epoch_seconds(10).seconds(), 10);
    }

    #[test]
    fn duration_api() {
        let nanos = Duration::from_nanos(-1).unwrap();
        assert_eq!(nanos.seconds(), -1);
        assert_eq!(nanos.subsec_nanos(), 999_999_999);
        assert!(nanos.is_negative());
        assert_eq!(nanos.to_nanos(), -1);
        assert_eq!(nanos.to_micros().unwrap(), -1);

        let duration = Duration::from_parts(1, 1_500_000_000).unwrap();
        assert_eq!(duration, Duration::from_normalized(2, 500_000_000).unwrap());
        assert_eq!(
            Duration::from_millis(-1),
            Duration::from_normalized(-1, 999_000_000).unwrap()
        );
        assert_eq!(
            nanos.checked_neg().unwrap(),
            Duration::from_normalized(0, 1).unwrap()
        );
        assert_eq!(
            Duration::from_secs(1)
                .checked_add(Duration::from_millis(500))
                .unwrap(),
            Duration::from_normalized(1, 500_000_000).unwrap()
        );
        assert_eq!(Duration::from_minutes(2).unwrap(), Duration::from_secs(120));
        assert_eq!(Duration::from_hours(2).unwrap(), Duration::from_secs(7_200));
        assert_eq!(
            Duration::from_days(2).unwrap(),
            Duration::from_secs(172_800)
        );
        assert_eq!(Duration::ZERO.abs().unwrap(), Duration::ZERO);
    }

    #[test]
    fn std_time_conversions() {
        let std_duration = std::time::Duration::new(3, 4);
        let duration = Duration::try_from(std_duration).unwrap();
        assert_eq!(duration, Duration::from_normalized(3, 4).unwrap());
        let roundtrip: std::time::Duration = duration.try_into().unwrap();
        assert_eq!(roundtrip, std_duration);
        assert!(std::time::Duration::try_from(Duration::from_nanos(-1).unwrap()).is_err());

        let before_epoch = Timestamp::from_epoch_nanos(-1).unwrap();
        let system_time: std::time::SystemTime = before_epoch.try_into().unwrap();
        assert_eq!(Timestamp::try_from(system_time).unwrap(), before_epoch);

        let after_epoch = Timestamp::from_epoch_nanos(1_500_000_001).unwrap();
        let system_time: std::time::SystemTime = after_epoch.try_into().unwrap();
        assert_eq!(Timestamp::try_from(system_time).unwrap(), after_epoch);
    }

    #[cfg(feature = "chrono")]
    #[test]
    fn chrono_utc_conversion() {
        use chrono::{DateTime, Utc};

        let datetime = DateTime::<Utc>::from_timestamp(1, 2).unwrap();
        let timestamp = Timestamp::from(datetime);
        assert_eq!(timestamp, Timestamp::new(1, 2).unwrap());
        let roundtrip: DateTime<Utc> = timestamp.try_into().unwrap();
        assert_eq!(roundtrip, datetime);
    }
}

#[cfg(feature = "chrono")]
mod chrono_support {
    use super::{Date, Duration, Timestamp};
    use crate::error::Error;
    use chrono::{DateTime, NaiveDate, NaiveDateTime, TimeDelta, Utc};

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

    impl From<DateTime<Utc>> for Timestamp {
        #[inline(always)]
        fn from(value: DateTime<Utc>) -> Self {
            Self {
                seconds: value.timestamp(),
                nanos: value.timestamp_subsec_nanos(),
            }
        }
    }

    impl TryFrom<Timestamp> for DateTime<Utc> {
        type Error = Error;

        #[inline(always)]
        fn try_from(value: Timestamp) -> Result<Self, Self::Error> {
            DateTime::from_timestamp(value.seconds(), value.subsec_nanos()).ok_or_else(|| {
                Error::invalid_data(format!(
                    "timestamp seconds {} nanoseconds {} is out of chrono::DateTime<Utc> range",
                    value.seconds(),
                    value.subsec_nanos()
                ))
            })
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
