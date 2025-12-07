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

package main

import (
	"flag"
	"fmt"
	"os"

	"github.com/apache/fory/go/fory"
	"github.com/spaolacci/murmur3"
)

// ============================================================================
// Helper functions
// ============================================================================

func getDataFile() string {
	dataFile := os.Getenv("DATA_FILE")
	if dataFile == "" {
		panic("DATA_FILE environment variable not set")
	}
	return dataFile
}

func readFile(path string) []byte {
	data, err := os.ReadFile(path)
	if err != nil {
		panic(fmt.Sprintf("Failed to read file %s: %v", path, err))
	}
	return data
}

func writeFile(path string, data []byte) {
	err := os.WriteFile(path, data, 0644)
	if err != nil {
		panic(fmt.Sprintf("Failed to write file %s: %v", path, err))
	}
}

func assertEqual(expected, actual interface{}, name string) {
	if expected != actual {
		panic(fmt.Sprintf("%s: expected %v, got %v", name, expected, actual))
	}
}

func assertEqualFloat32(expected, actual float32, name string) {
	diff := expected - actual
	if diff < 0 {
		diff = -diff
	}
	if diff > 0.0001 {
		panic(fmt.Sprintf("%s: expected %v, got %v", name, expected, actual))
	}
}

func assertEqualFloat64(expected, actual float64, name string) {
	diff := expected - actual
	if diff < 0 {
		diff = -diff
	}
	if diff > 0.000001 {
		panic(fmt.Sprintf("%s: expected %v, got %v", name, expected, actual))
	}
}

func murmurHash3_x64_128(data []byte, seed int64) (uint64, uint64) {
	h := murmur3.New128WithSeed(uint32(seed))
	h.Write(data)
	h1, h2 := h.Sum128()
	return h1, h2
}

// ============================================================================
// Test Data Structures
// ============================================================================

type Color int32

const (
	RED   Color = 0
	GREEN Color = 1
	BLUE  Color = 2
)

type Item struct {
	Name   string
	Amount int32
}

type Item1 struct {
	A int8
	B int16
	C int32
	D int64
	E int
}

type SimpleStruct struct {
	Color Color
	Item  Item
}

type StructWithList struct {
	Items []string
}

type StructWithMap struct {
	Data map[string]int32
}

type MyExt struct {
	Id int32
}

type EmptyWrapper struct {
	Value interface{}
}

type MyStruct struct {
	A int32
	B string
}

type VersionCheckStruct struct {
	Field1 string
	Field2 int32
}

type Dog struct {
	Name string
	Age  int32
}

type Cat struct {
	Name  string
	Color string
}

type AnimalListHolder struct {
	Animals []interface{}
}

type AnimalMapHolder struct {
	Animals map[string]interface{}
}

// ============================================================================
// Custom Serializer
// ============================================================================

type MyExtSerializer struct{}

func (s *MyExtSerializer) TypeId() int16 {
	return int16(fory.NotSupportCrossLanguage)
}

func (s *MyExtSerializer) Write(ctx *fory.WriteContext, value interface{}) error {
	myExt := value.(MyExt)
	ctx.Buffer().WriteVarInt32(myExt.Id)
	return nil
}

func (s *MyExtSerializer) Read(ctx *fory.ReadContext, type_ interface{}) (interface{}, error) {
	id := ctx.Buffer().ReadVarInt32()
	return MyExt{Id: id}, nil
}

// ============================================================================
// Test Cases
// ============================================================================

