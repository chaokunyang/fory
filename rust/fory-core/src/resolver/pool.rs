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

use crate::util::Spinlock;
use std::cell::Cell;
use std::sync::atomic::{AtomicU64, Ordering};

/// Number of segments in the pool. Using 16 segments to reduce contention.
const NUM_SEGMENTS: usize = 16;

/// Global counter to assign unique IDs to threads for segment selection.
static THREAD_ID_COUNTER: AtomicU64 = AtomicU64::new(0);

thread_local! {
    /// Cached segment index for the current thread.
    /// Using a simple incrementing counter ensures even distribution across segments.
    static SEGMENT_INDEX: Cell<usize> = Cell::new(
        (THREAD_ID_COUNTER.fetch_add(1, Ordering::Relaxed) as usize) % NUM_SEGMENTS
    );
}

/// A segment containing a spinlock-protected vector of pooled items.
struct Segment<T> {
    items: Spinlock<Vec<T>>,
}

impl<T> Segment<T> {
    fn new() -> Self {
        Segment {
            items: Spinlock::new(Vec::new()),
        }
    }

    #[inline(always)]
    fn get(&self, factory: &dyn Fn() -> T) -> T {
        self.items.lock().pop().unwrap_or_else(factory)
    }

    #[inline(always)]
    fn put(&self, item: T) {
        self.items.lock().push(item);
    }
}

/// A segmented object pool that reduces lock contention by distributing
/// access across multiple segments based on thread ID.
///
/// Each thread is assigned to a specific segment, so threads accessing
/// the pool concurrently will typically hit different locks, reducing contention.
pub struct Pool<T> {
    segments: [Segment<T>; NUM_SEGMENTS],
    factory: Box<dyn Fn() -> T + Send + Sync>,
}

impl<T> Pool<T> {
    pub fn new<F>(factory: F) -> Self
    where
        F: Fn() -> T + Send + Sync + 'static,
    {
        Pool {
            segments: std::array::from_fn(|_| Segment::new()),
            factory: Box::new(factory),
        }
    }

    /// Borrows an item from the pool, executes the handler, and returns the item to the pool.
    #[inline(always)]
    pub fn borrow_mut<Result>(&self, handler: impl FnOnce(&mut T) -> Result) -> Result {
        let segment_idx = SEGMENT_INDEX.with(|idx| idx.get());
        let segment = &self.segments[segment_idx];

        let mut obj = segment.get(&*self.factory);
        let result = handler(&mut obj);
        segment.put(obj);
        result
    }
}
