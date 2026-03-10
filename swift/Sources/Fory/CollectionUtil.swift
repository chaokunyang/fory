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

struct ReusableArray<Element> {
    private var a: [Element] = []
    private(set) var used = 0

    init(reserve: Int = 0) {
        a.reserveCapacity(reserve)
    }

    @inline(__always)
    mutating func reset() {
        used = 0
    }

    @inline(__always)
    mutating func push(_ x: Element) {
        if used < a.count {
            a[used] = x
        } else {
            a.append(x)
        }
        used += 1
    }

    @inline(__always)
    subscript(index: Int) -> Element {
        get { a[index] }
        set { a[index] = newValue }
    }

    var slice: ArraySlice<Element> { a[..<used] }
}
