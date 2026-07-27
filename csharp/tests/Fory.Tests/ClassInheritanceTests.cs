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
using System.Runtime.CompilerServices;
using Apache.Fory;
using Fory.ExternalTypes;
using Fory.InheritanceConsumerA;
using Fory.InheritanceConsumerB;
using Fory.InheritanceProviders;
using ForyRuntime = Apache.Fory.Fory;

namespace Apache.Fory.Tests;

[ForyStruct]
internal abstract class InheritedRoot
{
    [ForyField(1)]
    private int PrivateValue { get; set; }

    [ForyField(2)]
    protected string ProtectedText { get; set; } = string.Empty;

    [ForyField(3)]
    public int PublicValue;

    [ForyField(4)]
    public InheritedLeaf? Self { get; set; }

    private readonly long _cache = 41;

    public void SetRootState(int privateValue, string protectedText)
    {
        PrivateValue = privateValue;
        ProtectedText = protectedText;
    }

    public (int PrivateValue, string ProtectedText, long Cache) RootState()
    {
        return (PrivateValue, ProtectedText, _cache);
    }
}

[ForyStruct]
internal class InheritedMiddle : InheritedRoot
{
    [ForyField(5)]
    public virtual int VirtualValue { get; set; }
}

[ForyStruct]
internal sealed class InheritedLeaf : InheritedMiddle
{
    public override int VirtualValue { get; set; }

    [ForyField(6)]
    public string LeafValue { get; set; } = string.Empty;
}

[ForyStruct]
internal abstract class HiddenMemberBase
{
    [ForyField(1)]
    private int Value;

    public int ReadBaseValue()
    {
        return Value;
    }

    public void SetBaseValue(int value)
    {
        Value = value;
    }
}

[ForyStruct]
internal sealed class HiddenMemberLeaf : HiddenMemberBase
{
    [ForyField(2)]
    private int Value;

    public int ReadLeafValue()
    {
        return Value;
    }

    public void SetLeafValue(int value)
    {
        Value = value;
    }
}

[ForyStruct]
internal sealed class StorageProjection(int seed)
{
    private readonly long _cache = 71;

    public StorageProjection()
        : this(0)
    {
    }

    [ForyField(1)]
    public int Value { get; set; }

    [ForyField(2)]
    public int Alias
    {
        get => Value;
        set => Value = value;
    }

    public event Action? Changed;

    public static long StaticValue = 1;

    public const int ConstantValue = 73;

    public (int Seed, long Cache) ReadStorageState()
    {
        return (seed, _cache);
    }

    public void RaiseChanged()
    {
        Changed?.Invoke();
    }
}

internal sealed class InvalidPrivateAbiTarget
{
    private int Value { get; set; }

    public void SetValue(int value)
    {
        Value = value;
    }
}

[ForyStruct]
internal abstract class AccessorBase
{
    [ForyField(1)]
    public int MixedAccess { get; private set; }

    [ForyField(2)]
    protected virtual string VirtualText { get; set; } = string.Empty;

    public void SetAccessorState(int mixedAccess, string virtualText)
    {
        MixedAccess = mixedAccess;
        VirtualText = virtualText;
    }

    public (int MixedAccess, string VirtualText) ReadAccessorState()
    {
        return (MixedAccess, VirtualText);
    }
}

[ForyStruct]
internal sealed class AccessorLeaf : AccessorBase
{
    protected override string VirtualText { get; set; } = string.Empty;

    [ForyField(3)]
    public int LeafValue;
}

[ForyStruct]
internal struct InheritedValue
{
    [ForyField(1)]
    public int Number;
}

[ForyEnum]
internal enum InheritedStatus : ushort
{
    Unknown = 0,
    Ready = 7,
}

[ForyStruct]
internal sealed class SharedChild
{
    [ForyField(1)]
    public string Name { get; set; } = string.Empty;
}

[ForyStruct]
internal abstract class RichBase
{
    [ForyField(1)]
    public int? Optional { get; set; }

