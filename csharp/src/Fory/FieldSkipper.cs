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

public static class FieldSkipper
{
    public static void SkipFieldValue(ReadContext context, TypeMetaFieldType fieldType)
    {
        _ = ReadFieldValue(context, fieldType);
    }

    private static object? ReadFieldValue(
        ReadContext context,
        TypeMetaFieldType fieldType,
        RefMode? refModeOverride = null,
        TypeInfo? declaredTypeInfo = null)
    {
        RefMode refMode = refModeOverride ?? RefModeExtensions.From(fieldType.Nullable, fieldType.TrackRef);
        if (refMode == RefMode.Tracking &&
            declaredTypeInfo is null &&
            IsStructLikeTypeId((TypeId)fieldType.TypeId))
        {
            sbyte marker = context.Reader.ReadInt8();
            context.Reader.MoveBack(1);
            if (marker > 0)
            {
                refMode = RefMode.None;
            }
        }

        switch (refMode)
        {
            case RefMode.None:
                return ReadNonNullFieldValue(context, fieldType, declaredTypeInfo);
            case RefMode.NullOnly:
                {
                    sbyte flag = context.Reader.ReadInt8();
                    if (flag == (sbyte)RefFlag.Null)
                    {
                        return null;
                    }

                    if (flag != (sbyte)RefFlag.NotNullValue)
                    {
                        throw new InvalidDataException($"unexpected nullOnly flag {flag}");
                    }

                    return ReadNonNullFieldValue(context, fieldType, declaredTypeInfo);
                }
            case RefMode.Tracking:
                {
                    RefFlag flag = context.RefReader.ReadRefFlag(context.Reader);
                    switch (flag)
                    {
                        case RefFlag.Null:
                            return null;
                        case RefFlag.Ref:
                            return context.RefReader.GetRefValue(context.RefReader.ReadRefId(context.Reader));
                        case RefFlag.RefValue:
                            {
                                uint reservedRefId = context.RefReader.ReserveRefId();
                                context.SetReservedRefId(reservedRefId);
                                try
                                {
                                    object? value = ReadNonNullFieldValue(context, fieldType, declaredTypeInfo);
                                    context.StoreRef(value);
                                    return value;
                                }
                                finally
                                {
                                    context.ClearReservedRefId();
                                }
                            }
                        case RefFlag.NotNullValue:
                            return ReadNonNullFieldValue(context, fieldType, declaredTypeInfo);
                        default:
                            throw new RefException($"invalid ref flag {(sbyte)flag}");
                    }
                }
            default:
                throw new InvalidDataException($"unsupported ref mode {refMode}");
        }
    }

