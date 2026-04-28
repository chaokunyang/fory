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

package idl_test

import (
	"reflect"
	"time"

	fory "github.com/apache/fory/go/fory"
	bfloat16 "github.com/apache/fory/go/fory/bfloat16"
	float16 "github.com/apache/fory/go/fory/float16"
	examplecommon "github.com/apache/fory/integration_tests/idl_tests/go/example_common/generated"
)

const exampleMessageTypeID = 1500

type exampleVariantSpec struct {
	fieldID     int
	fieldName   string
	variantType reflect.Type
}

type exampleMessageEmpty struct{}

type exampleFieldBoolValue struct {
	BoolValue bool `fory:"id=1"`
}

type exampleFieldInt8Value struct {
	Int8Value int8 `fory:"id=2"`
}

type exampleFieldInt16Value struct {
	Int16Value int16 `fory:"id=3"`
}

type exampleFieldFixedInt32Value struct {
	FixedInt32Value int32 `fory:"id=4,compress=false"`
}

type exampleFieldVarint32Value struct {
	Varint32Value int32 `fory:"id=5,compress=true"`
}

type exampleFieldFixedInt64Value struct {
	FixedInt64Value int64 `fory:"id=6,encoding=fixed"`
}

type exampleFieldVarint64Value struct {
	Varint64Value int64 `fory:"id=7,encoding=varint"`
}

type exampleFieldTaggedInt64Value struct {
	TaggedInt64Value int64 `fory:"id=8,encoding=tagged"`
}

type exampleFieldUint8Value struct {
	Uint8Value uint8 `fory:"id=9"`
}

type exampleFieldUint16Value struct {
	Uint16Value uint16 `fory:"id=10"`
}

type exampleFieldFixedUint32Value struct {
	FixedUint32Value uint32 `fory:"id=11,compress=false"`
}

type exampleFieldVarUint32Value struct {
	VarUint32Value uint32 `fory:"id=12,compress=true"`
}

type exampleFieldFixedUint64Value struct {
	FixedUint64Value uint64 `fory:"id=13,encoding=fixed"`
}

type exampleFieldVarUint64Value struct {
	VarUint64Value uint64 `fory:"id=14,encoding=varint"`
}

type exampleFieldTaggedUint64Value struct {
	TaggedUint64Value uint64 `fory:"id=15,encoding=tagged"`
}

type exampleFieldFloat16Value struct {
	Float16Value float16.Float16 `fory:"id=16"`
}

type exampleFieldBfloat16Value struct {
	Bfloat16Value bfloat16.BFloat16 `fory:"id=17"`
}

type exampleFieldFloat32Value struct {
	Float32Value float32 `fory:"id=18"`
}

type exampleFieldFloat64Value struct {
	Float64Value float64 `fory:"id=19"`
}

type exampleFieldStringValue struct {
	StringValue string `fory:"id=20"`
}

type exampleFieldBytesValue struct {
	BytesValue []byte `fory:"id=21"`
}

type exampleFieldDateValue struct {
	DateValue fory.Date `fory:"id=22"`
}

type exampleFieldTimestampValue struct {
	TimestampValue time.Time `fory:"id=23"`
}

type exampleFieldDurationValue struct {
	DurationValue time.Duration `fory:"id=24"`
}

type exampleFieldDecimalValue struct {
	DecimalValue fory.Decimal `fory:"id=25"`
}

type exampleFieldEnumValue struct {
	EnumValue examplecommon.ExampleState `fory:"id=26"`
}

type exampleFieldMessageValue struct {
	MessageValue *examplecommon.ExampleLeaf `fory:"id=27,nullable"`
}

type exampleFieldUnionValue struct {
	UnionValue examplecommon.ExampleLeafUnion `fory:"id=28"`
}

type exampleFieldBoolList struct {
	BoolList []bool `fory:"id=101"`
}

type exampleFieldInt8List struct {
	Int8List []int8 `fory:"id=102,type=int8_array"`
}

type exampleFieldInt16List struct {
	Int16List []int16 `fory:"id=103"`
}

type exampleFieldFixedInt32List struct {
	FixedInt32List []int32 `fory:"id=104"`
}

type exampleFieldVarint32List struct {
	Varint32List []int32 `fory:"id=105"`
}

type exampleFieldFixedInt64List struct {
	FixedInt64List []int64 `fory:"id=106"`
}

type exampleFieldVarint64List struct {
	Varint64List []int64 `fory:"id=107"`
}

type exampleFieldTaggedInt64List struct {
	TaggedInt64List []int64 `fory:"id=108"`
}

