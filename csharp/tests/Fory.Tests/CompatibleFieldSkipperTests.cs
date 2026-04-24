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

using System.Numerics;
using Apache.Fory;
using ForyRuntime = Apache.Fory.Fory;

namespace Apache.Fory.Tests;

[ForyObject]
public enum ExampleSkipState
{
    Unknown = 0,
    Ready = 1,
    Done = 2,
}

[ForyObject]
public sealed class ExampleSkipLeaf
{
    [Field(Id = 1)]
    public string Name { get; set; } = string.Empty;

    [Field(Id = 2)]
    public int Count { get; set; }
}

public sealed class ExampleSkipLeafUnion : Union
{
    private ExampleSkipLeafUnion(int index, object? value) : base(index, value)
    {
    }

    public static ExampleSkipLeafUnion Text(string value)
    {
        return new ExampleSkipLeafUnion(1, value);
    }

    public static ExampleSkipLeafUnion Leaf(ExampleSkipLeaf value)
    {
        return new ExampleSkipLeafUnion(2, value);
    }
}

[ForyObject]
public sealed class SkipTailMarker
{
    [Field(Id = 1)]
    public string Label { get; set; } = string.Empty;

    [Field(Id = 2)]
    public int Order { get; set; }
}

[ForyObject]
public sealed class ExampleDirectWriter
{
    [Field(Id = 1)]
    public bool AaaKnown { get; set; }

    [Field(Id = 4, Encoding = FieldEncoding.Fixed)]
    public int FixedInt32Value { get; set; }

    [Field(Id = 6, Encoding = FieldEncoding.Fixed)]
    public long FixedInt64Value { get; set; }

    [Field(Id = 8, Encoding = FieldEncoding.Tagged)]
    public long TaggedInt64Value { get; set; }

    [Field(Id = 9)]
    public byte Uint8Value { get; set; }

    [Field(Id = 10)]
    public ushort Uint16Value { get; set; }

    [Field(Id = 11, Encoding = FieldEncoding.Fixed)]
    public uint FixedUint32Value { get; set; }

    [Field(Id = 12)]
    public uint VarUint32Value { get; set; }

    [Field(Id = 13, Encoding = FieldEncoding.Fixed)]
    public ulong FixedUint64Value { get; set; }

    [Field(Id = 14)]
    public ulong VarUint64Value { get; set; }

    [Field(Id = 15, Encoding = FieldEncoding.Tagged)]
    public ulong TaggedUint64Value { get; set; }

    [Field(Id = 21)]
    public byte[] BytesValue { get; set; } = [];

    [Field(Id = 22)]
    public DateOnly DateValue { get; set; }

    [Field(Id = 23)]
    public DateTimeOffset TimestampValue { get; set; }

    [Field(Id = 24)]
    public TimeSpan DurationValue { get; set; }

    [Field(Id = 25)]
    public ForyDecimal DecimalValue { get; set; }

    [Field(Id = 26)]
    public ExampleSkipState EnumValue { get; set; }

    [Field(Id = 27)]
    public ExampleSkipLeaf? MessageValue { get; set; }

    [Field(Id = 28)]
    public ExampleSkipLeafUnion? UnionValue { get; set; }

    [Field(Id = 900)]
    public SkipTailMarker ZzzTailMarker { get; set; } = new();
}

[ForyObject]
public sealed class ExampleDirectReader
{
    [Field(Id = 1)]
    public bool AaaKnown { get; set; }

    [Field(Id = 900)]
    public SkipTailMarker ZzzTailMarker { get; set; } = new();
}

[ForyObject]
public sealed class ExampleListsWriter
{
    [Field(Id = 1)]
    public string AaaKnown { get; set; } = string.Empty;

    [Field(Id = 104)]
    public List<int> FixedInt32List { get; set; } = [];

    [Field(Id = 107)]
    public List<long> Varint64List { get; set; } = [];

    [Field(Id = 109)]
    public List<byte> Uint8List { get; set; } = [];

    [Field(Id = 110)]
    public List<ushort> Uint16List { get; set; } = [];

    [Field(Id = 112)]
    public List<uint> VarUint32List { get; set; } = [];

