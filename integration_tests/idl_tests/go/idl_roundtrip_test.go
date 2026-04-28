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
	"math/big"
	"os"
	"reflect"
	"testing"
	"time"

	fory "github.com/apache/fory/go/fory"
	bfloat16 "github.com/apache/fory/go/fory/bfloat16"
	float16 "github.com/apache/fory/go/fory/float16"
	"github.com/apache/fory/go/fory/optional"
	addressbook "github.com/apache/fory/integration_tests/idl_tests/go/addressbook/generated"
	anyexample "github.com/apache/fory/integration_tests/idl_tests/go/any_example/generated"
	autoid "github.com/apache/fory/integration_tests/idl_tests/go/auto_id/generated"
	collection "github.com/apache/fory/integration_tests/idl_tests/go/collection/generated"
	complexfbs "github.com/apache/fory/integration_tests/idl_tests/go/complex_fbs/generated"
	complexpb "github.com/apache/fory/integration_tests/idl_tests/go/complex_pb/generated"
	evolving1 "github.com/apache/fory/integration_tests/idl_tests/go/evolving1/generated"
	evolving2 "github.com/apache/fory/integration_tests/idl_tests/go/evolving2/generated"
	examplecommon "github.com/apache/fory/integration_tests/idl_tests/go/example_common/generated"
	graphpkg "github.com/apache/fory/integration_tests/idl_tests/go/graph/generated"
	monster "github.com/apache/fory/integration_tests/idl_tests/go/monster/generated"
	optionaltypes "github.com/apache/fory/integration_tests/idl_tests/go/optional_types/generated"
	treepkg "github.com/apache/fory/integration_tests/idl_tests/go/tree/generated"
)

type exampleMessageUnionCase uint32

const (
	exampleMessageUnionCaseInvalid           exampleMessageUnionCase = 0
	exampleMessageUnionCaseUnionValue        exampleMessageUnionCase = 28
	exampleMessageUnionCaseMaybeFloat16List  exampleMessageUnionCase = 118
	exampleMessageUnionCaseMaybeBfloat16List exampleMessageUnionCase = 119
)

type exampleMessageUnion struct {
	case_ exampleMessageUnionCase
	value any
}

func maybeFloat16ListExampleMessageUnion(v []float16.Float16) exampleMessageUnion {
	return exampleMessageUnion{case_: exampleMessageUnionCaseMaybeFloat16List, value: v}
}

func maybeBfloat16ListExampleMessageUnion(v []bfloat16.BFloat16) exampleMessageUnion {
	return exampleMessageUnion{case_: exampleMessageUnionCaseMaybeBfloat16List, value: v}
}

func unionValueExampleMessageUnion(v *examplecommon.ExampleLeafUnion) exampleMessageUnion {
	if v == nil {
		panic("unionValueExampleMessageUnion: nil pointer")
	}
	return exampleMessageUnion{case_: exampleMessageUnionCaseUnionValue, value: v}
}

func (u exampleMessageUnion) ForyUnionMarker() {}

func (u exampleMessageUnion) ForyUnionGet() (uint32, any) {
	return uint32(u.case_), u.value
}

func (u *exampleMessageUnion) ForyUnionSet(caseID uint32, value any) {
	u.case_ = exampleMessageUnionCase(caseID)
	u.value = value
}

type exampleMessage struct {
	BoolValue                  bool                                      `fory:"id=1"`
	Int8Value                  int8                                      `fory:"id=2"`
	Int16Value                 int16                                     `fory:"id=3"`
	FixedInt32Value            int32                                     `fory:"id=4,compress=false"`
	Varint32Value              int32                                     `fory:"id=5,compress=true"`
	FixedInt64Value            int64                                     `fory:"id=6,encoding=fixed"`
	Varint64Value              int64                                     `fory:"id=7,encoding=varint"`
	TaggedInt64Value           int64                                     `fory:"id=8,encoding=tagged"`
	Uint8Value                 uint8                                     `fory:"id=9"`
	Uint16Value                uint16                                    `fory:"id=10"`
	FixedUint32Value           uint32                                    `fory:"id=11,compress=false"`
	VarUint32Value             uint32                                    `fory:"id=12,compress=true"`
	FixedUint64Value           uint64                                    `fory:"id=13,encoding=fixed"`
	VarUint64Value             uint64                                    `fory:"id=14,encoding=varint"`
	TaggedUint64Value          uint64                                    `fory:"id=15,encoding=tagged"`
	Float16Value               float16.Float16                           `fory:"id=16"`
	Bfloat16Value              bfloat16.BFloat16                         `fory:"id=17"`
	Float32Value               float32                                   `fory:"id=18"`
	Float64Value               float64                                   `fory:"id=19"`
	StringValue                string                                    `fory:"id=20"`
	BytesValue                 []byte                                    `fory:"id=21"`
	DateValue                  fory.Date                                 `fory:"id=22"`
	TimestampValue             time.Time                                 `fory:"id=23"`
	DurationValue              time.Duration                             `fory:"id=24"`
	DecimalValue               fory.Decimal                              `fory:"id=25"`
	EnumValue                  examplecommon.ExampleState                `fory:"id=26"`
	MessageValue               *examplecommon.ExampleLeaf                `fory:"id=27,nullable"`
	UnionValue                 examplecommon.ExampleLeafUnion            `fory:"id=28"`
	BoolList                   []bool                                    `fory:"id=101"`
	Int8List                   []int8                                    `fory:"id=102,type=int8_array"`
	Int16List                  []int16                                   `fory:"id=103"`
	FixedInt32List             []int32                                   `fory:"id=104"`
	Varint32List               []int32                                   `fory:"id=105"`
	FixedInt64List             []int64                                   `fory:"id=106"`
	Varint64List               []int64                                   `fory:"id=107"`
	TaggedInt64List            []int64                                   `fory:"id=108"`
	Uint8List                  []uint8                                   `fory:"id=109,type=uint8_array"`
	Uint16List                 []uint16                                  `fory:"id=110"`
	FixedUint32List            []uint32                                  `fory:"id=111"`
	VarUint32List              []uint32                                  `fory:"id=112"`
	FixedUint64List            []uint64                                  `fory:"id=113"`
	VarUint64List              []uint64                                  `fory:"id=114"`
	TaggedUint64List           []uint64                                  `fory:"id=115"`
	Float16List                []float16.Float16                         `fory:"id=116"`
	Bfloat16List               []bfloat16.BFloat16                       `fory:"id=117"`
	MaybeFloat16List           []*float16.Float16                        `fory:"id=118,nullable=false"`
	MaybeBfloat16List          []*bfloat16.BFloat16                      `fory:"id=119,nullable=false"`
	Float32List                []float32                                 `fory:"id=120"`
	Float64List                []float64                                 `fory:"id=121"`
	StringList                 []string                                  `fory:"id=122"`
	BytesList                  [][]byte                                  `fory:"id=123"`
	DateList                   []fory.Date                               `fory:"id=124"`
	TimestampList              []time.Time                               `fory:"id=125"`
	DurationList               []time.Duration                           `fory:"id=126"`
	DecimalList                []fory.Decimal                            `fory:"id=127"`
	EnumList                   []examplecommon.ExampleState              `fory:"id=128"`
	MessageList                []examplecommon.ExampleLeaf               `fory:"id=129,nested_ref=[[]]"`
	UnionList                  []examplecommon.ExampleLeafUnion          `fory:"id=130,nested_ref=[[]]"`
	StringValuesByBool         map[bool]string                           `fory:"id=201"`
	StringValuesByInt8         map[int8]string                           `fory:"id=202"`
	StringValuesByInt16        map[int16]string                          `fory:"id=203"`
	StringValuesByFixedInt32   map[int32]string                          `fory:"id=204"`
	StringValuesByVarint32     map[int32]string                          `fory:"id=205"`
	StringValuesByFixedInt64   map[int64]string                          `fory:"id=206"`
	StringValuesByVarint64     map[int64]string                          `fory:"id=207"`
	StringValuesByTaggedInt64  map[int64]string                          `fory:"id=208"`
	StringValuesByUint8        map[uint8]string                          `fory:"id=209"`
	StringValuesByUint16       map[uint16]string                         `fory:"id=210"`
	StringValuesByFixedUint32  map[uint32]string                         `fory:"id=211"`
	StringValuesByVarUint32    map[uint32]string                         `fory:"id=212"`
	StringValuesByFixedUint64  map[uint64]string                         `fory:"id=213"`
	StringValuesByVarUint64    map[uint64]string                         `fory:"id=214"`
	StringValuesByTaggedUint64 map[uint64]string                         `fory:"id=215"`
	StringValuesByString       map[string]string                         `fory:"id=218"`
	StringValuesByTimestamp    map[time.Time]string                      `fory:"id=219"`
	StringValuesByDuration     map[time.Duration]string                  `fory:"id=220"`
	StringValuesByEnum         map[examplecommon.ExampleState]string     `fory:"id=221"`
	Float16ValuesByName        map[string]float16.Float16                `fory:"id=222"`
	MaybeFloat16ValuesByName   map[string]*float16.Float16               `fory:"id=223"`
	Bfloat16ValuesByName       map[string]bfloat16.BFloat16              `fory:"id=224"`
	MaybeBfloat16ValuesByName  map[string]*bfloat16.BFloat16             `fory:"id=225"`
	BytesValuesByName          map[string][]byte                         `fory:"id=226"`
	DateValuesByName           map[string]fory.Date                      `fory:"id=227"`
	DecimalValuesByName        map[string]fory.Decimal                   `fory:"id=228"`
	MessageValuesByName        map[string]examplecommon.ExampleLeaf      `fory:"id=229,nested_ref=[[],[]]"`
	UnionValuesByName          map[string]examplecommon.ExampleLeafUnion `fory:"id=230,nested_ref=[[],[]]"`
}

func TestExampleRoundTripCompatible(t *testing.T) {
	runExampleRoundTrip(t, true)
}

