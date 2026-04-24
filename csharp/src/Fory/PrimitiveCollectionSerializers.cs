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

using System.Collections.Immutable;

namespace Apache.Fory;

internal static class PrimitiveCollectionHeader
{
    public static void WriteListHeader(WriteContext context, int count, bool hasGenerics, TypeId elementTypeId, bool hasNull)
    {
        context.Writer.WriteVarUInt32((uint)count);
        if (count == 0)
        {
            return;
        }

        bool declared = hasGenerics && !TypeResolver.NeedToWriteTypeInfoForField(elementTypeId);
        byte header = CollectionBits.SameType;
        if (hasNull)
        {
            header |= CollectionBits.HasNull;
        }

        if (declared)
        {
            header |= CollectionBits.DeclaredElementType;
        }

        context.Writer.WriteUInt8(header);
        if (!declared)
        {
            context.Writer.WriteUInt8((byte)elementTypeId);
        }
    }
}

internal static class PrimitiveCollectionCodec
{
    public static void WriteValues<T, TCodec>(WriteContext context, IReadOnlyList<T> values, bool hasGenerics)
        where TCodec : struct, IPrimitiveDictionaryCodec<T>
    {
        bool hasNull = false;
        if (TCodec.IsNullable)
        {
            for (int i = 0; i < values.Count; i++)
            {
                if (TCodec.IsNone(values[i]))
                {
                    hasNull = true;
                    break;
                }
            }
        }

        PrimitiveCollectionHeader.WriteListHeader(context, values.Count, hasGenerics, TCodec.WireTypeId, hasNull);
        if (!hasNull)
        {
            for (int i = 0; i < values.Count; i++)
            {
                TCodec.Write(context, values[i]);
            }

            return;
        }

        for (int i = 0; i < values.Count; i++)
        {
            T value = values[i];
            if (TCodec.IsNone(value))
            {
                context.Writer.WriteInt8((sbyte)RefFlag.Null);
            }
            else
            {
                context.Writer.WriteInt8((sbyte)RefFlag.NotNullValue);
                TCodec.Write(context, value);
            }
        }
    }

    public static List<T> ReadValues<T, TCodec>(ReadContext context)
        where TCodec : struct, IPrimitiveDictionaryCodec<T>
    {
        int length = checked((int)context.Reader.ReadVarUInt32());
        if (length == 0)
        {
            return [];
        }

        byte header = context.Reader.ReadUInt8();
        bool trackRef = (header & CollectionBits.TrackingRef) != 0;
        bool hasNull = (header & CollectionBits.HasNull) != 0;
        bool declared = (header & CollectionBits.DeclaredElementType) != 0;
        bool sameType = (header & CollectionBits.SameType) != 0;
        if (!sameType)
        {
            throw new InvalidDataException("primitive collection codecs require same-type payloads");
        }

        if (trackRef)
        {
            throw new InvalidDataException("primitive collection codecs do not support reference-tracking payloads");
        }

        if (!declared)
        {
            byte actualTypeId = context.Reader.ReadUInt8();
            if (actualTypeId != (byte)TCodec.WireTypeId)
            {
                throw new TypeMismatchException((uint)TCodec.WireTypeId, actualTypeId);
            }
        }

        List<T> values = new(length);
        if (!hasNull)
        {
            for (int i = 0; i < length; i++)
            {
                values.Add(TCodec.Read(context));
            }

            return values;
        }

        if (!TCodec.IsNullable)
        {
            throw new InvalidDataException("non-nullable primitive collection codec cannot read null elements");
        }

        for (int i = 0; i < length; i++)
        {
            sbyte refFlag = context.Reader.ReadInt8();
            if (refFlag == (sbyte)RefFlag.Null)
            {
                values.Add(TCodec.DefaultValue);
            }
            else if (refFlag == (sbyte)RefFlag.NotNullValue)
            {
                values.Add(TCodec.Read(context));
            }
            else
            {
                throw new RefException($"invalid nullability flag {refFlag}");
            }
        }

        return values;
    }
}

public class PrimitiveListSerializer<T, TCodec> : Serializer<List<T>>
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    public override List<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<T> value, bool hasGenerics)
    {
        PrimitiveCollectionCodec.WriteValues<T, TCodec>(context, value ?? [], hasGenerics);
    }

    public override List<T> ReadData(ReadContext context)
    {
        return PrimitiveCollectionCodec.ReadValues<T, TCodec>(context);
    }
}

public class PrimitiveSetSerializer<T, TCodec> : Serializer<HashSet<T>>
    where T : notnull
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    public override HashSet<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<T> value, bool hasGenerics)
    {
        HashSet<T> set = value ?? [];
        List<T> values = new(set.Count);
        foreach (T item in set)
        {
            values.Add(item);
        }

        PrimitiveCollectionCodec.WriteValues<T, TCodec>(context, values, hasGenerics);
    }

    public override HashSet<T> ReadData(ReadContext context)
    {
        return [.. PrimitiveCollectionCodec.ReadValues<T, TCodec>(context)];
    }
}