    [ForyField(2)]
    public List<int> Values { get; set; } = [];

    [ForyField(3)]
    public Dictionary<string, int> ValuesByName { get; set; } = [];

    [ForyField(4)]
    public InheritedValue StructValue { get; set; }

    [ForyField(5)]
    public InheritedStatus Status { get; set; }

    [ForyField(6)]
    public SharedChild? BaseChild { get; set; }

    [ForyField(7)]
    public RichLeaf? Self { get; set; }
}

[ForyStruct]
internal sealed class RichLeaf : RichBase
{
    [ForyField(8)]
    public SharedChild? LeafChild { get; set; }
}

[ForyStruct]
internal abstract class EvolutionBaseV1
{
    [ForyField(1)]
    public int BaseValue { get; set; }
}

[ForyStruct]
internal sealed class EvolutionLeafV1 : EvolutionBaseV1
{
    [ForyField(3)]
    public int LeafValue { get; set; }
}

[ForyStruct]
internal abstract class EvolutionBaseV2
{
    [ForyField(1)]
    public int BaseValue { get; set; }

    [ForyField(2)]
    public string AddedBaseValue { get; set; } = "default";
}

[ForyStruct]
internal sealed class EvolutionLeafV2 : EvolutionBaseV2
{
    [ForyField(3)]
    public int LeafValue { get; set; }
}

[ForyStruct]
internal sealed class WireStableA
{
    [ForyField(1)]
    public int Value { get; set; }
}

[ForyStruct]
internal sealed class WireStableB
{
    private readonly long _cache = 211;

    [ForyField(1)]
    public int Value { get; set; }

    public long ReadCache()
    {
        return _cache;
    }
}

internal unsafe struct FixedStorage
{
    public fixed byte Bytes[7];
}

[ForyStruct]
internal unsafe sealed class StorageWidths
{
    private int* _pointer;
    private InheritedStatus _status;
    private int? _optional;
    private FixedStorage _fixedStorage;

    [ForyField(1)]
    public int Value;

    public StorageWidths()
    {
        _pointer = null;
        _status = InheritedStatus.Unknown;
        _optional = null;
        _fixedStorage = default;
    }

    public (nint Pointer, InheritedStatus Status, int? Optional)
        ReadScalarStorage()
    {
        return ((nint)_pointer, _status, _optional);
    }

    public FixedStorage ReadFixedStorage()
    {
        return _fixedStorage;
    }
}

[ForyStruct(Target = typeof(ExternalGenericBase<int>), BaseOnly = true)]
internal abstract class ExternalGenericBaseSerializer
{
    [ForyField(1)]
    public abstract int Value { get; }

