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

internal static class PrimitiveListWireCodec
{
    public static int ReadCount(ReadContext context)
    {
        return checked((int)context.Reader.ReadVarUInt32());
    }

    public static int ReadElementCount(ReadContext context, int elementSize)
    {
        int byteSize = checked((int)context.Reader.ReadVarUInt32());
        if (byteSize % elementSize != 0)
        {
            throw new InvalidDataException(
                $"primitive list payload size {byteSize} is not aligned to element size {elementSize}");
        }

        return byteSize / elementSize;
    }

    public static void WriteCount(WriteContext context, int count)
    {
        context.Writer.WriteVarUInt32((uint)count);
    }

    public static void WriteByteSize(WriteContext context, int count, int elementSize)
    {
        context.Writer.WriteVarUInt32((uint)checked(count * elementSize));
    }
}

internal sealed class ListBoolSerializer : Serializer<List<bool>>
{
    public override List<bool> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<bool> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<bool> list = value ?? [];
        PrimitiveListWireCodec.WriteCount(context, list.Count);
        for (int i = 0; i < list.Count; i++)
        {
            context.Writer.WriteUInt8(list[i] ? (byte)1 : (byte)0);
        }
    }

    public override List<bool> ReadData(ReadContext context)
    {
        int count = PrimitiveListWireCodec.ReadCount(context);
        List<bool> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(context.Reader.ReadUInt8() != 0);
        }

        return list;
    }
}

internal sealed class ListInt8Serializer : Serializer<List<sbyte>>
{
    public override List<sbyte> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<sbyte> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<sbyte> list = value ?? [];
        PrimitiveListWireCodec.WriteCount(context, list.Count);
        for (int i = 0; i < list.Count; i++)
        {
            context.Writer.WriteInt8(list[i]);
        }
    }

    public override List<sbyte> ReadData(ReadContext context)
    {
        int count = PrimitiveListWireCodec.ReadCount(context);
        List<sbyte> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(context.Reader.ReadInt8());
        }

        return list;
    }
}

internal sealed class ListInt16Serializer : Serializer<List<short>>
{
    public override List<short> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<short> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<short> list = value ?? [];
        PrimitiveListWireCodec.WriteByteSize(context, list.Count, 2);
        for (int i = 0; i < list.Count; i++)
        {
            context.Writer.WriteInt16(list[i]);
        }
    }

    public override List<short> ReadData(ReadContext context)
    {
        int count = PrimitiveListWireCodec.ReadElementCount(context, 2);
        List<short> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(context.Reader.ReadInt16());
        }

        return list;
    }
}

internal sealed class ListIntSerializer : Serializer<List<int>>
{
    public override List<int> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<int> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<int> list = value ?? [];
        TypeId wireTypeId = context.TryGetCollectionElementWireTypeOverride(out TypeId overriddenTypeId)
            ? overriddenTypeId
            : TypeId.Int32;
        switch (wireTypeId)
        {
            case TypeId.Int32:
                PrimitiveListWireCodec.WriteByteSize(context, list.Count, 4);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteInt32(list[i]);
                }

                return;
            case TypeId.VarInt32:
                PrimitiveListWireCodec.WriteCount(context, list.Count);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteVarInt32(list[i]);
                }

                return;
            default:
                throw new InvalidDataException($"unsupported int list wire type {wireTypeId}");
        }
    }

    public override List<int> ReadData(ReadContext context)
    {
        TypeId wireTypeId = context.TryGetCollectionElementWireTypeOverride(out TypeId overriddenTypeId)
            ? overriddenTypeId
            : TypeId.Int32;
        int count = wireTypeId switch
        {
            TypeId.Int32 => PrimitiveListWireCodec.ReadElementCount(context, 4),
            TypeId.VarInt32 => PrimitiveListWireCodec.ReadCount(context),
            _ => throw new InvalidDataException($"unsupported int list wire type {wireTypeId}"),
        };
        List<int> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(wireTypeId == TypeId.Int32
                ? context.Reader.ReadInt32()
                : context.Reader.ReadVarInt32());
        }

        return list;
    }
}

internal sealed class ListLongSerializer : Serializer<List<long>>
{
    public override List<long> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<long> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<long> list = value ?? [];
        TypeId wireTypeId = context.TryGetCollectionElementWireTypeOverride(out TypeId overriddenTypeId)
            ? overriddenTypeId
            : TypeId.Int64;
        switch (wireTypeId)
        {
            case TypeId.Int64:
                PrimitiveListWireCodec.WriteByteSize(context, list.Count, 8);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteInt64(list[i]);
                }

