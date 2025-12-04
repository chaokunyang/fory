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
	"reflect"
	"unsafe"
)

// MetaContext used to share data across multiple serialization calls
type MetaContext struct {
	// typeMap make sure each type just fully serialize once, the following serialization will use the index
	typeMap map[reflect.Type]uint32
	// record typeDefs need to be serialized during one serialization
	writingTypeDefs []*TypeDef
	// read from peer
	readTypeInfos []TypeInfo
	// scopedMetaShareEnabled controls whether meta sharing is scoped to single serialization
	scopedMetaShareEnabled bool
}

// NewMetaContext creates a new MetaContext
func NewMetaContext(scopedMetaShareEnabled bool) *MetaContext {
	return &MetaContext{
		typeMap:                make(map[reflect.Type]uint32),
		scopedMetaShareEnabled: scopedMetaShareEnabled,
	}
}

// resetRead resets the read-related state of the MetaContext
func (mc *MetaContext) resetRead() {
	if mc.scopedMetaShareEnabled {
		mc.readTypeInfos = mc.readTypeInfos[:0] // Reset slice but keep capacity
	} else {
		mc.readTypeInfos = nil
	}
}

// resetWrite resets the write-related state of the MetaContext
func (mc *MetaContext) resetWrite() {
	if mc.scopedMetaShareEnabled {
		for k := range mc.typeMap {
			delete(mc.typeMap, k)
		}
		mc.writingTypeDefs = mc.writingTypeDefs[:0] // Reset slice but keep capacity
	} else {
		mc.typeMap = nil
		mc.writingTypeDefs = nil
	}
}

// SetScopedMetaShareEnabled sets the scoped meta share mode
func (mc *MetaContext) SetScopedMetaShareEnabled(enabled bool) {
	mc.scopedMetaShareEnabled = enabled
}

// IsScopedMetaShareEnabled returns whether scoped meta sharing is enabled
func (mc *MetaContext) IsScopedMetaShareEnabled() bool {
	return mc.scopedMetaShareEnabled
}

// WriteContext holds all state needed during serialization.
// It replaces passing (*Fory, *ByteBuffer) to every method.
type WriteContext struct {
	buffer      *ByteBuffer
	refWriter   *RefWriter
	registry    *Registry
	refTracking bool // Cached flag to avoid indirection
	depth       int
	maxDepth    int
}

// NewWriteContext creates a new write context
func NewWriteContext(buffer *ByteBuffer, registry *Registry, refTracking bool, maxDepth int) *WriteContext {
	return &WriteContext{
		buffer:      buffer,
		refWriter:   NewRefWriter(refTracking),
		registry:    registry,
		refTracking: refTracking,
		maxDepth:    maxDepth,
	}
}

// Reset clears state for reuse (called before each Serialize)
func (c *WriteContext) Reset() {
	c.buffer.Reset()
	c.refWriter.Reset()
	c.depth = 0
}

// Buffer returns the underlying buffer
func (c *WriteContext) Buffer() *ByteBuffer {
	return c.buffer
}

// Registry returns the type registry
func (c *WriteContext) Registry() *Registry {
	return c.registry
}

// RefTracking returns whether reference tracking is enabled
func (c *WriteContext) RefTracking() bool {
	return c.refTracking
}

// Inline primitive writes (compiler will inline these)
func (c *WriteContext) WriteBool(v bool)        { c.buffer.WriteBool(v) }
func (c *WriteContext) WriteInt8(v int8)        { c.buffer.WriteByte_(byte(v)) }
func (c *WriteContext) WriteInt16(v int16)      { c.buffer.WriteInt16(v) }
func (c *WriteContext) WriteInt32(v int32)      { c.buffer.WriteInt32(v) }
func (c *WriteContext) WriteInt64(v int64)      { c.buffer.WriteInt64(v) }
func (c *WriteContext) WriteFloat32(v float32)  { c.buffer.WriteFloat32(v) }
func (c *WriteContext) WriteFloat64(v float64)  { c.buffer.WriteFloat64(v) }
func (c *WriteContext) WriteVarInt32(v int32)   { c.buffer.WriteVarint32(v) }
func (c *WriteContext) WriteVarInt64(v int64)   { c.buffer.WriteVarint64(v) }
func (c *WriteContext) WriteVarUint32(v uint32) { c.buffer.WriteVarUint32(v) }
func (c *WriteContext) WriteByte(v byte)        { c.buffer.WriteByte_(v) }
func (c *WriteContext) WriteBytes(v []byte)     { c.buffer.WriteBinary(v) }

func (c *WriteContext) WriteString(v string) {
	c.buffer.WriteVarUint32(uint32(len(v)))
	if len(v) > 0 {
		c.buffer.WriteBinary(unsafe.Slice(unsafe.StringData(v), len(v)))
	}
}

func (c *WriteContext) WriteBinary(v []byte) {
	c.buffer.WriteVarUint32(uint32(len(v)))
	c.buffer.WriteBinary(v)
}

func (c *WriteContext) WriteTypeId(id TypeId) {
	c.buffer.WriteInt16(id)
}

// RefWriter handles reference tracking during serialization
type RefWriter struct {
	enabled bool
	refs    map[uintptr]int32
	nextId  int32
}