type exampleFieldUint8List struct {
	Uint8List []uint8 `fory:"id=109,type=uint8_array"`
}

type exampleFieldUint16List struct {
	Uint16List []uint16 `fory:"id=110"`
}

type exampleFieldFixedUint32List struct {
	FixedUint32List []uint32 `fory:"id=111"`
}

type exampleFieldVarUint32List struct {
	VarUint32List []uint32 `fory:"id=112"`
}

type exampleFieldFixedUint64List struct {
	FixedUint64List []uint64 `fory:"id=113"`
}

type exampleFieldVarUint64List struct {
	VarUint64List []uint64 `fory:"id=114"`
}

type exampleFieldTaggedUint64List struct {
	TaggedUint64List []uint64 `fory:"id=115"`
}

type exampleFieldFloat16List struct {
	Float16List []float16.Float16 `fory:"id=116"`
}

type exampleFieldBfloat16List struct {
	Bfloat16List []bfloat16.BFloat16 `fory:"id=117"`
}

type exampleFieldMaybeFloat16List struct {
	MaybeFloat16List []*float16.Float16 `fory:"id=118,nullable=false"`
}

type exampleFieldMaybeBfloat16List struct {
	MaybeBfloat16List []*bfloat16.BFloat16 `fory:"id=119,nullable=false"`
}

type exampleFieldFloat32List struct {
	Float32List []float32 `fory:"id=120"`
}

type exampleFieldFloat64List struct {
	Float64List []float64 `fory:"id=121"`
}

type exampleFieldStringList struct {
	StringList []string `fory:"id=122"`
}

type exampleFieldBytesList struct {
	BytesList [][]byte `fory:"id=123"`
}

type exampleFieldDateList struct {
	DateList []fory.Date `fory:"id=124"`
}

type exampleFieldTimestampList struct {
	TimestampList []time.Time `fory:"id=125"`
}

type exampleFieldDurationList struct {
	DurationList []time.Duration `fory:"id=126"`
}

type exampleFieldDecimalList struct {
	DecimalList []fory.Decimal `fory:"id=127"`
}

type exampleFieldEnumList struct {
	EnumList []examplecommon.ExampleState `fory:"id=128"`
}

type exampleFieldMessageList struct {
	MessageList []examplecommon.ExampleLeaf `fory:"id=129,nested_ref=[[]]"`
}

type exampleFieldUnionList struct {
	UnionList []examplecommon.ExampleLeafUnion `fory:"id=130,nested_ref=[[]]"`
}

type exampleFieldStringValuesByBool struct {
	StringValuesByBool map[bool]string `fory:"id=201"`
}

type exampleFieldStringValuesByInt8 struct {
	StringValuesByInt8 map[int8]string `fory:"id=202"`
}

type exampleFieldStringValuesByInt16 struct {
	StringValuesByInt16 map[int16]string `fory:"id=203"`
}

type exampleFieldStringValuesByFixedInt32 struct {
	StringValuesByFixedInt32 map[int32]string `fory:"id=204"`
}

type exampleFieldStringValuesByVarint32 struct {
	StringValuesByVarint32 map[int32]string `fory:"id=205"`
}

type exampleFieldStringValuesByFixedInt64 struct {
	StringValuesByFixedInt64 map[int64]string `fory:"id=206"`
}

type exampleFieldStringValuesByVarint64 struct {
	StringValuesByVarint64 map[int64]string `fory:"id=207"`
}

type exampleFieldStringValuesByTaggedInt64 struct {
	StringValuesByTaggedInt64 map[int64]string `fory:"id=208"`
}

type exampleFieldStringValuesByUint8 struct {
	StringValuesByUint8 map[uint8]string `fory:"id=209"`
}

type exampleFieldStringValuesByUint16 struct {
	StringValuesByUint16 map[uint16]string `fory:"id=210"`
}

type exampleFieldStringValuesByFixedUint32 struct {
	StringValuesByFixedUint32 map[uint32]string `fory:"id=211"`
}

type exampleFieldStringValuesByVarUint32 struct {
	StringValuesByVarUint32 map[uint32]string `fory:"id=212"`
}

type exampleFieldStringValuesByFixedUint64 struct {
	StringValuesByFixedUint64 map[uint64]string `fory:"id=213"`
}

type exampleFieldStringValuesByVarUint64 struct {
	StringValuesByVarUint64 map[uint64]string `fory:"id=214"`
}

type exampleFieldStringValuesByTaggedUint64 struct {
	StringValuesByTaggedUint64 map[uint64]string `fory:"id=215"`
}

type exampleFieldStringValuesByString struct {
	StringValuesByString map[string]string `fory:"id=218"`
}