func TestExampleRoundTripSchemaConsistent(t *testing.T) {
	runExampleRoundTrip(t, false)
}

func runExampleRoundTrip(t *testing.T, compatible bool) {
	f := fory.NewFory(
		fory.WithXlang(true),
		fory.WithRefTracking(false),
		fory.WithCompatible(compatible),
	)
	if err := registerExampleTypes(f); err != nil {
		t.Fatalf("register example types: %v", err)
	}

	message := buildExampleMessage()
	runLocalExampleMessageRoundTrip(t, f, message)
	runFileExampleMessageRoundTrip(t, f, compatible, message)

	runLocalExampleUnionRoundTrip(t, f, buildExampleBfloat16Union())
	runFileExampleUnionRoundTrip(t, f, buildExampleMessageUnion())
}

func registerExampleTypes(f *fory.Fory) error {
	if err := examplecommon.RegisterTypes(f); err != nil {
		return err
	}
	if err := f.RegisterUnion(exampleMessageUnion{}, 1501, fory.NewUnionSerializer(
		exampleMessageUnionCaseSpec(28, (*examplecommon.ExampleLeafUnion)(nil), fory.UNION),
		exampleMessageUnionCaseSpec(118, []float16.Float16(nil), fory.LIST),
		exampleMessageUnionCaseSpec(119, []bfloat16.BFloat16(nil), fory.LIST),
	)); err != nil {
		return err
	}
	if err := f.RegisterStruct(exampleMessage{}, 1500); err != nil {
		return err
	}
	return nil
}

func exampleMessageUnionCaseSpec(id uint32, sample any, typeID fory.TypeId) fory.UnionCase {
	return fory.UnionCase{ID: id, Type: reflect.TypeOf(sample), TypeID: typeID}
}

func buildExampleMessage() exampleMessage {
	leafA := examplecommon.ExampleLeaf{Label: "leaf-a", Count: 7}
	leafB := examplecommon.ExampleLeaf{Label: "leaf-b", Count: -3}
	leafUnion := examplecommon.LeafExampleLeafUnion(&leafB)
	dateValue := fory.Date{Year: 2024, Month: time.February, Day: 29}
	timestampValue := time.Date(2024, time.February, 29, 12, 34, 56, 789123456, time.UTC)
	durationValue := 3723*time.Second + 456789123*time.Nanosecond
	decimalValue := mustExampleDecimal("1234567890123456789", 4)
	bfloat16Positive := exampleBFloat16(2.25)

	return exampleMessage{
		BoolValue:         true,
		Int8Value:         -12,
		Int16Value:        1234,
		FixedInt32Value:   123456789,
		Varint32Value:     -1234567,
		FixedInt64Value:   1234567890123456789,
		Varint64Value:     -1234567890123456789,
		TaggedInt64Value:  1073741824,
		Uint8Value:        200,
		Uint16Value:       60000,
		FixedUint32Value:  2000000000,
		VarUint32Value:    2100000000,
		FixedUint64Value:  9000000000,
		VarUint64Value:    12000000000,
		TaggedUint64Value: 2222222222,
		Float16Value:      exampleFloat16(1.5),
		Bfloat16Value:     exampleBFloat16(-2.75),
		Float32Value:      3.25,
		Float64Value:      -4.5,
		StringValue:       "example-string",
		BytesValue:        []byte{1, 2, 3, 4},
		DateValue:         dateValue,
		TimestampValue:    timestampValue,
		DurationValue:     durationValue,
		DecimalValue:      decimalValue,
		EnumValue:         examplecommon.ExampleStateReady,
		MessageValue:      &leafA,
		UnionValue:        leafUnion,
		BoolList:          []bool{true, false},
		Int8List:          []int8{-12, 7},
		Int16List:         []int16{1234, -2345},
		FixedInt32List:    []int32{123456789, -123456789},
		Varint32List:      []int32{-1234567, 7654321},
		FixedInt64List:    []int64{1234567890123456789, -123456789012345678},
		Varint64List:      []int64{-1234567890123456789, 123456789012345678},
		TaggedInt64List:   []int64{1073741824, -1073741824},
		Uint8List:         []uint8{200, 42},
		Uint16List:        []uint16{60000, 12345},
		FixedUint32List:   []uint32{2000000000, 1234567890},
		VarUint32List:     []uint32{2100000000, 1234567890},
		FixedUint64List:   []uint64{9000000000, 4000000000},
		VarUint64List:     []uint64{12000000000, 5000000000},
		TaggedUint64List:  []uint64{2222222222, 3333333333},
		Float16List: []float16.Float16{
			exampleFloat16(1.5),
			exampleFloat16(-0.5),
		},
		Bfloat16List: []bfloat16.BFloat16{
			exampleBFloat16(-2.75),
			exampleBFloat16(2.25),
		},
		MaybeFloat16List: []*float16.Float16{
			exampleFloat16Ptr(1.5),
			nil,
			exampleFloat16Ptr(-0.5),
		},
		MaybeBfloat16List: []*bfloat16.BFloat16{
			nil,
			exampleBFloat16Ptr(2.25),
			exampleBFloat16Ptr(-1.0),
		},
		Float32List: []float32{3.25, -0.5},
		Float64List: []float64{-4.5, 6.75},
		StringList:  []string{"example-string", "secondary"},
		BytesList:   [][]byte{{1, 2, 3, 4}, {5, 6}},
		DateList: []fory.Date{
			dateValue,
			{Year: 2024, Month: time.March, Day: 1},
		},
		TimestampList: []time.Time{
			timestampValue,
			time.Date(2024, time.March, 1, 0, 0, 0, 123456000, time.UTC),
		},
		DurationList: []time.Duration{
			durationValue,
			time.Second + 234567*time.Microsecond,
		},
		DecimalList: []fory.Decimal{
			decimalValue,
			mustExampleDecimal("-5", 1),
		},
		EnumList: []examplecommon.ExampleState{
			examplecommon.ExampleStateReady,
			examplecommon.ExampleStateFailed,
		},
		MessageList: []examplecommon.ExampleLeaf{
			leafA,
			leafB,
		},
		UnionList: []examplecommon.ExampleLeafUnion{
			examplecommon.LeafExampleLeafUnion(&leafA),
			leafUnion,
		},
		StringValuesByBool:         map[bool]string{true: "true-value", false: "false-value"},
		StringValuesByInt8:         map[int8]string{-12: "minus-twelve"},
		StringValuesByInt16:        map[int16]string{1234: "twelve-thirty-four"},
		StringValuesByFixedInt32:   map[int32]string{123456789: "fixed-int32"},
		StringValuesByVarint32:     map[int32]string{-1234567: "varint32"},
		StringValuesByFixedInt64:   map[int64]string{1234567890123456789: "fixed-int64"},
		StringValuesByVarint64:     map[int64]string{-1234567890123456789: "varint64"},
		StringValuesByTaggedInt64:  map[int64]string{1073741824: "tagged-int64"},
		StringValuesByUint8:        map[uint8]string{200: "uint8"},
		StringValuesByUint16:       map[uint16]string{60000: "uint16"},
		StringValuesByFixedUint32:  map[uint32]string{2000000000: "fixed-uint32"},
		StringValuesByVarUint32:    map[uint32]string{2100000000: "var-uint32"},
		StringValuesByFixedUint64:  map[uint64]string{9000000000: "fixed-uint64"},
		StringValuesByVarUint64:    map[uint64]string{12000000000: "var-uint64"},
		StringValuesByTaggedUint64: map[uint64]string{2222222222: "tagged-uint64"},
		StringValuesByString:       map[string]string{"example-string": "string"},
		StringValuesByTimestamp:    map[time.Time]string{timestampValue: "timestamp"},
		StringValuesByDuration:     map[time.Duration]string{durationValue: "duration"},
		StringValuesByEnum:         map[examplecommon.ExampleState]string{examplecommon.ExampleStateReady: "ready"},
		Float16ValuesByName:        map[string]float16.Float16{"primary": exampleFloat16(1.5)},
		MaybeFloat16ValuesByName: map[string]*float16.Float16{
			"primary": exampleFloat16Ptr(1.5),
			"missing": nil,
		},
		Bfloat16ValuesByName: map[string]bfloat16.BFloat16{
			"primary": exampleBFloat16(-2.75),
		},
		MaybeBfloat16ValuesByName: map[string]*bfloat16.BFloat16{
			"missing":   nil,
			"secondary": &bfloat16Positive,
		},
		BytesValuesByName:   map[string][]byte{"payload": {1, 2, 3, 4}},
		DateValuesByName:    map[string]fory.Date{"leap-day": dateValue},
		DecimalValuesByName: map[string]fory.Decimal{"amount": decimalValue},
		MessageValuesByName: map[string]examplecommon.ExampleLeaf{
			"leaf-a": leafA,
			"leaf-b": leafB,
		},
		UnionValuesByName: map[string]examplecommon.ExampleLeafUnion{
			"leaf-b": leafUnion,
		},
	}
}

func buildExampleMessageUnion() exampleMessageUnion {
	return buildExampleUnionValue()
}

func buildExampleUnionValue() exampleMessageUnion {
	leafB := examplecommon.ExampleLeaf{Label: "leaf-b", Count: -3}
	leafUnion := examplecommon.LeafExampleLeafUnion(&leafB)
	return unionValueExampleMessageUnion(&leafUnion)
}

func buildExampleBfloat16Union() exampleMessageUnion {
	return maybeBfloat16ListExampleMessageUnion([]bfloat16.BFloat16{
		exampleBFloat16(2.25),
		exampleBFloat16(-1.0),
	})
}

func exampleFloat16(value float32) float16.Float16 {
	return float16.Float16FromFloat32(value)
}

func exampleFloat16Ptr(value float32) *float16.Float16 {
	f16 := exampleFloat16(value)
	return &f16
}

func exampleBFloat16(value float32) bfloat16.BFloat16 {
	return bfloat16.BFloat16FromFloat32(value)
}