func testBuffer() {
	dataFile := getDataFile()
	data := readFile(dataFile)
	buf := fory.NewByteBuffer(data)

	boolVal := buf.ReadBool()
	assertEqual(true, boolVal, "bool")

	byteVal, _ := buf.ReadByte()
	assertEqual(byte(0x7F), byteVal, "byte")

	int16Val := buf.ReadInt16()
	assertEqual(int16(32767), int16Val, "int16")

	int32Val := buf.ReadInt32()
	assertEqual(int32(2147483647), int32Val, "int32")

	int64Val := buf.ReadInt64()
	assertEqual(int64(9223372036854775807), int64Val, "int64")

	float32Val := buf.ReadFloat32()
	assertEqualFloat32(-1.1, float32Val, "float32")

	float64Val := buf.ReadFloat64()
	assertEqualFloat64(-1.1, float64Val, "float64")

	varUint32Val := buf.ReadVarUint32()
	assertEqual(uint32(100), varUint32Val, "varuint32")

	length := buf.ReadInt32()
	bytes := buf.ReadBinary(int(length))
	if string(bytes) != "ab" {
		panic(fmt.Sprintf("bytes: expected 'ab', got '%s'", string(bytes)))
	}

	outBuf := fory.NewByteBuffer(make([]byte, 0, 256))
	outBuf.WriteBool(true)
	outBuf.WriteByte_(byte(0x7F))
	outBuf.WriteInt16(32767)
	outBuf.WriteInt32(2147483647)
	outBuf.WriteInt64(9223372036854775807)
	outBuf.WriteFloat32(-1.1)
	outBuf.WriteFloat64(-1.1)
	outBuf.WriteVarUint32(100)
	outBuf.WriteInt32(2)
	outBuf.WriteBinary([]byte("ab"))

	writeFile(dataFile, outBuf.GetByteSlice(0, outBuf.WriterIndex()))
}

func testBufferVar() {
	dataFile := getDataFile()
	data := readFile(dataFile)
	buf := fory.NewByteBuffer(data)

	varInt32Values := []int32{
		-2147483648, -2147483647, -1000000, -1000, -128, -1, 0, 1,
		127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456,
		2147483646, 2147483647,
	}
	for _, expected := range varInt32Values {
		val := buf.ReadVarInt32()
		assertEqual(expected, val, fmt.Sprintf("varint32 %d", expected))
	}

	varUint32Values := []uint32{
		0, 1, 127, 128, 16383, 16384, 2097151, 2097152,
		268435455, 268435456, 2147483646, 2147483647,
	}
	for _, expected := range varUint32Values {
		val := buf.ReadVarUint32()
		assertEqual(expected, val, fmt.Sprintf("varuint32 %d", expected))
	}

	varUint64Values := []uint64{
		0, 1, 127, 128, 16383, 16384, 2097151, 2097152,
		268435455, 268435456, 34359738367, 34359738368,
		4398046511103, 4398046511104, 562949953421311, 562949953421312,
		72057594037927935, 72057594037927936, 9223372036854775807,
	}
	for _, expected := range varUint64Values {
		val := buf.ReadVarUint64()
		assertEqual(expected, val, fmt.Sprintf("varuint64 %d", expected))
	}

	varInt64Values := []int64{
		-9223372036854775808, -9223372036854775807, -1000000000000,
		-1000000, -1000, -128, -1, 0, 1, 127, 1000, 1000000,
		1000000000000, 9223372036854775806, 9223372036854775807,
	}
	for _, expected := range varInt64Values {
		val := buf.ReadVarint64()
		assertEqual(expected, val, fmt.Sprintf("varint64 %d", expected))
	}

	outBuf := fory.NewByteBuffer(make([]byte, 0, 512))
	for _, val := range varInt32Values {
		outBuf.WriteVarInt32(val)
	}
	for _, val := range varUint32Values {
		outBuf.WriteVarUint32(val)
	}
	for _, val := range varUint64Values {
		outBuf.WriteVarUint64(val)
	}
	for _, val := range varInt64Values {
		outBuf.WriteVarint64(val)
	}

	writeFile(dataFile, outBuf.GetByteSlice(0, outBuf.WriterIndex()))
}

