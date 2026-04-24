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

using System.Linq.Expressions;
using System.Reflection;

namespace Apache.Fory;

public sealed class UnionSerializer<TUnion> : Serializer<TUnion>
    where TUnion : Union
{
    private static readonly Func<int, object?, TUnion> Factory = BuildFactory();
    private static readonly IReadOnlyDictionary<int, Type> CaseTypeByIndex = BuildCaseTypeMap();

    public override TUnion DefaultValue => null!;

    public override void WriteData(WriteContext context, in TUnion value, bool hasGenerics)
    {
        _ = hasGenerics;
        if (value is null)
        {
            throw new InvalidDataException("union value is null");
        }

        context.Writer.WriteVarUInt32((uint)value.Index);
        if (CaseTypeByIndex.TryGetValue(value.Index, out Type? caseType))
        {
            WriteTypedCaseValue(context, caseType, value.Value);
            return;
        }

        DynamicAnyCodec.WriteAny(context, value.Value, RefMode.Tracking, true, false);
    }

    public override TUnion ReadData(ReadContext context)
    {
        uint rawCaseId = context.Reader.ReadVarUInt32();
        if (rawCaseId > int.MaxValue)
        {
            throw new InvalidDataException($"union case id out of range: {rawCaseId}");
        }

        int caseId = (int)rawCaseId;
        object? caseValue;
        if (CaseTypeByIndex.TryGetValue(caseId, out Type? caseType))
        {
            caseValue = ReadTypedCaseValue(context, caseType);
        }
        else
        {
            caseValue = DynamicAnyCodec.ReadAny(context, RefMode.Tracking, true);
        }

        return Factory(caseId, caseValue);
    }

    private static Func<int, object?, TUnion> BuildFactory()
    {
        if (typeof(TUnion) == typeof(Union))
        {
            return (index, value) => (TUnion)(object)new Union(index, value);
        }

        ConstructorInfo? ctor = typeof(TUnion).GetConstructor(
            BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic,
            binder: null,
            [typeof(int), typeof(object)],
            modifiers: null);
        if (ctor is not null)
        {
            ParameterExpression indexParam = Expression.Parameter(typeof(int), "index");
            ParameterExpression valueParam = Expression.Parameter(typeof(object), "value");
            NewExpression created = Expression.New(ctor, indexParam, valueParam);
            return Expression.Lambda<Func<int, object?, TUnion>>(created, indexParam, valueParam).Compile();
        }

        MethodInfo? ofFactory = typeof(TUnion).GetMethod(
            "Of",
            BindingFlags.Public | BindingFlags.Static,
            binder: null,
            [typeof(int), typeof(object)],
            modifiers: null);
        if (ofFactory is not null && typeof(TUnion).IsAssignableFrom(ofFactory.ReturnType))
        {
            return (index, value) => (TUnion)ofFactory.Invoke(null, [index, value])!;
        }

        throw new InvalidDataException(
            $"union type {typeof(TUnion)} must define (int, object) constructor or static Of(int, object)");
    }

    private static IReadOnlyDictionary<int, Type> BuildCaseTypeMap()
    {
        if (typeof(TUnion) == typeof(Union))
        {
            return new Dictionary<int, Type>();
        }

        Dictionary<int, Type> caseTypes = new();
        MethodInfo[] methods = typeof(TUnion).GetMethods(BindingFlags.Public | BindingFlags.Static);
        foreach (MethodInfo method in methods)
        {
            if (!typeof(TUnion).IsAssignableFrom(method.ReturnType))
            {
                continue;
            }

            ParameterInfo[] parameters = method.GetParameters();
            if (parameters.Length != 1)
            {
                continue;
            }

            Type caseType = parameters[0].ParameterType;
            if (!TryResolveCaseIndex(method, caseType, out int caseIndex))
            {
                continue;
            }

            caseTypes.TryAdd(caseIndex, caseType);
        }

        return caseTypes;
    }

    private static bool TryResolveCaseIndex(MethodInfo method, Type caseType, out int caseIndex)
    {
        caseIndex = default;
        object? probeArg = CreateProbeArgument(caseType);
        try
        {
            object? result = method.Invoke(null, [probeArg]);
            if (result is not Union union)
            {
                return false;
            }

            caseIndex = union.Index;
            return true;
        }
        catch
        {
            return false;
        }
    }

    private static object? CreateProbeArgument(Type caseType)
    {
        if (!caseType.IsValueType)
        {
            return null;
        }

        return Activator.CreateInstance(caseType);
    }

    private static void WriteTypedCaseValue(WriteContext context, Type caseType, object? value)
    {
        object? normalized = NormalizeWriteCaseValue(value, caseType);
        DynamicAnyCodec.WriteAny(context, normalized, RefMode.Tracking, writeTypeInfo: true, hasGenerics: caseType.IsGenericType);
    }

    private static object? ReadTypedCaseValue(ReadContext context, Type caseType)
    {
        object? value = DynamicAnyCodec.ReadAny(context, RefMode.Tracking, readTypeInfo: true);
        return CollectionValueAdapter.Normalize(value, caseType);
    }

    private static object? NormalizeWriteCaseValue(object? value, Type caseType)
    {
        if (CollectionValueAdapter.TryGetPrimitiveArrayCarrier(caseType, out Type? carrierType))
        {
            return CollectionValueAdapter.Normalize(value, carrierType!);
        }

        return CollectionValueAdapter.Normalize(value, caseType);
    }
}
