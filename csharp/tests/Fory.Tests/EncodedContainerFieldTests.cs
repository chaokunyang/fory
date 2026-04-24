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

using Apache.Fory;
using ForyRuntime = Apache.Fory.Fory;

namespace Apache.Fory.Tests;

[ForyObject]
public sealed class EncodedUInt64Lists
{
    [Field(Id = 1)]
    public List<ulong> FixedUInt64List { get; set; } = [];

    [Field(Id = 2, Encoding = FieldEncoding.Varint)]
    public List<ulong> VarUInt64List { get; set; } = [];

    [Field(Id = 3, Encoding = FieldEncoding.Tagged)]
    public List<ulong> TaggedUInt64List { get; set; } = [];
}

[ForyObject]
public sealed class EncodedNumericMaps
{
    [Field(Id = 1, Encoding = FieldEncoding.Fixed)]
    public Dictionary<ulong, string> FixedUInt64Keys { get; set; } = [];

    [Field(Id = 2, Encoding = FieldEncoding.Varint)]
    public Dictionary<ulong, string> VarUInt64Keys { get; set; } = [];

    [Field(Id = 3, Encoding = FieldEncoding.Tagged)]
    public Dictionary<ulong, string> TaggedUInt64Keys { get; set; } = [];

    [Field(Id = 4, Encoding = FieldEncoding.Varint)]
    public Dictionary<string, ulong> VarUInt64Values { get; set; } = [];
}

public sealed class EncodedContainerFieldTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void EncodedUInt64ListsRoundTrip(bool compatible)
    {
        ForyRuntime fory = ForyRuntime.Builder().Compatible(compatible).Build();
        fory.Register<EncodedUInt64Lists>(411);

        EncodedUInt64Lists value = new()
        {
            FixedUInt64List = [0x1122334455667788UL, 0x8877665544332211UL],
            VarUInt64List = [127UL, 16384UL],
            TaggedUInt64List = [(ulong)int.MaxValue + 7UL, (ulong)uint.MaxValue + 13UL],
        };

        EncodedUInt64Lists decoded = fory.Deserialize<EncodedUInt64Lists>(fory.Serialize(value));
        Assert.Equal(value.FixedUInt64List, decoded.FixedUInt64List);
        Assert.Equal(value.VarUInt64List, decoded.VarUInt64List);
        Assert.Equal(value.TaggedUInt64List, decoded.TaggedUInt64List);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void EncodedNumericMapsRoundTrip(bool compatible)
    {
        ForyRuntime fory = ForyRuntime.Builder().Compatible(compatible).Build();
        fory.Register<EncodedNumericMaps>(412);

        EncodedNumericMaps value = new()
        {
            FixedUInt64Keys = new()
            {
                [0x1122334455667788UL] = "fixed-a",
                [0x8877665544332211UL] = "fixed-b",
            },
            VarUInt64Keys = new()
            {
                [127UL] = "var-a",
                [16384UL] = "var-b",
            },
            TaggedUInt64Keys = new()
            {
                [(ulong)int.MaxValue + 7UL] = "tagged-a",
                [(ulong)uint.MaxValue + 13UL] = "tagged-b",
            },
            VarUInt64Values = new()
            {
                ["left"] = 127UL,
                ["right"] = 16384UL,
            },
        };

        EncodedNumericMaps decoded = fory.Deserialize<EncodedNumericMaps>(fory.Serialize(value));
        Assert.Equal(value.FixedUInt64Keys, decoded.FixedUInt64Keys);
        Assert.Equal(value.VarUInt64Keys, decoded.VarUInt64Keys);
        Assert.Equal(value.TaggedUInt64Keys, decoded.TaggedUInt64Keys);
        Assert.Equal(value.VarUInt64Values, decoded.VarUInt64Values);
    }
}