func exampleBFloat16Ptr(value float32) *bfloat16.BFloat16 {
	bf16 := exampleBFloat16(value)
	return &bf16
}

func mustExampleDecimal(unscaled string, scale int32) fory.Decimal {
	value, ok := new(big.Int).SetString(unscaled, 10)
	if !ok {
		panic("invalid example decimal")
	}
	return fory.NewDecimal(value, scale)
}

func runLocalExampleMessageRoundTrip(t *testing.T, f *fory.Fory, message exampleMessage) {
	data, err := f.Serialize(&message)
	if err != nil {
		t.Fatalf("serialize example message: %v", err)
	}

	var out exampleMessage
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize example message: %v", err)
	}

	assertExampleMessageEqual(t, message, out)
}

func runFileExampleMessageRoundTrip(t *testing.T, f *fory.Fory, compatible bool, message exampleMessage) {
	dataFile := os.Getenv("DATA_FILE_EXAMPLE_MESSAGE")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read example message payload: %v", err)
	}

	var decoded exampleMessage
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize example message payload: %v", err)
	}
	assertExampleMessageEqual(t, message, decoded)
	if compatible {
		assertExampleMessageSchemaEvolution(t, payload, message)
	}

	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize example message payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write example message payload: %v", err)
	}
}

func assertExampleMessageSchemaEvolution(t *testing.T, payload []byte, message exampleMessage) {
	t.Helper()

	emptyFory := newExampleSchemaEvolutionFory(t, reflect.TypeOf(exampleMessageEmpty{}))
	var empty exampleMessageEmpty
	if err := emptyFory.Deserialize(payload, &empty); err != nil {
		t.Fatalf("deserialize example message payload as empty schema: %v", err)
	}

	normalized := canonicalizeExampleMessage(message)
	value := reflect.ValueOf(normalized)
	for _, spec := range exampleVariantSpecs {
		variantFory := newExampleSchemaEvolutionFory(t, spec.variantType)
		variantValuePtr := reflect.New(spec.variantType)
		if err := variantFory.Deserialize(payload, variantValuePtr.Interface()); err != nil {
			t.Fatalf("deserialize example message payload as %s: %v", spec.fieldName, err)
		}
		expected := value.FieldByName(spec.fieldName).Interface()
		actual := variantValuePtr.Elem().FieldByName(spec.fieldName).Interface()
		assertExampleSchemaEvolutionFieldEqual(t, spec.fieldName, expected, actual)
	}
}

func newExampleSchemaEvolutionFory(t *testing.T, variantType reflect.Type) *fory.Fory {
	t.Helper()

	f := fory.NewFory(
		fory.WithXlang(true),
		fory.WithRefTracking(false),
		fory.WithCompatible(true),
	)
	if err := examplecommon.RegisterTypes(f); err != nil {
		t.Fatalf("register example common types for schema evolution: %v", err)
	}
	if err := f.RegisterStruct(variantType, exampleMessageTypeID); err != nil {
		t.Fatalf("register schema evolution type %s: %v", variantType, err)
	}
	return f
}

func assertExampleSchemaEvolutionFieldEqual(t *testing.T, fieldName string, expected, actual any) {
	t.Helper()

	switch fieldName {
	case "TimestampValue":
		expectedValue, ok := expected.(time.Time)
		if !ok {
			t.Fatalf("expected timestamp field %s to be time.Time, got %T", fieldName, expected)
		}
		actualValue, ok := actual.(time.Time)
		if !ok {
			t.Fatalf("actual timestamp field %s to be time.Time, got %T", fieldName, actual)
		}
		if !canonicalizeExampleTime(expectedValue).Equal(canonicalizeExampleTime(actualValue)) {
			t.Fatalf("%s mismatch: %#v != %#v", fieldName, actualValue, expectedValue)
		}
		return
	case "TimestampList":
		expectedValue, ok := expected.([]time.Time)
		if !ok {
			t.Fatalf("expected timestamp list field %s to be []time.Time, got %T", fieldName, expected)
		}
		actualValue, ok := actual.([]time.Time)
		if !ok {
			t.Fatalf("actual timestamp list field %s to be []time.Time, got %T", fieldName, actual)
		}
		if !reflect.DeepEqual(canonicalizeExampleTimes(expectedValue), canonicalizeExampleTimes(actualValue)) {
			t.Fatalf("%s mismatch: %#v != %#v", fieldName, actualValue, expectedValue)
		}
		return
	case "StringValuesByTimestamp":
		expectedValue, ok := expected.(map[time.Time]string)
		if !ok {
			t.Fatalf("expected timestamp map field %s to be map[time.Time]string, got %T", fieldName, expected)
		}
		actualValue, ok := actual.(map[time.Time]string)
		if !ok {
			t.Fatalf("actual timestamp map field %s to be map[time.Time]string, got %T", fieldName, actual)
		}
		if !reflect.DeepEqual(
			canonicalizeExampleTimeKeyMap(expectedValue),
			canonicalizeExampleTimeKeyMap(actualValue),
		) {
			t.Fatalf("%s mismatch: %#v != %#v", fieldName, actualValue, expectedValue)
		}
		return
	case "MaybeFloat16ValuesByName":
		expectedValue, ok := expected.(map[string]*float16.Float16)
		if !ok {
			t.Fatalf("expected nullable float16 map field %s to be map[string]*float16.Float16, got %T", fieldName, expected)
		}
		actualValue, ok := actual.(map[string]*float16.Float16)
		if !ok {
			t.Fatalf("actual nullable float16 map field %s to be map[string]*float16.Float16, got %T", fieldName, actual)
		}
		if !reflect.DeepEqual(
			canonicalizeMaybeFloat16Map(expectedValue),
			canonicalizeMaybeFloat16Map(actualValue),
		) {
			t.Fatalf("%s mismatch: %#v != %#v", fieldName, actualValue, expectedValue)
		}
		return
	case "MaybeBfloat16ValuesByName":
		expectedValue, ok := expected.(map[string]*bfloat16.BFloat16)
		if !ok {
			t.Fatalf("expected nullable bfloat16 map field %s to be map[string]*bfloat16.BFloat16, got %T", fieldName, expected)
		}
		actualValue, ok := actual.(map[string]*bfloat16.BFloat16)
		if !ok {
			t.Fatalf("actual nullable bfloat16 map field %s to be map[string]*bfloat16.BFloat16, got %T", fieldName, actual)
		}
		if !reflect.DeepEqual(
			canonicalizeMaybeBFloat16Map(expectedValue),
			canonicalizeMaybeBFloat16Map(actualValue),
		) {
			t.Fatalf("%s mismatch: %#v != %#v", fieldName, actualValue, expectedValue)
		}
		return
	}

	if !reflect.DeepEqual(expected, actual) {
		t.Fatalf("%s mismatch: %#v != %#v", fieldName, actual, expected)
	}
}

func runLocalExampleUnionRoundTrip(t *testing.T, f *fory.Fory, unionValue exampleMessageUnion) {
	data, err := f.Serialize(&unionValue)
	if err != nil {
		t.Fatalf("serialize example union: %v", err)
	}

	var out exampleMessageUnion
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize example union: %v", err)
	}

	assertExampleUnionEqual(t, unionValue, out)
}

func runFileExampleUnionRoundTrip(t *testing.T, f *fory.Fory, unionValue exampleMessageUnion) {
	dataFile := os.Getenv("DATA_FILE_EXAMPLE_UNION")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read example union payload: %v", err)
	}

	var decoded exampleMessageUnion
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize example union payload: %v", err)
	}
	assertExampleUnionEqual(t, unionValue, decoded)

	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize example union payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write example union payload: %v", err)
	}
}

func assertExampleMessageEqual(t *testing.T, expected, actual exampleMessage) {
	t.Helper()
	normalizedExpected := canonicalizeExampleMessage(expected)
	normalizedActual := canonicalizeExampleMessage(actual)
	if !reflect.DeepEqual(normalizedExpected, normalizedActual) {
		t.Fatalf("example message mismatch: %#v != %#v", normalizedActual, normalizedExpected)
	}
}

func assertExampleUnionEqual(t *testing.T, expected, actual exampleMessageUnion) {
	t.Helper()
	if !reflect.DeepEqual(expected, actual) {
		t.Fatalf("example union mismatch: %#v != %#v", expected, actual)
	}
}

func canonicalizeExampleMessage(message exampleMessage) exampleMessage {
	out := message
	out.TimestampValue = canonicalizeExampleTime(message.TimestampValue)
	out.TimestampList = canonicalizeExampleTimes(message.TimestampList)
	out.StringValuesByTimestamp = canonicalizeExampleTimeKeyMap(message.StringValuesByTimestamp)
	out.MaybeFloat16ValuesByName = canonicalizeMaybeFloat16Map(message.MaybeFloat16ValuesByName)
	out.MaybeBfloat16ValuesByName = canonicalizeMaybeBFloat16Map(message.MaybeBfloat16ValuesByName)
	return out
}

func canonicalizeExampleTime(value time.Time) time.Time {
	if value.IsZero() {
		return value
	}
	return time.Unix(0, value.UnixNano()).UTC()
}

func canonicalizeExampleTimes(values []time.Time) []time.Time {
	if values == nil {
		return nil
	}
	out := make([]time.Time, len(values))
	for i, value := range values {
		out[i] = canonicalizeExampleTime(value)
	}
	return out
}

func canonicalizeExampleTimeKeyMap(values map[time.Time]string) map[time.Time]string {
	if values == nil {
		return nil
	}
	out := make(map[time.Time]string, len(values))
	for key, value := range values {
		out[canonicalizeExampleTime(key)] = value
	}
	return out
}

func canonicalizeMaybeFloat16Map(values map[string]*float16.Float16) map[string]*float16.Float16 {
	if values == nil {
		return nil
	}
	out := make(map[string]*float16.Float16, len(values))
	for key, value := range values {
		out[key] = canonicalizeMaybeFloat16Value(value)
	}
	return out
}

