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

package fory

import (
	"testing"

	"github.com/stretchr/testify/require"
)

// TestSerializeGenericPrimitives tests Serialize[T]/Deserialize[T] with primitives.
// These use TypedSerializer[T] when registered, otherwise fall back to reflection.
func TestSerializeGenericPrimitives(t *testing.T) {
	f := NewFory(WithRefTracking(true))

	t.Run("Bool", func(t *testing.T) {
		// bool is registered with TypedSerializer
		data, err := Serialize(f, true)
		require.NoError(t, err)
		result, err := Deserialize[bool](f, data)
		require.NoError(t, err)
		require.True(t, result)

		data, err = Serialize(f, false)
		require.NoError(t, err)
		result, err = Deserialize[bool](f, data)
		require.NoError(t, err)
		require.False(t, result)
	})

	t.Run("Int8", func(t *testing.T) {
		data, err := Serialize(f, int8(-42))
		require.NoError(t, err)
		result, err := Deserialize[int8](f, data)
		require.NoError(t, err)
		require.Equal(t, int8(-42), result)
	})

	t.Run("Int16", func(t *testing.T) {
		data, err := Serialize(f, int16(1234))
		require.NoError(t, err)
		result, err := Deserialize[int16](f, data)
		require.NoError(t, err)
		require.Equal(t, int16(1234), result)
	})

	t.Run("Int32", func(t *testing.T) {
		data, err := Serialize(f, int32(42))
		require.NoError(t, err)
		result, err := Deserialize[int32](f, data)
		require.NoError(t, err)
		require.Equal(t, int32(42), result)

		// Test negative
		data, err = Serialize(f, int32(-12345))
		require.NoError(t, err)
		result, err = Deserialize[int32](f, data)
		require.NoError(t, err)
		require.Equal(t, int32(-12345), result)
	})

	t.Run("Int64", func(t *testing.T) {
		data, err := Serialize(f, int64(9876543210))
		require.NoError(t, err)
		result, err := Deserialize[int64](f, data)
		require.NoError(t, err)
		require.Equal(t, int64(9876543210), result)
	})

	t.Run("Float32", func(t *testing.T) {
		data, err := Serialize(f, float32(3.14))
		require.NoError(t, err)
		result, err := Deserialize[float32](f, data)
		require.NoError(t, err)
		require.InDelta(t, float32(3.14), result, 0.001)
	})

	t.Run("Float64", func(t *testing.T) {
		data, err := Serialize(f, 2.71828)
		require.NoError(t, err)
		result, err := Deserialize[float64](f, data)
		require.NoError(t, err)
		require.InDelta(t, 2.71828, result, 0.00001)
	})

	t.Run("String", func(t *testing.T) {
		data, err := Serialize(f, "hello fory")
		require.NoError(t, err)
		result, err := Deserialize[string](f, data)
		require.NoError(t, err)
		require.Equal(t, "hello fory", result)

		// Test empty string
		data, err = Serialize(f, "")
		require.NoError(t, err)
		result, err = Deserialize[string](f, data)
		require.NoError(t, err)
		require.Equal(t, "", result)
	})
}

// TestSerializeGenericComplex tests Serialize[T]/Deserialize[T] with complex types.
// These fall back to reflection-based serialization.
func TestSerializeGenericComplex(t *testing.T) {
	f := NewFory(WithRefTracking(true))

	t.Run("Struct", func(t *testing.T) {
		type TestStruct struct {
			Name  string
			Value int32
		}
		err := f.RegisterByNamespace(TestStruct{}, "example", "TestStruct")
		require.NoError(t, err)

		original := TestStruct{Name: "test", Value: 100}
		data, err := Serialize(f, original)
		require.NoError(t, err)

		// Use reflection-based path for deserialization
		result, err := Deserialize[TestStruct](f, data)
		require.NoError(t, err)
		require.Equal(t, original, result)
	})

	t.Run("Slice", func(t *testing.T) {
		original := []int32{1, 2, 3, 4, 5}
		data, err := Serialize(f, original)
		require.NoError(t, err)

		result, err := Deserialize[[]int32](f, data)
		require.NoError(t, err)
		require.Equal(t, original, result)
	})

	t.Run("Map", func(t *testing.T) {
		original := map[string]int32{"a": 1, "b": 2, "c": 3}
		data, err := Serialize(f, original)
		require.NoError(t, err)

		result, err := Deserialize[map[string]int32](f, data)
		require.NoError(t, err)
		require.Equal(t, original, result)
	})
}

// TestSerializeDeserializeRoundTrip tests that serialized data can be correctly deserialized.
func TestSerializeDeserializeRoundTrip(t *testing.T) {
	f := NewFory(WithRefTracking(true))

	// Test that Serialize[T] uses TypedSerializer when available
	t.Run("TypedSerializerPath", func(t *testing.T) {
		// Int32 has a registered TypedSerializer
		original := int32(999)
		data, err := Serialize(f, original)
		require.NoError(t, err)
		require.NotEmpty(t, data)

		result, err := Deserialize[int32](f, data)
		require.NoError(t, err)
		require.Equal(t, original, result)
	})

	t.Run("ReflectionFallbackPath", func(t *testing.T) {
		// Custom struct falls back to reflection
		type CustomStruct struct {
			ID   int64
			Name string
		}
		f.RegisterByNamespace(CustomStruct{}, "test", "CustomStruct")

		original := CustomStruct{ID: 123, Name: "test"}
		data, err := Serialize(f, original)
		require.NoError(t, err)

		result, err := Deserialize[CustomStruct](f, data)
		require.NoError(t, err)
		require.Equal(t, original, result)
	})
}
