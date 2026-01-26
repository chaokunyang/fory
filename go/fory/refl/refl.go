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

package refl

import "unsafe"

// ForyReflectValue wraps the address returned by ForyReflect.
type ForyReflectValue struct {
	Ptr unsafe.Pointer
}

// NewForyReflectValue constructs a ForyReflectValue from a pointer.
func NewForyReflectValue(ptr unsafe.Pointer) ForyReflectValue {
	return ForyReflectValue{Ptr: ptr}
}

// ForyAddressable exposes an address for unsafe fast paths.
type ForyAddressable interface {
	ForyReflect() ForyReflectValue
}