func testMurmurHash3() {
	dataFile := getDataFile()
	data := readFile(dataFile)
	buf := fory.NewByteBuffer(data)

	_ = buf.ReadInt64()
	_ = buf.ReadInt64()
	_ = buf.ReadInt64()
	_ = buf.ReadInt64()

	h1_1, h1_2 := murmurHash3_x64_128([]byte{1, 2, 8}, 47)
	h2_1, h2_2 := murmurHash3_x64_128([]byte("01234567890123456789"), 47)

	outBuf := fory.NewByteBuffer(make([]byte, 0, 32))
	outBuf.WriteInt64(int64(h1_1))
	outBuf.WriteInt64(int64(h1_2))
	outBuf.WriteInt64(int64(h2_1))
	outBuf.WriteInt64(int64(h2_2))

	writeFile(dataFile, outBuf.GetByteSlice(0, outBuf.WriterIndex()))
}

func testStringSerializer() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))

	testStrings := []string{
		"ab",
		"Rust123",
		"Çüéâäàåçêëèïî",
		"こんにちは",
		"Привет",
		"𝄞🎵🎶",
		"Hello, 世界",
	}

	buf := fory.NewByteBuffer(data)
	for range testStrings {
		var result string
		err := f.Deserialize(buf, &result, nil)
		if err != nil {
			panic(fmt.Sprintf("Failed to deserialize: %v", err))
		}
	}

	var outData []byte
	for _, s := range testStrings {
		serialized, err := f.SerializeAny(s)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

func testCrossLanguageSerializer() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(Color(0), "color")

	vals := make([]interface{}, 0)
	buf := fory.NewByteBuffer(data)
	for buf.ReaderIndex() < len(data) {
		var val interface{}
		err := f.Deserialize(buf, &val, nil)
		if err != nil {
			break
		}
		vals = append(vals, val)
	}

	var outData []byte
	for _, val := range vals {
		serialized, err := f.SerializeAny(val)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

func testSimpleStruct() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(Color(0), "color")
	f.RegisterNamedType(Item{}, "item")
	f.RegisterNamedType(SimpleStruct{}, "simple_struct")

	obj, err := f.DeserializeAny(data)
	if err != nil {
		panic(fmt.Sprintf("Failed to deserialize: %v", err))
	}

	serialized, err := f.SerializeAny(obj)
	if err != nil {
		panic(fmt.Sprintf("Failed to serialize: %v", err))
	}

	writeFile(dataFile, serialized)
}

func testNamedSimpleStruct() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(Color(0), "demo.color")
	f.RegisterNamedType(Item{}, "demo.item")
	f.RegisterNamedType(SimpleStruct{}, "demo.simple_struct")

	obj, err := f.DeserializeAny(data)
	if err != nil {
		panic(fmt.Sprintf("Failed to deserialize: %v", err))
	}

	serialized, err := f.SerializeAny(obj)
	if err != nil {
		panic(fmt.Sprintf("Failed to serialize: %v", err))
	}

	writeFile(dataFile, serialized)
}

