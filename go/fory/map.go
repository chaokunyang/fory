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
	"fmt"
	"reflect"
)

const (
	TRACKING_KEY_REF   = 1 << 0 // 0b00000001
	KEY_HAS_NULL       = 1 << 1 // 0b00000010
	KEY_DECL_TYPE      = 1 << 2 // 0b00000100
	TRACKING_VALUE_REF = 1 << 3 // 0b00001000
	VALUE_HAS_NULL     = 1 << 4 // 0b00010000
	VALUE_DECL_TYPE    = 1 << 5 // 0b00100000
	MAX_CHUNK_SIZE     = 255
)

const (
	KV_NULL                               = KEY_HAS_NULL | VALUE_HAS_NULL                       // 0b00010010
	NULL_KEY_VALUE_DECL_TYPE              = KEY_HAS_NULL | VALUE_DECL_TYPE                      // 0b00100010
	NULL_KEY_VALUE_DECL_TYPE_TRACKING_REF = KEY_HAS_NULL | VALUE_DECL_TYPE | TRACKING_VALUE_REF // 0b00101010
	NULL_VALUE_KEY_DECL_TYPE              = VALUE_HAS_NULL | KEY_DECL_TYPE                      // 0b00010100
	NULL_VALUE_KEY_DECL_TYPE_TRACKING_REF = VALUE_HAS_NULL | KEY_DECL_TYPE | TRACKING_KEY_REF   // 0b00010101
)

type mapSerializer struct {
	type_             reflect.Type
	keySerializer     Serializer
	valueSerializer   Serializer
	keyReferencable   bool
	valueReferencable bool
	mapInStruct       bool // Use mapInStruct to distinguish concrete map types during deserialization

}

func (s mapSerializer) TypeId() TypeId {
	return MAP
}

func (s mapSerializer) NeedToWriteRef() bool {
	return true
}