                return;
            case TypeId.VarInt64:
                PrimitiveListWireCodec.WriteCount(context, list.Count);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteVarInt64(list[i]);
                }

                return;
            case TypeId.TaggedInt64:
                PrimitiveListWireCodec.WriteCount(context, list.Count);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteTaggedInt64(list[i]);
                }

                return;
            default:
                throw new InvalidDataException($"unsupported int64 list wire type {wireTypeId}");
        }
    }

    public override List<long> ReadData(ReadContext context)
    {
        TypeId wireTypeId = context.TryGetCollectionElementWireTypeOverride(out TypeId overriddenTypeId)
            ? overriddenTypeId
            : TypeId.Int64;
        int count = wireTypeId switch
        {
            TypeId.Int64 => PrimitiveListWireCodec.ReadElementCount(context, 8),
            TypeId.VarInt64 or TypeId.TaggedInt64 => PrimitiveListWireCodec.ReadCount(context),
            _ => throw new InvalidDataException($"unsupported int64 list wire type {wireTypeId}"),
        };
        List<long> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(wireTypeId switch
            {
                TypeId.Int64 => context.Reader.ReadInt64(),
                TypeId.VarInt64 => context.Reader.ReadVarInt64(),
                TypeId.TaggedInt64 => context.Reader.ReadTaggedInt64(),
                _ => throw new InvalidDataException($"unsupported int64 list wire type {wireTypeId}"),
            });
        }

        return list;
    }
}

internal sealed class ListUInt8Serializer : Serializer<List<byte>>
{
    public override List<byte> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<byte> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<byte> list = value ?? [];
        PrimitiveListWireCodec.WriteCount(context, list.Count);
        for (int i = 0; i < list.Count; i++)
        {
            context.Writer.WriteUInt8(list[i]);
        }
    }

    public override List<byte> ReadData(ReadContext context)
    {
        int count = PrimitiveListWireCodec.ReadCount(context);
        byte[] values = context.Reader.ReadBytes(count);
        return [.. values];
    }
}

internal sealed class ListUInt16Serializer : Serializer<List<ushort>>
{
    public override List<ushort> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<ushort> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<ushort> list = value ?? [];
        PrimitiveListWireCodec.WriteByteSize(context, list.Count, 2);
        for (int i = 0; i < list.Count; i++)
        {
            context.Writer.WriteUInt16(list[i]);
        }
    }

    public override List<ushort> ReadData(ReadContext context)
    {
        int count = PrimitiveListWireCodec.ReadElementCount(context, 2);
        List<ushort> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(context.Reader.ReadUInt16());
        }

        return list;
    }
}

internal sealed class ListUIntSerializer : Serializer<List<uint>>
{
    public override List<uint> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<uint> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<uint> list = value ?? [];
        TypeId wireTypeId = context.TryGetCollectionElementWireTypeOverride(out TypeId overriddenTypeId)
            ? overriddenTypeId
            : TypeId.UInt32;
        switch (wireTypeId)
        {
            case TypeId.UInt32:
                PrimitiveListWireCodec.WriteByteSize(context, list.Count, 4);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteUInt32(list[i]);
                }

                return;
            case TypeId.VarUInt32:
                PrimitiveListWireCodec.WriteCount(context, list.Count);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteVarUInt32(list[i]);
                }

                return;
            default:
                throw new InvalidDataException($"unsupported uint32 list wire type {wireTypeId}");
        }
    }

    public override List<uint> ReadData(ReadContext context)
    {
        TypeId wireTypeId = context.TryGetCollectionElementWireTypeOverride(out TypeId overriddenTypeId)
            ? overriddenTypeId
            : TypeId.UInt32;
        int count = wireTypeId switch
        {
            TypeId.UInt32 => PrimitiveListWireCodec.ReadElementCount(context, 4),
            TypeId.VarUInt32 => PrimitiveListWireCodec.ReadCount(context),
            _ => throw new InvalidDataException($"unsupported uint32 list wire type {wireTypeId}"),
        };
        List<uint> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(wireTypeId == TypeId.UInt32
                ? context.Reader.ReadUInt32()
                : context.Reader.ReadVarUInt32());
        }

        return list;
    }
}

