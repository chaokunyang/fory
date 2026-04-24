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

using System.Collections.Concurrent;

namespace Apache.Fory;

internal static class DictionaryBits
{
    public const byte TrackingKeyRef = 0b0000_0001;
    public const byte KeyNull = 0b0000_0010;
    public const byte DeclaredKeyType = 0b0000_0100;
    public const byte TrackingValueRef = 0b0000_1000;
    public const byte ValueNull = 0b0001_0000;
    public const byte DeclaredValueType = 0b0010_0000;
}

public abstract class DictionaryLikeSerializer<TDictionary, TKey, TValue> : Serializer<TDictionary>
    where TDictionary : class, IDictionary<TKey, TValue>
    where TKey : notnull
{
    public override TDictionary DefaultValue => null!;

    protected abstract TDictionary CreateMap(int capacity);

    protected virtual KeyValuePair<TKey, TValue>[] SnapshotPairs(TDictionary map)
    {
        return [.. map];
    }

    protected virtual void SetValue(TDictionary map, TKey key, TValue value)
    {
        map[key] = value;
    }

    public override void WriteData(WriteContext context, in TDictionary value, bool hasGenerics)
    {
        Serializer<TKey> keySerializer = context.TypeResolver.GetSerializer<TKey>();
        Serializer<TValue> valueSerializer = context.TypeResolver.GetSerializer<TValue>();
        TypeInfo keyTypeInfo = context.TypeResolver.GetTypeInfo<TKey>();
        TypeInfo valueTypeInfo = context.TypeResolver.GetTypeInfo<TValue>();
        bool hasKeyWireTypeOverride = TryResolveNumericWireTypeOverride<TKey>(context, isKey: true, out TypeId keyWireTypeId);
        bool hasValueWireTypeOverride = TryResolveNumericWireTypeOverride<TValue>(context, isKey: false, out TypeId valueWireTypeId);
        TDictionary map = value ?? CreateMap(0);
        context.Writer.WriteVarUInt32((uint)map.Count);
        if (map.Count == 0)
        {
            return;
        }

        bool trackKeyRef = context.TrackRef && keyTypeInfo.IsRefType;
        bool trackValueRef = context.TrackRef && valueTypeInfo.IsRefType;
        bool keyNeedsTypeInfo = hasKeyWireTypeOverride
            ? TypeResolver.NeedToWriteTypeInfoForField(keyWireTypeId)
            : TypeResolver.NeedToWriteTypeInfoForField(keyTypeInfo);
        bool valueNeedsTypeInfo = hasValueWireTypeOverride
            ? TypeResolver.NeedToWriteTypeInfoForField(valueWireTypeId)
            : TypeResolver.NeedToWriteTypeInfoForField(valueTypeInfo);
        bool keyDeclared = hasGenerics && !keyNeedsTypeInfo;
        bool valueDeclared = hasGenerics && !valueNeedsTypeInfo;
        bool keyDynamicType = keyTypeInfo.IsDynamicType;
        bool valueDynamicType = valueTypeInfo.IsDynamicType;

        KeyValuePair<TKey, TValue>[] pairs = SnapshotPairs(map);
        if (keyDynamicType || valueDynamicType)
        {
            WriteDynamicMapPairs(
                pairs,
                context,
                hasGenerics,
                trackKeyRef,
                trackValueRef,
                keyDeclared,
                valueDeclared,
                keyDynamicType,
                valueDynamicType,
                hasKeyWireTypeOverride,
                hasValueWireTypeOverride,
                keyWireTypeId,
                valueWireTypeId,
                keyTypeInfo,
                valueTypeInfo,
                keySerializer,
                valueSerializer);
            return;
        }

        int index = 0;
        while (index < pairs.Length)
        {
            KeyValuePair<TKey, TValue> pair = pairs[index];
            bool keyIsNull = context.TypeResolver.IsNoneObject(keyTypeInfo, pair.Key);
            bool valueIsNull = context.TypeResolver.IsNoneObject(valueTypeInfo, pair.Value);
            if (keyIsNull || valueIsNull)
            {
                byte header = 0;
                if (trackKeyRef)
                {
                    header |= DictionaryBits.TrackingKeyRef;
                }

                if (trackValueRef)
                {
                    header |= DictionaryBits.TrackingValueRef;
                }

                if (keyIsNull)
                {
                    header |= DictionaryBits.KeyNull;
                }

                if (valueIsNull)
                {
                    header |= DictionaryBits.ValueNull;
                }

                if (!keyIsNull && keyDeclared)
                {
                    header |= DictionaryBits.DeclaredKeyType;
                }

                if (!valueIsNull && valueDeclared)
                {
                    header |= DictionaryBits.DeclaredValueType;
                }

                context.Writer.WriteUInt8(header);
                if (!keyIsNull)
                {
                    if (!keyDeclared)
                    {
                        WriteMapElementTypeInfo(
                            context,
                            keySerializer,
                            hasKeyWireTypeOverride,
                            keyWireTypeId);
                    }

                    WriteMapElement(
                        context,
                        keySerializer,
                        pair.Key,
                        trackKeyRef ? RefMode.Tracking : RefMode.None,
                        hasGenerics,
                        hasKeyWireTypeOverride,
                        keyWireTypeId);
                }

                if (!valueIsNull)
                {
                    if (!valueDeclared)
                    {
                        WriteMapElementTypeInfo(
                            context,
                            valueSerializer,
                            hasValueWireTypeOverride,
                            valueWireTypeId);
                    }

                    WriteMapElement(
                        context,
                        valueSerializer,
                        pair.Value,
                        trackValueRef ? RefMode.Tracking : RefMode.None,
                        hasGenerics,
                        hasValueWireTypeOverride,
                        valueWireTypeId);
                }

                index += 1;
                continue;
            }

            byte blockHeader = 0;
            if (trackKeyRef)
            {
                blockHeader |= DictionaryBits.TrackingKeyRef;
            }

            if (trackValueRef)
            {
                blockHeader |= DictionaryBits.TrackingValueRef;
            }

            if (keyDeclared)
            {
                blockHeader |= DictionaryBits.DeclaredKeyType;
            }

            if (valueDeclared)
            {
                blockHeader |= DictionaryBits.DeclaredValueType;
            }

            context.Writer.WriteUInt8(blockHeader);
            int chunkSizeOffset = context.Writer.Count;
            context.Writer.WriteUInt8(0);
            if (!keyDeclared)
            {
                WriteMapElementTypeInfo(
                    context,
                    keySerializer,
                    hasKeyWireTypeOverride,
                    keyWireTypeId);
            }

            if (!valueDeclared)
            {
                WriteMapElementTypeInfo(
                    context,
                    valueSerializer,
                    hasValueWireTypeOverride,
                    valueWireTypeId);
            }

            byte chunkSize = 0;
            while (index < pairs.Length && chunkSize < byte.MaxValue)
            {
                KeyValuePair<TKey, TValue> current = pairs[index];
                if (context.TypeResolver.IsNoneObject(keyTypeInfo, current.Key) ||
                    context.TypeResolver.IsNoneObject(valueTypeInfo, current.Value))
                {
                    break;
                }

                WriteMapElement(
                    context,
                    keySerializer,
                    current.Key,
                    trackKeyRef ? RefMode.Tracking : RefMode.None,
                    hasGenerics,
                    hasKeyWireTypeOverride,
                    keyWireTypeId);
                WriteMapElement(
                    context,
                    valueSerializer,
                    current.Value,
                    trackValueRef ? RefMode.Tracking : RefMode.None,
                    hasGenerics,
                    hasValueWireTypeOverride,
                    valueWireTypeId);
                chunkSize += 1;
                index += 1;
            }

            context.Writer.SetByte(chunkSizeOffset, chunkSize);
        }
    }

    public override TDictionary ReadData(ReadContext context)
    {
        Serializer<TKey> keySerializer = context.TypeResolver.GetSerializer<TKey>();
        Serializer<TValue> valueSerializer = context.TypeResolver.GetSerializer<TValue>();
        TypeInfo keyTypeInfo = context.TypeResolver.GetTypeInfo<TKey>();
        TypeInfo valueTypeInfo = context.TypeResolver.GetTypeInfo<TValue>();
        bool hasKeyWireTypeOverride = TryResolveNumericWireTypeOverride<TKey>(context, isKey: true, out TypeId keyWireTypeId);
        bool hasValueWireTypeOverride = TryResolveNumericWireTypeOverride<TValue>(context, isKey: false, out TypeId valueWireTypeId);
        int totalLength = checked((int)context.Reader.ReadVarUInt32());
        if (totalLength == 0)
        {
            return CreateMap(0);
        }

        TDictionary map = CreateMap(totalLength);
        bool keyDynamicType = keyTypeInfo.IsDynamicType;
        bool valueDynamicType = valueTypeInfo.IsDynamicType;
        bool canonicalizeValues = context.TrackRef && valueTypeInfo.IsRefType;

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
                // Dictionary-like containers cannot represent a null key.
                // Drop this entry instead of mapping it to default(TKey), which would corrupt key semantics.
                readCount += 1;
                continue;
            }

            if (keyNull)
            {
                _ = ReadValueElement(
                    context,
                    trackValueRef,
                    !valueDeclared,
                    canonicalizeValues,
                    valueSerializer,
                    hasValueWireTypeOverride,
                    valueWireTypeId);

                // Preserve stream/reference state by reading value payload, then skip null-key entry.
                readCount += 1;
                continue;
            }

            if (valueNull)
            {
                TKey key = ReadKeyElement(
                    context,
                    trackKeyRef ? RefMode.Tracking : RefMode.None,
                    !keyDeclared,
                    keySerializer,
                    hasKeyWireTypeOverride,
                    keyWireTypeId);

                SetValue(map, key, (TValue)valueSerializer.DefaultObject!);
                readCount += 1;
                continue;
            }

            int chunkSize = context.Reader.ReadUInt8();
            if (keyDynamicType || valueDynamicType)
            {
                for (int i = 0; i < chunkSize; i++)
                {
                    TypeInfo? keyTypeInfoForRead = null;
                    TypeInfo? valueTypeInfoForRead = null;

                    if (!keyDeclared)
                    {
                        if (keyDynamicType)
                        {
                            keyTypeInfoForRead = context.TypeResolver.ReadAnyTypeInfo(context);
                        }
                        else
                        {
                            ReadMapElementTypeInfo(
                                context,
                                keySerializer,
                                hasKeyWireTypeOverride,
                                keyWireTypeId);
                        }
                    }

                    if (!valueDeclared)
                    {
                        if (valueDynamicType)
                        {
                            valueTypeInfoForRead = context.TypeResolver.ReadAnyTypeInfo(context);
                        }
                        else
                        {
                            ReadMapElementTypeInfo(
                                context,
                                valueSerializer,
                                hasValueWireTypeOverride,
                                valueWireTypeId);
                        }
                    }

                    if (keyTypeInfoForRead is not null)
                    {
                        context.SetReadTypeInfo(typeof(TKey), keyTypeInfoForRead);
                    }

                    TKey key = ReadKeyElement(
                        context,
                        trackKeyRef ? RefMode.Tracking : RefMode.None,
                        false,
                        keySerializer,
                        hasKeyWireTypeOverride,
                        keyWireTypeId);
                    if (keyTypeInfoForRead is not null)
                    {
                        context.ClearReadTypeInfo(typeof(TKey));
                    }

                    if (valueTypeInfoForRead is not null)
                    {
                        context.SetReadTypeInfo(typeof(TValue), valueTypeInfoForRead);
                    }

                    TValue value = ReadValueElement(
                        context,
                        trackValueRef,
                        false,
                        canonicalizeValues,
                        valueSerializer,
                        hasValueWireTypeOverride,
                        valueWireTypeId);
                    if (valueTypeInfoForRead is not null)
                    {
                        context.ClearReadTypeInfo(typeof(TValue));
                    }

                    SetValue(map, key, value);
                }

                readCount += chunkSize;
                continue;
            }

            if (!keyDeclared)
            {
                ReadMapElementTypeInfo(
                    context,
                    keySerializer,
                    hasKeyWireTypeOverride,
                    keyWireTypeId);
            }

            if (!valueDeclared)
            {
                ReadMapElementTypeInfo(
                    context,
                    valueSerializer,
                    hasValueWireTypeOverride,
                    valueWireTypeId);
            }

            for (int i = 0; i < chunkSize; i++)
            {
                TKey key = ReadKeyElement(
                    context,
                    trackKeyRef ? RefMode.Tracking : RefMode.None,
                    false,
                    keySerializer,
                    hasKeyWireTypeOverride,
                    keyWireTypeId);
                TValue value = ReadValueElement(
                    context,
                    trackValueRef,
                    false,
                    canonicalizeValues,
                    valueSerializer,
                    hasValueWireTypeOverride,
                    valueWireTypeId);
                SetValue(map, key, value);
            }

            if (!keyDeclared)
            {
                context.ClearReadTypeInfo(typeof(TKey));
            }

            if (!valueDeclared)
            {
                context.ClearReadTypeInfo(typeof(TValue));
            }

            readCount += chunkSize;
        }

        return map;
    }

    private static bool TryResolveNumericWireTypeOverride<T>(
        WriteContext context,
        bool isKey,
        out TypeId wireTypeId)
    {
        bool hasOverride = isKey
            ? context.TryGetMapKeyWireTypeOverride(out wireTypeId)
            : context.TryGetMapValueWireTypeOverride(out wireTypeId);
        if (!hasOverride)
        {
            return false;
        }

        if (!NumericWireTypeCodec.Supports(typeof(T), wireTypeId))
        {
            string role = isKey ? "map key" : "map value";
            throw new InvalidDataException($"wire type override {wireTypeId} is not supported for {role} type {typeof(T)}");
        }

        return true;
    }

    private static bool TryResolveNumericWireTypeOverride<T>(
        ReadContext context,
        bool isKey,
        out TypeId wireTypeId)
    {
        bool hasOverride = isKey
            ? context.TryGetMapKeyWireTypeOverride(out wireTypeId)
            : context.TryGetMapValueWireTypeOverride(out wireTypeId);
        if (!hasOverride)
        {
            return false;
        }

        if (!NumericWireTypeCodec.Supports(typeof(T), wireTypeId))
        {
            string role = isKey ? "map key" : "map value";
            throw new InvalidDataException($"wire type override {wireTypeId} is not supported for {role} type {typeof(T)}");
        }

        return true;
    }

    private static void WriteDynamicMapPairs(
        KeyValuePair<TKey, TValue>[] pairs,
        WriteContext context,
        bool hasGenerics,
        bool trackKeyRef,
        bool trackValueRef,
        bool keyDeclared,
        bool valueDeclared,
        bool keyDynamicType,
        bool valueDynamicType,
        bool hasKeyWireTypeOverride,
        bool hasValueWireTypeOverride,
        TypeId keyWireTypeId,
        TypeId valueWireTypeId,
        TypeInfo keyTypeInfo,
        TypeInfo valueTypeInfo,
        Serializer<TKey> keySerializer,
        Serializer<TValue> valueSerializer)
    {
        foreach (KeyValuePair<TKey, TValue> pair in pairs)
        {
            bool keyIsNull = context.TypeResolver.IsNoneObject(keyTypeInfo, pair.Key);
            bool valueIsNull = context.TypeResolver.IsNoneObject(valueTypeInfo, pair.Value);
            byte header = 0;
            if (trackKeyRef)
            {
                header |= DictionaryBits.TrackingKeyRef;
            }

            if (trackValueRef)
            {
                header |= DictionaryBits.TrackingValueRef;
            }

            if (keyIsNull)
            {
                header |= DictionaryBits.KeyNull;
            }
            else if (!keyDynamicType && keyDeclared)
            {
                header |= DictionaryBits.DeclaredKeyType;
            }

            if (valueIsNull)
            {
                header |= DictionaryBits.ValueNull;
            }
            else if (!valueDynamicType && valueDeclared)
            {
                header |= DictionaryBits.DeclaredValueType;
            }

            context.Writer.WriteUInt8(header);
            if (keyIsNull && valueIsNull)
            {
                continue;
            }

            if (keyIsNull)
            {
                if (!valueDeclared)
                {
                    if (valueDynamicType)
                    {
                        DynamicAnyCodec.WriteAnyTypeInfo(pair.Value!, context);
                    }
                    else
                    {
                        WriteMapElementTypeInfo(
                            context,
                            valueSerializer,
                            hasValueWireTypeOverride,
                            valueWireTypeId);
                    }
                }

                WriteMapElement(
                    context,
                    valueSerializer,
                    pair.Value,
                    trackValueRef ? RefMode.Tracking : RefMode.None,
                    hasGenerics,
                    hasValueWireTypeOverride,
                    valueWireTypeId);
                continue;
            }

            if (valueIsNull)
            {
                if (!keyDeclared)
                {
                    if (keyDynamicType)
                    {
                        DynamicAnyCodec.WriteAnyTypeInfo(pair.Key!, context);
                    }
                    else
                    {
                        WriteMapElementTypeInfo(
                            context,
                            keySerializer,
                            hasKeyWireTypeOverride,
                            keyWireTypeId);
                    }
                }

                WriteMapElement(
                    context,
                    keySerializer,
                    pair.Key,
                    trackKeyRef ? RefMode.Tracking : RefMode.None,
                    hasGenerics,
                    hasKeyWireTypeOverride,
                    keyWireTypeId);
                continue;
            }

            context.Writer.WriteUInt8(1);
            if (!keyDeclared)
            {
                if (keyDynamicType)
                {
                    DynamicAnyCodec.WriteAnyTypeInfo(pair.Key!, context);
                }
                else
                {
                    WriteMapElementTypeInfo(
                        context,
                        keySerializer,
                        hasKeyWireTypeOverride,
                        keyWireTypeId);
                }
            }

            if (!valueDeclared)
            {
                if (valueDynamicType)
                {
                    DynamicAnyCodec.WriteAnyTypeInfo(pair.Value!, context);
                }
                else
                {
                    WriteMapElementTypeInfo(
                        context,
                        valueSerializer,
                        hasValueWireTypeOverride,
                        valueWireTypeId);
                }
            }

            WriteMapElement(
                context,
                keySerializer,
                pair.Key,
                trackKeyRef ? RefMode.Tracking : RefMode.None,
                hasGenerics,
                hasKeyWireTypeOverride,
                keyWireTypeId);
            WriteMapElement(
                context,
                valueSerializer,
                pair.Value,
                trackValueRef ? RefMode.Tracking : RefMode.None,
                hasGenerics,
                hasValueWireTypeOverride,
                valueWireTypeId);
        }
    }

    private static void WriteMapElementTypeInfo<T>(
        WriteContext context,
        Serializer<T> serializer,
        bool hasWireTypeOverride,
        TypeId wireTypeId)
    {
        if (hasWireTypeOverride)
        {
            NumericWireTypeCodec.WriteTypeInfo(context, wireTypeId);
            return;
        }

        context.TypeResolver.WriteTypeInfo(serializer, context);
    }

    private static void WriteMapElement<T>(
        WriteContext context,
        Serializer<T> serializer,
        T value,
        RefMode refMode,
        bool hasGenerics,
        bool hasWireTypeOverride,
        TypeId wireTypeId)
    {
        if (hasWireTypeOverride)
        {
            NumericWireTypeCodec.WriteValue(context, wireTypeId, value!);
            return;
        }

        serializer.Write(context, value, refMode, false, hasGenerics);
    }

    private static void ReadMapElementTypeInfo<T>(
        ReadContext context,
        Serializer<T> serializer,
        bool hasWireTypeOverride,
        TypeId wireTypeId)
    {
        if (hasWireTypeOverride)
        {
            NumericWireTypeCodec.ReadAndValidateTypeInfo(context, wireTypeId);
            return;
        }

        context.TypeResolver.ReadTypeInfo(serializer, context);
    }

    private static TKey ReadKeyElement(
        ReadContext context,
        RefMode refMode,
        bool readTypeInfo,
        Serializer<TKey> keySerializer,
        bool hasWireTypeOverride,
        TypeId wireTypeId)
    {
        if (hasWireTypeOverride)
        {
            if (readTypeInfo)
            {
                NumericWireTypeCodec.ReadAndValidateTypeInfo(context, wireTypeId);
            }

            return (TKey)NumericWireTypeCodec.ReadValue(context, wireTypeId);
        }

        return keySerializer.Read(context, refMode, readTypeInfo);
    }

    private static TValue ReadValueElement(
        ReadContext context,
        bool trackValueRef,
        bool readTypeInfo,
        bool canonicalizeValues,
        Serializer<TValue> valueSerializer,
        bool hasWireTypeOverride,
        TypeId wireTypeId)
    {
        if (hasWireTypeOverride)
        {
            if (readTypeInfo)
            {
                NumericWireTypeCodec.ReadAndValidateTypeInfo(context, wireTypeId);
            }

            return (TValue)NumericWireTypeCodec.ReadValue(context, wireTypeId);
        }

        if (trackValueRef || !canonicalizeValues)
        {
            return valueSerializer.Read(context, trackValueRef ? RefMode.Tracking : RefMode.None, readTypeInfo);
        }

        int start = context.Reader.Cursor;
        TValue value = valueSerializer.Read(context, RefMode.None, readTypeInfo);
        int end = context.Reader.Cursor;
        return context.CanonicalizeNonTrackingRef(value, start, end);
    }
}