func canonicalizeMaybeFloat16Value(value *float16.Float16) *float16.Float16 {
	if value == nil {
		zero := float16.Zero
		return &zero
	}
	copied := *value
	return &copied
}

func canonicalizeMaybeBFloat16Map(values map[string]*bfloat16.BFloat16) map[string]*bfloat16.BFloat16 {
	if values == nil {
		return nil
	}
	out := make(map[string]*bfloat16.BFloat16, len(values))
	for key, value := range values {
		out[key] = canonicalizeMaybeBFloat16Value(value)
	}
	return out
}

func canonicalizeMaybeBFloat16Value(value *bfloat16.BFloat16) *bfloat16.BFloat16 {
	if value == nil {
		zero := bfloat16.BFloat16FromBits(0)
		return &zero
	}
	copied := *value
	return &copied
}

func buildAddressBook() addressbook.AddressBook {
	mobile := addressbook.Person_PhoneNumber{
		Number:    "555-0100",
		PhoneType: addressbook.Person_PhoneTypeMobile,
	}
	work := addressbook.Person_PhoneNumber{
		Number:    "555-0111",
		PhoneType: addressbook.Person_PhoneTypeWork,
	}

	pet := addressbook.DogAnimal(&addressbook.Dog{
		Name:       "Rex",
		BarkVolume: 5,
	})
	pet = addressbook.CatAnimal(&addressbook.Cat{
		Name:  "Mimi",
		Lives: 9,
	})

	person := addressbook.Person{
		Name:   "Alice",
		Id:     123,
		Email:  "alice@example.com",
		Tags:   []string{"friend", "colleague"},
		Scores: map[string]int32{"math": 100, "science": 98},
		Salary: 120000.5,
		Phones: []addressbook.Person_PhoneNumber{mobile, work},
		Pet:    pet,
	}

	return addressbook.AddressBook{
		People:       []addressbook.Person{person},
		PeopleByName: map[string]addressbook.Person{person.Name: person},
	}
}

func buildAutoIdEnvelope() autoid.Envelope {
	payload := autoid.Envelope_Payload{Value: 42}
	detail := autoid.PayloadEnvelope_Detail(&payload)
	return autoid.Envelope{
		Id:      "env-1",
		Payload: &payload,
		Detail:  detail,
		Status:  autoid.StatusOk,
	}
}

func buildAutoIdWrapper(envelope autoid.Envelope) autoid.Wrapper {
	return autoid.EnvelopeWrapper(&envelope)
}

func TestAddressBookRoundTripCompatible(t *testing.T) {
	runAddressBookRoundTrip(t, true)
}

func TestAddressBookRoundTripSchemaConsistent(t *testing.T) {
	runAddressBookRoundTrip(t, false)
}

func TestAutoIdRoundTripCompatible(t *testing.T) {
	runAutoIdRoundTrip(t, true)
}

func TestAutoIdRoundTripSchemaConsistent(t *testing.T) {
	runAutoIdRoundTrip(t, false)
}

func TestEvolvingRoundTrip(t *testing.T) {
	foryV1 := fory.NewFory(
		fory.WithXlang(true),
		fory.WithRefTracking(false),
		fory.WithCompatible(true),
	)
	if err := evolving1.RegisterTypes(foryV1); err != nil {
		t.Fatalf("register evolving1 types: %v", err)
	}
	foryV2 := fory.NewFory(
		fory.WithXlang(true),
		fory.WithRefTracking(false),
		fory.WithCompatible(true),
	)
	if err := evolving2.RegisterTypes(foryV2); err != nil {
		t.Fatalf("register evolving2 types: %v", err)
	}

	msgV1 := evolving1.EvolvingMessage{
		Id:   1,
		Name: "Alice",
		City: "NYC",
	}
	data, err := foryV1.Serialize(&msgV1)
	if err != nil {
		t.Fatalf("serialize evolving message v1: %v", err)
	}
	var msgV2 evolving2.EvolvingMessage
	if err := foryV2.Deserialize(data, &msgV2); err != nil {
		t.Fatalf("deserialize evolving message v2: %v", err)
	}
	if msgV2.Id != msgV1.Id || msgV2.Name != msgV1.Name || msgV2.City != msgV1.City {
		t.Fatalf("evolving message mismatch: v1=%+v v2=%+v", msgV1, msgV2)
	}
	msgV2.Email = optional.Some("alice@example.com")
	roundBytes, err := foryV2.Serialize(&msgV2)
	if err != nil {
		t.Fatalf("serialize evolving message v2: %v", err)
	}
	var msgV1Round evolving1.EvolvingMessage
	if err := foryV1.Deserialize(roundBytes, &msgV1Round); err != nil {
		t.Fatalf("deserialize evolving message v1: %v", err)
	}
	if !reflect.DeepEqual(msgV1Round, msgV1) {
		t.Fatalf("evolving round trip mismatch: %v vs %v", msgV1Round, msgV1)
	}

	fixedV1 := evolving1.FixedMessage{
		Id:    10,
		Name:  "Bob",
		Score: 90,
		Note:  "note",
	}
	fixedData, err := foryV1.Serialize(&fixedV1)
	if err != nil {
		t.Fatalf("serialize fixed message v1: %v", err)
	}
	var fixedV2 evolving2.FixedMessage
	if err := foryV2.Deserialize(fixedData, &fixedV2); err != nil {
		return
	}
	fixedRound, err := foryV2.Serialize(&fixedV2)
	if err != nil {
		return
	}
	var fixedV1Round evolving1.FixedMessage
	if err := foryV1.Deserialize(fixedRound, &fixedV1Round); err != nil {
		return
	}
	if reflect.DeepEqual(fixedV1Round, fixedV1) {
		t.Fatalf("fixed message unexpectedly compatible: %v", fixedV1Round)
	}

	evolvingSizeV1 := evolving1.EvolvingSizeMessage{Payload: "payload"}
	fixedSizeV1 := evolving1.FixedSizeMessage{Payload: "payload"}
	evolvingSizeBytes, err := foryV1.Serialize(&evolvingSizeV1)
	if err != nil {
		t.Fatalf("serialize evolving size v1: %v", err)
	}
	fixedSizeBytes, err := foryV1.Serialize(&fixedSizeV1)
	if err != nil {
		t.Fatalf("serialize fixed size v1: %v", err)
	}
	if len(fixedSizeBytes) >= len(evolvingSizeBytes) {
		t.Fatalf("fixed size payload was not smaller: fixed=%d evolving=%d", len(fixedSizeBytes), len(evolvingSizeBytes))
	}

	var evolvingSizeV2 evolving2.EvolvingSizeMessage
	if err := foryV2.Deserialize(evolvingSizeBytes, &evolvingSizeV2); err != nil {
		t.Fatalf("deserialize evolving size v2: %v", err)
	}
	if evolvingSizeV2.Payload != evolvingSizeV1.Payload {
		t.Fatalf("evolving size payload mismatch: v1=%+v v2=%+v", evolvingSizeV1, evolvingSizeV2)
	}
	evolvingSizeRoundBytes, err := foryV2.Serialize(&evolvingSizeV2)
	if err != nil {
		t.Fatalf("serialize evolving size v2: %v", err)
	}
	var evolvingSizeV1Round evolving1.EvolvingSizeMessage
	if err := foryV1.Deserialize(evolvingSizeRoundBytes, &evolvingSizeV1Round); err != nil {
		t.Fatalf("deserialize evolving size v1: %v", err)
	}
	if !reflect.DeepEqual(evolvingSizeV1Round, evolvingSizeV1) {
		t.Fatalf("evolving size roundtrip mismatch: %v vs %v", evolvingSizeV1Round, evolvingSizeV1)
	}

	var fixedSizeV2 evolving2.FixedSizeMessage
	if err := foryV2.Deserialize(fixedSizeBytes, &fixedSizeV2); err != nil {
		t.Fatalf("deserialize fixed size v2: %v", err)
	}
	if fixedSizeV2.Payload != fixedSizeV1.Payload {
		t.Fatalf("fixed size payload mismatch: v1=%+v v2=%+v", fixedSizeV1, fixedSizeV2)
	}
	fixedSizeRoundBytes, err := foryV2.Serialize(&fixedSizeV2)
	if err != nil {
		t.Fatalf("serialize fixed size v2: %v", err)
	}
	var fixedSizeV1Round evolving1.FixedSizeMessage
	if err := foryV1.Deserialize(fixedSizeRoundBytes, &fixedSizeV1Round); err != nil {
		t.Fatalf("deserialize fixed size v1: %v", err)
	}
	if !reflect.DeepEqual(fixedSizeV1Round, fixedSizeV1) {
		t.Fatalf("fixed size roundtrip mismatch: %v vs %v", fixedSizeV1Round, fixedSizeV1)
	}
}