type exampleFieldStringValuesByTimestamp struct {
	StringValuesByTimestamp map[time.Time]string `fory:"id=219"`
}

type exampleFieldStringValuesByDuration struct {
	StringValuesByDuration map[time.Duration]string `fory:"id=220"`
}

type exampleFieldStringValuesByEnum struct {
	StringValuesByEnum map[examplecommon.ExampleState]string `fory:"id=221"`
}

type exampleFieldFloat16ValuesByName struct {
	Float16ValuesByName map[string]float16.Float16 `fory:"id=222"`
}

type exampleFieldMaybeFloat16ValuesByName struct {
	MaybeFloat16ValuesByName map[string]*float16.Float16 `fory:"id=223"`
}

type exampleFieldBfloat16ValuesByName struct {
	Bfloat16ValuesByName map[string]bfloat16.BFloat16 `fory:"id=224"`
}

type exampleFieldMaybeBfloat16ValuesByName struct {
	MaybeBfloat16ValuesByName map[string]*bfloat16.BFloat16 `fory:"id=225"`
}

type exampleFieldBytesValuesByName struct {
	BytesValuesByName map[string][]byte `fory:"id=226"`
}

type exampleFieldDateValuesByName struct {
	DateValuesByName map[string]fory.Date `fory:"id=227"`
}

type exampleFieldDecimalValuesByName struct {
	DecimalValuesByName map[string]fory.Decimal `fory:"id=228"`
}

type exampleFieldMessageValuesByName struct {
	MessageValuesByName map[string]examplecommon.ExampleLeaf `fory:"id=229,nested_ref=[[],[]]"`
}

type exampleFieldUnionValuesByName struct {
	UnionValuesByName map[string]examplecommon.ExampleLeafUnion `fory:"id=230,nested_ref=[[],[]]"`
}

