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

package addressbook

import (
	"os"
	"reflect"
	"testing"

	fory "github.com/apache/fory/go/fory"
)

func buildAddressBook() AddressBook {
	mobile := Person_PhoneNumber{
		Number:    "555-0100",
		PhoneType: Person_PhoneTypeMobile,
	}
	work := Person_PhoneNumber{
		Number:    "555-0111",
		PhoneType: Person_PhoneTypeWork,
	}

	person := Person{
		Name:   "Alice",
		Id:     123,
		Email:  "alice@example.com",
		Tags:   []string{"friend", "colleague"},
		Scores: map[string]int32{"math": 100, "science": 98},
		Salary: 120000.5,
		Phones: []Person_PhoneNumber{mobile, work},
	}

	return AddressBook{
		People:       []Person{person},
		PeopleByName: map[string]Person{person.Name: person},
	}
}

func TestAddressBookRoundTrip(t *testing.T) {
	f := fory.NewFory(fory.WithXlang(true), fory.WithRefTracking(false))
	if err := RegisterTypes(f); err != nil {
		t.Fatalf("register types: %v", err)
	}

	book := buildAddressBook()
	runLocalRoundTrip(t, f, book)
	runFileRoundTrip(t, f, book)

	types := buildPrimitiveTypes()
	runLocalPrimitiveRoundTrip(t, f, types)
	runFilePrimitiveRoundTrip(t, f, types)
}

func runLocalRoundTrip(t *testing.T, f *fory.Fory, book AddressBook) {
	data, err := f.Serialize(book)
	if err != nil {
		t.Fatalf("serialize: %v", err)
	}

	var out AddressBook
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if !reflect.DeepEqual(book, out) {
		t.Fatalf("roundtrip mismatch: %#v != %#v", book, out)
	}
}

func runFileRoundTrip(t *testing.T, f *fory.Fory, book AddressBook) {
	dataFile := os.Getenv("DATA_FILE")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read data file: %v", err)
	}

	var decoded AddressBook
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize peer payload: %v", err)
	}
	if !reflect.DeepEqual(book, decoded) {
		t.Fatalf("peer payload mismatch: %#v != %#v", book, decoded)
	}

	out, err := f.Serialize(decoded)
	if err != nil {
		t.Fatalf("serialize peer payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write data file: %v", err)
	}
}

func buildPrimitiveTypes() PrimitiveTypes {
	return PrimitiveTypes{
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
		Float16Value:      1.5,
		Float32Value:      2.5,
		Float64Value:      3.5,
	}
}

func runLocalPrimitiveRoundTrip(t *testing.T, f *fory.Fory, types PrimitiveTypes) {
	data, err := f.Serialize(types)
	if err != nil {
		t.Fatalf("serialize: %v", err)
	}

	var out PrimitiveTypes
	if err := f.Deserialize(data, &out); err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if !reflect.DeepEqual(types, out) {
		t.Fatalf("roundtrip mismatch: %#v != %#v", types, out)
	}
}

func runFilePrimitiveRoundTrip(t *testing.T, f *fory.Fory, types PrimitiveTypes) {
	dataFile := os.Getenv("DATA_FILE_PRIMITIVES")
	if dataFile == "" {
		return
	}
	payload, err := os.ReadFile(dataFile)
	if err != nil {
		t.Fatalf("read data file: %v", err)
	}

	var decoded PrimitiveTypes
	if err := f.Deserialize(payload, &decoded); err != nil {
		t.Fatalf("deserialize peer payload: %v", err)
	}
	if !reflect.DeepEqual(types, decoded) {
		t.Fatalf("peer payload mismatch: %#v != %#v", types, decoded)
	}

	out, err := f.Serialize(decoded)
	if err != nil {
		t.Fatalf("serialize peer payload: %v", err)
	}
	if err := os.WriteFile(dataFile, out, 0o644); err != nil {
		t.Fatalf("write data file: %v", err)
	}
}
