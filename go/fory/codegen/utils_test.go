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

package codegen

import (
	"go/token"
	"go/types"
	"reflect"
	"strings"
	"testing"
)

func TestSortFieldsUsesTagIDs(t *testing.T) {
	fields := []*FieldInfo{
		{
			GoName:    "StringValue",
			SnakeName: "string_value",
			TypeID:    "STRING",
			TagID:     2,
			HasTagID:  true,
		},
		{
			GoName:    "MapValue",
			SnakeName: "map_value",
			TypeID:    "MAP",
			TagID:     1,
			HasTagID:  true,
		},
		{
			GoName:    "CustomValue",
			SnakeName: "custom_value",
			TypeID:    "NAMED_STRUCT",
		},
		{
			GoName:        "IntValue",
			SnakeName:     "int_value",
			TypeID:        "VARINT32",
			TagID:         10,
			HasTagID:      true,
			IsPrimitive:   true,
			PrimitiveSize: 4,
		},
	}

	sortFields(fields)

	names := make([]string, len(fields))
	for i, field := range fields {
		names[i] = field.GoName
	}
	want := []string{"IntValue", "MapValue", "StringValue", "CustomValue"}
	if !reflect.DeepEqual(names, want) {
		t.Fatalf("unexpected field order: got %v, want %v", names, want)
	}
}

func TestExtractStructInfoRejectsDuplicateTagIDs(t *testing.T) {
	structType := types.NewStruct(
		[]*types.Var{
			types.NewField(token.NoPos, nil, "First", types.Typ[types.String], false),
			types.NewField(token.NoPos, nil, "Second", types.Typ[types.String], false),
		},
		[]string{`fory:"id=1"`, `fory:"id=1"`},
	)

	_, err := extractStructInfo("DuplicateTagID", structType)
	if err == nil || !strings.Contains(err.Error(), "duplicate field id 1") {
		t.Fatalf("expected duplicate field id error, got %v", err)
	}
}

func TestExtractStructInfoRejectsNegativeTagIDs(t *testing.T) {
	structType := types.NewStruct(
		[]*types.Var{
			types.NewField(token.NoPos, nil, "Name", types.Typ[types.String], false),
		},
		[]string{`fory:"id=-1"`},
	)

	_, err := extractStructInfo("NegativeTagID", structType)
	if err == nil || !strings.Contains(err.Error(), "field id must be non-negative") {
		t.Fatalf("expected negative field id error, got %v", err)
	}
}

func TestExtractStructInfoRejectsMalformedTagIDs(t *testing.T) {
	for _, test := range []struct {
		name    string
		tag     string
		message string
	}{
		{
			name:    "MissingValue",
			tag:     `fory:"id"`,
			message: "field id requires a value",
		},
		{
			name:    "DuplicateKey",
			tag:     `fory:"id=1,id=2"`,
			message: "duplicate field id tag",
		},
	} {
		t.Run(test.name, func(t *testing.T) {
			structType := types.NewStruct(
				[]*types.Var{
					types.NewField(token.NoPos, nil, "Name", types.Typ[types.String], false),
				},
				[]string{test.tag},
			)

			_, err := extractStructInfo(test.name, structType)
			if err == nil || !strings.Contains(err.Error(), test.message) {
				t.Fatalf("expected %q error, got %v", test.message, err)
			}
		})
	}
}