var exampleVariantSpecs = []exampleVariantSpec{
	{fieldID: 1, fieldName: "BoolValue", variantType: reflect.TypeOf(exampleFieldBoolValue{})},
	{fieldID: 2, fieldName: "Int8Value", variantType: reflect.TypeOf(exampleFieldInt8Value{})},
	{fieldID: 3, fieldName: "Int16Value", variantType: reflect.TypeOf(exampleFieldInt16Value{})},
	{fieldID: 4, fieldName: "FixedInt32Value", variantType: reflect.TypeOf(exampleFieldFixedInt32Value{})},
	{fieldID: 5, fieldName: "Varint32Value", variantType: reflect.TypeOf(exampleFieldVarint32Value{})},
	{fieldID: 6, fieldName: "FixedInt64Value", variantType: reflect.TypeOf(exampleFieldFixedInt64Value{})},
	{fieldID: 7, fieldName: "Varint64Value", variantType: reflect.TypeOf(exampleFieldVarint64Value{})},
	{fieldID: 8, fieldName: "TaggedInt64Value", variantType: reflect.TypeOf(exampleFieldTaggedInt64Value{})},
	{fieldID: 9, fieldName: "Uint8Value", variantType: reflect.TypeOf(exampleFieldUint8Value{})},
	{fieldID: 10, fieldName: "Uint16Value", variantType: reflect.TypeOf(exampleFieldUint16Value{})},
	{fieldID: 11, fieldName: "FixedUint32Value", variantType: reflect.TypeOf(exampleFieldFixedUint32Value{})},
	{fieldID: 12, fieldName: "VarUint32Value", variantType: reflect.TypeOf(exampleFieldVarUint32Value{})},
	{fieldID: 13, fieldName: "FixedUint64Value", variantType: reflect.TypeOf(exampleFieldFixedUint64Value{})},
	{fieldID: 14, fieldName: "VarUint64Value", variantType: reflect.TypeOf(exampleFieldVarUint64Value{})},
	{fieldID: 15, fieldName: "TaggedUint64Value", variantType: reflect.TypeOf(exampleFieldTaggedUint64Value{})},
	{fieldID: 16, fieldName: "Float16Value", variantType: reflect.TypeOf(exampleFieldFloat16Value{})},
	{fieldID: 17, fieldName: "Bfloat16Value", variantType: reflect.TypeOf(exampleFieldBfloat16Value{})},
	{fieldID: 18, fieldName: "Float32Value", variantType: reflect.TypeOf(exampleFieldFloat32Value{})},
	{fieldID: 19, fieldName: "Float64Value", variantType: reflect.TypeOf(exampleFieldFloat64Value{})},
	{fieldID: 20, fieldName: "StringValue", variantType: reflect.TypeOf(exampleFieldStringValue{})},
	{fieldID: 21, fieldName: "BytesValue", variantType: reflect.TypeOf(exampleFieldBytesValue{})},
	{fieldID: 22, fieldName: "DateValue", variantType: reflect.TypeOf(exampleFieldDateValue{})},
	{fieldID: 23, fieldName: "TimestampValue", variantType: reflect.TypeOf(exampleFieldTimestampValue{})},
	{fieldID: 24, fieldName: "DurationValue", variantType: reflect.TypeOf(exampleFieldDurationValue{})},
	{fieldID: 25, fieldName: "DecimalValue", variantType: reflect.TypeOf(exampleFieldDecimalValue{})},
	{fieldID: 26, fieldName: "EnumValue", variantType: reflect.TypeOf(exampleFieldEnumValue{})},
	{fieldID: 27, fieldName: "MessageValue", variantType: reflect.TypeOf(exampleFieldMessageValue{})},
	{fieldID: 28, fieldName: "UnionValue", variantType: reflect.TypeOf(exampleFieldUnionValue{})},
	{fieldID: 101, fieldName: "BoolList", variantType: reflect.TypeOf(exampleFieldBoolList{})},
	{fieldID: 102, fieldName: "Int8List", variantType: reflect.TypeOf(exampleFieldInt8List{})},
	{fieldID: 103, fieldName: "Int16List", variantType: reflect.TypeOf(exampleFieldInt16List{})},
	{fieldID: 104, fieldName: "FixedInt32List", variantType: reflect.TypeOf(exampleFieldFixedInt32List{})},
	{fieldID: 105, fieldName: "Varint32List", variantType: reflect.TypeOf(exampleFieldVarint32List{})},
	{fieldID: 106, fieldName: "FixedInt64List", variantType: reflect.TypeOf(exampleFieldFixedInt64List{})},
	{fieldID: 107, fieldName: "Varint64List", variantType: reflect.TypeOf(exampleFieldVarint64List{})},
	{fieldID: 108, fieldName: "TaggedInt64List", variantType: reflect.TypeOf(exampleFieldTaggedInt64List{})},
	{fieldID: 109, fieldName: "Uint8List", variantType: reflect.TypeOf(exampleFieldUint8List{})},
	{fieldID: 110, fieldName: "Uint16List", variantType: reflect.TypeOf(exampleFieldUint16List{})},
	{fieldID: 111, fieldName: "FixedUint32List", variantType: reflect.TypeOf(exampleFieldFixedUint32List{})},
	{fieldID: 112, fieldName: "VarUint32List", variantType: reflect.TypeOf(exampleFieldVarUint32List{})},
	{fieldID: 113, fieldName: "FixedUint64List", variantType: reflect.TypeOf(exampleFieldFixedUint64List{})},
	{fieldID: 114, fieldName: "VarUint64List", variantType: reflect.TypeOf(exampleFieldVarUint64List{})},
	{fieldID: 115, fieldName: "TaggedUint64List", variantType: reflect.TypeOf(exampleFieldTaggedUint64List{})},
	{fieldID: 116, fieldName: "Float16List", variantType: reflect.TypeOf(exampleFieldFloat16List{})},
	{fieldID: 117, fieldName: "Bfloat16List", variantType: reflect.TypeOf(exampleFieldBfloat16List{})},
	{fieldID: 118, fieldName: "MaybeFloat16List", variantType: reflect.TypeOf(exampleFieldMaybeFloat16List{})},
	{fieldID: 119, fieldName: "MaybeBfloat16List", variantType: reflect.TypeOf(exampleFieldMaybeBfloat16List{})},
	{fieldID: 120, fieldName: "Float32List", variantType: reflect.TypeOf(exampleFieldFloat32List{})},
	{fieldID: 121, fieldName: "Float64List", variantType: reflect.TypeOf(exampleFieldFloat64List{})},
	{fieldID: 122, fieldName: "StringList", variantType: reflect.TypeOf(exampleFieldStringList{})},
	{fieldID: 123, fieldName: "BytesList", variantType: reflect.TypeOf(exampleFieldBytesList{})},
	{fieldID: 124, fieldName: "DateList", variantType: reflect.TypeOf(exampleFieldDateList{})},
	{fieldID: 125, fieldName: "TimestampList", variantType: reflect.TypeOf(exampleFieldTimestampList{})},
	{fieldID: 126, fieldName: "DurationList", variantType: reflect.TypeOf(exampleFieldDurationList{})},
	{fieldID: 127, fieldName: "DecimalList", variantType: reflect.TypeOf(exampleFieldDecimalList{})},
	{fieldID: 128, fieldName: "EnumList", variantType: reflect.TypeOf(exampleFieldEnumList{})},
	{fieldID: 129, fieldName: "MessageList", variantType: reflect.TypeOf(exampleFieldMessageList{})},
	{fieldID: 130, fieldName: "UnionList", variantType: reflect.TypeOf(exampleFieldUnionList{})},
	{fieldID: 201, fieldName: "StringValuesByBool", variantType: reflect.TypeOf(exampleFieldStringValuesByBool{})},
	{fieldID: 202, fieldName: "StringValuesByInt8", variantType: reflect.TypeOf(exampleFieldStringValuesByInt8{})},
	{fieldID: 203, fieldName: "StringValuesByInt16", variantType: reflect.TypeOf(exampleFieldStringValuesByInt16{})},
	{fieldID: 204, fieldName: "StringValuesByFixedInt32", variantType: reflect.TypeOf(exampleFieldStringValuesByFixedInt32{})},
	{fieldID: 205, fieldName: "StringValuesByVarint32", variantType: reflect.TypeOf(exampleFieldStringValuesByVarint32{})},
	{fieldID: 206, fieldName: "StringValuesByFixedInt64", variantType: reflect.TypeOf(exampleFieldStringValuesByFixedInt64{})},
	{fieldID: 207, fieldName: "StringValuesByVarint64", variantType: reflect.TypeOf(exampleFieldStringValuesByVarint64{})},
	{fieldID: 208, fieldName: "StringValuesByTaggedInt64", variantType: reflect.TypeOf(exampleFieldStringValuesByTaggedInt64{})},
	{fieldID: 209, fieldName: "StringValuesByUint8", variantType: reflect.TypeOf(exampleFieldStringValuesByUint8{})},
	{fieldID: 210, fieldName: "StringValuesByUint16", variantType: reflect.TypeOf(exampleFieldStringValuesByUint16{})},
	{fieldID: 211, fieldName: "StringValuesByFixedUint32", variantType: reflect.TypeOf(exampleFieldStringValuesByFixedUint32{})},
	{fieldID: 212, fieldName: "StringValuesByVarUint32", variantType: reflect.TypeOf(exampleFieldStringValuesByVarUint32{})},
	{fieldID: 213, fieldName: "StringValuesByFixedUint64", variantType: reflect.TypeOf(exampleFieldStringValuesByFixedUint64{})},
	{fieldID: 214, fieldName: "StringValuesByVarUint64", variantType: reflect.TypeOf(exampleFieldStringValuesByVarUint64{})},
	{fieldID: 215, fieldName: "StringValuesByTaggedUint64", variantType: reflect.TypeOf(exampleFieldStringValuesByTaggedUint64{})},
	{fieldID: 218, fieldName: "StringValuesByString", variantType: reflect.TypeOf(exampleFieldStringValuesByString{})},
	{fieldID: 219, fieldName: "StringValuesByTimestamp", variantType: reflect.TypeOf(exampleFieldStringValuesByTimestamp{})},
	{fieldID: 220, fieldName: "StringValuesByDuration", variantType: reflect.TypeOf(exampleFieldStringValuesByDuration{})},
	{fieldID: 221, fieldName: "StringValuesByEnum", variantType: reflect.TypeOf(exampleFieldStringValuesByEnum{})},
	{fieldID: 222, fieldName: "Float16ValuesByName", variantType: reflect.TypeOf(exampleFieldFloat16ValuesByName{})},
	{fieldID: 223, fieldName: "MaybeFloat16ValuesByName", variantType: reflect.TypeOf(exampleFieldMaybeFloat16ValuesByName{})},
	{fieldID: 224, fieldName: "Bfloat16ValuesByName", variantType: reflect.TypeOf(exampleFieldBfloat16ValuesByName{})},
	{fieldID: 225, fieldName: "MaybeBfloat16ValuesByName", variantType: reflect.TypeOf(exampleFieldMaybeBfloat16ValuesByName{})},
	{fieldID: 226, fieldName: "BytesValuesByName", variantType: reflect.TypeOf(exampleFieldBytesValuesByName{})},
	{fieldID: 227, fieldName: "DateValuesByName", variantType: reflect.TypeOf(exampleFieldDateValuesByName{})},
	{fieldID: 228, fieldName: "DecimalValuesByName", variantType: reflect.TypeOf(exampleFieldDecimalValuesByName{})},
	{fieldID: 229, fieldName: "MessageValuesByName", variantType: reflect.TypeOf(exampleFieldMessageValuesByName{})},
	{fieldID: 230, fieldName: "UnionValuesByName", variantType: reflect.TypeOf(exampleFieldUnionValuesByName{})},
}