public class DictionarySerializer<TKey, TValue> : DictionaryLikeSerializer<Dictionary<TKey, TValue>, TKey, TValue>
    where TKey : notnull
{
    protected override Dictionary<TKey, TValue> CreateMap(int capacity)
    {
        return new Dictionary<TKey, TValue>(capacity);
    }
}

public class SortedDictionarySerializer<TKey, TValue> : DictionaryLikeSerializer<SortedDictionary<TKey, TValue>, TKey, TValue>
    where TKey : notnull
{
    protected override SortedDictionary<TKey, TValue> CreateMap(int capacity)
    {
        _ = capacity;
        return new SortedDictionary<TKey, TValue>();
    }
}

public class SortedListSerializer<TKey, TValue> : DictionaryLikeSerializer<SortedList<TKey, TValue>, TKey, TValue>
    where TKey : notnull
{
    protected override SortedList<TKey, TValue> CreateMap(int capacity)
    {
        return new SortedList<TKey, TValue>(capacity);
    }
}

public class ConcurrentDictionarySerializer<TKey, TValue> : DictionaryLikeSerializer<ConcurrentDictionary<TKey, TValue>, TKey, TValue>
    where TKey : notnull
{
    protected override ConcurrentDictionary<TKey, TValue> CreateMap(int capacity)
    {
        int initialCapacity = Math.Max(capacity, 1);
        return new ConcurrentDictionary<TKey, TValue>(Environment.ProcessorCount, initialCapacity);
    }

    protected override KeyValuePair<TKey, TValue>[] SnapshotPairs(ConcurrentDictionary<TKey, TValue> map)
    {
        return map.ToArray();
    }
}
