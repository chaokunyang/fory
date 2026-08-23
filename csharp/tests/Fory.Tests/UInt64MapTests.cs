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
        const ulong secondSeed = 0xE72B_9C5A_6E3D_F195;
        ulong[] keys = LegacyClusterKeys(65);
        UInt64Map<int> first = new(
            initialCapacity: Capacity,
            placementSeed: firstSeed);
        UInt64Map<int> second = new(
            initialCapacity: Capacity,
            placementSeed: secondSeed);

        int[] firstSlots = keys.Select(key => Placement(first, key)).ToArray();
        int[] secondSlots = keys.Select(key => Placement(second, key)).ToArray();

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
