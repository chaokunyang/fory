# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""Tests for the proto frontend translation."""

import pytest

from fory_compiler.frontend.base import FrontendError
from fory_compiler.frontend.proto import ProtoFrontend
from fory_compiler.ir.ast import PrimitiveType
from fory_compiler.ir.types import PrimitiveKind


def test_proto_type_mapping():
    source = """
    syntax = "proto3";
    package demo;

    message Person {
        int32 age = 1;
        sint32 score = 2;
        fixed32 id = 3;
        sfixed64 balance = 4;
    }
    """
    schema = ProtoFrontend().parse(source)
    fields = {f.name: f.field_type for f in schema.messages[0].fields}

    assert isinstance(fields["age"], PrimitiveType)
    assert fields["age"].kind == PrimitiveKind.VAR_UINT32
    assert fields["score"].kind == PrimitiveKind.VARINT32
    assert fields["id"].kind == PrimitiveKind.UINT32
    assert fields["balance"].kind == PrimitiveKind.INT64


def test_proto_oneof_error():
    source = """
    syntax = "proto3";

    message Event {
        oneof payload {
            string text = 1;
            int32 number = 2;
        }
    }
    """
    with pytest.raises(FrontendError):
        ProtoFrontend().parse(source)