    [ForyField(
        Ignore = true,
        TargetDeclaringType = typeof(ExternalGenericBase<int>),
        TargetMemberName = "<Value>k__BackingField",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract int ValueStorage { get; }
}

[ForyStruct]
internal sealed class ExternalGenericLeaf : ExternalGenericBase<int>
{
    [ForyField(2)]
    public string LeafValue { get; set; } = string.Empty;
}

[ForyStruct(Target = typeof(ExternalNonConstructibleBase), BaseOnly = true)]
internal abstract class ExternalNonConstructibleSerializer
{
    [ForyField(1)]
    public abstract int BaseValue { get; }

    [ForyField(
        Ignore = true,
        TargetDeclaringType = typeof(ExternalNonConstructibleBase),
        TargetMemberName = "<ConstructorSeed>k__BackingField",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract int ConstructorSeedStorage { get; }
}

[ForyStruct]
internal sealed class ExternalConstructibleLeaf : ExternalNonConstructibleBase
{
    public ExternalConstructibleLeaf()
        : base(223)
    {
    }

    [ForyField(2)]
    public int LeafValue;
}

[ForyStruct(Target = typeof(InvalidPrivateAbiTarget))]
internal abstract class InvalidPrivateAbiSerializer
{
    [ForyField(
        1,
        TargetDeclaringType = typeof(InvalidPrivateAbiTarget),
        TargetMemberName = "Missing",
        TargetMemberKind = ForyTargetMemberKind.Property)]
    public abstract int Value { get; }

    [ForyField(
        Ignore = true,
        TargetDeclaringType = typeof(InvalidPrivateAbiTarget),
        TargetMemberName = "<Value>k__BackingField",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract int ValueStorage { get; }
}

public sealed class ClassInheritanceTests
{
    private static readonly long ObjectOwnerBytes =
        IntPtr.Size + IntPtr.Size + sizeof(int);

    [Theory]
    [InlineData(false, false)]
    [InlineData(false, true)]
    [InlineData(true, false)]
    [InlineData(true, true)]
    public void FlattenedHierarchyRoundTrips(bool compatible, bool trackRef)
    {
        ForyRuntime fory = ForyRuntime.Builder()
            .Compatible(compatible)
            .TrackRef(trackRef)
            .Build()
            .Register<InheritedLeaf>(6401);
        InheritedLeaf value = new()
        {
            PublicValue = 13,
            VirtualValue = 17,
            LeafValue = "leaf",
        };
        value.SetRootState(7, "root");
        if (trackRef)
        {
            value.Self = value;
        }

        InheritedLeaf decoded = fory.Deserialize<InheritedLeaf>(fory.Serialize(value));

        Assert.Equal((7, "root", 41L), decoded.RootState());
        Assert.Equal(13, decoded.PublicValue);
        Assert.Equal(17, decoded.VirtualValue);
        Assert.Equal("leaf", decoded.LeafValue);
        if (trackRef)
        {
            Assert.Same(decoded, decoded.Self);
        }
        else
        {
            Assert.Null(decoded.Self);
        }
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void ExternalPrivatePrefixRoundTrips(bool compatible)
    {
        ForyRuntime fory = ForyRuntime.Builder()
            .Compatible(compatible)
            .Build()
            .Register<ExternalLeaf>(6402);
        ExternalLeaf value = new()
        {
            PublicValue = 31,
            MiddleValue = "middle",
            LeafValue = 43,
        };
        value.SetPrivateState(23, "vendor");

        ExternalLeaf decoded = fory.Deserialize<ExternalLeaf>(fory.Serialize(value));

        Assert.Equal((23L, "vendor", 29), decoded.ReadPrivateState());
        Assert.Equal(31, decoded.PublicValue);
        Assert.Equal("middle", decoded.MiddleValue);
        Assert.Equal(43, decoded.LeafValue);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void ReferencedOrdinaryHierarchyRoundTrips(bool compatible)
    {
        ForyRuntime fory = ForyRuntime.Builder()
            .Compatible(compatible)
            .Build()
            .Register<CrossAssemblyLeaf>(6403);
        CrossAssemblyLeaf value = new()
        {
            PublicBaseValue = 47,
            MiddleValue = 53,
            LeafValue = "leaf",
        };
        value.SetBaseState(59, "base", 61);

        CrossAssemblyLeaf decoded =
            fory.Deserialize<CrossAssemblyLeaf>(fory.Serialize(value));

        Assert.Equal((59, "base", 61, 37), decoded.ReadBaseState());
        Assert.Equal(47, decoded.PublicBaseValue);
        Assert.Equal(53, decoded.MiddleValue);
        Assert.Equal("leaf", decoded.LeafValue);
    }

    [Fact]
    public void SharedExternalProviderSupportsAnotherConsumer()
    {
        ForyRuntime fory = ForyRuntime.Builder()
            .Build()
            .Register<SecondExternalLeaf>(6404);
        SecondExternalLeaf value = new()
        {
            PublicValue = 61,
            LeafValue = true,
        };
        value.SetPrivateState(67, "shared");

        SecondExternalLeaf decoded =
            fory.Deserialize<SecondExternalLeaf>(fory.Serialize(value));

        Assert.Equal((67L, "shared", 29), decoded.ReadPrivateState());
        Assert.Equal(61, decoded.PublicValue);
        Assert.True(decoded.LeafValue);
    }

    [Fact]
    public void GeneratedHierarchyBudgetsAreCumulative()
    {
        Assert.Equal(24, HierarchyShallowBytes(typeof(InheritedRoot)));
        Assert.Equal(28, HierarchyShallowBytes(typeof(InheritedMiddle)));
        Assert.Equal(36, HierarchyShallowBytes(typeof(InheritedLeaf)));

        Assert.Equal(24, HierarchyShallowBytes(typeof(SharedOrdinaryBase)));
        Assert.Equal(28, HierarchyShallowBytes(typeof(CrossAssemblyMiddle)));
        Assert.Equal(32, HierarchyShallowBytes(typeof(CrossAssemblyLeaf)));

        Assert.Equal(
            20,
            HierarchyShallowBytes(
                typeof(ExternalPrivateDerived),
                typeof(SharedExternalHierarchy).Assembly));
        Assert.Equal(24, HierarchyShallowBytes(typeof(ExternalMiddle)));
        Assert.Equal(32, HierarchyShallowBytes(typeof(ExternalLeaf)));
        Assert.Equal(21, HierarchyShallowBytes(typeof(SecondExternalLeaf)));
    }

    [Fact]
    public void FlattenedTypeMetaUsesOneOverrideSlot()
    {
        short?[] fieldIds = new TypeResolver()
            .GetTypeInfo<InheritedLeaf>()
            .TypeMetaFields(false)
            .Select(field => field.FieldId)
            .ToArray();

        Assert.Equal(
            new short?[] { 1, 3, 5, 2, 4, 6 },
            fieldIds);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void HiddenMembersUseExactDeclaringOwners(bool compatible)
    {
        HiddenMemberLeaf value = new();
        value.SetBaseValue(113);
        value.SetLeafValue(127);
        ForyRuntime fory = ForyRuntime.Builder()
            .Compatible(compatible)
            .Build()
            .Register<HiddenMemberLeaf>(6409);

        HiddenMemberLeaf decoded =
            fory.Deserialize<HiddenMemberLeaf>(fory.Serialize(value));

        Assert.Equal(113, decoded.ReadBaseValue());
        Assert.Equal(127, decoded.ReadLeafValue());
        Assert.Equal(8, HierarchyShallowBytes(typeof(HiddenMemberLeaf)));
    }

    [Theory]
    [InlineData(false, false)]
    [InlineData(false, true)]
    [InlineData(true, false)]
    [InlineData(true, true)]
    public void MixedAccessAndVirtualOverrideRoundTrip(
        bool compatible,
        bool trackRef)
    {
        AccessorLeaf value = new()
        {
            LeafValue = 137,
        };
        value.SetAccessorState(131, "override");
        ForyRuntime fory = ForyRuntime.Builder()
            .Compatible(compatible)
            .TrackRef(trackRef)
            .Build()
            .Register<AccessorLeaf>(6410);

        AccessorLeaf decoded =
            fory.Deserialize<AccessorLeaf>(fory.Serialize(value));

        Assert.Equal((131, "override"), decoded.ReadAccessorState());
        Assert.Equal(137, decoded.LeafValue);
        Assert.Equal(16, HierarchyShallowBytes(typeof(AccessorLeaf)));
    }

    [Theory]
    [InlineData(false, false)]
    [InlineData(false, true)]
    [InlineData(true, false)]
    [InlineData(true, true)]
    public void InheritedCompositeMembersRoundTrip(
        bool compatible,
        bool trackRef)
    {
        SharedChild shared = new()
        {
            Name = "shared",
        };
        RichLeaf value = new()
        {
            Optional = 139,
            Values = [149, 151],
            ValuesByName = { ["value"] = 157 },
            StructValue = new InheritedValue
            {
                Number = 163,
            },
            Status = InheritedStatus.Ready,
            BaseChild = shared,
            LeafChild = shared,
        };
        if (trackRef)
        {
            value.Self = value;
        }

        ForyRuntime fory = ForyRuntime.Builder()
            .Compatible(compatible)
            .TrackRef(trackRef)
            .Build()
            .Register<RichLeaf>(6411)
            .Register<InheritedValue>(6412)
            .Register<InheritedStatus>(6413)
            .Register<SharedChild>(6414);
        RichLeaf decoded = fory.Deserialize<RichLeaf>(fory.Serialize(value));

        Assert.Equal(139, decoded.Optional);
        Assert.Equal([149, 151], decoded.Values);
        Assert.Equal(157, decoded.ValuesByName["value"]);
        Assert.Equal(163, decoded.StructValue.Number);
        Assert.Equal(InheritedStatus.Ready, decoded.Status);
        Assert.Equal("shared", decoded.BaseChild!.Name);
        Assert.Equal("shared", decoded.LeafChild!.Name);
        if (trackRef)
        {
            Assert.Same(decoded, decoded.Self);
            Assert.Same(decoded.BaseChild, decoded.LeafChild);
        }
        else
        {
            Assert.Null(decoded.Self);
            Assert.NotSame(decoded.BaseChild, decoded.LeafChild);
        }
    }

    [Fact]
    public void CompatibleSchemaEvolvesBaseMembers()
    {
        ForyRuntime newWriter = ForyRuntime.Builder()
            .Compatible(true)
            .Build()
            .Register<EvolutionLeafV2>(6415);
        ForyRuntime oldReader = ForyRuntime.Builder()
            .Compatible(true)
            .Build()
            .Register<EvolutionLeafV1>(6415);
        EvolutionLeafV1 oldValue = oldReader.Deserialize<EvolutionLeafV1>(
            newWriter.Serialize(new EvolutionLeafV2
            {
                BaseValue = 167,
                AddedBaseValue = "added",
                LeafValue = 173,
            }));

        Assert.Equal(167, oldValue.BaseValue);
        Assert.Equal(173, oldValue.LeafValue);

        ForyRuntime oldWriter = ForyRuntime.Builder()
            .Compatible(true)
            .Build()
            .Register<EvolutionLeafV1>(6415);
        ForyRuntime newReader = ForyRuntime.Builder()
            .Compatible(true)
            .Build()
            .Register<EvolutionLeafV2>(6415);
        EvolutionLeafV2 newValue = newReader.Deserialize<EvolutionLeafV2>(
            oldWriter.Serialize(new EvolutionLeafV1
            {
                BaseValue = 179,
                LeafValue = 181,
            }));

        Assert.Equal(179, newValue.BaseValue);
        Assert.Equal("default", newValue.AddedBaseValue);
        Assert.Equal(181, newValue.LeafValue);
    }

    [Fact]
    public void NonWireStorageDoesNotChangeWireSchema()
    {
        ForyRuntime first = ForyRuntime.Builder()
            .Compatible(false)
            .Build()
            .Register<WireStableA>(6416);
        ForyRuntime second = ForyRuntime.Builder()
            .Compatible(false)
            .Build()
            .Register<WireStableB>(6416);

        Assert.Equal(
            first.Serialize(new WireStableA { Value = 191 }),
            second.Serialize(new WireStableB { Value = 191 }));
        Assert.Equal(
            new TypeResolver()
                .GetTypeInfo<WireStableA>()
                .TypeMetaFields(false),
            new TypeResolver()
                .GetTypeInfo<WireStableB>()
                .TypeMetaFields(false));
        Assert.Equal(4, HierarchyShallowBytes(typeof(WireStableA)));
        Assert.Equal(12, HierarchyShallowBytes(typeof(WireStableB)));
    }

    [Fact]
    public void StorageWidthsUsePhysicalFieldTypes()
    {
        long shallowBytes =
            sizeof(int) +
            IntPtr.Size +
            sizeof(ushort) +
            Unsafe.SizeOf<int?>() +
            Unsafe.SizeOf<FixedStorage>();
        Assert.Equal(shallowBytes, HierarchyShallowBytes(typeof(StorageWidths)));

        ForyRuntime writer = ForyRuntime.Builder().Compatible(false).Build()
            .Register<StorageWidths>(6417);
        byte[] bytes = writer.Serialize(new StorageWidths { Value = 193 });
        long required = ObjectOwnerBytes + shallowBytes;
        ForyRuntime tooSmall = ForyRuntime.Builder()
            .Compatible(false)
            .MaxGraphMemoryBytes(required - 1)
            .Build()
            .Register<StorageWidths>(6417);
        Assert.Throws<InvalidDataException>(
            () => tooSmall.Deserialize<StorageWidths>(bytes));

        ForyRuntime exact = ForyRuntime.Builder()
            .Compatible(false)
            .MaxGraphMemoryBytes(required)
            .Build()
            .Register<StorageWidths>(6417);
        Assert.Equal(193, exact.Deserialize<StorageWidths>(bytes).Value);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void ClosedGenericExternalPrefixRoundTrips(bool compatible)
    {
        ForyRuntime fory = ForyRuntime.Builder()
            .Compatible(compatible)
            .Build()
            .Register<ExternalGenericLeaf>(6418);
        ExternalGenericLeaf value = new()
        {
            Value = 197,
            LeafValue = "generic",
        };

        ExternalGenericLeaf decoded =
            fory.Deserialize<ExternalGenericLeaf>(fory.Serialize(value));

        Assert.Equal(197, decoded.Value);
        Assert.Equal("generic", decoded.LeafValue);
        Assert.Equal(8, HierarchyShallowBytes(typeof(ExternalGenericLeaf)));
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void NonConstructibleExternalBaseUsesChildConstructor(bool compatible)
    {
        ForyRuntime fory = ForyRuntime.Builder()
            .Compatible(compatible)
            .Build()
            .Register<ExternalConstructibleLeaf>(6419);
        ExternalConstructibleLeaf decoded =
            fory.Deserialize<ExternalConstructibleLeaf>(
                fory.Serialize(new ExternalConstructibleLeaf
                {
                    BaseValue = 199,
                    LeafValue = 211,
                }));

        Assert.Equal(199, decoded.BaseValue);
        Assert.Equal(211, decoded.LeafValue);
        Assert.Equal(223, decoded.ConstructorSeed);
        Assert.Equal(12, HierarchyShallowBytes(typeof(ExternalConstructibleLeaf)));
    }

    [Fact]
    public void InheritedStorageUsesExactGraphLimit()
    {
        InheritedLeaf value = new()
        {
            PublicValue = 79,
            VirtualValue = 83,
            LeafValue = "budget",
        };
        value.SetRootState(89, "root");
        value.Self = value;
        ForyRuntime writer = ForyRuntime.Builder()
            .Compatible(false)
            .TrackRef(true)
            .Build()
            .Register<InheritedLeaf>(6405);
        byte[] bytes = writer.Serialize(value);
        long required = ObjectOwnerBytes + 36;

        ForyRuntime tooSmall = ForyRuntime.Builder()
            .Compatible(false)
            .TrackRef(true)
            .MaxGraphMemoryBytes(required - 1)
            .Build()
            .Register<InheritedLeaf>(6405);
        Assert.Throws<InvalidDataException>(
            () => tooSmall.Deserialize<InheritedLeaf>(bytes));

        ForyRuntime exact = ForyRuntime.Builder()
            .Compatible(false)
            .TrackRef(true)
            .MaxGraphMemoryBytes(required)
            .Build()
            .Register<InheritedLeaf>(6405);
        InheritedLeaf decoded = exact.Deserialize<InheritedLeaf>(bytes);
        Assert.Equal(79, decoded.PublicValue);
        Assert.Same(decoded, decoded.Self);
    }

    [Fact]
    public void ExternalPrefixUsesExactGraphLimit()
    {
        ExternalLeaf value = new()
        {
            PublicValue = 97,
            MiddleValue = "external",
            LeafValue = 101,
        };
        value.SetPrivateState(103, "private");
        ForyRuntime writer = ForyRuntime.Builder().Compatible(false).Build()
            .Register<ExternalLeaf>(6406);
        byte[] bytes = writer.Serialize(value);
        long required = ObjectOwnerBytes + 32;

        ForyRuntime tooSmall = ForyRuntime.Builder()
            .Compatible(false)
            .MaxGraphMemoryBytes(required - 1)
            .Build()
            .Register<ExternalLeaf>(6406);
        Assert.Throws<InvalidDataException>(
            () => tooSmall.Deserialize<ExternalLeaf>(bytes));

        ForyRuntime exact = ForyRuntime.Builder()
            .Compatible(false)
            .MaxGraphMemoryBytes(required)
            .Build()
            .Register<ExternalLeaf>(6406);
        Assert.Equal(
            (103L, "private", 29),
            exact.Deserialize<ExternalLeaf>(bytes).ReadPrivateState());
    }

    [Fact]
    public void DirectStorageProjectionIsIndependentOfWireMembers()
    {
        Assert.Equal(20, HierarchyShallowBytes(typeof(StorageProjection)));
        StorageProjection value = new()
        {
            Value = 107,
        };
        ForyRuntime writer = ForyRuntime.Builder().Compatible(false).Build()
            .Register<StorageProjection>(6407);
        byte[] bytes = writer.Serialize(value);
        long required = ObjectOwnerBytes + 20;

        ForyRuntime tooSmall = ForyRuntime.Builder()
            .Compatible(false)
            .MaxGraphMemoryBytes(required - 1)
            .Build()
            .Register<StorageProjection>(6407);
        Assert.Throws<InvalidDataException>(
            () => tooSmall.Deserialize<StorageProjection>(bytes));

        ForyRuntime exact = ForyRuntime.Builder()
            .Compatible(false)
            .MaxGraphMemoryBytes(required)
            .Build()
            .Register<StorageProjection>(6407);
        StorageProjection decoded =
            exact.Deserialize<StorageProjection>(bytes);
        Assert.Equal(107, decoded.Value);
        Assert.Equal((0, 71L), decoded.ReadStorageState());
    }

    [Fact]
    public void ThirdPartyPrivateAbiIsPinned()
    {
        const BindingFlags flags =
            BindingFlags.Instance |
            BindingFlags.NonPublic |
            BindingFlags.DeclaredOnly;
        Assert.Equal(
            typeof(long),
            typeof(ExternalPrivateBase).GetField("_identifier", flags)!.FieldType);
        Assert.Equal(
            typeof(string),
            typeof(ExternalPrivateBase)
                .GetField("<Secret>k__BackingField", flags)!.FieldType);
        Assert.Equal(
            typeof(int),
            typeof(ExternalPrivateBase).GetField("_cache", flags)!.FieldType);
    }

    [Fact]
    public void PrivateAbiMismatchHasNoFallback()
    {
        ForyRuntime fory = ForyRuntime.Builder()
            .Compatible(false)
            .Build()
            .Register<InvalidPrivateAbiTarget>(6408);
        InvalidPrivateAbiTarget value = new();
        value.SetValue(109);

        Assert.Throws<MissingMethodException>(() => fory.Serialize(value));
    }

    private static long HierarchyShallowBytes(
        Type target,
        Assembly? providerAssembly = null)
    {
        Type provider = (providerAssembly ?? target.Assembly)
            .GetTypes()
            .Single(type =>
                type.GetCustomAttribute<ForyGeneratedSerializerApiAttribute>()
                    is { TargetType: var providerTarget } &&
                providerTarget == target);
        return (long)provider.GetField(
            "HierarchyShallowBytes",
            BindingFlags.Public | BindingFlags.Static)!.GetValue(null)!;
    }
}