func runAddressBookRoundTrip(t *testing.T, compatible bool) {
	f := fory.NewFory(
		fory.WithXlang(true),
		fory.WithRefTracking(false),
		fory.WithCompatible(compatible),
	)
	if err := addressbook.RegisterTypes(f); err != nil {
		t.Fatalf("register types: %v", err)
	}
	if err := complexpb.RegisterTypes(f); err != nil {
		t.Fatalf("register complex pb types: %v", err)
	}
	if err := monster.RegisterTypes(f); err != nil {
		t.Fatalf("register monster types: %v", err)
	}
	if err := complexfbs.RegisterTypes(f); err != nil {
		t.Fatalf("register flatbuffers types: %v", err)
	}
	if err := collection.RegisterTypes(f); err != nil {
		t.Fatalf("register collection types: %v", err)
	}
	if err := optionaltypes.RegisterTypes(f); err != nil {
		t.Fatalf("register optional types: %v", err)
	}
	if err := anyexample.RegisterTypes(f); err != nil {
		t.Fatalf("register any example types: %v", err)
	}

	book := buildAddressBook()
	runLocalRoundTrip(t, f, book)
	runFileRoundTrip(t, f, book)

	types := buildPrimitiveTypes()
	runLocalPrimitiveRoundTrip(t, f, types)
	runFilePrimitiveRoundTrip(t, f, types)

	collections := buildNumericCollections()
	collectionUnion := buildNumericCollectionUnion()
	collectionsArray := buildNumericCollectionsArray()
	collectionArrayUnion := buildNumericCollectionArrayUnion()
	runLocalCollectionRoundTrip(t, f, collections, collectionUnion, collectionsArray, collectionArrayUnion)
	runFileCollectionRoundTrip(t, f, collections, collectionUnion, collectionsArray, collectionArrayUnion)

	monster := buildMonster()
	runLocalMonsterRoundTrip(t, f, monster)
	runFileMonsterRoundTrip(t, f, monster)

	container := buildContainer()
	runLocalContainerRoundTrip(t, f, container)
	runFileContainerRoundTrip(t, f, container)

	holder := buildOptionalHolder()
	runLocalOptionalRoundTrip(t, f, holder)
	runFileOptionalRoundTrip(t, f, holder)

	anyHolder := buildAnyHolder()
	runLocalAnyRoundTrip(t, f, anyHolder)

	refFory := fory.NewFory(
		fory.WithXlang(true),
		fory.WithRefTracking(true),
		fory.WithCompatible(compatible),
	)
	if err := treepkg.RegisterTypes(refFory); err != nil {
		t.Fatalf("register tree types: %v", err)
	}
	if err := graphpkg.RegisterTypes(refFory); err != nil {
		t.Fatalf("register graph types: %v", err)
	}
	treeRoot := buildTree()
	runLocalTreeRoundTrip(t, refFory, treeRoot)
	runFileTreeRoundTrip(t, refFory, treeRoot)
	graphValue := buildGraph()
	runLocalGraphRoundTrip(t, refFory, graphValue)
	runFileGraphRoundTrip(t, refFory, graphValue)
}

func runAutoIdRoundTrip(t *testing.T, compatible bool) {
	f := fory.NewFory(
		fory.WithXlang(true),
		fory.WithRefTracking(false),
		fory.WithCompatible(compatible),
	)
	if err := autoid.RegisterTypes(f); err != nil {
		t.Fatalf("register auto_id types: %v", err)
	}

	envelope := buildAutoIdEnvelope()
	wrapper := buildAutoIdWrapper(envelope)
	runLocalAutoIdEnvelopeRoundTrip(t, f, envelope)
	runLocalAutoIdWrapperRoundTrip(t, f, wrapper)
	runFileAutoIdRoundTrip(t, f, envelope)
}

func TestToBytesFromBytes(t *testing.T) {
	book := buildAddressBook()
	data, err := book.ToBytes()
	if err != nil {
		t.Fatalf("addressbook to_bytes: %v", err)
	}
	var decodedBook addressbook.AddressBook
	if err := decodedBook.FromBytes(data); err != nil {
		t.Fatalf("addressbook from_bytes: %v", err)
	}
	if !reflect.DeepEqual(book, decodedBook) {
		t.Fatalf("addressbook to_bytes roundtrip mismatch")
	}

	dog := addressbook.Dog{Name: "Rex", BarkVolume: 5}
	animal := addressbook.DogAnimal(&dog)
	animalBytes, err := animal.ToBytes()
	if err != nil {
		t.Fatalf("animal to_bytes: %v", err)
	}
	var decodedAnimal addressbook.Animal
	if err := decodedAnimal.FromBytes(animalBytes); err != nil {
		t.Fatalf("animal from_bytes: %v", err)
	}
	if !reflect.DeepEqual(animal, decodedAnimal) {
		t.Fatalf("animal to_bytes roundtrip mismatch")
	}
}

func buildAnyHolder() anyexample.AnyHolder {
	inner := anyexample.AnyInner{Name: "inner"}
	unionValue := anyexample.TextAnyUnion("union")
	return anyexample.AnyHolder{
		BoolValue:      true,
		StringValue:    "hello",
		DateValue:      fory.Date{Year: 2024, Month: time.January, Day: 2},
		TimestampValue: time.Unix(1704164645, 0).UTC(),
		MessageValue:   &inner,
		UnionValue:     unionValue,
		ListValue:      []string{"alpha", "beta"},
		MapValue:       map[string]string{"k1": "v1", "k2": "v2"},
	}
}

func runLocalAnyRoundTrip(t *testing.T, f *fory.Fory, holder anyexample.AnyHolder) {
	data, err := f.Serialize(&holder)
	if err != nil {
		t.Fatalf("serialize any: %v", err)
	}

	var out anyexample.AnyHolder
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize any: %v", err)
	}

	assertAnyHolderEqual(t, holder, out)
}

func assertAnyHolderEqual(t *testing.T, expected, actual anyexample.AnyHolder) {
	t.Helper()

	if !anyBoolEqual(expected.BoolValue, actual.BoolValue) {
		t.Fatalf("any bool mismatch: %#v != %#v", expected.BoolValue, actual.BoolValue)
	}
	if !anyStringEqual(expected.StringValue, actual.StringValue) {
		t.Fatalf("any string mismatch: %#v != %#v", expected.StringValue, actual.StringValue)
	}
	if !anyDateEqual(expected.DateValue, actual.DateValue) {
		t.Fatalf("any date mismatch: %#v != %#v", expected.DateValue, actual.DateValue)
	}
	if !anyTimeEqual(expected.TimestampValue, actual.TimestampValue) {
		t.Fatalf("any timestamp mismatch: %#v != %#v", expected.TimestampValue, actual.TimestampValue)
	}
	if !anyInnerEqual(expected.MessageValue, actual.MessageValue) {
		t.Fatalf("any message mismatch: %#v != %#v", expected.MessageValue, actual.MessageValue)
	}
	if !reflect.DeepEqual(expected.UnionValue, actual.UnionValue) {
		t.Fatalf("any union mismatch: %#v != %#v", expected.UnionValue, actual.UnionValue)
	}
	if !anyStringSliceEqual(expected.ListValue, actual.ListValue) {
		t.Fatalf("any list mismatch: %#v != %#v", expected.ListValue, actual.ListValue)
	}
	if !anyStringMapEqual(expected.MapValue, actual.MapValue) {
		t.Fatalf("any map mismatch: %#v != %#v", expected.MapValue, actual.MapValue)
	}
}

func anyBoolEqual(expected, actual any) bool {
	expectedValue, ok := expected.(bool)
	if !ok {
		return false
	}
	actualValue, ok := actual.(bool)
	if !ok {
		return false
	}
	return expectedValue == actualValue
}

func anyStringEqual(expected, actual any) bool {
	expectedValue, ok := expected.(string)
	if !ok {
		return false
	}
	actualValue, ok := actual.(string)
	if !ok {
		return false
	}
	return expectedValue == actualValue
}

func anyDateEqual(expected, actual any) bool {
	expectedValue, ok := expected.(fory.Date)
	if !ok {
		return false
	}
	actualValue, ok := actual.(fory.Date)
	if !ok {
		return false
	}
	return expectedValue == actualValue
}

func anyTimeEqual(expected, actual any) bool {
	expectedValue, ok := expected.(time.Time)
	if !ok {
		return false
	}
	actualValue, ok := actual.(time.Time)
	if !ok {
		return false
	}
	return expectedValue.Equal(actualValue)
}

func anyInnerEqual(expected, actual any) bool {
	expectedValue, ok := normalizeAnyInner(expected)
	if !ok {
		return false
	}
	actualValue, ok := normalizeAnyInner(actual)
	if !ok {
		return false
	}
	return expectedValue == actualValue
}

func normalizeAnyInner(value any) (string, bool) {
	switch typed := value.(type) {
	case *anyexample.AnyInner:
		if typed == nil {
			return "", true
		}
		return typed.Name, true
	case anyexample.AnyInner:
		return typed.Name, true
	default:
		return "", false
	}
}

func anyStringSliceEqual(expected, actual any) bool {
	expectedValue, ok := normalizeStringSlice(expected)
	if !ok {
		return false
	}
	actualValue, ok := normalizeStringSlice(actual)
	if !ok {
		return false
	}
	return reflect.DeepEqual(expectedValue, actualValue)
}

func normalizeStringSlice(value any) ([]string, bool) {
	switch typed := value.(type) {
	case []string:
		return typed, true
	case []any:
		out := make([]string, 0, len(typed))
		for _, item := range typed {
			str, ok := item.(string)
			if !ok {
				return nil, false
			}
			out = append(out, str)
		}
		return out, true
	default:
		return nil, false
	}
}

func anyStringMapEqual(expected, actual any) bool {
	expectedValue, ok := normalizeStringMap(expected)
	if !ok {
		return false
	}
	actualValue, ok := normalizeStringMap(actual)
	if !ok {
		return false
	}
	return reflect.DeepEqual(expectedValue, actualValue)
}

func normalizeStringMap(value any) (map[string]string, bool) {
	switch typed := value.(type) {
	case map[string]string:
		return typed, true
	case map[any]any:
		out := make(map[string]string, len(typed))
		for k, v := range typed {
			key, ok := k.(string)
			if !ok {
				return nil, false
			}
			val, ok := v.(string)
			if !ok {
				return nil, false
			}
			out[key] = val
		}
		return out, true
	default:
		return nil, false
	}
}

func runLocalRoundTrip(t *testing.T, f *fory.Fory, book addressbook.AddressBook) {
	data, err := f.Serialize(&book)
	if err != nil {
		t.Fatalf("serialize: %v", err)
	}

	var out addressbook.AddressBook
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if !reflect.DeepEqual(book, out) {
		t.Fatalf("roundtrip mismatch: %#v != %#v", book, out)
	}
}

