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
using Fory.ExternalTypes;
using S = Apache.Fory.Schema.Types;

namespace Apache.Fory.Tests;

[ForyStruct(Target = typeof(ExternalUser))]
internal abstract class ExternalUserSerializer
{
    [ForyField(1)]
    public abstract int Id { get; }

    [ForyField(2)]
    public abstract string Name { get; }

    [ForyField(3)]
    public abstract ExternalUser? Friend { get; }

    [ForyField(4)]
    public abstract List<ExternalUser> Links { get; }
}

[ForyStruct(Target = typeof(ExternalPoint))]
internal abstract class ExternalPointSerializer
{
    [ForyField(1)]
    public abstract int X { get; }

    [ForyField(2)]
    public abstract int Y { get; }
}

[ForyEnum(Target = typeof(ExternalStatus))]
internal static class ExternalStatusSerializer
{
}

[ForyStruct(Target = typeof(ExternalBox<string>))]
internal abstract class ExternalStringBoxSerializer
{
    [ForyField(1)]
    public abstract string Value { get; }
}

[ForyStruct(Target = typeof(ExternalDerived))]
internal abstract class ExternalDerivedSerializer
{
    [ForyField(1)]
    public abstract int Id { get; }

    [ForyField(2)]
    public abstract string BaseName { get; }
}

[ForyStruct(Target = typeof(ExternalFields))]
internal abstract class ExternalFieldsSerializer
{
    [ForyField(1)]
    public abstract int Count { get; }

    [ForyField(2)]
    public abstract string Name { get; }

    [ForyField(3)]
    public abstract int @event { get; }
}

[ForyStruct(Target = typeof(ExternalSchemaModel))]
internal abstract class ExternalSchemaSerializer
{
    [ForyField(1, Type = typeof(S.Fixed<S.Int32>))]
    public abstract int FixedValue { get; }

    [ForyField(2, Type = typeof(S.Tagged<S.Int64>))]
    public abstract long TaggedValue { get; }

    [ForyField(3, Type = typeof(S.Array<S.Int32>))]
    public abstract int[] ArrayValue { get; }

    [ForyField(4, Type = typeof(S.List<S.Int32>))]
    public abstract List<int> ListValue { get; }

    [ForyField(5, Type = typeof(S.Set<S.Int32>))]
    public abstract HashSet<int> SetValue { get; }

    [ForyField(6, Type = typeof(S.Map<S.Fixed<S.UInt32>, S.List<S.Tagged<S.UInt64>>>))]
    public abstract Dictionary<uint, List<ulong?>?> NestedValue { get; }
}

[ForyStruct(Target = typeof(ExternalVersionOne))]
internal abstract class ExternalVersionOneSerializer
{
    [ForyField(1)]
    public abstract int Id { get; }

    [ForyField(2)]
    public abstract string Name { get; }
}

[ForyStruct(Target = typeof(ExternalVersionTwo))]
internal abstract class ExternalVersionTwoSerializer
{
    [ForyField(2)]
    public abstract string Name { get; }

    [ForyField(1)]
    public abstract int Id { get; }

    [ForyField(3)]
    public abstract long Added { get; }
}

[ForyStruct(Target = typeof(ExternalVersionRenamed))]
internal abstract class ExternalVersionRenamedSerializer
{
    [ForyField(1)]
    public abstract int Identifier { get; }

    [ForyField(2)]
    public abstract string Name { get; }
}

[ForyStruct(Target = typeof(ExternalEvolutionOff), Evolving = false)]
internal abstract class ExternalEvolutionOffSerializer
{
    [ForyField(1)]
    public abstract int Value { get; }
}

[ForyStruct(Target = typeof(ExternalBudgetModel))]
internal abstract class ExternalBudgetModelSerializer
{
    [ForyField(1)]
    public abstract int Value { get; }

    [ForyField(Ignore = true)]
    public abstract ExternalBudgetValue HiddenState { get; }
}

[ForyStruct]
internal sealed class OrdinaryUser
{
    [ForyField(1)]
    public int Id { get; set; }

    [ForyField(2)]
    public string Name { get; set; } = string.Empty;

    [ForyField(3)]
    public OrdinaryUser? Friend { get; set; }

    [ForyField(4)]
    public List<OrdinaryUser> Links { get; set; } = [];
}

[ForyStruct]
internal struct OrdinaryPoint
{
    [ForyField(1)]
    public int X { get; set; }

    [ForyField(2)]
    public int Y { get; set; }
}

[ForyEnum]
internal enum OrdinaryStatus : uint
{
    Unknown = 0,
    Ready = 1,
    Done = 2,
    Complete = Done,
}

[ForyStruct]
internal sealed class ExternalTargetsHolder
{
    [ForyField(1)]
    public ExternalUser User { get; set; } = new();

    [ForyField(2)]
    public ExternalPoint Point { get; set; }

    [ForyField(3)]
    public ExternalPoint? OptionalPoint { get; set; }

    [ForyField(4)]
    public ExternalStatus Status { get; set; }

    [ForyField(5)]
    public List<ExternalUser> Users { get; set; } = [];

    [ForyField(6)]
    public Dictionary<string, ExternalUser> UsersByName { get; set; } = [];
}

[ForyStruct]
internal sealed class ExternalDynamicHolder
{
    [ForyField(1)]
    public object? DynamicValue { get; set; }
}

[ForyStruct]
internal sealed class OrdinaryTargetsHolder
{
    [ForyField(1)]
    public OrdinaryUser User { get; set; } = new();

    [ForyField(2)]
    public OrdinaryPoint Point { get; set; }

    [ForyField(3)]
    public OrdinaryPoint? OptionalPoint { get; set; }

    [ForyField(4)]
    public OrdinaryStatus Status { get; set; }

    [ForyField(5)]
    public List<OrdinaryUser> Users { get; set; } = [];

    [ForyField(6)]
    public Dictionary<string, OrdinaryUser> UsersByName { get; set; } = [];
}

[ForyStruct(Evolving = false)]
internal sealed class OrdinaryEvolutionOff
{
    [ForyField(1)]
    public int Value { get; set; }
}

internal sealed class ExternalFieldsManualSerializer : Serializer<ExternalFields>
{
    public override ExternalFields DefaultValue => null!;

    public override void WriteData(
        WriteContext context,
        in ExternalFields value,
        bool hasGenerics)
    {
        _ = hasGenerics;
        context.Writer.WriteVarInt32(value.Count + 100);
        context.TypeResolver.GetSerializer<string>().WriteData(context, value.Name, false);
    }

    public override ExternalFields ReadData(ReadContext context)
    {
        int count = context.Reader.ReadVarInt32() - 100;
        string name = context.TypeResolver.GetSerializer<string>().ReadData(context);
        return new ExternalFields { Count = count, Name = name };
    }
}
