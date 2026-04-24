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

using System.Collections;

namespace Apache.Fory;

internal static class CollectionValueAdapter
{
    public static object? Normalize(object? value, Type targetType)
    {
        if (value is null || targetType.IsInstanceOfType(value))
        {
            return value;
        }

        if (!TryGetElementType(targetType, out Type? elementType) || value is not IEnumerable source)
        {
            return value;
        }

        if (targetType.IsArray)
        {
            List<object?> items = new();
            foreach (object? item in source)
            {
                items.Add(ConvertElement(item, elementType!));
            }

            Array array = Array.CreateInstance(elementType!, items.Count);
            for (int i = 0; i < items.Count; i++)
            {
                array.SetValue(items[i], i);
            }

            return array;
        }

        IList typedList = (IList)Activator.CreateInstance(typeof(List<>).MakeGenericType(elementType!))!;
        foreach (object? item in source)
        {
            typedList.Add(ConvertElement(item, elementType!));
        }

        return typedList;
    }

    public static bool TryGetElementType(Type targetType, out Type? elementType)
    {
        if (targetType.IsArray)
        {
            elementType = targetType.GetElementType();
            return elementType is not null;
        }

        if (targetType.IsGenericType && targetType.GetGenericTypeDefinition() == typeof(List<>))
        {
            elementType = targetType.GetGenericArguments()[0];
            return true;
        }

        foreach (Type iface in targetType.GetInterfaces())
        {
            if (!iface.IsGenericType)
            {
                continue;
            }

            Type genericDef = iface.GetGenericTypeDefinition();
            if (genericDef == typeof(IList<>) ||
                genericDef == typeof(IReadOnlyList<>) ||
                genericDef == typeof(IEnumerable<>))
            {
                elementType = iface.GetGenericArguments()[0];
                return true;
            }
        }

        elementType = null;
        return false;
    }

    public static bool TryGetPrimitiveArrayCarrier(Type targetType, out Type? carrierType)
    {
        if (targetType.IsArray)
        {
            carrierType = targetType;
            return IsPrimitiveArrayCarrier(targetType);
        }

        if (!TryGetElementType(targetType, out Type? elementType))
        {
            carrierType = null;
            return false;
        }

        if (Nullable.GetUnderlyingType(elementType!) is not null)
        {
            carrierType = null;
            return false;
        }

        carrierType = elementType switch
        {
            { } t when t == typeof(bool) => typeof(bool[]),
            { } t when t == typeof(sbyte) => typeof(sbyte[]),
            { } t when t == typeof(byte) => typeof(byte[]),
            { } t when t == typeof(short) => typeof(short[]),
            { } t when t == typeof(int) => typeof(int[]),
            { } t when t == typeof(long) => typeof(long[]),
            { } t when t == typeof(ushort) => typeof(ushort[]),
            { } t when t == typeof(uint) => typeof(uint[]),
            { } t when t == typeof(ulong) => typeof(ulong[]),
            { } t when t == typeof(Half) => typeof(Half[]),
            { } t when t == typeof(BFloat16) => typeof(BFloat16[]),
            { } t when t == typeof(float) => typeof(float[]),
            { } t when t == typeof(double) => typeof(double[]),
            _ => null,
        };
        return carrierType is not null;
    }

    public static bool TryGetPrimitiveArrayCarrier(TypeId typeId, out Type? carrierType)
    {
        carrierType = typeId switch
        {
            TypeId.Binary or TypeId.UInt8Array => typeof(byte[]),
            TypeId.BoolArray => typeof(bool[]),
            TypeId.Int8Array => typeof(sbyte[]),
            TypeId.Int16Array => typeof(short[]),
            TypeId.Int32Array => typeof(int[]),
            TypeId.Int64Array => typeof(long[]),
            TypeId.UInt16Array => typeof(ushort[]),
            TypeId.UInt32Array => typeof(uint[]),
            TypeId.UInt64Array => typeof(ulong[]),
            TypeId.Float16Array => typeof(Half[]),
            TypeId.BFloat16Array => typeof(BFloat16[]),
            TypeId.Float32Array => typeof(float[]),
            TypeId.Float64Array => typeof(double[]),
            _ => null,
        };
        return carrierType is not null;
    }

    private static bool IsPrimitiveArrayCarrier(Type type)
    {
        return type == typeof(bool[]) ||
               type == typeof(sbyte[]) ||
               type == typeof(byte[]) ||
               type == typeof(short[]) ||
               type == typeof(int[]) ||
               type == typeof(long[]) ||
               type == typeof(ushort[]) ||
               type == typeof(uint[]) ||
               type == typeof(ulong[]) ||
               type == typeof(Half[]) ||
               type == typeof(BFloat16[]) ||
               type == typeof(float[]) ||
               type == typeof(double[]);
    }

    private static object? ConvertElement(object? value, Type elementType)
    {
        if (value is null || elementType.IsInstanceOfType(value))
        {
            return value;
        }

        Type target = Nullable.GetUnderlyingType(elementType) ?? elementType;
        try
        {
            return Convert.ChangeType(value, target);
        }
        catch
        {
            return value;
        }
    }
}