func runFileRoundTrip(t *testing.T, f *fory.Fory, book addressbook.AddressBook) {
	dataFile := os.Getenv("DATA_FILE")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read data file: %v", err)
	}

	var decoded addressbook.AddressBook
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize peer payload: %v", err)
	}
	if !reflect.DeepEqual(book, decoded) {
		t.Fatalf("peer payload mismatch: %#v != %#v", book, decoded)
	}

	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize peer payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write data file: %v", err)
	}
}

func runLocalAutoIdEnvelopeRoundTrip(t *testing.T, f *fory.Fory, env autoid.Envelope) {
	data, err := f.Serialize(&env)
	if err != nil {
		t.Fatalf("serialize auto_id envelope: %v", err)
	}

	var out autoid.Envelope
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize auto_id envelope: %v", err)
	}

	if !reflect.DeepEqual(env, out) {
		t.Fatalf("auto_id envelope mismatch: %#v != %#v", env, out)
	}
}

func runLocalAutoIdWrapperRoundTrip(t *testing.T, f *fory.Fory, wrapper autoid.Wrapper) {
	data, err := f.Serialize(&wrapper)
	if err != nil {
		t.Fatalf("serialize auto_id wrapper: %v", err)
	}

	var out autoid.Wrapper
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize auto_id wrapper: %v", err)
	}

	if !reflect.DeepEqual(wrapper, out) {
		t.Fatalf("auto_id wrapper mismatch: %#v != %#v", wrapper, out)
	}
}

func runFileAutoIdRoundTrip(t *testing.T, f *fory.Fory, env autoid.Envelope) {
	dataFile := os.Getenv("DATA_FILE_AUTO_ID")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read auto_id data file: %v", err)
	}

	var decoded autoid.Envelope
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize auto_id peer payload: %v", err)
	}
	if !reflect.DeepEqual(env, decoded) {
		t.Fatalf("auto_id peer payload mismatch: %#v != %#v", env, decoded)
	}

	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize auto_id peer payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write auto_id data file: %v", err)
	}
}

func buildPrimitiveTypes() complexpb.PrimitiveTypes {
	contact := complexpb.EmailPrimitiveTypes_Contact("alice@example.com")
	contact = complexpb.PhonePrimitiveTypes_Contact(12345)
	return complexpb.PrimitiveTypes{
		BoolValue:         true,
		Int8Value:         12,
		Int16Value:        1234,
		Int32Value:        -123456,
		Varint32Value:     -12345,
		Int64Value:        -123456789,
		Varint64Value:     -987654321,
		TaggedInt64Value:  123456789,
		Uint8Value:        200,
		Uint16Value:       60000,
		Uint32Value:       1234567890,
		VarUint32Value:    1234567890,
		Uint64Value:       9876543210,
		VarUint64Value:    12345678901,
		TaggedUint64Value: 2222222222,
		Float32Value:      2.5,
		Float64Value:      3.5,
		Contact:           &contact,
	}
}

func buildNumericCollections() collection.NumericCollections {
	return collection.NumericCollections{
		Int8Values:    []int8{1, -2, 3},
		Int16Values:   []int16{100, -200, 300},
		Int32Values:   []int32{1000, -2000, 3000},
		Int64Values:   []int64{10000, -20000, 30000},
		Uint8Values:   []uint8{200, 250},
		Uint16Values:  []uint16{50000, 60000},
		Uint32Values:  []uint32{2000000000, 2100000000},
		Uint64Values:  []uint64{9000000000, 12000000000},
		Float32Values: []float32{1.5, 2.5},
		Float64Values: []float64{3.5, 4.5},
	}
}

func buildNumericCollectionUnion() collection.NumericCollectionUnion {
	return collection.Int32ValuesNumericCollectionUnion([]int32{7, 8, 9})
}

func buildNumericCollectionsArray() collection.NumericCollectionsArray {
	return collection.NumericCollectionsArray{
		Int8Values:    []int8{1, -2, 3},
		Int16Values:   []int16{100, -200, 300},
		Int32Values:   []int32{1000, -2000, 3000},
		Int64Values:   []int64{10000, -20000, 30000},
		Uint8Values:   []uint8{200, 250},
		Uint16Values:  []uint16{50000, 60000},
		Uint32Values:  []uint32{2000000000, 2100000000},
		Uint64Values:  []uint64{9000000000, 12000000000},
		Float32Values: []float32{1.5, 2.5},
		Float64Values: []float64{3.5, 4.5},
	}
}

func buildNumericCollectionArrayUnion() collection.NumericCollectionArrayUnion {
	return collection.Uint16ValuesNumericCollectionArrayUnion([]uint16{1000, 2000, 3000})
}

func runLocalPrimitiveRoundTrip(t *testing.T, f *fory.Fory, types complexpb.PrimitiveTypes) {
	data, err := f.Serialize(&types)
	if err != nil {
		t.Fatalf("serialize: %v", err)
	}

	var out complexpb.PrimitiveTypes
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if !reflect.DeepEqual(types, out) {
		t.Fatalf("roundtrip mismatch: %#v != %#v", types, out)
	}
}

func runFilePrimitiveRoundTrip(t *testing.T, f *fory.Fory, types complexpb.PrimitiveTypes) {
	dataFile := os.Getenv("DATA_FILE_PRIMITIVES")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read data file: %v", err)
	}

	var decoded complexpb.PrimitiveTypes
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize peer payload: %v", err)
	}
	if !reflect.DeepEqual(types, decoded) {
		t.Fatalf("peer payload mismatch: %#v != %#v", types, decoded)
	}

	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize peer payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write data file: %v", err)
	}
}

func runLocalCollectionRoundTrip(
	t *testing.T,
	f *fory.Fory,
	collections collection.NumericCollections,
	unionValue collection.NumericCollectionUnion,
	collectionsArray collection.NumericCollectionsArray,
	arrayUnion collection.NumericCollectionArrayUnion,
) {
	data, err := f.Serialize(&collections)
	if err != nil {
		t.Fatalf("serialize collections: %v", err)
	}

	var out collection.NumericCollections
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize collections: %v", err)
	}

	if !reflect.DeepEqual(collections, out) {
		t.Fatalf("collection roundtrip mismatch: %#v != %#v", collections, out)
	}

	unionData, err := f.Serialize(&unionValue)
	if err != nil {
		t.Fatalf("serialize collection union: %v", err)
	}
	var unionOut collection.NumericCollectionUnion
	if err := f.Deserialize(unionData, &unionOut); err != nil {
		t.Fatalf("deserialize collection union: %v", err)
	}
	if !reflect.DeepEqual(unionValue, unionOut) {
		t.Fatalf("collection union mismatch: %#v != %#v", unionValue, unionOut)
	}

	arrayData, err := f.Serialize(&collectionsArray)
	if err != nil {
		t.Fatalf("serialize collection array: %v", err)
	}
	var arrayOut collection.NumericCollectionsArray
	if err := f.Deserialize(arrayData, &arrayOut); err != nil {
		t.Fatalf("deserialize collection array: %v", err)
	}
	if !reflect.DeepEqual(collectionsArray, arrayOut) {
		t.Fatalf("collection array mismatch: %#v != %#v", collectionsArray, arrayOut)
	}

	arrayUnionData, err := f.Serialize(&arrayUnion)
	if err != nil {
		t.Fatalf("serialize collection array union: %v", err)
	}
	var arrayUnionOut collection.NumericCollectionArrayUnion
	if err := f.Deserialize(arrayUnionData, &arrayUnionOut); err != nil {
		t.Fatalf("deserialize collection array union: %v", err)
	}
	if !reflect.DeepEqual(arrayUnion, arrayUnionOut) {
		t.Fatalf("collection array union mismatch: %#v != %#v", arrayUnion, arrayUnionOut)
	}
}

func runFileCollectionRoundTrip(
	t *testing.T,
	f *fory.Fory,
	collections collection.NumericCollections,
	unionValue collection.NumericCollectionUnion,
	collectionsArray collection.NumericCollectionsArray,
	arrayUnion collection.NumericCollectionArrayUnion,
) {
	dataFile := os.Getenv("DATA_FILE_COLLECTION")
	if dataFile != "" {
		payload, err := os.ReadFile(dataFile)
		if err != nil {
			t.Fatalf("read collection file: %v", err)
		}
		var decoded collection.NumericCollections
		if err := f.Deserialize(payload, &decoded); err != nil {
			t.Fatalf("deserialize collection file: %v", err)
		}
		if !reflect.DeepEqual(collections, decoded) {
			t.Fatalf("collection file mismatch: %#v != %#v", collections, decoded)
		}
		out, err := f.Serialize(&decoded)
		if err != nil {
			t.Fatalf("serialize collection file: %v", err)
		}
		if err := os.WriteFile(dataFile, out, 0o644); err != nil {
			t.Fatalf("write collection file: %v", err)
		}
	}

	unionFile := os.Getenv("DATA_FILE_COLLECTION_UNION")
	if unionFile != "" {
		payload, err := os.ReadFile(unionFile)
		if err != nil {
			t.Fatalf("read collection union file: %v", err)
		}
		var decoded collection.NumericCollectionUnion
		if err := f.Deserialize(payload, &decoded); err != nil {
			t.Fatalf("deserialize collection union file: %v", err)
		}
		if !reflect.DeepEqual(unionValue, decoded) {
			t.Fatalf("collection union file mismatch: %#v != %#v", unionValue, decoded)
		}
		out, err := f.Serialize(&decoded)
		if err != nil {
			t.Fatalf("serialize collection union file: %v", err)
		}
		if err := os.WriteFile(unionFile, out, 0o644); err != nil {
			t.Fatalf("write collection union file: %v", err)
		}
	}

	arrayFile := os.Getenv("DATA_FILE_COLLECTION_ARRAY")
	if arrayFile != "" {
		payload, err := os.ReadFile(arrayFile)
		if err != nil {
			t.Fatalf("read collection array file: %v", err)
		}
		var decoded collection.NumericCollectionsArray
		if err := f.Deserialize(payload, &decoded); err != nil {
			t.Fatalf("deserialize collection array file: %v", err)
		}
		if !reflect.DeepEqual(collectionsArray, decoded) {
			t.Fatalf("collection array file mismatch: %#v != %#v", collectionsArray, decoded)
		}
		out, err := f.Serialize(&decoded)
		if err != nil {
			t.Fatalf("serialize collection array file: %v", err)
		}
		if err := os.WriteFile(arrayFile, out, 0o644); err != nil {
			t.Fatalf("write collection array file: %v", err)
		}
	}

	arrayUnionFile := os.Getenv("DATA_FILE_COLLECTION_ARRAY_UNION")
	if arrayUnionFile != "" {
		payload, err := os.ReadFile(arrayUnionFile)
		if err != nil {
			t.Fatalf("read collection array union file: %v", err)
		}
		var decoded collection.NumericCollectionArrayUnion
		if err := f.Deserialize(payload, &decoded); err != nil {
			t.Fatalf("deserialize collection array union file: %v", err)
		}
		if !reflect.DeepEqual(arrayUnion, decoded) {
			t.Fatalf("collection array union file mismatch: %#v != %#v", arrayUnion, decoded)
		}
		out, err := f.Serialize(&decoded)
		if err != nil {
			t.Fatalf("serialize collection array union file: %v", err)
		}
		if err := os.WriteFile(arrayUnionFile, out, 0o644); err != nil {
			t.Fatalf("write collection array union file: %v", err)
		}
	}
}