func (s mapSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	if value.Kind() == reflect.Interface {
		value = value.Elem()
	}
	length := value.Len()
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return nil
	}
	typeResolver := ctx.TypeResolver()
	// Use declared serializers if available (mapInStruct case)
	// Don't clear them - we need them for KEY_DECL_TYPE/VALUE_DECL_TYPE flags
	keySerializer := s.keySerializer
	valueSerializer := s.valueSerializer
	iter := value.MapRange()
	if !iter.Next() {
		return nil
	}
	entryKey, entryVal := iter.Key(), iter.Value()
	if entryKey.Kind() == reflect.Interface {
		entryKey = entryKey.Elem()
	}
	if entryVal.Kind() == reflect.Interface {
		entryVal = entryVal.Elem()
	}
	hasNext := true
	for hasNext {
		for {
			keyValid := isValid(entryKey)
			valValid := isValid(entryVal)
			if keyValid {
				if valValid {
					break
				}
				if keySerializer != nil {
					trackKeyRef := keySerializer.NeedToWriteRef()
					if trackKeyRef {
						buf.WriteInt8(NULL_VALUE_KEY_DECL_TYPE_TRACKING_REF)
						err := keySerializer.Write(ctx, true, false, entryKey)
						if err != nil {
							return err
						}
					} else {
						buf.WriteInt8(NULL_VALUE_KEY_DECL_TYPE)
						err := keySerializer.WriteData(ctx, entryKey)
						if err != nil {
							return err
						}
					}
				} else {
					buf.WriteInt8(VALUE_HAS_NULL | TRACKING_KEY_REF)
					err := ctx.WriteValue(entryKey)
					if err != nil {
						return err
					}
				}
			} else {
				if valValid {
					if valueSerializer != nil {
						trackValRef := valueSerializer.NeedToWriteRef()
						if trackValRef {
							buf.WriteInt8(NULL_KEY_VALUE_DECL_TYPE_TRACKING_REF)
							err := valueSerializer.Write(ctx, true, false, entryVal)
							if err != nil {
								return err
							}
						} else {
							buf.WriteInt8(NULL_KEY_VALUE_DECL_TYPE)
							err := valueSerializer.WriteData(ctx, entryVal)
							if err != nil {
								return err
							}
						}
					} else {
						buf.WriteInt8(KEY_HAS_NULL | TRACKING_VALUE_REF)
						err := ctx.WriteValue(entryVal)
						if err != nil {
							return err
						}
					}
				} else {
					buf.WriteInt8(KV_NULL)
				}
			}
			if iter.Next() {
				entryKey, entryVal = iter.Key(), iter.Value()
				if entryKey.Kind() == reflect.Interface {
					entryKey = entryKey.Elem()
				}
				if entryVal.Kind() == reflect.Interface {
					entryVal = entryVal.Elem()
				}
			} else {
				hasNext = false
				break
			}
		}
		if !hasNext {
			break
		}
		keyCls := getActualType(entryKey)
		valueCls := getActualType(entryVal)
		buf.WriteInt16(-1)
		chunkSizeOffset := buf.writerIndex
		chunkHeader := 0
		if keySerializer != nil {
			chunkHeader |= KEY_DECL_TYPE
		} else {
			keyTypeInfo, _ := getActualTypeInfo(entryKey, typeResolver)
			if err := typeResolver.writeTypeInfo(buf, keyTypeInfo); err != nil {
				return err
			}
			keySerializer = keyTypeInfo.Serializer
		}
		if valueSerializer != nil {
			chunkHeader |= VALUE_DECL_TYPE
		} else {
			valueTypeInfo, _ := getActualTypeInfo(entryVal, typeResolver)
			if err := typeResolver.writeTypeInfo(buf, valueTypeInfo); err != nil {
				return err
			}
			valueSerializer = valueTypeInfo.Serializer
		}
		keyWriteRef := s.keyReferencable
		if keySerializer != nil {
			keyWriteRef = keySerializer.NeedToWriteRef()
		} else {
			keyWriteRef = false
		}
		valueWriteRef := s.valueReferencable
		if valueSerializer != nil {
			valueWriteRef = valueSerializer.NeedToWriteRef()
		} else {
			valueWriteRef = false
		}

		if keyWriteRef {
			chunkHeader |= TRACKING_KEY_REF
		}
		if valueWriteRef {
			chunkHeader |= TRACKING_VALUE_REF
		}
		buf.PutUint8(chunkSizeOffset-2, uint8(chunkHeader))
		chunkSize := 0
		for chunkSize < MAX_CHUNK_SIZE {
			if !isValid(entryKey) || !isValid(entryVal) || getActualType(entryKey) != keyCls || getActualType(entryVal) != valueCls {
				break
			}

			// WriteData key with optional ref tracking
			err := keySerializer.Write(ctx, keyWriteRef, false, entryKey)
			if err != nil {
				return err
			}

			// WriteData value with optional ref tracking
			err = valueSerializer.Write(ctx, valueWriteRef, false, entryVal)
			if err != nil {
				return err
			}

			chunkSize++

			if iter.Next() {
				entryKey, entryVal = iter.Key(), iter.Value()
				if entryKey.Kind() == reflect.Interface {
					entryKey = entryKey.Elem()
				}
				if entryVal.Kind() == reflect.Interface {
					entryVal = entryVal.Elem()
				}
			} else {
				hasNext = false
				break
			}
		}
		keySerializer = s.keySerializer
		valueSerializer = s.valueSerializer
		buf.PutUint8(chunkSizeOffset-1, uint8(chunkSize))
	}
	return nil
}

func (s mapSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
			ctx.buffer.WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.buffer, value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		// For polymorphic map key/values, need to write full type info
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value, true)
		if err != nil {
			return err
		}
		if err := ctx.TypeResolver().writeTypeInfo(ctx.buffer, typeInfo); err != nil {
			return err
		}
	}
	return s.WriteData(ctx, value)
}

func (s mapSerializer) writeObj(ctx *WriteContext, serializer Serializer, obj reflect.Value) error {
	return serializer.WriteData(ctx, obj)
}

