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
)

func TestUnsignedTypeSerialization(t *testing.T) {
	type TestStruct struct {
		U32Var    uint32 `fory:"compress=true"`
		U32Fixed  uint32 `fory:"compress=false"`
		U64Var    uint64 `fory:"encoding=varint"`
		U64Fixed  uint64 `fory:"encoding=fixed"`
		U64Tagged uint64 `fory:"encoding=tagged"`
	}

	f := New(WithXlang(true), WithCompatible(false))
	f.Register(TestStruct{}, 9999)

	obj := TestStruct{
		U32Var:    3000000000,
		U32Fixed:  4000000000,
		U64Var:    10000000000,
		U64Fixed:  15000000000,
		U64Tagged: 1000000000,
	}

	data, err := f.Serialize(obj)
	if err != nil {
		t.Fatalf("Serialize failed: %v", err)
	}

	var result interface{}
	err = f.Deserialize(data, &result)
	if err != nil {
		t.Fatalf("Deserialize failed: %v", err)
	}

	resultObj := result.(*TestStruct)
	if resultObj.U32Var != obj.U32Var {
		t.Errorf("U32Var mismatch: expected %d, got %d", obj.U32Var, resultObj.U32Var)
	}
	if resultObj.U32Fixed != obj.U32Fixed {
		t.Errorf("U32Fixed mismatch: expected %d, got %d", obj.U32Fixed, resultObj.U32Fixed)
	}
	if resultObj.U64Var != obj.U64Var {
		t.Errorf("U64Var mismatch: expected %d, got %d", obj.U64Var, resultObj.U64Var)
	}
	if resultObj.U64Fixed != obj.U64Fixed {
		t.Errorf("U64Fixed mismatch: expected %d, got %d", obj.U64Fixed, resultObj.U64Fixed)
	}
	if resultObj.U64Tagged != obj.U64Tagged {
		t.Errorf("U64Tagged mismatch: expected %d, got %d", obj.U64Tagged, resultObj.U64Tagged)
	}
}