public class PrimitiveLinkedListSerializer<T, TCodec> : Serializer<LinkedList<T>>
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    public override LinkedList<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in LinkedList<T> value, bool hasGenerics)
    {
        LinkedList<T> list = value ?? new LinkedList<T>();
        List<T> values = new(list.Count);
        foreach (T item in list)
        {
            values.Add(item);
        }

        PrimitiveCollectionCodec.WriteValues<T, TCodec>(context, values, hasGenerics);
    }

    public override LinkedList<T> ReadData(ReadContext context)
    {
        return new LinkedList<T>(PrimitiveCollectionCodec.ReadValues<T, TCodec>(context));
    }
}

public class PrimitiveQueueSerializer<T, TCodec> : Serializer<Queue<T>>
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    public override Queue<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in Queue<T> value, bool hasGenerics)
    {
        Queue<T> queue = value ?? new Queue<T>();
        List<T> values = new(queue.Count);
        foreach (T item in queue)
        {
            values.Add(item);
        }

        PrimitiveCollectionCodec.WriteValues<T, TCodec>(context, values, hasGenerics);
    }

    public override Queue<T> ReadData(ReadContext context)
    {
        List<T> values = PrimitiveCollectionCodec.ReadValues<T, TCodec>(context);
        Queue<T> queue = new(values.Count);
        for (int i = 0; i < values.Count; i++)
        {
            queue.Enqueue(values[i]);
        }

        return queue;
    }
}

public class PrimitiveStackSerializer<T, TCodec> : Serializer<Stack<T>>
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    public override Stack<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in Stack<T> value, bool hasGenerics)
    {
        Stack<T> stack = value ?? new Stack<T>();
        if (stack.Count == 0)
        {
            PrimitiveCollectionCodec.WriteValues<T, TCodec>(context, [], hasGenerics);
            return;
        }

        T[] topToBottom = stack.ToArray();
        List<T> bottomToTop = new(topToBottom.Length);
        for (int i = topToBottom.Length - 1; i >= 0; i--)
        {
            bottomToTop.Add(topToBottom[i]);
        }

        PrimitiveCollectionCodec.WriteValues<T, TCodec>(context, bottomToTop, hasGenerics);
    }

    public override Stack<T> ReadData(ReadContext context)
    {
        List<T> values = PrimitiveCollectionCodec.ReadValues<T, TCodec>(context);
        Stack<T> stack = new(values.Count);
        for (int i = 0; i < values.Count; i++)
        {
            stack.Push(values[i]);
        }

        return stack;
    }
}

public class PrimitiveSortedSetSerializer<T, TCodec> : Serializer<SortedSet<T>>
    where T : notnull
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    public override SortedSet<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in SortedSet<T> value, bool hasGenerics)
    {
        SortedSet<T> set = value ?? new SortedSet<T>();
        List<T> values = new(set.Count);
        foreach (T item in set)
        {
            values.Add(item);
        }

        PrimitiveCollectionCodec.WriteValues<T, TCodec>(context, values, hasGenerics);
    }

    public override SortedSet<T> ReadData(ReadContext context)
    {
        return [.. PrimitiveCollectionCodec.ReadValues<T, TCodec>(context)];
    }
}

public class PrimitiveImmutableHashSetSerializer<T, TCodec> : Serializer<ImmutableHashSet<T>>
    where T : notnull
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    public override ImmutableHashSet<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in ImmutableHashSet<T> value, bool hasGenerics)
    {
        ImmutableHashSet<T> set = value ?? ImmutableHashSet<T>.Empty;
        List<T> values = new(set.Count);
        foreach (T item in set)
        {
            values.Add(item);
        }

        PrimitiveCollectionCodec.WriteValues<T, TCodec>(context, values, hasGenerics);
    }

    public override ImmutableHashSet<T> ReadData(ReadContext context)
    {
        return ImmutableHashSet.CreateRange(PrimitiveCollectionCodec.ReadValues<T, TCodec>(context));
    }
}

internal sealed class ListBoolSerializer : PrimitiveListSerializer<bool, BoolPrimitiveDictionaryCodec> { }

internal sealed class ListInt8Serializer : PrimitiveListSerializer<sbyte, Int8PrimitiveDictionaryCodec> { }

internal sealed class ListInt16Serializer : PrimitiveListSerializer<short, Int16PrimitiveDictionaryCodec> { }

internal sealed class ListIntSerializer : PrimitiveListSerializer<int, VarInt32PrimitiveDictionaryCodec> { }

public sealed class ListVarInt32Serializer : PrimitiveListSerializer<int, VarInt32PrimitiveDictionaryCodec> { }

public sealed class ListFixedInt32Serializer : PrimitiveListSerializer<int, FixedInt32PrimitiveDictionaryCodec> { }

internal sealed class ListLongSerializer : PrimitiveListSerializer<long, VarInt64PrimitiveDictionaryCodec> { }

public sealed class ListVarInt64Serializer : PrimitiveListSerializer<long, VarInt64PrimitiveDictionaryCodec> { }