    private static object? ReadNonNullFieldValue(
        ReadContext context,
        TypeMetaFieldType fieldType,
        TypeInfo? declaredTypeInfo)
    {
        TypeInfo? resolvedTypeInfo = declaredTypeInfo;
        if (fieldType.TypeId == (uint)TypeId.Unknown && resolvedTypeInfo is null)
        {
            resolvedTypeInfo = ReadDeclaredTypeInfo(context);
        }

        TypeId wireTypeId = resolvedTypeInfo?.WireTypeId ?? (TypeId)fieldType.TypeId;
        switch (wireTypeId)
        {
            case TypeId.Bool:
                context.Reader.Skip(1);
                return null;
            case TypeId.Int8:
                context.Reader.Skip(1);
                return null;
            case TypeId.Int16:
                context.Reader.Skip(2);
                return null;
            case TypeId.Int32:
                context.Reader.Skip(4);
                return null;
            case TypeId.VarInt32:
                _ = context.Reader.ReadVarInt32();
                return null;
            case TypeId.Int64:
                context.Reader.Skip(8);
                return null;
            case TypeId.VarInt64:
                _ = context.Reader.ReadVarInt64();
                return null;
            case TypeId.TaggedInt64:
                _ = context.Reader.ReadTaggedInt64();
                return null;
            case TypeId.UInt8:
                context.Reader.Skip(1);
                return null;
            case TypeId.UInt16:
                context.Reader.Skip(2);
                return null;
            case TypeId.UInt32:
                context.Reader.Skip(4);
                return null;
            case TypeId.VarUInt32:
                _ = context.Reader.ReadVarUInt32();
                return null;
            case TypeId.UInt64:
                context.Reader.Skip(8);
                return null;
            case TypeId.VarUInt64:
                _ = context.Reader.ReadVarUInt64();
                return null;
            case TypeId.TaggedUInt64:
                _ = context.Reader.ReadTaggedUInt64();
                return null;
            case TypeId.Float16:
            case TypeId.BFloat16:
                context.Reader.Skip(2);
                return null;
            case TypeId.Float32:
                context.Reader.Skip(4);
                return null;
            case TypeId.Float64:
                context.Reader.Skip(8);
                return null;
            case TypeId.String:
                SkipStringPayload(context);
                return null;
            case TypeId.Decimal:
                SkipDecimalPayload(context);
                return null;
            case TypeId.Binary:
            case TypeId.BoolArray:
            case TypeId.Int8Array:
            case TypeId.UInt8Array:
            case TypeId.Int16Array:
            case TypeId.Int32Array:
            case TypeId.Int64Array:
            case TypeId.UInt16Array:
            case TypeId.UInt32Array:
            case TypeId.UInt64Array:
            case TypeId.Float16Array:
            case TypeId.BFloat16Array:
            case TypeId.Float32Array:
            case TypeId.Float64Array:
                SkipSizedPayload(context);
                return null;
            case TypeId.Date:
                _ = context.Reader.ReadVarInt64();
                return null;
            case TypeId.Timestamp:
                context.Reader.Skip(12);
                return null;
            case TypeId.Duration:
                _ = context.Reader.ReadVarInt64();
                context.Reader.Skip(4);
                return null;
            case TypeId.Enum:
            case TypeId.NamedEnum:
                _ = context.Reader.ReadVarUInt32();
                return null;
            case TypeId.Union:
            case TypeId.TypedUnion:
            case TypeId.NamedUnion:
                return context.TypeResolver.GetSerializer<Union>().ReadData(context);
            case TypeId.Struct:
            case TypeId.CompatibleStruct:
            case TypeId.NamedStruct:
            case TypeId.NamedCompatibleStruct:
            case TypeId.Ext:
            case TypeId.NamedExt:
                {
                    TypeInfo actualTypeInfo = resolvedTypeInfo ?? ReadDeclaredTypeInfo(context);
                    return context.TypeResolver.ReadAnyValue(actualTypeInfo, context);
                }
            case TypeId.List:
                return ReadCollectionValue(context, fieldType, DynamicContainerCodec.ReadListPayload);
            case TypeId.Set:
                return ReadCollectionValue(context, fieldType, DynamicContainerCodec.ReadSetPayload);
            case TypeId.Map:
                return ReadMapValue(context, fieldType);
            case TypeId.None:
                return null;
            default:
                if (resolvedTypeInfo is not null)
                {
                    return context.TypeResolver.ReadAnyValue(resolvedTypeInfo, context);
                }

                throw new InvalidDataException($"unsupported compatible field type id {fieldType.TypeId}");
        }
    }

