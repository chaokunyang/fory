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

using System.Reflection;
using Apache.Fory;

namespace Apache.Fory.Tests;

public sealed class UInt64MapTests
{
    private const int Capacity = 128;
    private const ulong GoldenRatio = 0x9E3779B97F4A7C15;

    [Fact]
    public void SeededPlacementDispersesCluster()
    {
        const ulong firstSeed = 0x18D7_43A5_91C2_0E6B;
        const ulong secondSeed = 0xE72B_9C5A_6E3D_F194;
        ulong[] keys = LegacyClusterKeys(65);
        UInt64Map<int> first = new(
            initialCapacity: Capacity,
            placementSeed: firstSeed);
        UInt64Map<int> second = new(
            initialCapacity: Capacity,
            placementSeed: secondSeed);

        int[] firstSlots = keys.Select(key => Placement(first, key)).ToArray();
        int[] secondSlots = keys.Select(key => Placement(second, key)).ToArray();

        Assert.Equal(
            keys.Select(key => SeededPlacement(key, firstSeed)).ToArray(),
            firstSlots);
        Assert.Equal(
            keys.Select(key => SeededPlacement(key, secondSeed)).ToArray(),
            secondSlots);
        Assert.False(firstSlots.SequenceEqual(secondSlots));
        Assert.True(firstSlots.Take(64).Distinct().Count() > 32);
        Assert.True(secondSlots.Take(64).Distinct().Count() > 32);
        Assert.InRange(SimulateMissProbes(firstSlots), 0, 8);
        Assert.InRange(SimulateMissProbes(secondSlots), 0, 8);

        for (int i = 0; i < 64; i++)
        {
            first.Set(keys[i], i);
            second.Set(keys[i], i);
        }

        for (int i = 0; i < 64; i++)
        {
            Assert.True(first.TryGetValue(keys[i], out int firstValue));
            Assert.Equal(i, firstValue);
            Assert.True(second.TryGetValue(keys[i], out int secondValue));
            Assert.Equal(i, secondValue);
        }

        Assert.False(first.TryGetValue(keys[64], out _));
        Assert.False(second.TryGetValue(keys[64], out _));
    }

    [Fact]
    public void TypeMetaCachesPlaceIndependently()
    {
        Config config = Fory.Builder().Build().Config;
        ReadContext firstContext = new(
            new ByteReader(Array.Empty<byte>()),
            new TypeResolver(),
            config);
        ReadContext secondContext = new(
            new ByteReader(Array.Empty<byte>()),
            new TypeResolver(),
            config);
        UInt64Map<TypeMeta> first = TypeMetaCache(firstContext);
        UInt64Map<TypeMeta> second = TypeMetaCache(secondContext);
        TypeMeta typeMeta = new(
            (uint)TypeId.Struct,
            903,
            MetaString.Empty('.', '_'),
            MetaString.Empty('$', '_'),
            registerByName: false,
            []);
        ulong[] keys = LegacyClusterKeys(65);
        for (int i = 0; i < 64; i++)
        {
            firstContext.StoreExactLocalTypeMeta(keys[i], typeMeta);
            secondContext.StoreExactLocalTypeMeta(keys[i], typeMeta);
        }

        int[] firstSlots = keys.Select(key => Placement(first, key)).ToArray();
        int[] secondSlots = keys.Select(key => Placement(second, key)).ToArray();
        ulong firstSeed = PlacementSeed(first);
        ulong secondSeed = PlacementSeed(second);

        Assert.NotEqual(0UL, firstSeed);
        Assert.NotEqual(0UL, secondSeed);
        Assert.NotEqual(firstSeed, secondSeed);
        Assert.Equal(
            keys.Select(key => SeededPlacement(key, firstSeed)).ToArray(),
            firstSlots);
        Assert.Equal(
            keys.Select(key => SeededPlacement(key, secondSeed)).ToArray(),
            secondSlots);
    }

    [Fact]
    public void TypeMetaCacheHitSurvivesReset()
    {
        const ulong header = 0xffUL;
        TypeMeta typeMeta = new(
            (uint)TypeId.Struct,
            902,
            MetaString.Empty('.', '_'),
            MetaString.Empty('$', '_'),
            registerByName: false,
            []);
        ByteWriter writer = new();
        writer.WriteVarUInt32(0);
        writer.WriteUInt64(header);
        writer.WriteVarUInt32(0);
        writer.WriteBytes(new byte[0xff]);
        writer.WriteUInt8(0x7b);

        Config config = Fory.Builder().Compatible(false).Build().Config;
        ReadContext context = new(
            new ByteReader(Array.Empty<byte>()),
            new TypeResolver(),
            config);
        context.StoreRemoteTypeMeta(header, typeMeta);
        context.ResetFor(new ByteReader(writer.ToArray()));

        Assert.Same(typeMeta, context.ReadTypeMeta());
        Assert.Equal(0x7b, context.Reader.ReadUInt8());
    }

    private static ulong[] LegacyClusterKeys(int count)
    {
        ulong[] keys = new ulong[count];
        int found = 0;
        for (ulong key = 0; found < count; key++)
        {
            if ((unchecked(key * GoldenRatio) >> 57) == 0)
            {
                keys[found++] = key;
            }
        }

        return keys;
    }

    private static int Placement<TValue>(UInt64Map<TValue> map, ulong key)
    {
        MethodInfo? method = typeof(UInt64Map<TValue>).GetMethod(
            "Place",
            BindingFlags.Instance | BindingFlags.NonPublic);
        Assert.NotNull(method);
        return Assert.IsType<int>(method.Invoke(map, [key]));
    }

    private static UInt64Map<TypeMeta> TypeMetaCache(ReadContext context)
    {
        FieldInfo? field = typeof(ReadContext).GetField(
            "_typeMetasByHeader",
            BindingFlags.Instance | BindingFlags.NonPublic);
        Assert.NotNull(field);
        return Assert.IsType<UInt64Map<TypeMeta>>(field.GetValue(context));
    }

    private static ulong PlacementSeed<TValue>(UInt64Map<TValue> map)
    {
        FieldInfo? field = typeof(UInt64Map<TValue>).GetField(
            "_placementSeed",
            BindingFlags.Instance | BindingFlags.NonPublic);
        Assert.NotNull(field);
        return Assert.IsType<ulong>(field.GetValue(map));
    }

    private static int SeededPlacement(ulong key, ulong seed)
    {
        const ulong mix1 = 0xBF58476D1CE4E5B9;
        const ulong mix2 = 0x94D049BB133111EB;
        ulong value = key ^ seed;
        value = unchecked((value ^ (value >> 30)) * mix1);
        value = unchecked((value ^ (value >> 27)) * mix2);
        return (int)((value ^ (value >> 31)) >> 57);
    }

    private static int SimulateMissProbes(int[] initialSlots)
    {
        bool[] occupied = new bool[Capacity];
        for (int i = 0; i < initialSlots.Length - 1; i++)
        {
            int slot = initialSlots[i];
            while (occupied[slot])
            {
                slot = (slot + 1) & (Capacity - 1);
            }

            occupied[slot] = true;
        }

        int probes = 0;
        int missSlot = initialSlots[^1];
        while (occupied[missSlot])
        {
            probes++;
            missSlot = (missSlot + 1) & (Capacity - 1);
        }

        return probes;
    }
}