func (s mapSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	refResolver := ctx.RefResolver()
	if s.type_ == nil {
		s.type_ = type_
	}

	if value.IsNil() {
		isIfaceMap := func(t reflect.Type) bool {
			return t.Kind() == reflect.Map &&
				t.Key().Kind() == reflect.Interface &&
				t.Elem().Kind() == reflect.Interface
		}
		// case 1: A map inside a struct will have a fixed key and value type.
		// case 2: The user has specified the type of the map explicitly.
		// Otherwise, a generic map type will be used
		switch {
		case s.mapInStruct:
			value.Set(reflect.MakeMap(type_))
		case !isIfaceMap(type_):
			value.Set(reflect.MakeMap(type_))
		default:
			iface := reflect.TypeOf((*interface{})(nil)).Elem()
			newMapType := reflect.MapOf(iface, iface)
			value.Set(reflect.MakeMap(newMapType))
		}
	}

	refResolver.Reference(value)
	size := int(buf.ReadVaruint32())
	var chunkHeader uint8
	if size > 0 {
		chunkHeader = buf.ReadUint8()
	}

	keyType := type_.Key()
	valueType := type_.Elem()
	keySer := s.keySerializer
	valSer := s.valueSerializer
	typeResolver := ctx.TypeResolver()

	for size > 0 {
		for {
			keyHasNull := (chunkHeader & KEY_HAS_NULL) != 0
			valueHasNull := (chunkHeader & VALUE_HAS_NULL) != 0
			var k, v reflect.Value
			if !keyHasNull {
				if !valueHasNull {
					break
				} else {
					// Null value case: read key only
					keyDeclared := (chunkHeader & KEY_DECL_TYPE) != 0
					trackKeyRef := (chunkHeader & TRACKING_KEY_REF) != 0

					// When trackKeyRef is set and type is not declared, Java writes:
					// ref flag + type info + data
					// So we need to read ref flag first, then type info, then data
					if trackKeyRef && !keyDeclared {
						// Read ref flag first
						refID, err := refResolver.TryPreserveRefId(buf)
						if err != nil {
							return err
						}
						if int8(refID) < NotNullValueFlag {
							// Reference to existing object
							obj := refResolver.GetReadObject(refID)
							if obj.IsValid() {
								// Use zero value for null value (nil for interface{}/pointer types)
								nullVal := reflect.Zero(value.Type().Elem())
								value.SetMapIndex(obj, nullVal)
							}
							size--
							if size == 0 {
								return nil
							}
							chunkHeader = buf.ReadUint8()
							continue
						}

						// Read type info
						ti, err := typeResolver.readTypeInfo(buf, value)
						if err != nil {
							return err
						}
						keySer = ti.Serializer
						keyType = ti.Type

						kt := keyType
						if kt == nil {
							kt = value.Type().Key()
						}
						k = reflect.New(kt).Elem()

						// Read data (ref already handled)
						if err := keySer.ReadData(ctx, keyType, k); err != nil {
							return fmt.Errorf("failed to read map key: %w", err)
						}
						refResolver.Reference(k)
						// Use zero value for null value (nil for interface{}/pointer types)
						nullVal := reflect.Zero(value.Type().Elem())
						value.SetMapIndex(k, nullVal)
					} else {
						// ReadData type info if not declared
						var keyTypeInfo *TypeInfo
						if !keyDeclared {
							ti, err := typeResolver.readTypeInfo(buf, value)
							if err != nil {
								return err
							}
							keySer = ti.Serializer
							keyType = ti.Type
							keyTypeInfo = &ti
						}

						kt := keyType
						if kt == nil {
							kt = value.Type().Key()
						}
						k = reflect.New(kt).Elem()

						// Use ReadWithTypeInfo if type was read, otherwise Read
						if keyTypeInfo != nil {
							if err := keySer.ReadWithTypeInfo(ctx, trackKeyRef, keyTypeInfo, k); err != nil {
								return fmt.Errorf("failed to read map key: %w", err)
							}
						} else {
							if err := keySer.Read(ctx, trackKeyRef, false, k); err != nil {
								return fmt.Errorf("failed to read map key: %w", err)
							}
						}
						// Use zero value for null value (nil for interface{}/pointer types)
						nullVal := reflect.Zero(value.Type().Elem())
						value.SetMapIndex(k, nullVal)
					}
				}
			} else {
				if !valueHasNull {
					// Null key case: read value only
					valueDeclared := (chunkHeader & VALUE_DECL_TYPE) != 0
					trackValueRef := (chunkHeader & TRACKING_VALUE_REF) != 0

					// When trackValueRef is set and type is not declared, Java writes:
					// ref flag + type info + data
					// So we need to read ref flag first, then type info, then data
					if trackValueRef && !valueDeclared {
						// Read ref flag first
						refID, err := refResolver.TryPreserveRefId(buf)
						if err != nil {
							return err
						}
						if int8(refID) < NotNullValueFlag {
							// Reference to existing object
							obj := refResolver.GetReadObject(refID)
							if obj.IsValid() {
								// Use zero value for null key (nil for interface{}/pointer types)
								nullKey := reflect.Zero(value.Type().Key())
								value.SetMapIndex(nullKey, obj)
							}
							size--
							if size == 0 {
								return nil
							}
							chunkHeader = buf.ReadUint8()
							continue
						}

						// Read type info
						ti, err := typeResolver.readTypeInfo(buf, value)
						if err != nil {
							return err
						}
						valSer = ti.Serializer
						valueType = ti.Type

						vt := valueType
						if vt == nil {
							vt = value.Type().Elem()
						}
						v = reflect.New(vt).Elem()

						// Read data (ref already handled)
						if err := valSer.ReadData(ctx, valueType, v); err != nil {
							return fmt.Errorf("failed to read map value: %w", err)
						}
						refResolver.Reference(v)
						// Use zero value for null key (nil for interface{}/pointer types)
						nullKey := reflect.Zero(value.Type().Key())
						value.SetMapIndex(nullKey, v)
					} else {
						// ReadData type info if not declared
						var valueTypeInfo *TypeInfo
						if !valueDeclared {
							ti, err := typeResolver.readTypeInfo(buf, value)
							if err != nil {
								return err
							}
							valSer = ti.Serializer
							valueType = ti.Type
							valueTypeInfo = &ti
						}

						vt := valueType
						if vt == nil {
							vt = value.Type().Elem()
						}
						v = reflect.New(vt).Elem()

						// Use ReadWithTypeInfo if type was read, otherwise Read
						if valueTypeInfo != nil {
							if err := valSer.ReadWithTypeInfo(ctx, trackValueRef, valueTypeInfo, v); err != nil {
								return fmt.Errorf("failed to read map value: %w", err)
							}
						} else {
							if err := valSer.Read(ctx, trackValueRef, false, v); err != nil {
								return fmt.Errorf("failed to read map value: %w", err)
							}
						}
						// Use zero value for null key (nil for interface{}/pointer types)
						nullKey := reflect.Zero(value.Type().Key())
						value.SetMapIndex(nullKey, v)
					}
				} else {
					// Both key and value are null
					nullKey := reflect.Zero(value.Type().Key())
					nullVal := reflect.Zero(value.Type().Elem())
					value.SetMapIndex(nullKey, nullVal)
				}
			}

			size--
			if size == 0 {
				return nil
			} else {
				chunkHeader = buf.ReadUint8()
			}
		}

		trackKeyRef := (chunkHeader & TRACKING_KEY_REF) != 0
		trackValRef := (chunkHeader & TRACKING_VALUE_REF) != 0
		keyDeclType := (chunkHeader & KEY_DECL_TYPE) != 0
		valDeclType := (chunkHeader & VALUE_DECL_TYPE) != 0
		chunkSize := int(buf.ReadUint8())

		// ReadData type info if not declared
		var keyTypeInfo *TypeInfo
		if !keyDeclType {
			ti, err := typeResolver.readTypeInfo(buf, value)
			if err != nil {
				return err
			}
			keySer = ti.Serializer
			keyType = ti.Type
			keyTypeInfo = &ti
		} else if keySer == nil {
			// KEY_DECL_TYPE is set but we don't have a serializer - get one from the map's key type
			keySer, _ = typeResolver.getSerializerByType(keyType, false)
		}
		var valueTypeInfo *TypeInfo
		if !valDeclType {
			ti, err := typeResolver.readTypeInfo(buf, value)
			if err != nil {
				return err
			}
			valSer = ti.Serializer
			valueType = ti.Type
			valueTypeInfo = &ti
		} else if valSer == nil {
			// VALUE_DECL_TYPE is set but we don't have a serializer - get one from the map's value type
			valSer, _ = typeResolver.getSerializerByType(valueType, false)
		}

		for i := 0; i < chunkSize; i++ {
			var k, v reflect.Value

			// ReadData key
			kt := keyType
			if kt == nil {
				kt = value.Type().Key()
			}
			k = reflect.New(kt).Elem()

			// Use ReadWithTypeInfo if type was read, otherwise Read
			if keyTypeInfo != nil {
				if err := keySer.ReadWithTypeInfo(ctx, trackKeyRef, keyTypeInfo, k); err != nil {
					return fmt.Errorf("failed to read map key in chunk: %w", err)
				}
			} else {
				if err := keySer.Read(ctx, trackKeyRef, false, k); err != nil {
					return fmt.Errorf("failed to read map key in chunk: %w", err)
				}
			}

			// ReadData value
			vt := valueType
			if vt == nil {
				vt = value.Type().Elem()
			}
			v = reflect.New(vt).Elem()

			// Use ReadWithTypeInfo if type was read, otherwise Read
			if valueTypeInfo != nil {
				if err := valSer.ReadWithTypeInfo(ctx, trackValRef, valueTypeInfo, v); err != nil {
					return fmt.Errorf("failed to read map value in chunk: %w", err)
				}
			} else {
				if err := valSer.Read(ctx, trackValRef, false, v); err != nil {
					return fmt.Errorf("failed to read map value in chunk: %w", err)
				}
			}
			// Unwrap interfaces if they're not the map's type
			if k.Kind() == reflect.Interface {
				k = k.Elem()
			}
			if v.Kind() == reflect.Interface {
				v = v.Elem()
			}
			setMapValue(value, k, v)
			size--
		}

		keySer = s.keySerializer
		valSer = s.valueSerializer
		if size > 0 {
			chunkHeader = buf.ReadUint8()
		}
	}
	return nil
}