internal sealed class ListULongSerializer : Serializer<List<ulong>>
{
    public override List<ulong> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<ulong> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<ulong> list = value ?? [];
        TypeId wireTypeId = context.TryGetCollectionElementWireTypeOverride(out TypeId overriddenTypeId)
            ? overriddenTypeId
            : TypeId.UInt64;
        switch (wireTypeId)
        {
            case TypeId.UInt64:
                PrimitiveListWireCodec.WriteByteSize(context, list.Count, 8);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteUInt64(list[i]);
                }

                return;
            case TypeId.VarUInt64:
                PrimitiveListWireCodec.WriteCount(context, list.Count);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteVarUInt64(list[i]);
                }

                return;
            case TypeId.TaggedUInt64:
                PrimitiveListWireCodec.WriteCount(context, list.Count);
                for (int i = 0; i < list.Count; i++)
                {
                    context.Writer.WriteTaggedUInt64(list[i]);
                }

                return;
            default:
                throw new InvalidDataException($"unsupported uint64 list wire type {wireTypeId}");
        }
    }

    public override List<ulong> ReadData(ReadContext context)
    {
        TypeId wireTypeId = context.TryGetCollectionElementWireTypeOverride(out TypeId overriddenTypeId)
            ? overriddenTypeId
            : TypeId.UInt64;
        int count = wireTypeId switch
        {
            TypeId.UInt64 => PrimitiveListWireCodec.ReadElementCount(context, 8),
            TypeId.VarUInt64 or TypeId.TaggedUInt64 => PrimitiveListWireCodec.ReadCount(context),
            _ => throw new InvalidDataException($"unsupported uint64 list wire type {wireTypeId}"),
        };
        List<ulong> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(wireTypeId switch
            {
                TypeId.UInt64 => context.Reader.ReadUInt64(),
                TypeId.VarUInt64 => context.Reader.ReadVarUInt64(),
                TypeId.TaggedUInt64 => context.Reader.ReadTaggedUInt64(),
                _ => throw new InvalidDataException($"unsupported uint64 list wire type {wireTypeId}"),
            });
        }

        return list;
    }
}

internal sealed class ListHalfSerializer : Serializer<List<Half>>
{
    public override List<Half> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<Half> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<Half> list = value ?? [];
        PrimitiveListWireCodec.WriteByteSize(context, list.Count, 2);
        for (int i = 0; i < list.Count; i++)
        {
            context.Writer.WriteUInt16(BitConverter.HalfToUInt16Bits(list[i]));
        }
    }

    public override List<Half> ReadData(ReadContext context)
    {
        int count = PrimitiveListWireCodec.ReadElementCount(context, 2);
        List<Half> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(BitConverter.UInt16BitsToHalf(context.Reader.ReadUInt16()));
        }

        return list;
    }
}

internal sealed class ListBFloat16Serializer : Serializer<List<BFloat16>>
{
    public override List<BFloat16> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<BFloat16> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<BFloat16> list = value ?? [];
        PrimitiveListWireCodec.WriteByteSize(context, list.Count, 2);
        for (int i = 0; i < list.Count; i++)
        {
            context.Writer.WriteUInt16(list[i].ToBits());
        }
    }

    public override List<BFloat16> ReadData(ReadContext context)
    {
        int count = PrimitiveListWireCodec.ReadElementCount(context, 2);
        List<BFloat16> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(BFloat16.FromBits(context.Reader.ReadUInt16()));
        }

        return list;
    }
}

internal sealed class ListFloatSerializer : Serializer<List<float>>
{
    public override List<float> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<float> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<float> list = value ?? [];
        PrimitiveListWireCodec.WriteByteSize(context, list.Count, 4);
        for (int i = 0; i < list.Count; i++)
        {
            context.Writer.WriteFloat32(list[i]);
        }
    }

    public override List<float> ReadData(ReadContext context)
    {
        int count = PrimitiveListWireCodec.ReadElementCount(context, 4);
        List<float> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(context.Reader.ReadFloat32());
        }

        return list;
    }
}

internal sealed class ListDoubleSerializer : Serializer<List<double>>
{
    public override List<double> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<double> value, bool hasGenerics)
    {
        _ = hasGenerics;
        List<double> list = value ?? [];
        PrimitiveListWireCodec.WriteByteSize(context, list.Count, 8);
        for (int i = 0; i < list.Count; i++)
        {
            context.Writer.WriteFloat64(list[i]);
        }
    }

    public override List<double> ReadData(ReadContext context)
    {
        int count = PrimitiveListWireCodec.ReadElementCount(context, 8);
        List<double> list = new(count);
        for (int i = 0; i < count; i++)
        {
            list.Add(context.Reader.ReadFloat64());
        }

        return list;
    }
}