    private static object? ReadCollectionValue(
        ReadContext context,
        TypeMetaFieldType fieldType,
        Func<ReadContext, object?> dynamicReader)
    {
        if (fieldType.Generics.Count != 1)
        {
            return dynamicReader(context);
        }

        TypeMetaFieldType elementType = fieldType.Generics[0];
        int length = checked((int)context.Reader.ReadVarUInt32());
        if (length == 0)
        {
            return null;
        }

        byte header = context.Reader.ReadUInt8();
        bool trackRef = (header & CollectionBits.TrackingRef) != 0;
        bool hasNull = (header & CollectionBits.HasNull) != 0;
        bool declared = (header & CollectionBits.DeclaredElementType) != 0;
        bool sameType = (header & CollectionBits.SameType) != 0;
        if (!sameType)
        {
            throw new InvalidDataException("unsupported compatible collection element layout");
        }

        TypeMetaFieldType normalizedElementType = NormalizeContainerFieldType(elementType);
        if (normalizedElementType.TypeId == (uint)TypeId.Struct &&
            declared &&
            !StartsWithStructTypeInfo(context))
        {
            normalizedElementType = new TypeMetaFieldType((uint)TypeId.Decimal, normalizedElementType.Nullable, false);
        }

        TypeInfo? elementTypeInfo = null;
        if (!declared || RequiresDeclaredTypeInfo(normalizedElementType))
        {
            elementTypeInfo = ReadDeclaredTypeInfo(context);
        }

        RefMode elementRefMode = trackRef
            ? RefMode.Tracking
            : hasNull
                ? RefMode.NullOnly
                : RefMode.None;
        for (int i = 0; i < length; i++)
        {
            _ = ReadFieldValue(context, normalizedElementType, elementRefMode, elementTypeInfo);
        }

        return null;
    }

    private static object? ReadMapValue(ReadContext context, TypeMetaFieldType fieldType)
    {
        if (fieldType.Generics.Count != 2)
        {
            return DynamicContainerCodec.ReadMapPayload(context);
        }

        TypeMetaFieldType keyType = fieldType.Generics[0];
        TypeMetaFieldType valueType = fieldType.Generics[1];
        int totalLength = checked((int)context.Reader.ReadVarUInt32());
        if (totalLength == 0)
        {
            return null;
        }

        int readCount = 0;
        while (readCount < totalLength)
        {
            byte header = context.Reader.ReadUInt8();
            bool trackKeyRef = (header & DictionaryBits.TrackingKeyRef) != 0;
            bool keyNull = (header & DictionaryBits.KeyNull) != 0;
            bool keyDeclared = (header & DictionaryBits.DeclaredKeyType) != 0;
            bool trackValueRef = (header & DictionaryBits.TrackingValueRef) != 0;
            bool valueNull = (header & DictionaryBits.ValueNull) != 0;
            bool valueDeclared = (header & DictionaryBits.DeclaredValueType) != 0;

            if (keyNull && valueNull)
            {
                readCount += 1;
                continue;
            }

            if (keyNull || valueNull)
            {
                if (!keyNull)
                {
                    TypeMetaFieldType singularKeyType = NormalizeContainerFieldType(keyType);
                    if (singularKeyType.TypeId == (uint)TypeId.Struct && keyDeclared)
                    {
                        singularKeyType = new TypeMetaFieldType((uint)TypeId.Decimal, singularKeyType.Nullable, false);
                    }

                    TypeInfo? keyTypeInfo = keyDeclared && !RequiresDeclaredTypeInfo(singularKeyType)
                        ? null
                        : ReadDeclaredTypeInfo(context);
                    _ = ReadFieldValue(
                        context,
                        singularKeyType,
                        trackKeyRef ? RefMode.Tracking : RefMode.None,
                        keyTypeInfo);
                }

                if (!valueNull)
                {
                    TypeMetaFieldType singularValueType = NormalizeContainerFieldType(valueType);
                    if (singularValueType.TypeId == (uint)TypeId.Struct && valueDeclared)
                    {
                        singularValueType = new TypeMetaFieldType((uint)TypeId.Decimal, singularValueType.Nullable, false);
                    }

                    TypeInfo? valueTypeInfo = valueDeclared && !RequiresDeclaredTypeInfo(singularValueType)
                        ? null
                        : ReadDeclaredTypeInfo(context);
                    _ = ReadFieldValue(
                        context,
                        singularValueType,
                        trackValueRef ? RefMode.Tracking : RefMode.None,
                        valueTypeInfo);
                }

                readCount += 1;
                continue;
            }

            int chunkSize = context.Reader.ReadUInt8();
            if (chunkSize == 0)
            {
                throw new InvalidDataException("invalid compatible map chunk size 0");
            }

            TypeMetaFieldType normalizedKeyType = NormalizeContainerFieldType(keyType);
            if (normalizedKeyType.TypeId == (uint)TypeId.Struct && keyDeclared)
            {
                normalizedKeyType = new TypeMetaFieldType((uint)TypeId.Decimal, normalizedKeyType.Nullable, false);
            }

            TypeInfo? sharedKeyTypeInfo = keyDeclared && !RequiresDeclaredTypeInfo(normalizedKeyType)
                ? null
                : ReadDeclaredTypeInfo(context);
            TypeMetaFieldType normalizedValueType = NormalizeContainerFieldType(valueType);
            if (normalizedValueType.TypeId == (uint)TypeId.Struct && valueDeclared)
            {
                normalizedValueType = new TypeMetaFieldType((uint)TypeId.Decimal, normalizedValueType.Nullable, false);
            }

            TypeInfo? sharedValueTypeInfo = valueDeclared && !RequiresDeclaredTypeInfo(normalizedValueType)
                ? null
                : ReadDeclaredTypeInfo(context);

            for (int i = 0; i < chunkSize; i++)
            {
                _ = ReadFieldValue(
                    context,
                    normalizedKeyType,
                    trackKeyRef ? RefMode.Tracking : RefMode.None,
                    sharedKeyTypeInfo);
                _ = ReadFieldValue(
                    context,
                    normalizedValueType,
                    trackValueRef ? RefMode.Tracking : RefMode.None,
                    sharedValueTypeInfo);
            }

            readCount += chunkSize;
        }