func buildMonster() monster.Monster {
	pos := &monster.Vec3{
		X: 1.0,
		Y: 2.0,
		Z: 3.0,
	}
	return monster.Monster{
		Pos:       pos,
		Mana:      int16(200),
		Hp:        int16(80),
		Name:      "Orc",
		Friendly:  true,
		Inventory: []uint8{1, 2, 3},
		Color:     monster.ColorBlue,
	}
}

func runLocalMonsterRoundTrip(t *testing.T, f *fory.Fory, monsterValue monster.Monster) {
	data, err := f.Serialize(&monsterValue)
	if err != nil {
		t.Fatalf("serialize: %v", err)
	}

	var out monster.Monster
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if !reflect.DeepEqual(monsterValue, out) {
		t.Fatalf("roundtrip mismatch: %#v != %#v", monsterValue, out)
	}
}

func runFileMonsterRoundTrip(t *testing.T, f *fory.Fory, monsterValue monster.Monster) {
	dataFile := os.Getenv("DATA_FILE_FLATBUFFERS_MONSTER")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read data file: %v", err)
	}

	var decoded monster.Monster
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize peer payload: %v", err)
	}
	if !reflect.DeepEqual(monsterValue, decoded) {
		t.Fatalf("peer payload mismatch: %#v != %#v", monsterValue, decoded)
	}

	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize peer payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write data file: %v", err)
	}
}

func buildContainer() complexfbs.Container {
	scalars := &complexfbs.ScalarPack{
		B:  -8,
		Ub: 200,
		S:  -1234,
		Us: 40000,
		I:  -123456,
		Ui: 123456,
		L:  -123456789,
		Ul: 987654321,
		F:  1.5,
		D:  2.5,
		Ok: true,
	}
	payload := complexfbs.NotePayload(&complexfbs.Note{Text: "alpha"})
	payload = complexfbs.MetricPayload(&complexfbs.Metric{Value: 42.0})
	return complexfbs.Container{
		Id:      9876543210,
		Status:  complexfbs.StatusStarted,
		Bytes:   []int8{1, 2, 3},
		Numbers: []int32{10, 20, 30},
		Scalars: scalars,
		Names:   []string{"alpha", "beta"},
		Flags:   []bool{true, false},
		Payload: payload,
	}
}

func runLocalContainerRoundTrip(t *testing.T, f *fory.Fory, container complexfbs.Container) {
	data, err := f.Serialize(&container)
	if err != nil {
		t.Fatalf("serialize: %v", err)
	}

	var out complexfbs.Container
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if !reflect.DeepEqual(container, out) {
		t.Fatalf("roundtrip mismatch: %#v != %#v", container, out)
	}
}

func runFileContainerRoundTrip(t *testing.T, f *fory.Fory, container complexfbs.Container) {
	dataFile := os.Getenv("DATA_FILE_FLATBUFFERS_TEST2")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read data file: %v", err)
	}

	var decoded complexfbs.Container
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize peer payload: %v", err)
	}
	if !reflect.DeepEqual(container, decoded) {
		t.Fatalf("peer payload mismatch: %#v != %#v", container, decoded)
	}

	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize peer payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write data file: %v", err)
	}
}

func buildOptionalHolder() optionaltypes.OptionalHolder {
	dateValue := fory.Date{Year: 2024, Month: time.January, Day: 2}
	timestampValue := time.Date(2024, 1, 2, 3, 4, 5, 0, time.UTC)
	allTypes := &optionaltypes.AllOptionalTypes{
		BoolValue:         optional.Some(true),
		Int8Value:         optional.Some(int8(12)),
		Int16Value:        optional.Some(int16(1234)),
		Int32Value:        optional.Some(int32(-123456)),
		FixedInt32Value:   optional.Some(int32(-123456)),
		Varint32Value:     optional.Some(int32(-12345)),
		Int64Value:        optional.Some(int64(-123456789)),
		FixedInt64Value:   optional.Some(int64(-123456789)),
		Varint64Value:     optional.Some(int64(-987654321)),
		TaggedInt64Value:  optional.Some(int64(123456789)),
		Uint8Value:        optional.Some(uint8(200)),
		Uint16Value:       optional.Some(uint16(60000)),
		Uint32Value:       optional.Some(uint32(1234567890)),
		FixedUint32Value:  optional.Some(uint32(1234567890)),
		VarUint32Value:    optional.Some(uint32(1234567890)),
		Uint64Value:       optional.Some(uint64(9876543210)),
		FixedUint64Value:  optional.Some(uint64(9876543210)),
		VarUint64Value:    optional.Some(uint64(12345678901)),
		TaggedUint64Value: optional.Some(uint64(2222222222)),
		Float32Value:      optional.Some(float32(2.5)),
		Float64Value:      optional.Some(3.5),
		StringValue:       optional.Some("optional"),
		BytesValue:        []byte{1, 2, 3},
		DateValue:         &dateValue,
		TimestampValue:    &timestampValue,
		Int32List:         []int32{1, 2, 3},
		StringList:        []string{"alpha", "beta"},
		Int64Map:          map[string]int64{"alpha": 10, "beta": 20},
	}
	unionValue := optionaltypes.NoteOptionalUnion("optional")
	return optionaltypes.OptionalHolder{
		AllTypes: allTypes,
		Choice:   &unionValue,
	}
}

func runLocalOptionalRoundTrip(t *testing.T, f *fory.Fory, holder optionaltypes.OptionalHolder) {
	data, err := f.Serialize(&holder)
	if err != nil {
		t.Fatalf("serialize: %v", err)
	}

	var out optionaltypes.OptionalHolder
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	assertOptionalHolderEqual(t, holder, out)
}

func runFileOptionalRoundTrip(t *testing.T, f *fory.Fory, holder optionaltypes.OptionalHolder) {
	dataFile := os.Getenv("DATA_FILE_OPTIONAL_TYPES")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read data file: %v", err)
	}

	var decoded optionaltypes.OptionalHolder
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize peer payload: %v", err)
	}
	assertOptionalHolderEqual(t, holder, decoded)

	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize peer payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write data file: %v", err)
	}
}

func buildTree() treepkg.TreeNode {
	childA := &treepkg.TreeNode{Id: "child-a", Name: "child-a"}
	childB := &treepkg.TreeNode{Id: "child-b", Name: "child-b"}
	childA.Children = []*treepkg.TreeNode{}
	childB.Children = []*treepkg.TreeNode{}
	childA.Parent = childB
	childB.Parent = childA

	return treepkg.TreeNode{
		Id:       "root",
		Name:     "root",
		Children: []*treepkg.TreeNode{childA, childA, childB},
	}
}

func runLocalTreeRoundTrip(t *testing.T, f *fory.Fory, root treepkg.TreeNode) {
	data, err := f.Serialize(&root)
	if err != nil {
		t.Fatalf("serialize tree: %v", err)
	}
	var out treepkg.TreeNode
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize tree: %v", err)
	}
	assertTree(t, out)
}

func runFileTreeRoundTrip(t *testing.T, f *fory.Fory, root treepkg.TreeNode) {
	dataFile := os.Getenv("DATA_FILE_TREE")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read tree payload: %v", err)
	}
	var decoded treepkg.TreeNode
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize tree payload: %v", err)
	}
	assertTree(t, decoded)
	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize tree payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write tree payload: %v", err)
	}
}

func assertTree(t *testing.T, root treepkg.TreeNode) {
	t.Helper()
	if len(root.Children) != 3 {
		t.Fatalf("tree children size mismatch")
	}
	if root.Children[0] != root.Children[1] {
		t.Fatalf("tree shared child mismatch")
	}
	if root.Children[0] == root.Children[2] {
		t.Fatalf("tree distinct child mismatch")
	}
	if root.Children[0].Parent != root.Children[2] {
		t.Fatalf("tree parent back-pointer mismatch")
	}
	if root.Children[2].Parent != root.Children[0] {
		t.Fatalf("tree parent reverse mismatch")
	}
}

func buildGraph() graphpkg.Graph {
	nodeA := &graphpkg.Node{Id: "node-a"}
	nodeB := &graphpkg.Node{Id: "node-b"}
	edge := &graphpkg.Edge{Id: "edge-1", Weight: 1.5, From: nodeA, To: nodeB}
	nodeA.OutEdges = []*graphpkg.Edge{edge}
	nodeA.InEdges = []*graphpkg.Edge{edge}
	nodeB.InEdges = []*graphpkg.Edge{edge}
	nodeB.OutEdges = []*graphpkg.Edge{}

	return graphpkg.Graph{
		Nodes: []*graphpkg.Node{nodeA, nodeB},
		Edges: []*graphpkg.Edge{edge},
	}
}