internal sealed class ListStringSerializer : Serializer<List<string>>
{
    private static readonly ListSerializer<string> Fallback = new();




    public override List<string> DefaultValue => null!;

    public override void WriteData(WriteContext context, in List<string> value, bool hasGenerics)
    {
        List<string> list = value ?? [];
        bool hasNull = false;
        for (int i = 0; i < list.Count; i++)
        {
            if (list[i] is null)
            {
                hasNull = true;
                break;
            }
        }

        PrimitiveCollectionHeader.WriteListHeader(context, list.Count, hasGenerics, TypeId.String, hasNull);
        if (hasNull)
        {
            for (int i = 0; i < list.Count; i++)
            {
                string? item = list[i];
                if (item is null)
                {
                    context.Writer.WriteInt8((sbyte)RefFlag.Null);
                    continue;
                }

                context.Writer.WriteInt8((sbyte)RefFlag.NotNullValue);
                StringSerializer.WriteString(context, item);
            }

            return;
        }

        for (int i = 0; i < list.Count; i++)
        {
            StringSerializer.WriteString(context, list[i]);
        }
    }

    public override List<string> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetInt8Serializer : Serializer<HashSet<sbyte>>
{
    private static readonly SetSerializer<sbyte> Fallback = new();




    public override HashSet<sbyte> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<sbyte> value, bool hasGenerics)
    {
        HashSet<sbyte> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.Int8, false);
        foreach (sbyte item in set)
        {
            context.Writer.WriteInt8(item);
        }
    }

    public override HashSet<sbyte> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetInt16Serializer : Serializer<HashSet<short>>
{
    private static readonly SetSerializer<short> Fallback = new();




    public override HashSet<short> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<short> value, bool hasGenerics)
    {
        HashSet<short> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.Int16, false);
        foreach (short item in set)
        {
            context.Writer.WriteInt16(item);
        }
    }

    public override HashSet<short> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetIntSerializer : Serializer<HashSet<int>>
{
    private static readonly SetSerializer<int> Fallback = new();




    public override HashSet<int> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<int> value, bool hasGenerics)
    {
        HashSet<int> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.VarInt32, false);
        foreach (int item in set)
        {
            context.Writer.WriteVarInt32(item);
        }
    }

    public override HashSet<int> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetLongSerializer : Serializer<HashSet<long>>
{
    private static readonly SetSerializer<long> Fallback = new();




    public override HashSet<long> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<long> value, bool hasGenerics)
    {
        HashSet<long> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.VarInt64, false);
        foreach (long item in set)
        {
            context.Writer.WriteVarInt64(item);
        }
    }

    public override HashSet<long> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetUInt8Serializer : Serializer<HashSet<byte>>
{
    private static readonly SetSerializer<byte> Fallback = new();




    public override HashSet<byte> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<byte> value, bool hasGenerics)
    {
        HashSet<byte> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.UInt8, false);
        foreach (byte item in set)
        {
            context.Writer.WriteUInt8(item);
        }
    }

    public override HashSet<byte> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetUInt16Serializer : Serializer<HashSet<ushort>>
{
    private static readonly SetSerializer<ushort> Fallback = new();




    public override HashSet<ushort> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<ushort> value, bool hasGenerics)
    {
        HashSet<ushort> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.UInt16, false);
        foreach (ushort item in set)
        {
            context.Writer.WriteUInt16(item);
        }
    }

    public override HashSet<ushort> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetUIntSerializer : Serializer<HashSet<uint>>
{
    private static readonly SetSerializer<uint> Fallback = new();




    public override HashSet<uint> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<uint> value, bool hasGenerics)
    {
        HashSet<uint> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.VarUInt32, false);
        foreach (uint item in set)
        {
            context.Writer.WriteVarUInt32(item);
        }
    }

    public override HashSet<uint> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetULongSerializer : Serializer<HashSet<ulong>>
{
    private static readonly SetSerializer<ulong> Fallback = new();




    public override HashSet<ulong> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<ulong> value, bool hasGenerics)
    {
        HashSet<ulong> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.VarUInt64, false);
        foreach (ulong item in set)
        {
            context.Writer.WriteVarUInt64(item);
        }
    }

    public override HashSet<ulong> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetFloatSerializer : Serializer<HashSet<float>>
{
    private static readonly SetSerializer<float> Fallback = new();




    public override HashSet<float> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<float> value, bool hasGenerics)
    {
        HashSet<float> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.Float32, false);
        foreach (float item in set)
        {
            context.Writer.WriteFloat32(item);
        }
    }

    public override HashSet<float> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal sealed class SetDoubleSerializer : Serializer<HashSet<double>>
{
    private static readonly SetSerializer<double> Fallback = new();




    public override HashSet<double> DefaultValue => null!;

    public override void WriteData(WriteContext context, in HashSet<double> value, bool hasGenerics)
    {
        HashSet<double> set = value ?? [];
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TypeId.Float64, false);
        foreach (double item in set)
        {
            context.Writer.WriteFloat64(item);
        }
    }

    public override HashSet<double> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal class PrimitiveLinkedListSerializer<T, TCodec> : Serializer<LinkedList<T>>
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    private static readonly LinkedListSerializer<T> Fallback = new();




    public override LinkedList<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in LinkedList<T> value, bool hasGenerics)
    {
        if (TCodec.IsNullable)
        {
            throw new InvalidDataException("nullable primitive codecs are not supported for linked-list fast path");
        }

        LinkedList<T> list = value ?? new LinkedList<T>();
        PrimitiveCollectionHeader.WriteListHeader(context, list.Count, hasGenerics, TCodec.WireTypeId, false);
        foreach (T item in list)
        {
            TCodec.Write(context, item);
        }
    }

    public override LinkedList<T> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal class PrimitiveQueueSerializer<T, TCodec> : Serializer<Queue<T>>
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    private static readonly QueueSerializer<T> Fallback = new();




    public override Queue<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in Queue<T> value, bool hasGenerics)
    {
        if (TCodec.IsNullable)
        {
            throw new InvalidDataException("nullable primitive codecs are not supported for queue fast path");
        }

        Queue<T> queue = value ?? new Queue<T>();
        PrimitiveCollectionHeader.WriteListHeader(context, queue.Count, hasGenerics, TCodec.WireTypeId, false);
        foreach (T item in queue)
        {
            TCodec.Write(context, item);
        }
    }

    public override Queue<T> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal class PrimitiveStackSerializer<T, TCodec> : Serializer<Stack<T>>
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    private static readonly StackSerializer<T> Fallback = new();




    public override Stack<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in Stack<T> value, bool hasGenerics)
    {
        if (TCodec.IsNullable)
        {
            throw new InvalidDataException("nullable primitive codecs are not supported for stack fast path");
        }

        Stack<T> stack = value ?? new Stack<T>();
        PrimitiveCollectionHeader.WriteListHeader(context, stack.Count, hasGenerics, TCodec.WireTypeId, false);
        if (stack.Count == 0)
        {
            return;
        }

        T[] topToBottom = stack.ToArray();
        for (int i = topToBottom.Length - 1; i >= 0; i--)
        {
            TCodec.Write(context, topToBottom[i]);
        }
    }

    public override Stack<T> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal class PrimitiveSortedSetSerializer<T, TCodec> : Serializer<SortedSet<T>>
    where T : notnull
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    private static readonly SortedSetSerializer<T> Fallback = new();




    public override SortedSet<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in SortedSet<T> value, bool hasGenerics)
    {
        if (TCodec.IsNullable)
        {
            throw new InvalidDataException("nullable primitive codecs are not supported for sorted-set fast path");
        }

        SortedSet<T> set = value ?? new SortedSet<T>();
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TCodec.WireTypeId, false);
        foreach (T item in set)
        {
            TCodec.Write(context, item);
        }
    }

    public override SortedSet<T> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}

internal class PrimitiveImmutableHashSetSerializer<T, TCodec> : Serializer<ImmutableHashSet<T>>
    where T : notnull
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    private static readonly ImmutableHashSetSerializer<T> Fallback = new();




    public override ImmutableHashSet<T> DefaultValue => null!;

    public override void WriteData(WriteContext context, in ImmutableHashSet<T> value, bool hasGenerics)
    {
        if (TCodec.IsNullable)
        {
            throw new InvalidDataException("nullable primitive codecs are not supported for immutable-hash-set fast path");
        }

        ImmutableHashSet<T> set = value ?? ImmutableHashSet<T>.Empty;
        PrimitiveCollectionHeader.WriteListHeader(context, set.Count, hasGenerics, TCodec.WireTypeId, false);
        foreach (T item in set)
        {
            TCodec.Write(context, item);
        }
    }

    public override ImmutableHashSet<T> ReadData(ReadContext context)
    {
        return Fallback.ReadData(context);
    }
}
