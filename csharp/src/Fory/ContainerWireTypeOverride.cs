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

namespace Apache.Fory;

internal readonly record struct ContainerWireTypeOverride(
    TypeId? ElementTypeId = null,
    TypeId? KeyTypeId = null,
    TypeId? ValueTypeId = null)
{
    public bool HasOverrides => ElementTypeId.HasValue || KeyTypeId.HasValue || ValueTypeId.HasValue;
}

internal static class NumericWireTypeCodec
{
    public static bool TryResolveTypeId(Type type, FieldEncoding encoding, out TypeId typeId)
    {
        Type normalized = Nullable.GetUnderlyingType(type) ?? type;
        if (normalized == typeof(int))
        {
            typeId = encoding switch
            {
                FieldEncoding.Fixed => TypeId.Int32,
                FieldEncoding.Varint => TypeId.VarInt32,
                _ => default,
            };
            return encoding is FieldEncoding.Fixed or FieldEncoding.Varint;
        }

        if (normalized == typeof(long))
        {
            typeId = encoding switch
            {
                FieldEncoding.Fixed => TypeId.Int64,
                FieldEncoding.Varint => TypeId.VarInt64,
                FieldEncoding.Tagged => TypeId.TaggedInt64,
                _ => default,
            };
            return true;
        }

        if (normalized == typeof(uint))
        {
            typeId = encoding switch
            {
                FieldEncoding.Fixed => TypeId.UInt32,
                FieldEncoding.Varint => TypeId.VarUInt32,
                _ => default,
            };
            return encoding is FieldEncoding.Fixed or FieldEncoding.Varint;
        }

        if (normalized == typeof(ulong))
        {
            typeId = encoding switch
            {
                FieldEncoding.Fixed => TypeId.UInt64,
                FieldEncoding.Varint => TypeId.VarUInt64,
                FieldEncoding.Tagged => TypeId.TaggedUInt64,
                _ => default,
            };
            return true;
        }

        typeId = default;
        return false;
    }

    public static bool Supports(Type type, TypeId typeId)
    {
        Type normalized = Nullable.GetUnderlyingType(type) ?? type;
        return normalized == typeof(int) && typeId is TypeId.Int32 or TypeId.VarInt32 ||
               normalized == typeof(long) && typeId is TypeId.Int64 or TypeId.VarInt64 or TypeId.TaggedInt64 ||
               normalized == typeof(uint) && typeId is TypeId.UInt32 or TypeId.VarUInt32 ||
               normalized == typeof(ulong) && typeId is TypeId.UInt64 or TypeId.VarUInt64 or TypeId.TaggedUInt64;
    }

    public static void WriteValue(WriteContext context, TypeId typeId, object value)
    {
        switch (typeId)
        {
            case TypeId.Int32:
                context.Writer.WriteInt32((int)value);
                return;
            case TypeId.VarInt32:
                context.Writer.WriteVarInt32((int)value);
                return;
            case TypeId.Int64:
                context.Writer.WriteInt64((long)value);
                return;
            case TypeId.VarInt64:
                context.Writer.WriteVarInt64((long)value);
                return;
            case TypeId.TaggedInt64:
                context.Writer.WriteTaggedInt64((long)value);
                return;
            case TypeId.UInt32:
                context.Writer.WriteUInt32((uint)value);
                return;
            case TypeId.VarUInt32:
                context.Writer.WriteVarUInt32((uint)value);
                return;
            case TypeId.UInt64:
                context.Writer.WriteUInt64((ulong)value);
                return;
            case TypeId.VarUInt64:
                context.Writer.WriteVarUInt64((ulong)value);
                return;
            case TypeId.TaggedUInt64:
                context.Writer.WriteTaggedUInt64((ulong)value);
                return;
            default:
                throw new InvalidDataException($"unsupported numeric wire type {typeId}");
        }
    }

    public static object ReadValue(ReadContext context, TypeId typeId)
    {
        return typeId switch
        {
            TypeId.Int32 => context.Reader.ReadInt32(),
            TypeId.VarInt32 => context.Reader.ReadVarInt32(),
            TypeId.Int64 => context.Reader.ReadInt64(),
            TypeId.VarInt64 => context.Reader.ReadVarInt64(),
            TypeId.TaggedInt64 => context.Reader.ReadTaggedInt64(),
            TypeId.UInt32 => context.Reader.ReadUInt32(),
            TypeId.VarUInt32 => context.Reader.ReadVarUInt32(),
            TypeId.UInt64 => context.Reader.ReadUInt64(),
            TypeId.VarUInt64 => context.Reader.ReadVarUInt64(),
            TypeId.TaggedUInt64 => context.Reader.ReadTaggedUInt64(),
            _ => throw new InvalidDataException($"unsupported numeric wire type {typeId}"),
        };
    }

    public static void WriteTypeInfo(WriteContext context, TypeId typeId)
    {
        context.Writer.WriteUInt8((byte)typeId);
    }

    public static void ReadAndValidateTypeInfo(ReadContext context, TypeId expectedTypeId)
    {
        uint actualTypeId = context.Reader.ReadVarUInt32();
        if (actualTypeId != (uint)expectedTypeId)
        {
            throw new TypeMismatchException((uint)expectedTypeId, actualTypeId);
        }
    }
}