func testList() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(Item{}, "item")

	buf := fory.NewByteBuffer(data)
	lists := make([]interface{}, 4)
	
	for i := 0; i < 4; i++ {
		var obj interface{}
		err := f.Deserialize(buf, &obj, nil)
		if err != nil {
			panic(fmt.Sprintf("Failed to deserialize list %d: %v", i, err))
		}
		lists[i] = obj
	}

	var outData []byte
	for _, list := range lists {
		serialized, err := f.SerializeAny(list)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

func testMap() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(Item{}, "item")

	buf := fory.NewByteBuffer(data)
	maps := make([]interface{}, 2)
	
	for i := 0; i < 2; i++ {
		var obj interface{}
		err := f.Deserialize(buf, &obj, nil)
		if err != nil {
			panic(fmt.Sprintf("Failed to deserialize map %d: %v", i, err))
		}
		maps[i] = obj
	}

	var outData []byte
	for _, m := range maps {
		serialized, err := f.SerializeAny(m)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

func testInteger() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(Item1{}, "item1")

	buf := fory.NewByteBuffer(data)
	values := make([]interface{}, 7)
	
	for i := 0; i < 7; i++ {
		var obj interface{}
		err := f.Deserialize(buf, &obj, nil)
		if err != nil {
			panic(fmt.Sprintf("Failed to deserialize value %d: %v", i, err))
		}
		values[i] = obj
	}

	var outData []byte
	for _, val := range values {
		serialized, err := f.SerializeAny(val)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

func testItem() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(Item{}, "item")

	buf := fory.NewByteBuffer(data)
	items := make([]interface{}, 3)
	
	for i := 0; i < 3; i++ {
		var obj interface{}
		err := f.Deserialize(buf, &obj, nil)
		if err != nil {
			panic(fmt.Sprintf("Failed to deserialize item %d: %v", i, err))
		}
		items[i] = obj
	}

	var outData []byte
	for _, item := range items {
		serialized, err := f.SerializeAny(item)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

func testColor() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(Color(0), "color")

	buf := fory.NewByteBuffer(data)
	colors := make([]interface{}, 4)
	
	for i := 0; i < 4; i++ {
		var obj interface{}
		err := f.Deserialize(buf, &obj, nil)
		if err != nil {
			panic(fmt.Sprintf("Failed to deserialize color %d: %v", i, err))
		}
		colors[i] = obj
	}

	var outData []byte
	for _, color := range colors {
		serialized, err := f.SerializeAny(color)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

func testStructWithList() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(StructWithList{}, "struct_with_list")

	obj, err := f.DeserializeAny(data)
	if err != nil {
		panic(fmt.Sprintf("Failed to deserialize: %v", err))
	}

	serialized, err := f.SerializeAny(obj)
	if err != nil {
		panic(fmt.Sprintf("Failed to serialize: %v", err))
	}

	writeFile(dataFile, serialized)
}

func testStructWithMap() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(StructWithMap{}, "struct_with_map")

	obj, err := f.DeserializeAny(data)
	if err != nil {
		panic(fmt.Sprintf("Failed to deserialize: %v", err))
	}

	serialized, err := f.SerializeAny(obj)
	if err != nil {
		panic(fmt.Sprintf("Failed to serialize: %v", err))
	}

	writeFile(dataFile, serialized)
}

func testSkipIdCustom() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(MyExt{}, "my_ext")
	f.RegisterNamedType(EmptyWrapper{}, "my_wrapper")

	obj, err := f.DeserializeAny(data)
	if err != nil {
		panic(fmt.Sprintf("Failed to deserialize: %v", err))
	}

	serialized, err := f.SerializeAny(obj)
	if err != nil {
		panic(fmt.Sprintf("Failed to serialize: %v", err))
	}

	writeFile(dataFile, serialized)
}

func testSkipNameCustom() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(MyExt{}, "my_ext")
	f.RegisterNamedType(EmptyWrapper{}, "my_wrapper")

	obj, err := f.DeserializeAny(data)
	if err != nil {
		panic(fmt.Sprintf("Failed to deserialize: %v", err))
	}

	serialized, err := f.SerializeAny(obj)
	if err != nil {
		panic(fmt.Sprintf("Failed to serialize: %v", err))
	}

	writeFile(dataFile, serialized)
}

func testConsistentNamed() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(Color(0), "color")
	f.RegisterNamedType(MyStruct{}, "my_struct")
	f.RegisterNamedType(MyExt{}, "my_ext")

	buf := fory.NewByteBuffer(data)
	values := make([]interface{}, 9)
	
	for i := 0; i < 9; i++ {
		var obj interface{}
		err := f.Deserialize(buf, &obj, nil)
		if err != nil {
			panic(fmt.Sprintf("Failed to deserialize value %d: %v", i, err))
		}
		values[i] = obj
	}

	var outData []byte
	for _, val := range values {
		serialized, err := f.SerializeAny(val)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

func testStructVersionCheck() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(VersionCheckStruct{}, "version_check_struct")

	obj, err := f.DeserializeAny(data)
	if err != nil {
		panic(fmt.Sprintf("Failed to deserialize: %v", err))
	}

	serialized, err := f.SerializeAny(obj)
	if err != nil {
		panic(fmt.Sprintf("Failed to serialize: %v", err))
	}

	writeFile(dataFile, serialized)
}

func testPolymorphicList() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(&Dog{}, "dog")
	f.RegisterNamedType(&Cat{}, "cat")
	f.RegisterNamedType(AnimalListHolder{}, "animal_list_holder")

	buf := fory.NewByteBuffer(data)
	values := make([]interface{}, 2)
	
	for i := 0; i < 2; i++ {
		var obj interface{}
		err := f.Deserialize(buf, &obj, nil)
		if err != nil {
			panic(fmt.Sprintf("Failed to deserialize value %d: %v", i, err))
		}
		values[i] = obj
	}

	var outData []byte
	for _, val := range values {
		serialized, err := f.SerializeAny(val)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

func testPolymorphicMap() {
	dataFile := getDataFile()
	data := readFile(dataFile)

	f := fory.New(fory.WithCompatible(true))
	f.RegisterNamedType(&Dog{}, "dog")
	f.RegisterNamedType(&Cat{}, "cat")
	f.RegisterNamedType(AnimalMapHolder{}, "animal_map_holder")

	buf := fory.NewByteBuffer(data)
	values := make([]interface{}, 2)
	
	for i := 0; i < 2; i++ {
		var obj interface{}
		err := f.Deserialize(buf, &obj, nil)
		if err != nil {
			panic(fmt.Sprintf("Failed to deserialize value %d: %v", i, err))
		}
		values[i] = obj
	}

	var outData []byte
	for _, val := range values {
		serialized, err := f.SerializeAny(val)
		if err != nil {
			panic(fmt.Sprintf("Failed to serialize: %v", err))
		}
		outData = append(outData, serialized...)
	}

	writeFile(dataFile, outData)
}

// ============================================================================
// Main
// ============================================================================

func main() {
	caseName := flag.String("case", "", "Test case name")
	flag.Parse()

	if *caseName == "" {
		fmt.Println("Usage: go run xlang_test_main.go --case <case_name>")
		os.Exit(1)
	}

	defer func() {
		if r := recover(); r != nil {
			fmt.Printf("Test case %s failed: %v\n", *caseName, r)
			os.Exit(1)
		}
	}()

	switch *caseName {
	case "test_buffer":
		testBuffer()
	case "test_buffer_var":
		testBufferVar()
	case "test_murmurhash3":
		testMurmurHash3()
	case "test_string_serializer":
		testStringSerializer()
	case "test_cross_language_serializer":
		testCrossLanguageSerializer()
	case "test_simple_struct":
		testSimpleStruct()
	case "test_named_simple_struct":
		testNamedSimpleStruct()
	case "test_list":
		testList()
	case "test_map":
		testMap()
	case "test_integer":
		testInteger()
	case "test_item":
		testItem()
	case "test_color":
		testColor()
	case "test_struct_with_list":
		testStructWithList()
	case "test_struct_with_map":
		testStructWithMap()
	case "test_skip_id_custom":
		testSkipIdCustom()
	case "test_skip_name_custom":
		testSkipNameCustom()
	case "test_consistent_named":
		testConsistentNamed()
	case "test_struct_version_check":
		testStructVersionCheck()
	case "test_polymorphic_list":
		testPolymorphicList()
	case "test_polymorphic_map":
		testPolymorphicMap()
	default:
		panic(fmt.Sprintf("Unknown test case: %s", *caseName))
	}

	fmt.Printf("Test case %s passed\n", *caseName)
}