        return null;
    }

    private static bool RequiresDeclaredTypeInfo(TypeMetaFieldType fieldType)
    {
        return TypeResolver.NeedToWriteTypeInfoForField((TypeId)fieldType.TypeId);
    }

    private static TypeInfo ReadDeclaredTypeInfo(ReadContext context)
    {
        return context.TypeResolver.ReadAnyTypeInfo(context);
    }

    private static bool IsStructLikeTypeId(TypeId typeId)
    {
        return typeId is
            TypeId.Struct or
            TypeId.CompatibleStruct or
            TypeId.NamedStruct or
            TypeId.NamedCompatibleStruct or
            TypeId.Ext or
            TypeId.NamedExt;
    }

    private static TypeMetaFieldType NormalizeContainerFieldType(TypeMetaFieldType fieldType)
    {
        if (IsBinaryContainerFieldType(fieldType))
        {
            return new TypeMetaFieldType((uint)TypeId.Binary, fieldType.Nullable, false);
        }

        return fieldType;
    }

    private static bool IsBinaryContainerFieldType(TypeMetaFieldType fieldType)
    {
        return fieldType.TypeId == (uint)TypeId.List &&
               fieldType.Generics.Count == 1 &&
               fieldType.Generics[0].TypeId == (uint)TypeId.UInt8 &&
               !fieldType.Generics[0].Nullable;
    }

    private static bool StartsWithStructTypeInfo(ReadContext context)
    {
        byte marker = context.Reader.ReadUInt8();
        context.Reader.MoveBack(1);
        return IsStructLikeTypeId((TypeId)marker);
    }

    private static void SkipSizedPayload(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        context.Reader.Skip(payloadSize);
    }

    private static void SkipStringPayload(ReadContext context)
    {
        ulong header = context.Reader.ReadVarUInt36Small();
        int byteLength = checked((int)(header >> 2));
        context.Reader.Skip(byteLength);
    }

    private static void SkipDecimalPayload(ReadContext context)
    {
        _ = context.Reader.ReadVarInt32();
        ulong header = context.Reader.ReadVarUInt64();
        if ((header & 1UL) == 0UL)
        {
            return;
        }

        ulong payloadLength = header >> 2;
        if (payloadLength == 0 || payloadLength > int.MaxValue)
        {
            throw new InvalidDataException($"invalid decimal magnitude length {payloadLength}");
        }

        context.Reader.Skip(checked((int)payloadLength));
    }
}