func (s mapSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	if readRef {
		refID, err := ctx.RefResolver().TryPreserveRefId(buf)
		if err != nil {
			return err
		}
		if int8(refID) < NotNullValueFlag {
			// Reference found
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return nil
		}
	}
	if readType {
		// ReadData and discard type info for maps (we already know it's a map)
		typeID := uint32(buf.ReadVaruint32Small7())
		if IsNamespacedType(TypeId(typeID)) {
			// For namespaced types, need to read additional metadata
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s mapSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, readRef, false, value)
}

func (s mapSerializer) readObj(
	ctx *ReadContext,
	v *reflect.Value,
	serializer Serializer,
) error {
	return serializer.ReadData(ctx, v.Type(), *v)
}

func getActualType(v reflect.Value) reflect.Type {
	if v.Kind() == reflect.Interface && !v.IsNil() {
		return v.Elem().Type()
	}
	return v.Type()
}

func getActualTypeInfo(v reflect.Value, resolver *TypeResolver) (TypeInfo, error) {
	if v.Kind() == reflect.Interface && !v.IsNil() {
		elem := v.Elem()
		if !elem.IsValid() {
			return TypeInfo{}, fmt.Errorf("invalid interface value")
		}
		return resolver.getTypeInfo(elem, true)
	}
	return resolver.getTypeInfo(v, true)
}

func UnwrapReflectValue(v reflect.Value) reflect.Value {
	for v.Kind() == reflect.Interface && !v.IsNil() {
		v = v.Elem()
	}
	return v
}

func isValid(v reflect.Value) bool {
	// Zero values are valid, so apply this change temporarily.
	return v.IsValid()
}

// setMapValue sets a key-value pair into a map, handling interface types where
// the concrete type may need to be wrapped in a pointer to implement the interface.
func setMapValue(mapVal, key, value reflect.Value) {
	mapKeyType := mapVal.Type().Key()
	mapValueType := mapVal.Type().Elem()

	// Handle key
	finalKey := key
	if mapKeyType.Kind() == reflect.Interface {
		if !key.Type().AssignableTo(mapKeyType) {
			// Try pointer - common case where interface has pointer receivers
			ptr := reflect.New(key.Type())
			ptr.Elem().Set(key)
			finalKey = ptr
		}
	}

	// Handle value
	finalValue := value
	if mapValueType.Kind() == reflect.Interface {
		if !value.Type().AssignableTo(mapValueType) {
			// Try pointer - common case where interface has pointer receivers
			ptr := reflect.New(value.Type())
			ptr.Elem().Set(value)
			finalValue = ptr
		}
	}

	mapVal.SetMapIndex(finalKey, finalValue)
}