func runLocalGraphRoundTrip(t *testing.T, f *fory.Fory, graphValue graphpkg.Graph) {
	data, err := f.Serialize(&graphValue)
	if err != nil {
		t.Fatalf("serialize graph: %v", err)
	}
	var out graphpkg.Graph
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize graph: %v", err)
	}
	assertGraph(t, out)
}

func runFileGraphRoundTrip(t *testing.T, f *fory.Fory, graphValue graphpkg.Graph) {
	dataFile := os.Getenv("DATA_FILE_GRAPH")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read graph payload: %v", err)
	}
	var decoded graphpkg.Graph
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize graph payload: %v", err)
	}
	assertGraph(t, decoded)
	out, err := f.Serialize(&decoded)
	if err != nil {
		t.Fatalf("serialize graph payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write graph payload: %v", err)
	}
}

func assertGraph(t *testing.T, graphValue graphpkg.Graph) {
	t.Helper()
	if len(graphValue.Nodes) != 2 || len(graphValue.Edges) != 1 {
		t.Fatalf("graph size mismatch")
	}
	nodeA := graphValue.Nodes[0]
	nodeB := graphValue.Nodes[1]
	edge := graphValue.Edges[0]
	if nodeA.OutEdges[0] != nodeA.InEdges[0] {
		t.Fatalf("graph shared edge mismatch")
	}
	if edge != nodeA.OutEdges[0] {
		t.Fatalf("graph edge link mismatch")
	}
	if edge.From != nodeA || edge.To != nodeB {
		t.Fatalf("graph edge endpoints mismatch")
	}
}

func assertOptionalHolderEqual(t *testing.T, expected, actual optionaltypes.OptionalHolder) {
	t.Helper()
	if expected.AllTypes == nil || actual.AllTypes == nil {
		if expected.AllTypes != actual.AllTypes {
			t.Fatalf("optional holder all_types mismatch: %#v != %#v", expected.AllTypes, actual.AllTypes)
		}
	} else {
		assertOptionalTypesEqual(t, expected.AllTypes, actual.AllTypes)
	}
	if expected.Choice == nil || actual.Choice == nil {
		if expected.Choice != actual.Choice {
			t.Fatalf("optional holder choice mismatch: %#v != %#v", expected.Choice, actual.Choice)
		}
	} else {
		assertOptionalUnionEqual(t, *expected.Choice, *actual.Choice)
	}
}

func assertOptionalUnionEqual(t *testing.T, expected, actual optionaltypes.OptionalUnion) {
	t.Helper()
	if expected.Case() != actual.Case() {
		t.Fatalf("optional union case mismatch: %v != %v", expected.Case(), actual.Case())
	}
	switch expected.Case() {
	case optionaltypes.OptionalUnionCaseNote:
		expValue, _ := expected.AsNote()
		actValue, _ := actual.AsNote()
		if expValue != actValue {
			t.Fatalf("optional union note mismatch: %v != %v", expValue, actValue)
		}
	case optionaltypes.OptionalUnionCaseCode:
		expValue, _ := expected.AsCode()
		actValue, _ := actual.AsCode()
		if expValue != actValue {
			t.Fatalf("optional union code mismatch: %v != %v", expValue, actValue)
		}
	case optionaltypes.OptionalUnionCasePayload:
		expValue, _ := expected.AsPayload()
		actValue, _ := actual.AsPayload()
		if expValue == nil || actValue == nil {
			if expValue != actValue {
				t.Fatalf("optional union payload mismatch: %#v != %#v", expValue, actValue)
			}
			return
		}
		assertOptionalTypesEqual(t, expValue, actValue)
	default:
		t.Fatalf("unexpected optional union case: %v", expected.Case())
	}
}

func assertOptionalTypesEqual(t *testing.T, expected, actual *optionaltypes.AllOptionalTypes) {
	t.Helper()
	if expected.BoolValue != actual.BoolValue {
		t.Fatalf("bool_value mismatch: %#v != %#v", expected.BoolValue, actual.BoolValue)
	}
	if expected.Int8Value != actual.Int8Value {
		t.Fatalf("int8_value mismatch: %#v != %#v", expected.Int8Value, actual.Int8Value)
	}
	if expected.Int16Value != actual.Int16Value {
		t.Fatalf("int16_value mismatch: %#v != %#v", expected.Int16Value, actual.Int16Value)
	}
	if expected.Int32Value != actual.Int32Value {
		t.Fatalf("int32_value mismatch: %#v != %#v", expected.Int32Value, actual.Int32Value)
	}
	if expected.FixedInt32Value != actual.FixedInt32Value {
		t.Fatalf("fixed_int32_value mismatch: %#v != %#v", expected.FixedInt32Value, actual.FixedInt32Value)
	}
	if expected.Varint32Value != actual.Varint32Value {
		t.Fatalf("varint32_value mismatch: %#v != %#v", expected.Varint32Value, actual.Varint32Value)
	}
	if expected.Int64Value != actual.Int64Value {
		t.Fatalf("int64_value mismatch: %#v != %#v", expected.Int64Value, actual.Int64Value)
	}
	if expected.FixedInt64Value != actual.FixedInt64Value {
		t.Fatalf("fixed_int64_value mismatch: %#v != %#v", expected.FixedInt64Value, actual.FixedInt64Value)
	}
	if expected.Varint64Value != actual.Varint64Value {
		t.Fatalf("varint64_value mismatch: %#v != %#v", expected.Varint64Value, actual.Varint64Value)
	}
	if expected.TaggedInt64Value != actual.TaggedInt64Value {
		t.Fatalf("tagged_int64_value mismatch: %#v != %#v", expected.TaggedInt64Value, actual.TaggedInt64Value)
	}
	if expected.Uint8Value != actual.Uint8Value {
		t.Fatalf("uint8_value mismatch: %#v != %#v", expected.Uint8Value, actual.Uint8Value)
	}
	if expected.Uint16Value != actual.Uint16Value {
		t.Fatalf("uint16_value mismatch: %#v != %#v", expected.Uint16Value, actual.Uint16Value)
	}
	if expected.Uint32Value != actual.Uint32Value {
		t.Fatalf("uint32_value mismatch: %#v != %#v", expected.Uint32Value, actual.Uint32Value)
	}
	if expected.FixedUint32Value != actual.FixedUint32Value {
		t.Fatalf("fixed_uint32_value mismatch: %#v != %#v", expected.FixedUint32Value, actual.FixedUint32Value)
	}
	if expected.VarUint32Value != actual.VarUint32Value {
		t.Fatalf("var_uint32_value mismatch: %#v != %#v", expected.VarUint32Value, actual.VarUint32Value)
	}
	if expected.Uint64Value != actual.Uint64Value {
		t.Fatalf("uint64_value mismatch: %#v != %#v", expected.Uint64Value, actual.Uint64Value)
	}
	if expected.FixedUint64Value != actual.FixedUint64Value {
		t.Fatalf("fixed_uint64_value mismatch: %#v != %#v", expected.FixedUint64Value, actual.FixedUint64Value)
	}
	if expected.VarUint64Value != actual.VarUint64Value {
		t.Fatalf("var_uint64_value mismatch: %#v != %#v", expected.VarUint64Value, actual.VarUint64Value)
	}
	if expected.TaggedUint64Value != actual.TaggedUint64Value {
		t.Fatalf("tagged_uint64_value mismatch: %#v != %#v", expected.TaggedUint64Value, actual.TaggedUint64Value)
	}
	if expected.Float32Value != actual.Float32Value {
		t.Fatalf("float32_value mismatch: %#v != %#v", expected.Float32Value, actual.Float32Value)
	}
	if expected.Float64Value != actual.Float64Value {
		t.Fatalf("float64_value mismatch: %#v != %#v", expected.Float64Value, actual.Float64Value)
	}
	if expected.StringValue != actual.StringValue {
		t.Fatalf("string_value mismatch: %#v != %#v", expected.StringValue, actual.StringValue)
	}
	if !reflect.DeepEqual(expected.BytesValue, actual.BytesValue) {
		t.Fatalf("bytes_value mismatch: %#v != %#v", expected.BytesValue, actual.BytesValue)
	}
	if expected.DateValue == nil || actual.DateValue == nil {
		if expected.DateValue != actual.DateValue {
			t.Fatalf("date_value mismatch: %#v != %#v", expected.DateValue, actual.DateValue)
		}
	} else if expected.DateValue.Year != actual.DateValue.Year ||
		expected.DateValue.Month != actual.DateValue.Month ||
		expected.DateValue.Day != actual.DateValue.Day {
		t.Fatalf("date_value mismatch: %#v != %#v", expected.DateValue, actual.DateValue)
	}
	if expected.TimestampValue == nil || actual.TimestampValue == nil {
		if expected.TimestampValue != actual.TimestampValue {
			t.Fatalf("timestamp_value mismatch: %#v != %#v", expected.TimestampValue, actual.TimestampValue)
		}
	} else if !expected.TimestampValue.Equal(*actual.TimestampValue) {
		t.Fatalf("timestamp_value mismatch: %v != %v", expected.TimestampValue, actual.TimestampValue)
	}
	if !reflect.DeepEqual(expected.Int32List, actual.Int32List) {
		t.Fatalf("int32_list mismatch: %#v != %#v", expected.Int32List, actual.Int32List)
	}
	if !reflect.DeepEqual(expected.StringList, actual.StringList) {
		t.Fatalf("string_list mismatch: %#v != %#v", expected.StringList, actual.StringList)
	}
	if !reflect.DeepEqual(expected.Int64Map, actual.Int64Map) {
		t.Fatalf("int64_map mismatch: %#v != %#v", expected.Int64Map, actual.Int64Map)
	}
}