public sealed class ListFixedInt64Serializer : PrimitiveListSerializer<long, FixedInt64PrimitiveDictionaryCodec> { }

public sealed class ListTaggedInt64Serializer : PrimitiveListSerializer<long, TaggedInt64PrimitiveDictionaryCodec> { }

internal sealed class ListUInt8Serializer : PrimitiveListSerializer<byte, UInt8PrimitiveDictionaryCodec> { }

internal sealed class ListUInt16Serializer : PrimitiveListSerializer<ushort, UInt16PrimitiveDictionaryCodec> { }

internal sealed class ListUIntSerializer : PrimitiveListSerializer<uint, VarUInt32PrimitiveDictionaryCodec> { }

public sealed class ListVarUInt32Serializer : PrimitiveListSerializer<uint, VarUInt32PrimitiveDictionaryCodec> { }

public sealed class ListFixedUInt32Serializer : PrimitiveListSerializer<uint, FixedUInt32PrimitiveDictionaryCodec> { }

internal sealed class ListULongSerializer : PrimitiveListSerializer<ulong, VarUInt64PrimitiveDictionaryCodec> { }

public sealed class ListVarUInt64Serializer : PrimitiveListSerializer<ulong, VarUInt64PrimitiveDictionaryCodec> { }

public sealed class ListFixedUInt64Serializer : PrimitiveListSerializer<ulong, FixedUInt64PrimitiveDictionaryCodec> { }

public sealed class ListTaggedUInt64Serializer : PrimitiveListSerializer<ulong, TaggedUInt64PrimitiveDictionaryCodec> { }

internal sealed class ListHalfSerializer : PrimitiveListSerializer<Half, Float16PrimitiveDictionaryCodec> { }

internal sealed class ListBFloat16Serializer : PrimitiveListSerializer<BFloat16, BFloat16PrimitiveDictionaryCodec> { }

internal sealed class ListFloatSerializer : PrimitiveListSerializer<float, Float32PrimitiveDictionaryCodec> { }

internal sealed class ListDoubleSerializer : PrimitiveListSerializer<double, Float64PrimitiveDictionaryCodec> { }

internal sealed class ListStringSerializer : PrimitiveListSerializer<string, StringPrimitiveDictionaryCodec> { }

internal sealed class SetInt8Serializer : PrimitiveSetSerializer<sbyte, Int8PrimitiveDictionaryCodec> { }

internal sealed class SetInt16Serializer : PrimitiveSetSerializer<short, Int16PrimitiveDictionaryCodec> { }

internal sealed class SetIntSerializer : PrimitiveSetSerializer<int, VarInt32PrimitiveDictionaryCodec> { }

public sealed class SetVarInt32Serializer : PrimitiveSetSerializer<int, VarInt32PrimitiveDictionaryCodec> { }

public sealed class SetFixedInt32Serializer : PrimitiveSetSerializer<int, FixedInt32PrimitiveDictionaryCodec> { }

internal sealed class SetLongSerializer : PrimitiveSetSerializer<long, VarInt64PrimitiveDictionaryCodec> { }

public sealed class SetVarInt64Serializer : PrimitiveSetSerializer<long, VarInt64PrimitiveDictionaryCodec> { }

public sealed class SetFixedInt64Serializer : PrimitiveSetSerializer<long, FixedInt64PrimitiveDictionaryCodec> { }

public sealed class SetTaggedInt64Serializer : PrimitiveSetSerializer<long, TaggedInt64PrimitiveDictionaryCodec> { }

internal sealed class SetUInt8Serializer : PrimitiveSetSerializer<byte, UInt8PrimitiveDictionaryCodec> { }

internal sealed class SetUInt16Serializer : PrimitiveSetSerializer<ushort, UInt16PrimitiveDictionaryCodec> { }

internal sealed class SetUIntSerializer : PrimitiveSetSerializer<uint, VarUInt32PrimitiveDictionaryCodec> { }

public sealed class SetVarUInt32Serializer : PrimitiveSetSerializer<uint, VarUInt32PrimitiveDictionaryCodec> { }

public sealed class SetFixedUInt32Serializer : PrimitiveSetSerializer<uint, FixedUInt32PrimitiveDictionaryCodec> { }

internal sealed class SetULongSerializer : PrimitiveSetSerializer<ulong, VarUInt64PrimitiveDictionaryCodec> { }

public sealed class SetVarUInt64Serializer : PrimitiveSetSerializer<ulong, VarUInt64PrimitiveDictionaryCodec> { }

public sealed class SetFixedUInt64Serializer : PrimitiveSetSerializer<ulong, FixedUInt64PrimitiveDictionaryCodec> { }

public sealed class SetTaggedUInt64Serializer : PrimitiveSetSerializer<ulong, TaggedUInt64PrimitiveDictionaryCodec> { }

internal sealed class SetFloatSerializer : PrimitiveSetSerializer<float, Float32PrimitiveDictionaryCodec> { }

internal sealed class SetDoubleSerializer : PrimitiveSetSerializer<double, Float64PrimitiveDictionaryCodec> { }