    [Field(Id = 114)]
    public List<ulong> VarUint64List { get; set; } = [];

    [Field(Id = 118)]
    public List<Half?> MaybeFloat16List { get; set; } = [];

    [Field(Id = 119)]
    public List<BFloat16?> MaybeBfloat16List { get; set; } = [];

    [Field(Id = 120)]
    public List<float> Float32List { get; set; } = [];

    [Field(Id = 123)]
    public List<byte[]?> BytesList { get; set; } = [];

    [Field(Id = 124)]
    public List<DateOnly> DateList { get; set; } = [];

    [Field(Id = 125)]
    public List<DateTimeOffset> TimestampList { get; set; } = [];

    [Field(Id = 126)]
    public List<TimeSpan> DurationList { get; set; } = [];

    [Field(Id = 127)]
    public List<ForyDecimal> DecimalList { get; set; } = [];

    [Field(Id = 128)]
    public List<ExampleSkipState> EnumList { get; set; } = [];

    [Field(Id = 129)]
    public List<ExampleSkipLeaf?> MessageList { get; set; } = [];

    [Field(Id = 130)]
    public List<ExampleSkipLeafUnion?> UnionList { get; set; } = [];

    [Field(Id = 900)]
    public SkipTailMarker ZzzTailMarker { get; set; } = new();
}

[ForyObject]
public sealed class ExampleListsReader
{
    [Field(Id = 1)]
    public string AaaKnown { get; set; } = string.Empty;

    [Field(Id = 900)]
    public SkipTailMarker ZzzTailMarker { get; set; } = new();
}

[ForyObject]
public sealed class ExampleMapsWriter
{
    [Field(Id = 1)]
    public string AaaKnown { get; set; } = string.Empty;

    [Field(Id = 201)]
    public Dictionary<bool, string> StringValuesByBool { get; set; } = [];

    [Field(Id = 205)]
    public Dictionary<int, string> StringValuesByVarint32 { get; set; } = [];

    [Field(Id = 209)]
    public Dictionary<byte, string> StringValuesByUint8 { get; set; } = [];

    [Field(Id = 210)]
    public Dictionary<ushort, string> StringValuesByUint16 { get; set; } = [];

    [Field(Id = 212)]
    public Dictionary<uint, string> StringValuesByVarUint32 { get; set; } = [];

    [Field(Id = 214)]
    public Dictionary<ulong, string> StringValuesByVarUint64 { get; set; } = [];

    [Field(Id = 219)]
    public Dictionary<DateTimeOffset, string> StringValuesByTimestamp { get; set; } = [];

    [Field(Id = 220)]
    public Dictionary<TimeSpan, string> StringValuesByDuration { get; set; } = [];

    [Field(Id = 221)]
    public Dictionary<ExampleSkipState, string> StringValuesByEnum { get; set; } = [];

    [Field(Id = 223)]
    public Dictionary<string, Half?> MaybeFloat16ValuesByName { get; set; } = [];

    [Field(Id = 225)]
    public Dictionary<string, BFloat16?> MaybeBfloat16ValuesByName { get; set; } = [];

    [Field(Id = 226)]
    public Dictionary<string, byte[]> BytesValuesByName { get; set; } = [];

    [Field(Id = 227)]
    public Dictionary<string, DateOnly> DateValuesByName { get; set; } = [];

    [Field(Id = 228)]
    public Dictionary<string, ForyDecimal> DecimalValuesByName { get; set; } = [];

    [Field(Id = 229)]
    public Dictionary<string, ExampleSkipLeaf?> MessageValuesByName { get; set; } = [];

    [Field(Id = 230)]
    public Dictionary<string, ExampleSkipLeafUnion?> UnionValuesByName { get; set; } = [];

    [Field(Id = 900)]
    public SkipTailMarker ZzzTailMarker { get; set; } = new();
}

[ForyObject]
public sealed class ExampleMapsReader
{
    [Field(Id = 1)]
    public string AaaKnown { get; set; } = string.Empty;

    [Field(Id = 900)]
    public SkipTailMarker ZzzTailMarker { get; set; } = new();
}

public sealed class CompatibleFieldSkipperTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void CompatibleUnknownExampleDirectFieldsAreSkipped(bool trackRef)
    {
        ForyRuntime writer = CreateRuntime(trackRef);
        writer.Register<ExampleDirectWriter>(4410);

        ForyRuntime reader = CreateRuntime(trackRef);
        reader.Register<ExampleDirectReader>(4410);

        ExampleSkipLeaf sharedLeaf = new() { Name = "direct-leaf", Count = 7 };
        ExampleDirectWriter source = new()
        {
            AaaKnown = true,
            FixedInt32Value = 0x11223344,
            FixedInt64Value = 0x0102_0304_0506_0708L,
            TaggedInt64Value = (long)int.MaxValue + 42L,
            Uint8Value = byte.MaxValue,
            Uint16Value = ushort.MaxValue,
            FixedUint32Value = 0x55667788u,
            VarUint32Value = 3_000_000_000u,
            FixedUint64Value = 0x0102_0304_0506_0708UL,
            VarUint64Value = ulong.MaxValue - 11UL,
            TaggedUint64Value = (ulong)int.MaxValue + 99UL,
            BytesValue = [1, 2, 3, 4, 5],
            DateValue = new DateOnly(2026, 4, 24),
            TimestampValue = new DateTimeOffset(2026, 4, 24, 11, 12, 13, TimeSpan.Zero),
            DurationValue = TimeSpan.FromMinutes(95),
            DecimalValue = new ForyDecimal(new BigInteger(123456789), 4),
            EnumValue = ExampleSkipState.Done,
            MessageValue = sharedLeaf,
            UnionValue = ExampleSkipLeafUnion.Leaf(sharedLeaf),
            ZzzTailMarker = new SkipTailMarker { Label = "tail-direct", Order = 91 },
        };

        ExampleDirectReader reduced = reader.Deserialize<ExampleDirectReader>(writer.Serialize(source));
        Assert.True(reduced.AaaKnown);
        Assert.Equal("tail-direct", reduced.ZzzTailMarker.Label);
        Assert.Equal(91, reduced.ZzzTailMarker.Order);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void CompatibleUnknownExampleListFieldsAreSkipped(bool trackRef)
    {
        ForyRuntime writer = CreateRuntime(trackRef);
        writer.Register<ExampleListsWriter>(4411);

        ForyRuntime reader = CreateRuntime(trackRef);
        reader.Register<ExampleListsReader>(4411);

        ExampleSkipLeaf sharedLeaf = new() { Name = "list-leaf", Count = 3 };
        ExampleSkipLeafUnion sharedUnion = ExampleSkipLeafUnion.Leaf(sharedLeaf);
        ExampleListsWriter source = new()
        {
            AaaKnown = "known-list",
            FixedInt32List = [11, 22, 33],
            Varint64List = [44, 55, 66],
            Uint8List = [1, 2, 3],
            Uint16List = [600, 700, 800],
            VarUint32List = [4_000_000_000u, 9u],
            VarUint64List = [ulong.MaxValue - 5UL, 12UL],
            MaybeFloat16List = [(Half)1.5f, null, (Half)(-2.25f)],
            MaybeBfloat16List = [BFloat16.FromBits(0x3F80), null, BFloat16.FromBits(0x4020)],
            Float32List = [1.25f, -3.5f],
            BytesList = [[9, 8], null, [7, 6, 5]],
            DateList = [new DateOnly(2026, 4, 24), new DateOnly(2026, 4, 25)],
            TimestampList =
            [
                new DateTimeOffset(2026, 4, 24, 10, 0, 0, TimeSpan.Zero),
                new DateTimeOffset(2026, 4, 24, 10, 1, 2, TimeSpan.Zero),
            ],
            DurationList = [TimeSpan.FromSeconds(1), TimeSpan.FromHours(2)],
            DecimalList =
            [
                new ForyDecimal(new BigInteger(314159), 5),
                new ForyDecimal(new BigInteger(-271828), 5),
            ],
            EnumList = [ExampleSkipState.Ready, ExampleSkipState.Done],
            MessageList = [sharedLeaf, null, sharedLeaf],
            UnionList = [sharedUnion, null, sharedUnion],
            ZzzTailMarker = new SkipTailMarker { Label = "tail-list", Order = 92 },
        };

        ExampleListsReader reduced = reader.Deserialize<ExampleListsReader>(writer.Serialize(source));
        Assert.Equal("known-list", reduced.AaaKnown);
        Assert.Equal("tail-list", reduced.ZzzTailMarker.Label);
        Assert.Equal(92, reduced.ZzzTailMarker.Order);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void CompatibleUnknownExampleMapFieldsAreSkipped(bool trackRef)
    {
        ForyRuntime writer = CreateRuntime(trackRef);
        writer.Register<ExampleMapsWriter>(4412);

        ForyRuntime reader = CreateRuntime(trackRef);
        reader.Register<ExampleMapsReader>(4412);

        ExampleSkipLeaf sharedLeaf = new() { Name = "map-leaf", Count = 5 };
        ExampleSkipLeafUnion sharedUnion = ExampleSkipLeafUnion.Leaf(sharedLeaf);
        ExampleMapsWriter source = new()
        {
            AaaKnown = "known-map",
            StringValuesByBool = new Dictionary<bool, string> { [true] = "yes", [false] = "no" },
            StringValuesByVarint32 = new Dictionary<int, string> { [7] = "seven", [8] = "eight" },
            StringValuesByUint8 = new Dictionary<byte, string> { [1] = "one", [2] = "two" },
            StringValuesByUint16 = new Dictionary<ushort, string> { [300] = "three-hundred" },
            StringValuesByVarUint32 = new Dictionary<uint, string> { [4_000_000_000u] = "large-u32" },
            StringValuesByVarUint64 = new Dictionary<ulong, string> { [ulong.MaxValue - 3UL] = "large-u64" },
            StringValuesByTimestamp =
                new Dictionary<DateTimeOffset, string>
                {
                    [new DateTimeOffset(2026, 4, 24, 12, 0, 0, TimeSpan.Zero)] = "noon",
                },
            StringValuesByDuration = new Dictionary<TimeSpan, string> { [TimeSpan.FromMinutes(3)] = "three-minutes" },
            StringValuesByEnum = new Dictionary<ExampleSkipState, string> { [ExampleSkipState.Done] = "done" },
            MaybeFloat16ValuesByName = new Dictionary<string, Half?> { ["first"] = (Half)1.25f, ["second"] = null },
            MaybeBfloat16ValuesByName =
                new Dictionary<string, BFloat16?> { ["first"] = BFloat16.FromBits(0x3FC0), ["second"] = null },
            BytesValuesByName = new Dictionary<string, byte[]> { ["blob"] = [4, 5, 6] },
            DateValuesByName = new Dictionary<string, DateOnly> { ["today"] = new DateOnly(2026, 4, 24) },
            DecimalValuesByName = new Dictionary<string, ForyDecimal> { ["pi"] = new ForyDecimal(new BigInteger(314159), 5) },
            MessageValuesByName = new Dictionary<string, ExampleSkipLeaf?> { ["first"] = sharedLeaf, ["second"] = sharedLeaf },
            UnionValuesByName =
                new Dictionary<string, ExampleSkipLeafUnion?> { ["first"] = sharedUnion, ["second"] = sharedUnion },
            ZzzTailMarker = new SkipTailMarker { Label = "tail-map", Order = 93 },
        };

        ExampleMapsReader reduced = reader.Deserialize<ExampleMapsReader>(writer.Serialize(source));
        Assert.Equal("known-map", reduced.AaaKnown);
        Assert.Equal("tail-map", reduced.ZzzTailMarker.Label);
        Assert.Equal(93, reduced.ZzzTailMarker.Order);
    }

    private static ForyRuntime CreateRuntime(bool trackRef)
    {
        ForyRuntime runtime = ForyRuntime.Builder().Compatible(true).TrackRef(trackRef).Build();
        runtime.Register<ExampleSkipState>(4400);
        runtime.Register<ExampleSkipLeaf>(4401);
        runtime.Register<ExampleSkipLeafUnion>(4402);
        runtime.Register<SkipTailMarker>(4403);
        return runtime;
    }
}
