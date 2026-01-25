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

package optional

// Optional represents an optional value without pointer indirection.
type Optional[T any] struct {
	Value T
	Has   bool
}

// Some returns an Optional containing a value.
func Some[T any](v T) Optional[T] {
	return Optional[T]{Value: v, Has: true}
}

// None returns an empty Optional.
func None[T any]() Optional[T] {
	return Optional[T]{}
}

// FromPtr converts a pointer to an Optional.
func FromPtr[T any](v *T) Optional[T] {
	if v == nil {
		return None[T]()
	}
	return Some(*v)
}

// Ptr returns a pointer to the contained value or nil.
func (o Optional[T]) Ptr() *T {
	if !o.Has {
		return nil
	}
	v := o.Value
	return &v
}

// IsSome reports whether the optional contains a value.
func (o Optional[T]) IsSome() bool { return o.Has }

// IsNone reports whether the optional is empty.
func (o Optional[T]) IsNone() bool { return !o.Has }

// Expect returns the contained value or panics with the provided message.
func (o Optional[T]) Expect(message string) T {
	if o.Has {
		return o.Value
	}
	panic(message)
}

// Unwrap returns the contained value or panics.
func (o Optional[T]) Unwrap() T {
	if o.Has {
		return o.Value
	}
	panic("optional: unwrap on None")
}

// UnwrapOr returns the contained value or a default.
func (o Optional[T]) UnwrapOr(defaultValue T) T {
	if o.Has {
		return o.Value
	}
	return defaultValue
}

// UnwrapOrDefault returns the contained value or the zero value.
func (o Optional[T]) UnwrapOrDefault() T {
	if o.Has {
		return o.Value
	}
	var zero T
	return zero
}

// UnwrapOrElse returns the contained value or computes a default.
func (o Optional[T]) UnwrapOrElse(defaultFn func() T) T {
	if o.Has {
		return o.Value
	}
	return defaultFn()
}

// Map maps an Optional[T] to Optional[U] by applying a function.
func Map[T, U any](o Optional[T], f func(T) U) Optional[U] {
	if o.Has {
		return Some(f(o.Value))
	}
	return None[U]()
}

// MapOr applies a function to the contained value or returns a default.
func MapOr[T, U any](o Optional[T], defaultValue U, f func(T) U) U {
	if o.Has {
		return f(o.Value)
	}
	return defaultValue
}

// MapOrElse applies a function to the contained value or computes a default.
func MapOrElse[T, U any](o Optional[T], defaultFn func() U, f func(T) U) U {
	if o.Has {
		return f(o.Value)
	}
	return defaultFn()
}

// And returns None if either option is None, otherwise returns the second option.
func And[T, U any](o Optional[T], other Optional[U]) Optional[U] {
	if o.Has {
		return other
	}
	return None[U]()
}

// AndThen returns None if this option is None, otherwise calls f and returns its result.
func AndThen[T, U any](o Optional[T], f func(T) Optional[U]) Optional[U] {
	if o.Has {
		return f(o.Value)
	}
	return None[U]()
}

// Or returns the option if it is Some, otherwise returns other.
func (o Optional[T]) Or(other Optional[T]) Optional[T] {
	if o.Has {
		return o
	}
	return other
}

// OrElse returns the option if it is Some, otherwise returns the result of f.
func (o Optional[T]) OrElse(f func() Optional[T]) Optional[T] {
	if o.Has {
		return o
	}
	return f()
}

// Filter returns None if the predicate returns false.
func (o Optional[T]) Filter(predicate func(T) bool) Optional[T] {
	if o.Has && predicate(o.Value) {
		return o
	}
	return None[T]()
}

// Result represents a simplified Result type for OkOr helpers.
type Result[T any] struct {
	Value T
	Err   error
}

// OkOr transforms the option into a Result, using err if None.
func (o Optional[T]) OkOr(err error) Result[T] {
	if o.Has {
		return Result[T]{Value: o.Value}
	}
	return Result[T]{Err: err}
}

// OkOrElse transforms the option into a Result, using a function to produce the error.
func (o Optional[T]) OkOrElse(errFn func() error) Result[T] {
	if o.Has {
		return Result[T]{Value: o.Value}
	}
	return Result[T]{Err: errFn()}
}

// Take takes the value out, leaving None in its place.
func (o *Optional[T]) Take() Optional[T] {
	if o == nil || !o.Has {
		return None[T]()
	}
	v := o.Value
	o.Has = false
	var zero T
	o.Value = zero
	return Some(v)
}

// Set sets the option to Some(value).
func (o *Optional[T]) Set(v T) {
	if o == nil {
		return
	}
	o.Value = v
	o.Has = true
}

// Flatten transforms Optional[Optional[T]] into Optional[T].
func Flatten[T any](o Optional[Optional[T]]) Optional[T] {
	if !o.Has {
		return None[T]()
	}
	return o.Value
}

// Int8 wraps an int8 value in Optional.
func Int8(v int8) Optional[int8] { return Some(v) }

// Int16 wraps an int16 value in Optional.
func Int16(v int16) Optional[int16] { return Some(v) }

// Int32 wraps an int32 value in Optional.
func Int32(v int32) Optional[int32] { return Some(v) }

// Int64 wraps an int64 value in Optional.
func Int64(v int64) Optional[int64] { return Some(v) }

// Int wraps an int value in Optional.
func Int(v int) Optional[int] { return Some(v) }

// Uint8 wraps a uint8 value in Optional.
func Uint8(v uint8) Optional[uint8] { return Some(v) }

// Uint16 wraps a uint16 value in Optional.
func Uint16(v uint16) Optional[uint16] { return Some(v) }

// Uint32 wraps a uint32 value in Optional.
func Uint32(v uint32) Optional[uint32] { return Some(v) }

// Uint64 wraps a uint64 value in Optional.
func Uint64(v uint64) Optional[uint64] { return Some(v) }

// Uint wraps a uint value in Optional.
func Uint(v uint) Optional[uint] { return Some(v) }

// String wraps a string value in Optional.
func String(v string) Optional[string] { return Some(v) }

// Bool wraps a bool value in Optional.
func Bool(v bool) Optional[bool] { return Some(v) }