func NewRefWriter(enabled bool) *RefWriter {
	return &RefWriter{
		enabled: enabled,
		refs:    make(map[uintptr]int32),
		nextId:  0,
	}
}

func (w *RefWriter) Reset() {
	clear(w.refs)
	w.nextId = 0
}

// TryWriteRef attempts to write a reference. Returns true if the value was already seen.
func (w *RefWriter) TryWriteRef(ctx *WriteContext, ptr uintptr) bool {
	if !w.enabled {
		return false
	}
	if refId, exists := w.refs[ptr]; exists {
		ctx.buffer.WriteInt8(RefFlag)
		ctx.buffer.WriteVarint32(refId)
		return true
	}
	// First time seeing this reference
	w.refs[ptr] = w.nextId
	w.nextId++
	ctx.buffer.WriteInt8(RefValueFlag)
	return false
}

// TryWriteRefValue writes ref flag for a new value and registers it
func (w *RefWriter) TryWriteRefValue(ctx *WriteContext, ptr uintptr) {
	if w.enabled {
		w.refs[ptr] = w.nextId
		w.nextId++
		ctx.buffer.WriteInt8(RefValueFlag)
	} else {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
}

// ReadContext holds all state needed during deserialization.
type ReadContext struct {
	buffer      *ByteBuffer
	refReader   *RefReader
	registry    *Registry
	refTracking bool // Cached flag to avoid indirection
}

// NewReadContext creates a new read context
func NewReadContext(buffer *ByteBuffer, registry *Registry, refTracking bool) *ReadContext {
	return &ReadContext{
		buffer:      buffer,
		refReader:   NewRefReader(refTracking),
		registry:    registry,
		refTracking: refTracking,
	}
}

// Reset clears state for reuse (called before each Deserialize)
func (c *ReadContext) Reset() {
	c.buffer.Reset()
	c.refReader.Reset()
}

// SetData sets new input data (for buffer reuse)
func (c *ReadContext) SetData(data []byte) {
	c.buffer = NewByteBuffer(data)
}

// Buffer returns the underlying buffer
func (c *ReadContext) Buffer() *ByteBuffer {
	return c.buffer
}

// Registry returns the type registry
func (c *ReadContext) Registry() *Registry {
	return c.registry
}

// RefTracking returns whether reference tracking is enabled
func (c *ReadContext) RefTracking() bool {
	return c.refTracking
}

// Inline primitive reads
func (c *ReadContext) ReadBool() bool        { return c.buffer.ReadBool() }
func (c *ReadContext) ReadInt8() int8        { return int8(c.buffer.ReadByte_()) }
func (c *ReadContext) ReadInt16() int16      { return c.buffer.ReadInt16() }
func (c *ReadContext) ReadInt32() int32      { return c.buffer.ReadInt32() }
func (c *ReadContext) ReadInt64() int64      { return c.buffer.ReadInt64() }
func (c *ReadContext) ReadFloat32() float32  { return c.buffer.ReadFloat32() }
func (c *ReadContext) ReadFloat64() float64  { return c.buffer.ReadFloat64() }
func (c *ReadContext) ReadVarInt32() int32   { return c.buffer.ReadVarint32() }
func (c *ReadContext) ReadVarInt64() int64   { return c.buffer.ReadVarint64() }
func (c *ReadContext) ReadVarUint32() uint32 { return c.buffer.ReadVarUint32() }
func (c *ReadContext) ReadByte() byte        { return c.buffer.ReadByte_() }

func (c *ReadContext) ReadString() string {
	length := c.buffer.ReadVarUint32()
	if length == 0 {
		return ""
	}
	data := c.buffer.ReadBinary(int(length))
	return string(data)
}

func (c *ReadContext) ReadBinary() []byte {
	length := c.buffer.ReadVarUint32()
	return c.buffer.ReadBinary(int(length))
}

func (c *ReadContext) ReadTypeId() TypeId {
	return c.buffer.ReadInt16()
}

// RefReader handles reference tracking during deserialization
type RefReader struct {
	enabled bool
	refs    []any
}

func NewRefReader(enabled bool) *RefReader {
	return &RefReader{
		enabled: enabled,
		refs:    make([]any, 0, 16),
	}
}

func (r *RefReader) Reset() {
	r.refs = r.refs[:0]
}

// ReadRefFlag reads the reference flag and returns:
// - flag: the flag value
// - refId: the reference ID if flag is RefFlag
// - needRead: true if we need to read the actual data
func (r *RefReader) ReadRefFlag(ctx *ReadContext) (flag int8, refId int32, needRead bool) {
	flag = ctx.ReadInt8()
	switch flag {
	case NullFlag:
		return flag, 0, false
	case RefFlag:
		refId = ctx.ReadVarInt32()
		return flag, refId, false
	default: // RefValueFlag or NotNullValueFlag
		return flag, 0, true
	}
}

// Reference stores a reference for later retrieval
func (r *RefReader) Reference(value any) {
	if r.enabled {
		r.refs = append(r.refs, value)
	}
}

// GetRef retrieves a reference by ID
func (r *RefReader) GetRef(refId int32) any {
	if int(refId) < len(r.refs) {
		return r.refs[refId]
	}
	return nil
}

// Registry is a placeholder for the new generics-based registry
// TODO: This will be replaced with the proper Registry implementation
type Registry struct {
	// Placeholder for now - will be implemented in registry.go
}
