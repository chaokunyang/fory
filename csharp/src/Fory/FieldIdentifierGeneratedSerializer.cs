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
using System.Collections.Immutable;
using System.Collections.Generic;
using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using System.Linq.Expressions;
using System.Reflection;
using System.Text;

namespace Apache.Fory;

internal static class FieldIdentifierGeneratedSerializer
{
    public static Serializer<T>? TryCreate<T>(Type type, Serializer<T> serializer)
    {
        Type inspectedType = Nullable.GetUnderlyingType(type) ?? type;
        if (!TryCreateTypeMetaFieldsProvider(serializer, out Func<bool, IReadOnlyList<TypeMetaFieldInfo>> provider))
        {
            return null;
        }

        FieldBinding[]? bindings = TryBuildBindings(inspectedType, provider);
        if (bindings is null || bindings.Length == 0)
        {
            return null;
        }

        bool hasFieldIds = false;
        for (int i = 0; i < bindings.Length; i++)
        {
            if (bindings[i].NoTrackRefFieldInfo.FieldId.HasValue)
            {
                hasFieldIds = true;
                break;
            }
        }

        if (!hasFieldIds)
        {
            return null;
        }

        return new FieldIdentifierGeneratedSerializer<T>(type, serializer.DefaultValue, bindings);
    }

    private static bool TryCreateTypeMetaFieldsProvider(
        object serializer,
        out Func<bool, IReadOnlyList<TypeMetaFieldInfo>> provider)
    {
        MethodInfo? method = serializer.GetType().GetMethod(
            "TypeMetaFields",
            BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic,
            null,
            [typeof(bool)],
            null);
        if (method is null || method.ReturnType != typeof(IReadOnlyList<TypeMetaFieldInfo>))
        {
            provider = null!;
            return false;
        }

        try
        {
            Delegate del = method.CreateDelegate(typeof(Func<bool, IReadOnlyList<TypeMetaFieldInfo>>), serializer);
            provider = (Func<bool, IReadOnlyList<TypeMetaFieldInfo>>)del;
            return true;
        }
        catch
        {
            provider = null!;
            return false;
        }
    }

    private static FieldBinding[]? TryBuildBindings(
        Type type,
        Func<bool, IReadOnlyList<TypeMetaFieldInfo>> provider)
    {
        IReadOnlyDictionary<string, short> reflectedFieldIds = CreateReflectedFieldIdLookup(type);
        IReadOnlyList<TypeMetaFieldInfo> noTrackRefFields = OverlayReflectedFieldIds(provider(false), reflectedFieldIds);
        IReadOnlyList<TypeMetaFieldInfo> trackRefFields = OverlayReflectedFieldIds(provider(true), reflectedFieldIds);
        SerializableMember[] members = FindSerializableMembers(type);
        if (members.Length == 0)
        {
            return noTrackRefFields.Count == 0 ? Array.Empty<FieldBinding>() : null;
        }

        Dictionary<string, TypeMetaFieldInfo> noTrackRefLookup = CreateFieldLookup(noTrackRefFields);
        Dictionary<string, TypeMetaFieldInfo> trackRefLookup = CreateFieldLookup(trackRefFields);
        FieldBinding[] bindings = new FieldBinding[members.Length];
        for (int i = 0; i < members.Length; i++)
        {
            SerializableMember member = members[i];
            string rawFieldName = TypeMetaUtils.LowerCamelToLowerUnderscore(member.Name);
            string canonicalFieldName =
                GeneratedFieldNameResolver.GetCanonicalFieldName(type, member.Name, member.MemberType);
            if (!TryResolveFieldInfo(
                    noTrackRefLookup,
                    member.FieldId,
                    rawFieldName,
                    canonicalFieldName,
                    out TypeMetaFieldInfo? noTrackRefField) ||
                !TryResolveFieldInfo(
                    trackRefLookup,
                    member.FieldId,
                    rawFieldName,
                    canonicalFieldName,
                    out TypeMetaFieldInfo? trackRefField))
            {
                return null;
            }

            noTrackRefField = NormalizeFieldInfo(noTrackRefField, member.MemberType, canonicalFieldName, member.FieldEncoding);
            trackRefField = NormalizeFieldInfo(trackRefField, member.MemberType, canonicalFieldName, member.FieldEncoding);
            string fieldIdentifier = noTrackRefField.FieldId.HasValue
                ? noTrackRefField.FieldId.Value.ToString(CultureInfo.InvariantCulture)
                : noTrackRefField.FieldName;
            bindings[i] = new FieldBinding(
                member.Name,
                member.MemberType,
                member.Getter,
                member.Setter,
                member.OriginalIndex,
                IsCollectionLike(member.MemberType),
                ResolveContainerWireTypeOverride(member.MemberType, member.FieldEncoding),
                noTrackRefField,
                trackRefField,
                fieldIdentifier);
        }

        Array.Sort(bindings, FieldBindingComparer.Instance);
        return bindings;
    }

    private static IReadOnlyDictionary<string, short> CreateReflectedFieldIdLookup(Type type)
    {
        Dictionary<string, short> lookup = new(StringComparer.Ordinal);
        foreach (SerializableMember member in FindSerializableMembers(type))
        {
            if (!member.FieldId.HasValue)
            {
                continue;
            }

            AddLookupEntry(lookup, type, member.Name, member.MemberType, member.FieldId.Value);
        }

        return lookup;
    }

    private static void AddLookupEntry(
        Dictionary<string, short> lookup,
        Type declaringType,
        string memberName,
        Type memberType,
        short fieldId)
    {
        lookup[memberName] = fieldId;
        string normalizedName = TypeMetaUtils.LowerCamelToLowerUnderscore(memberName);
        if (!string.Equals(normalizedName, memberName, StringComparison.Ordinal))
        {
            lookup[normalizedName] = fieldId;
        }

        if (GeneratedFieldNameResolver.TryGetGeneratedAlias(
                declaringType,
                memberName,
                memberType,
                out string alias))
        {
            lookup[alias] = fieldId;
        }
    }

    private static IReadOnlyList<TypeMetaFieldInfo> OverlayReflectedFieldIds(
        IReadOnlyList<TypeMetaFieldInfo> fields,
        IReadOnlyDictionary<string, short> reflectedFieldIds)
    {
        if (reflectedFieldIds.Count == 0)
        {
            return fields;
        }

        TypeMetaFieldInfo[]? rewritten = null;
        for (int i = 0; i < fields.Count; i++)
        {
            TypeMetaFieldInfo field = fields[i];
            if (field.FieldId.HasValue ||
                !reflectedFieldIds.TryGetValue(field.FieldName, out short fieldId))
            {
                if (rewritten is not null)
                {
                    rewritten[i] = field;
                }

                continue;
            }

            rewritten ??= new TypeMetaFieldInfo[fields.Count];
            for (int copied = 0; copied < i; copied++)
            {
                rewritten[copied] = fields[copied];
            }

            rewritten[i] = new TypeMetaFieldInfo(fieldId, field.FieldName, field.FieldType);
        }

        return rewritten ?? fields;
    }

    private static Dictionary<string, TypeMetaFieldInfo> CreateFieldLookup(IReadOnlyList<TypeMetaFieldInfo> fields)
    {
        Dictionary<string, TypeMetaFieldInfo> lookup = new(StringComparer.Ordinal);
        for (int i = 0; i < fields.Count; i++)
        {
            TypeMetaFieldInfo field = fields[i];
            if (field.FieldId.HasValue)
            {
                lookup[BuildFieldIdKey(field.FieldId.Value)] = field;
            }

            lookup[field.FieldName] = field;
        }

        return lookup;
    }

    private static bool TryResolveFieldInfo(
        IReadOnlyDictionary<string, TypeMetaFieldInfo> lookup,
        short? fieldId,
        string fieldName,
        string alternateFieldName,
        [NotNullWhen(true)]
        out TypeMetaFieldInfo? fieldInfo)
    {
        if (fieldId.HasValue &&
            lookup.TryGetValue(BuildFieldIdKey(fieldId.Value), out fieldInfo))
        {
            return true;
        }

        if (lookup.TryGetValue(fieldName, out fieldInfo))
        {
            return true;
        }

        return !string.Equals(fieldName, alternateFieldName, StringComparison.Ordinal) &&
               lookup.TryGetValue(alternateFieldName, out fieldInfo);
    }

    private static TypeMetaFieldInfo NormalizeFieldInfo(
        TypeMetaFieldInfo fieldInfo,
        Type memberType,
        string canonicalFieldName,
        FieldEncoding? fieldEncoding)
    {
        TypeMetaFieldType normalizedFieldType = NormalizeFieldType(fieldInfo.FieldType, memberType, fieldEncoding);
        bool fieldNameChanged = !fieldInfo.FieldId.HasValue &&
                                !string.Equals(fieldInfo.FieldName, canonicalFieldName, StringComparison.Ordinal);
        if (!fieldNameChanged &&
            normalizedFieldType.Equals(fieldInfo.FieldType))
        {
            return fieldInfo;
        }

        return new TypeMetaFieldInfo(
            fieldInfo.FieldId,
            fieldNameChanged ? canonicalFieldName : fieldInfo.FieldName,
            normalizedFieldType);
    }

    private static TypeMetaFieldType NormalizeFieldType(
        TypeMetaFieldType fieldType,
        Type memberType,
        FieldEncoding? fieldEncoding)
    {
        TypeMetaFieldType normalizedFieldType = NormalizeFieldGenerics(fieldType, memberType, fieldEncoding);

        Type normalizedMemberType = Nullable.GetUnderlyingType(memberType) ?? memberType;
        if (IsStructLikeFieldType(normalizedFieldType.TypeId) &&
            TryResolveBuiltInOverrideTypeId(normalizedMemberType, out TypeId builtInTypeId))
        {
            normalizedFieldType = new TypeMetaFieldType(
                (uint)builtInTypeId,
                normalizedFieldType.Nullable,
                false,
                normalizedFieldType.Generics);
        }

        if (fieldEncoding.HasValue &&
            NumericWireTypeCodec.TryResolveTypeId(normalizedMemberType, fieldEncoding.Value, out TypeId explicitTypeId) &&
            normalizedFieldType.TypeId != (uint)explicitTypeId)
        {
            normalizedFieldType = new TypeMetaFieldType(
                (uint)explicitTypeId,
                normalizedFieldType.Nullable,
                normalizedFieldType.TrackRef,
                normalizedFieldType.Generics);
        }

        if (normalizedFieldType.TypeId == (uint)TypeId.Binary &&
            memberType != typeof(byte[]) &&
            CollectionValueAdapter.TryGetPrimitiveArrayCarrier(memberType, out Type? carrierType) &&
            carrierType == typeof(byte[]))
        {
            return new TypeMetaFieldType(
                (uint)TypeId.UInt8Array,
                normalizedFieldType.Nullable,
                normalizedFieldType.TrackRef,
                normalizedFieldType.Generics);
        }

        return normalizedFieldType;
    }

    private static TypeMetaFieldType NormalizeFieldGenerics(
        TypeMetaFieldType fieldType,
        Type memberType,
        FieldEncoding? fieldEncoding)
    {
        if (fieldType.Generics.Count == 0)
        {
            return fieldType;
        }

        Type? normalizedMemberType = Nullable.GetUnderlyingType(memberType) ?? memberType;
        TypeMetaFieldType[]? normalizedGenerics = null;
        switch ((TypeId)fieldType.TypeId)
        {
            case TypeId.List:
            case TypeId.Set:
                if (CollectionValueAdapter.TryGetElementType(normalizedMemberType, out Type? elementType) &&
                    elementType is not null &&
                    fieldType.Generics.Count == 1)
                {
                    TypeMetaFieldType normalizedElement = NormalizeContainerElementFieldType(
                        fieldType.Generics[0],
                        elementType,
                        fieldEncoding);
                    if (!normalizedElement.Equals(fieldType.Generics[0]))
                    {
                        normalizedGenerics = [normalizedElement];
                    }
                }

                break;
            case TypeId.Map:
                Type[] genericArguments = normalizedMemberType.GetGenericArguments();
                if (genericArguments.Length == 2 &&
                    fieldType.Generics.Count == 2)
                {
                    TypeMetaFieldType normalizedKey = NormalizeContainerElementFieldType(
                        fieldType.Generics[0],
                        genericArguments[0],
                        fieldEncoding);
                    TypeMetaFieldType normalizedValue = NormalizeContainerElementFieldType(
                        fieldType.Generics[1],
                        genericArguments[1],
                        fieldEncoding);
                    if (!normalizedKey.Equals(fieldType.Generics[0]) ||
                        !normalizedValue.Equals(fieldType.Generics[1]))
                    {
                        normalizedGenerics = [normalizedKey, normalizedValue];
                    }
                }

                break;
        }

        if (normalizedGenerics is null)
        {
            return fieldType;
        }

        return new TypeMetaFieldType(fieldType.TypeId, fieldType.Nullable, fieldType.TrackRef, normalizedGenerics);
    }

    private static TypeMetaFieldType NormalizeContainerElementFieldType(
        TypeMetaFieldType fieldType,
        Type memberType,
        FieldEncoding? fieldEncoding)
    {
        TypeMetaFieldType normalizedFieldType = NormalizeFieldType(fieldType, memberType, fieldEncoding: null);
        if (!fieldEncoding.HasValue)
        {
            return normalizedFieldType;
        }

        Type normalizedMemberType = Nullable.GetUnderlyingType(memberType) ?? memberType;
        if (!NumericWireTypeCodec.TryResolveTypeId(normalizedMemberType, fieldEncoding.Value, out TypeId explicitTypeId) ||
            normalizedFieldType.TypeId == (uint)explicitTypeId)
        {
            return normalizedFieldType;
        }

        return new TypeMetaFieldType(
            (uint)explicitTypeId,
            normalizedFieldType.Nullable,
            normalizedFieldType.TrackRef,
            normalizedFieldType.Generics);
    }

    private static bool IsStructLikeFieldType(uint typeId)
    {
        return typeId is
            (uint)TypeId.Unknown or
            (uint)TypeId.Struct or
            (uint)TypeId.CompatibleStruct or
            (uint)TypeId.NamedStruct or
            (uint)TypeId.NamedCompatibleStruct or
            (uint)TypeId.Ext or
            (uint)TypeId.NamedExt;
    }

    private static bool TryResolveBuiltInOverrideTypeId(Type memberType, out TypeId typeId)
    {
        if (memberType == typeof(ForyDecimal))
        {
            typeId = TypeId.Decimal;
            return true;
        }

        if (memberType == typeof(DateOnly))
        {
            typeId = TypeId.Date;
            return true;
        }

        if (memberType == typeof(DateTimeOffset) || memberType == typeof(DateTime))
        {
            typeId = TypeId.Timestamp;
            return true;
        }

        if (memberType == typeof(TimeSpan))
        {
            typeId = TypeId.Duration;
            return true;
        }

        if (memberType == typeof(string))
        {
            typeId = TypeId.String;
            return true;
        }

        if (memberType == typeof(Half))
        {
            typeId = TypeId.Float16;
            return true;
        }

        if (memberType == typeof(BFloat16))
        {
            typeId = TypeId.BFloat16;
            return true;
        }

        typeId = default;
        return false;
    }

    private static string BuildFieldIdKey(short fieldId)
    {
        return "#" + fieldId.ToString(CultureInfo.InvariantCulture);
    }

    private static SerializableMember[] FindSerializableMembers(Type type)
    {
        List<SerializableMember> members = [];
        foreach (PropertyInfo property in type.GetProperties(BindingFlags.Instance | BindingFlags.Public))
        {
            if (property.GetMethod is null ||
                property.SetMethod is null ||
                property.GetIndexParameters().Length != 0)
            {
                continue;
            }

            FieldAttribute? fieldAttribute = property.GetCustomAttribute<FieldAttribute>();
            short? fieldId = fieldAttribute is not null && fieldAttribute.Id >= 0 ? fieldAttribute.Id : null;
            members.Add(
                new SerializableMember(
                    property.Name,
                    property.PropertyType,
                    BuildGetter(property),
                    BuildSetter(property),
                    property.MetadataToken,
                    fieldId,
                    GetExplicitFieldEncoding(property)));
        }

        foreach (FieldInfo field in type.GetFields(BindingFlags.Instance | BindingFlags.Public))
        {
            if (field.IsStatic || field.IsInitOnly)
            {
                continue;
            }

            FieldAttribute? fieldAttribute = field.GetCustomAttribute<FieldAttribute>();
            short? fieldId = fieldAttribute is not null && fieldAttribute.Id >= 0 ? fieldAttribute.Id : null;
            members.Add(
                new SerializableMember(
                    field.Name,
                    field.FieldType,
                    BuildGetter(field),
                    BuildSetter(field),
                    field.MetadataToken,
                    fieldId,
                    GetExplicitFieldEncoding(field)));
        }

        members.Sort(static (left, right) => left.OriginalIndex.CompareTo(right.OriginalIndex));
        return members.ToArray();
    }

    private static FieldEncoding? GetExplicitFieldEncoding(MemberInfo member)
    {
        foreach (CustomAttributeData attribute in member.CustomAttributes)
        {
            if (attribute.AttributeType != typeof(FieldAttribute))
            {
                continue;
            }

            foreach (CustomAttributeNamedArgument namedArgument in attribute.NamedArguments)
            {
                if (!string.Equals(namedArgument.MemberName, nameof(FieldAttribute.Encoding), StringComparison.Ordinal))
                {
                    continue;
                }

                object? value = namedArgument.TypedValue.Value;
                if (value is FieldEncoding encoding)
                {
                    return encoding;
                }

                if (value is int rawEncoding)
                {
                    return (FieldEncoding)rawEncoding;
                }
            }

            break;
        }

        return null;
    }

    private static Func<object, object?> BuildGetter(PropertyInfo property)
    {
        return instance => property.GetValue(instance);
    }

    private static Action<object, object?> BuildSetter(PropertyInfo property)
    {
        return (instance, value) => property.SetValue(instance, value);
    }

    private static Func<object, object?> BuildGetter(FieldInfo field)
    {
        return instance => field.GetValue(instance);
    }

    private static Action<object, object?> BuildSetter(FieldInfo field)
    {
        return (instance, value) => field.SetValue(instance, value);
    }

    private static bool IsCollectionLike(Type type)
    {
        if (type.IsArray)
        {
            return true;
        }

        if (!type.IsGenericType)
        {
            return false;
        }

        Type genericType = type.GetGenericTypeDefinition();
        return genericType == typeof(List<>) ||
               genericType == typeof(LinkedList<>) ||
               genericType == typeof(Queue<>) ||
               genericType == typeof(Stack<>) ||
               genericType == typeof(HashSet<>) ||
               genericType == typeof(SortedSet<>) ||
               genericType == typeof(ImmutableHashSet<>) ||
               genericType == typeof(Dictionary<,>) ||
               genericType == typeof(SortedDictionary<,>) ||
               genericType == typeof(SortedList<,>) ||
               genericType == typeof(ConcurrentDictionary<,>) ||
               genericType == typeof(NullableKeyDictionary<,>);
    }

    private static ContainerWireTypeOverride ResolveContainerWireTypeOverride(
        Type memberType,
        FieldEncoding? fieldEncoding)
    {
        if (!fieldEncoding.HasValue)
        {
            return default;
        }

        Type normalizedType = Nullable.GetUnderlyingType(memberType) ?? memberType;
        if (TryGetListLikeElementType(normalizedType, out Type? elementType) &&
            NumericWireTypeCodec.TryResolveTypeId(elementType!, fieldEncoding.Value, out TypeId elementTypeId))
        {
            return new ContainerWireTypeOverride(ElementTypeId: elementTypeId);
        }

        if (TryGetMapTypeArguments(normalizedType, out Type? keyType, out Type? valueType))
        {
            TypeId? keyTypeId = NumericWireTypeCodec.TryResolveTypeId(
                keyType!,
                fieldEncoding.Value,
                out TypeId resolvedKeyTypeId)
                ? resolvedKeyTypeId
                : null;
            TypeId? valueTypeId = NumericWireTypeCodec.TryResolveTypeId(
                valueType!,
                fieldEncoding.Value,
                out TypeId resolvedValueTypeId)
                ? resolvedValueTypeId
                : null;
            return new ContainerWireTypeOverride(KeyTypeId: keyTypeId, ValueTypeId: valueTypeId);
        }

        return default;
    }

    private static bool TryGetListLikeElementType(Type type, out Type? elementType)
    {
        elementType = null;
        if (!type.IsGenericType)
        {
            return false;
        }

        Type genericType = type.GetGenericTypeDefinition();
        if (genericType == typeof(List<>) ||
            genericType == typeof(LinkedList<>) ||
            genericType == typeof(Queue<>) ||
            genericType == typeof(Stack<>) ||
            genericType == typeof(IList<>) ||
            genericType == typeof(IReadOnlyList<>))
        {
            elementType = type.GetGenericArguments()[0];
            return true;
        }

        return false;
    }

    private static bool TryGetMapTypeArguments(Type type, out Type? keyType, out Type? valueType)
    {
        keyType = null;
        valueType = null;
        if (!type.IsGenericType)
        {
            return false;
        }

        Type genericType = type.GetGenericTypeDefinition();
        if (genericType == typeof(Dictionary<,>) ||
            genericType == typeof(SortedDictionary<,>) ||
            genericType == typeof(SortedList<,>) ||
            genericType == typeof(ConcurrentDictionary<,>) ||
            genericType == typeof(NullableKeyDictionary<,>) ||
            genericType == typeof(IDictionary<,>) ||
            genericType == typeof(IReadOnlyDictionary<,>))
        {
            Type[] genericArgs = type.GetGenericArguments();
            keyType = genericArgs[0];
            valueType = genericArgs[1];
            return true;
        }

        return false;
    }

    private readonly record struct SerializableMember(
        string Name,
        Type MemberType,
        Func<object, object?> Getter,
        Action<object, object?> Setter,
        int OriginalIndex,
        short? FieldId,
        FieldEncoding? FieldEncoding);

    internal sealed class FieldBinding
    {
        public FieldBinding(
            string memberName,
            Type memberType,
            Func<object, object?> getter,
            Action<object, object?> setter,
            int originalIndex,
            bool hasGenerics,
            ContainerWireTypeOverride containerWireTypeOverride,
            TypeMetaFieldInfo noTrackRefFieldInfo,
            TypeMetaFieldInfo trackRefFieldInfo,
            string fieldIdentifier)
        {
            MemberName = memberName;
            MemberType = memberType;
            Getter = getter;
            Setter = setter;
            OriginalIndex = originalIndex;
            HasGenerics = hasGenerics;
            ContainerWireTypeOverride = containerWireTypeOverride;
            NoTrackRefFieldInfo = noTrackRefFieldInfo;
            TrackRefFieldInfo = trackRefFieldInfo;
            FieldIdentifier = fieldIdentifier;

            TypeId typeId = (TypeId)noTrackRefFieldInfo.FieldType.TypeId;
            Group = ResolveGroup(memberType, typeId, noTrackRefFieldInfo.FieldType.Nullable);
            PrimitiveSize = ResolvePrimitiveSize(typeId);
            CompressedPrimitive = IsCompressedPrimitive(typeId);
            SortTypeId = noTrackRefFieldInfo.FieldType.TypeId;
            DynamicAny = memberType == typeof(object);
        }

        public string MemberName { get; }

        public Type MemberType { get; }

        public Func<object, object?> Getter { get; }

        public Action<object, object?> Setter { get; }

        public int OriginalIndex { get; }

        public bool HasGenerics { get; }

        public ContainerWireTypeOverride ContainerWireTypeOverride { get; }

        public bool HasContainerWireTypeOverride => ContainerWireTypeOverride.HasOverrides;

        public TypeMetaFieldInfo NoTrackRefFieldInfo { get; }

        public TypeMetaFieldInfo TrackRefFieldInfo { get; }

        public string FieldIdentifier { get; }

        public int Group { get; }

        public int PrimitiveSize { get; }

        public bool CompressedPrimitive { get; }

        public uint SortTypeId { get; }

        public bool DynamicAny { get; }

        public TypeMetaFieldInfo GetFieldInfo(bool trackRef)
        {
            return trackRef ? TrackRefFieldInfo : NoTrackRefFieldInfo;
        }

        private static int ResolveGroup(Type memberType, TypeId typeId, bool nullable)
        {
            if (IsPrimitiveScalar(typeId))
            {
                return nullable ? 2 : 1;
            }

            if (UsesPrimitiveArrayCarrier(memberType, typeId))
            {
                return 4;
            }

            return typeId switch
            {
                TypeId.List or TypeId.Set => 5,
                TypeId.Map => 6,
                _ when IsOther(memberType, typeId) => 7,
                _ => 3,
            };
        }

        private static bool IsOther(Type memberType, TypeId typeId)
        {
            Type normalized = Nullable.GetUnderlyingType(memberType) ?? memberType;
            if (normalized == typeof(object) ||
                normalized.IsEnum ||
                typeof(Union).IsAssignableFrom(normalized))
            {
                return true;
            }

            return typeId is
                TypeId.Unknown or
                TypeId.Enum or
                TypeId.NamedEnum or
                TypeId.Struct or
                TypeId.CompatibleStruct or
                TypeId.NamedStruct or
                TypeId.NamedCompatibleStruct or
                TypeId.Ext or
                TypeId.NamedExt or
                TypeId.Union or
                TypeId.TypedUnion or
                TypeId.NamedUnion;
        }

        private static bool IsPrimitiveScalar(TypeId typeId)
        {
            return typeId is
                TypeId.Bool or
                TypeId.Int8 or
                TypeId.Int16 or
                TypeId.Int32 or
                TypeId.VarInt32 or
                TypeId.Int64 or
                TypeId.VarInt64 or
                TypeId.TaggedInt64 or
                TypeId.UInt8 or
                TypeId.UInt16 or
                TypeId.UInt32 or
                TypeId.VarUInt32 or
                TypeId.UInt64 or
                TypeId.VarUInt64 or
                TypeId.TaggedUInt64 or
                TypeId.Float8 or
                TypeId.Float16 or
                TypeId.BFloat16 or
                TypeId.Float32 or
                TypeId.Float64;
        }

        private static bool UsesPrimitiveArrayCarrier(Type memberType, TypeId typeId)
        {
            return typeId is
                       TypeId.Binary or
                       TypeId.BoolArray or
                       TypeId.Int8Array or
                       TypeId.Int16Array or
                       TypeId.Int32Array or
                       TypeId.Int64Array or
                       TypeId.UInt8Array or
                       TypeId.UInt16Array or
                       TypeId.UInt32Array or
                       TypeId.UInt64Array or
                       TypeId.Float16Array or
                       TypeId.BFloat16Array or
                       TypeId.Float32Array or
                       TypeId.Float64Array
                   && CollectionValueAdapter.TryGetElementType(memberType, out _)
                   && !memberType.IsArray;
        }

        private static int ResolvePrimitiveSize(TypeId typeId)
        {
            return typeId switch
            {
                TypeId.Bool or TypeId.Int8 or TypeId.UInt8 or TypeId.Float8 => 1,
                TypeId.Int16 or TypeId.UInt16 or TypeId.Float16 or TypeId.BFloat16 => 2,
                TypeId.Int32 or TypeId.VarInt32 or TypeId.UInt32 or TypeId.VarUInt32 or TypeId.Float32 => 4,
                TypeId.Int64 or TypeId.VarInt64 or TypeId.TaggedInt64 or
                TypeId.UInt64 or TypeId.VarUInt64 or TypeId.TaggedUInt64 or
                TypeId.Float64 => 8,
                _ => 0,
            };
        }

        private static bool IsCompressedPrimitive(TypeId typeId)
        {
            return typeId is
                TypeId.VarInt32 or
                TypeId.VarUInt32 or
                TypeId.VarInt64 or
                TypeId.VarUInt64 or
                TypeId.TaggedInt64 or
                TypeId.TaggedUInt64;
        }
    }

    private sealed class FieldBindingComparer : IComparer<FieldBinding>
    {
        public static readonly FieldBindingComparer Instance = new();

        public int Compare(FieldBinding? left, FieldBinding? right)
        {
            if (ReferenceEquals(left, right))
            {
                return 0;
            }

            if (left is null)
            {
                return -1;
            }

            if (right is null)
            {
                return 1;
            }

            int compare = left.Group.CompareTo(right.Group);
            if (compare != 0)
            {
                return compare;
            }

            if (left.Group is 1 or 2)
            {
                compare = left.CompressedPrimitive.CompareTo(right.CompressedPrimitive);
                if (compare != 0)
                {
                    return compare;
                }

                compare = right.PrimitiveSize.CompareTo(left.PrimitiveSize);
                if (compare != 0)
                {
                    return compare;
                }
            }

            if (left.Group is 1 or 2 or 3 or 4)
            {
                compare = left.SortTypeId.CompareTo(right.SortTypeId);
                if (compare != 0)
                {
                    return compare;
                }
            }

            compare = StringComparer.Ordinal.Compare(left.FieldIdentifier, right.FieldIdentifier);
            if (compare != 0)
            {
                return compare;
            }

            compare = StringComparer.Ordinal.Compare(left.MemberName, right.MemberName);
            if (compare != 0)
            {
                return compare;
            }

            return left.OriginalIndex.CompareTo(right.OriginalIndex);
        }
    }
}

internal sealed class FieldIdentifierGeneratedSerializer<T> : Serializer<T>
{
    private readonly Type _type;
    private readonly bool _referenceType;
    private readonly Func<object> _factory;
    private readonly FieldIdentifierGeneratedSerializer.FieldBinding[] _bindings;
    private readonly IReadOnlyList<TypeMetaFieldInfo> _noTrackRefFields;
    private readonly IReadOnlyList<TypeMetaFieldInfo> _trackRefFields;
    private readonly uint _schemaHashNoTrackRef;
    private readonly uint _schemaHashTrackRef;
    private readonly T _defaultValue;

    public FieldIdentifierGeneratedSerializer(
        Type type,
        T defaultValue,
        FieldIdentifierGeneratedSerializer.FieldBinding[] bindings)
    {
        _type = type;
        _referenceType = !type.IsValueType;
        _factory = BuildFactory(type);
        _bindings = bindings;
        TypeMetaFieldInfo[] noTrackRefFields = new TypeMetaFieldInfo[bindings.Length];
        TypeMetaFieldInfo[] trackRefFields = new TypeMetaFieldInfo[bindings.Length];
        for (int i = 0; i < bindings.Length; i++)
        {
            noTrackRefFields[i] = bindings[i].NoTrackRefFieldInfo;
            trackRefFields[i] = bindings[i].TrackRefFieldInfo;
        }

        _noTrackRefFields = noTrackRefFields;
        _trackRefFields = trackRefFields;
        _schemaHashNoTrackRef = BuildSchemaHash(bindings, trackRef: false);
        _schemaHashTrackRef = BuildSchemaHash(bindings, trackRef: true);
        _defaultValue = defaultValue;
    }

    public override T DefaultValue => _defaultValue;

    public override void WriteData(WriteContext context, in T value, bool hasGenerics)
    {
        _ = hasGenerics;
        if (!context.Compatible)
        {
            uint schemaHash = context.TrackRef ? _schemaHashTrackRef : _schemaHashNoTrackRef;
            context.Writer.WriteInt32(unchecked((int)schemaHash));
        }

        object instance = value as object ?? value!;
        for (int i = 0; i < _bindings.Length; i++)
        {
            WriteField(context, _bindings[i], instance);
        }
    }

    public override T ReadData(ReadContext context)
    {
        if (!context.Compatible)
        {
            uint schemaHash = unchecked((uint)context.Reader.ReadInt32());
            if (context.CheckStructVersion)
            {
                uint expectedHash = context.TrackRef ? _schemaHashTrackRef : _schemaHashNoTrackRef;
                if (schemaHash != expectedHash)
                {
                    throw new InvalidDataException(
                        $"class version hash mismatch: expected {expectedHash}, got {schemaHash}");
                }
            }
        }

        object instance = _factory();
        if (_referenceType)
        {
            context.StoreRef(instance);
        }

        if (!context.Compatible)
        {
            string? lastReadTrace = null;
            for (int i = 0; i < _bindings.Length; i++)
            {
                FieldIdentifierGeneratedSerializer.FieldBinding binding = _bindings[i];
                int startCursor = context.Reader.Cursor;
                try
                {
                    ReadFieldWithContext(
                        context,
                        binding,
                        instance,
                        binding.GetFieldInfo(context.TrackRef).FieldType,
                        readTypeInfo: false);
                    lastReadTrace =
                        $"{binding.FieldIdentifier}:{binding.MemberName}@{startCursor}->{context.Reader.Cursor}";
                }
                catch (InvalidDataException ex) when (lastReadTrace is not null)
                {
                    throw new InvalidDataException($"{ex.Message}; last successful read {lastReadTrace}");
                }
            }

            return (T)instance;
        }

        TypeMeta? typeMeta = context.GetTypeMeta<T>();
        if (typeMeta is null)
        {
            string? lastReadTrace = null;
            for (int i = 0; i < _bindings.Length; i++)
            {
                FieldIdentifierGeneratedSerializer.FieldBinding binding = _bindings[i];
                int startCursor = context.Reader.Cursor;
                try
                {
                    ReadFieldWithContext(
                        context,
                        binding,
                        instance,
                        binding.GetFieldInfo(context.TrackRef).FieldType,
                        readTypeInfo: true);
                    lastReadTrace =
                        $"{binding.FieldIdentifier}:{binding.MemberName}@{startCursor}->{context.Reader.Cursor}";
                }
                catch (InvalidDataException ex) when (lastReadTrace is not null)
                {
                    throw new InvalidDataException($"{ex.Message}; last successful read {lastReadTrace}");
                }
            }

            return (T)instance;
        }

        for (int i = 0; i < typeMeta.Fields.Count; i++)
        {
            TypeMetaFieldInfo remoteField = typeMeta.Fields[i];
            int assignedFieldId = remoteField.AssignedFieldId;
            if ((uint)assignedFieldId >= (uint)_bindings.Length)
            {
                FieldSkipper.SkipFieldValue(context, remoteField.FieldType);
                continue;
            }

            ReadFieldWithContext(
                context,
                _bindings[assignedFieldId],
                instance,
                remoteField.FieldType,
                readTypeInfo: true);
        }

        return (T)instance;
    }

    private void ReadFieldWithContext(
        ReadContext context,
        FieldIdentifierGeneratedSerializer.FieldBinding binding,
        object instance,
        TypeMetaFieldType fieldType,
        bool readTypeInfo)
    {
        int startCursor = context.Reader.Cursor;
        try
        {
            ReadField(context, binding, instance, fieldType, readTypeInfo);
        }
        catch (Exception ex) when (ex is not InvalidDataException invalidData ||
                                   !invalidData.Message.Contains(binding.MemberName, StringComparison.Ordinal))
        {
            int previewLength = Math.Min(16, context.Reader.Storage.Length - startCursor);
            string preview = previewLength > 0
                ? Convert.ToHexString(context.Reader.Storage, startCursor, previewLength)
                : string.Empty;
            throw new InvalidDataException(
                $"while reading field {binding.MemberName} ({binding.FieldIdentifier}) of {_type.Name} " +
                $"at cursor {startCursor} preview={preview}: {ex.Message}");
        }
    }

    private IReadOnlyList<TypeMetaFieldInfo> TypeMetaFields(bool trackRef)
    {
        return trackRef ? _trackRefFields : _noTrackRefFields;
    }

    private static Func<object> BuildFactory(Type type)
    {
        NewExpression create = Expression.New(type);
        UnaryExpression boxed = Expression.Convert(create, typeof(object));
        return Expression.Lambda<Func<object>>(boxed).Compile();
    }

    private static uint BuildSchemaHash(
        IReadOnlyList<FieldIdentifierGeneratedSerializer.FieldBinding> bindings,
        bool trackRef)
    {
        List<(string FieldIdentifier, uint TypeId, bool TrackRef, bool Nullable)> items = new(bindings.Count);
        for (int i = 0; i < bindings.Count; i++)
        {
            FieldIdentifierGeneratedSerializer.FieldBinding binding = bindings[i];
            TypeMetaFieldInfo fieldInfo = binding.GetFieldInfo(trackRef);
            items.Add((
                binding.FieldIdentifier,
                ResolveFingerprintTypeId(fieldInfo.FieldType.TypeId, binding.MemberType),
                fieldInfo.FieldType.TrackRef,
                fieldInfo.FieldType.Nullable));
        }

        items.Sort(static (left, right) => StringComparer.Ordinal.Compare(left.FieldIdentifier, right.FieldIdentifier));
        StringBuilder fingerprint = new();
        for (int i = 0; i < items.Count; i++)
        {
            (string fieldIdentifier, uint typeId, bool fieldTrackRef, bool nullable) = items[i];
            fingerprint
                .Append(fieldIdentifier)
                .Append(',')
                .Append(typeId)
                .Append(',')
                .Append(fieldTrackRef ? '1' : '0')
                .Append(',')
                .Append(nullable ? '1' : '0')
                .Append(';');
        }

        return SchemaHash.StructHash32(fingerprint.ToString());
    }

    private static uint ResolveFingerprintTypeId(uint declaredTypeId, Type memberType)
    {
        Type normalizedType = Nullable.GetUnderlyingType(memberType) ?? memberType;
        if (normalizedType == typeof(object) ||
            normalizedType.IsEnum ||
            typeof(Union).IsAssignableFrom(normalizedType))
        {
            return (uint)TypeId.Unknown;
        }

        TypeId typeId = (TypeId)declaredTypeId;
        return typeId switch
        {
            TypeId.Bool or
            TypeId.Int8 or
            TypeId.Int16 or
            TypeId.Int32 or
            TypeId.VarInt32 or
            TypeId.Int64 or
            TypeId.VarInt64 or
            TypeId.TaggedInt64 or
            TypeId.UInt8 or
            TypeId.UInt16 or
            TypeId.UInt32 or
            TypeId.VarUInt32 or
            TypeId.UInt64 or
            TypeId.VarUInt64 or
            TypeId.TaggedUInt64 or
            TypeId.Float8 or
            TypeId.Float16 or
            TypeId.BFloat16 or
            TypeId.Float32 or
            TypeId.Float64 or
            TypeId.String or
            TypeId.List or
            TypeId.Set or
            TypeId.Map or
            TypeId.Duration or
            TypeId.Timestamp or
            TypeId.Date or
            TypeId.Decimal or
            TypeId.Binary or
            TypeId.BoolArray or
            TypeId.Int8Array or
            TypeId.Int16Array or
            TypeId.Int32Array or
            TypeId.Int64Array or
            TypeId.UInt8Array or
            TypeId.UInt16Array or
            TypeId.UInt32Array or
            TypeId.UInt64Array or
            TypeId.Float8Array or
            TypeId.Float16Array or
            TypeId.BFloat16Array or
            TypeId.Float32Array or
            TypeId.Float64Array => declaredTypeId,
            _ => (uint)TypeId.Unknown,
        };
    }

    private static bool NeedsCompatibleTypeInfo(TypeInfo typeInfo, Type memberType)
    {
        return memberType == typeof(object) ||
               typeInfo.UserTypeKind is UserTypeKind.Struct or UserTypeKind.Ext;
    }

    private static void WriteField(
        WriteContext context,
        FieldIdentifierGeneratedSerializer.FieldBinding binding,
        object instance)
    {
        TypeMetaFieldInfo fieldInfo = binding.GetFieldInfo(context.TrackRef);
        RefMode refMode = RefModeExtensions.From(fieldInfo.FieldType.Nullable, fieldInfo.FieldType.TrackRef);
        object? value = binding.Getter(instance);
        if (TryWriteDirectFieldValue(context, fieldInfo.FieldType, refMode, value))
        {
            return;
        }

        if (binding.DynamicAny)
        {
            DynamicAnyCodec.WriteAny(context, value, refMode, writeTypeInfo: true, hasGenerics: false);
            return;
        }

        Type runtimeType = ResolveFieldCarrierType(fieldInfo.FieldType, binding.MemberType, binding);
        object? runtimeValue = runtimeType == binding.MemberType
            ? value
            : CollectionValueAdapter.Normalize(value, runtimeType);
        TypeInfo typeInfo = context.TypeResolver.GetTypeInfo(runtimeType);
        bool writeTypeInfo = context.Compatible && NeedsCompatibleTypeInfo(typeInfo, runtimeType);
        if (binding.HasContainerWireTypeOverride)
        {
            context.PushContainerWireTypeOverride(binding.ContainerWireTypeOverride);
        }

        try
        {
            context.TypeResolver.WriteObject(typeInfo, context, runtimeValue, refMode, writeTypeInfo, binding.HasGenerics);
        }
        finally
        {
            if (binding.HasContainerWireTypeOverride)
            {
                context.PopContainerWireTypeOverride();
            }
        }
    }

    private static void ReadField(
        ReadContext context,
        FieldIdentifierGeneratedSerializer.FieldBinding binding,
        object instance,
        TypeMetaFieldType fieldType,
        bool readTypeInfo)
    {
        RefMode refMode = RefModeExtensions.From(fieldType.Nullable, fieldType.TrackRef);
        if (TryReadDirectFieldValue(context, fieldType, refMode, out object? directValue))
        {
            binding.Setter(instance, directValue);
            return;
        }

        object? value;
        if (binding.DynamicAny)
        {
            value = DynamicAnyCodec.ReadAny(context, refMode, readTypeInfo: true);
        }
        else
        {
            int startCursor = context.Reader.Cursor;
            try
            {
                Type runtimeType = ResolveFieldCarrierType(fieldType, binding.MemberType, binding);
                TypeInfo typeInfo = context.TypeResolver.GetTypeInfo(runtimeType);
                bool shouldReadTypeInfo = readTypeInfo && NeedsCompatibleTypeInfo(typeInfo, runtimeType);

                if (binding.HasContainerWireTypeOverride)
                {
                    context.PushContainerWireTypeOverride(binding.ContainerWireTypeOverride);
                }

                try
                {
                    value = context.TypeResolver.ReadObject(typeInfo, context, refMode, shouldReadTypeInfo);
                }
                finally
                {
                    if (binding.HasContainerWireTypeOverride)
                    {
                        context.PopContainerWireTypeOverride();
                    }
                }

                if (runtimeType != binding.MemberType)
                {
                    value = CollectionValueAdapter.Normalize(value, binding.MemberType);
                }
            }
            catch (Exception ex) when (ex is not InvalidDataException or RefException)
            {
                string preview = CreateReadPreview(context, startCursor);
                throw new InvalidDataException(
                    $"failed reading field {binding.FieldIdentifier} ({binding.MemberName}) as {(TypeId)fieldType.TypeId} " +
                    $"into {binding.MemberType} at cursor {startCursor} preview={preview}: {ex.Message}");
            }
            catch (InvalidDataException ex)
            {
                string preview = CreateReadPreview(context, startCursor);
                throw new InvalidDataException(
                    $"failed reading field {binding.FieldIdentifier} ({binding.MemberName}) as {(TypeId)fieldType.TypeId} " +
                    $"into {binding.MemberType} at cursor {startCursor} preview={preview}: {ex.Message}");
            }
        }

        binding.Setter(instance, value);
    }

    private static Type ResolveFieldCarrierType(
        TypeMetaFieldType fieldType,
        Type memberType,
        FieldIdentifierGeneratedSerializer.FieldBinding binding)
    {
        if (binding.HasContainerWireTypeOverride)
        {
            return memberType;
        }

        if (!CollectionValueAdapter.TryGetPrimitiveArrayCarrier((TypeId)fieldType.TypeId, out Type? carrierType) ||
            carrierType == memberType ||
            !CollectionValueAdapter.TryGetElementType(memberType, out _))
        {
            return memberType;
        }

        return carrierType!;
    }

    private static string CreateReadPreview(ReadContext context, int startCursor)
    {
        int previewLength = Math.Min(16, context.Reader.Storage.Length - startCursor);
        return previewLength > 0
            ? Convert.ToHexString(context.Reader.Storage, startCursor, previewLength)
            : string.Empty;
    }

    private static bool TryWriteDirectFieldValue(
        WriteContext context,
        TypeMetaFieldType fieldType,
        RefMode refMode,
        object? value)
    {
        if (!IsDirectFieldType((TypeId)fieldType.TypeId) || refMode == RefMode.Tracking)
        {
            return false;
        }

        if (refMode == RefMode.NullOnly)
        {
            if (value is null)
            {
                context.Writer.WriteInt8((sbyte)RefFlag.Null);
                return true;
            }

            context.Writer.WriteInt8((sbyte)RefFlag.NotNullValue);
        }

        WriteDirectPayload(context, (TypeId)fieldType.TypeId, value);
        return true;
    }

    private static bool TryReadDirectFieldValue(
        ReadContext context,
        TypeMetaFieldType fieldType,
        RefMode refMode,
        out object? value)
    {
        if (!IsDirectFieldType((TypeId)fieldType.TypeId) || refMode == RefMode.Tracking)
        {
            value = null;
            return false;
        }

        if (refMode == RefMode.NullOnly)
        {
            RefFlag flag = (RefFlag)context.Reader.ReadInt8();
            switch (flag)
            {
                case RefFlag.Null:
                    value = null;
                    return true;
                case RefFlag.NotNullValue:
                    break;
                default:
                    throw new RefException($"invalid ref flag {(sbyte)flag}");
            }
        }

        value = ReadDirectPayload(context, (TypeId)fieldType.TypeId);
        return true;
    }

    private static bool IsDirectFieldType(TypeId typeId)
    {
        return typeId is
            TypeId.Bool or
            TypeId.Int8 or
            TypeId.Int16 or
            TypeId.Int32 or
            TypeId.VarInt32 or
            TypeId.Int64 or
            TypeId.VarInt64 or
            TypeId.TaggedInt64 or
            TypeId.UInt8 or
            TypeId.UInt16 or
            TypeId.UInt32 or
            TypeId.VarUInt32 or
            TypeId.UInt64 or
            TypeId.VarUInt64 or
            TypeId.TaggedUInt64 or
            TypeId.Float16 or
            TypeId.BFloat16 or
            TypeId.Float32 or
            TypeId.Float64 or
            TypeId.String;
    }

    private static void WriteDirectPayload(WriteContext context, TypeId typeId, object? value)
    {
        switch (typeId)
        {
            case TypeId.Bool:
                context.Writer.WriteUInt8((bool)value! ? (byte)1 : (byte)0);
                return;
            case TypeId.Int8:
                context.Writer.WriteInt8((sbyte)value!);
                return;
            case TypeId.Int16:
                context.Writer.WriteInt16((short)value!);
                return;
            case TypeId.Int32:
                context.Writer.WriteInt32((int)value!);
                return;
            case TypeId.VarInt32:
                context.Writer.WriteVarInt32((int)value!);
                return;
            case TypeId.Int64:
                context.Writer.WriteInt64((long)value!);
                return;
            case TypeId.VarInt64:
                context.Writer.WriteVarInt64((long)value!);
                return;
            case TypeId.TaggedInt64:
                context.Writer.WriteTaggedInt64((long)value!);
                return;
            case TypeId.UInt8:
                context.Writer.WriteUInt8((byte)value!);
                return;
            case TypeId.UInt16:
                context.Writer.WriteUInt16((ushort)value!);
                return;
            case TypeId.UInt32:
                context.Writer.WriteUInt32((uint)value!);
                return;
            case TypeId.VarUInt32:
                context.Writer.WriteVarUInt32((uint)value!);
                return;
            case TypeId.UInt64:
                context.Writer.WriteUInt64((ulong)value!);
                return;
            case TypeId.VarUInt64:
                context.Writer.WriteVarUInt64((ulong)value!);
                return;
            case TypeId.TaggedUInt64:
                context.Writer.WriteTaggedUInt64((ulong)value!);
                return;
            case TypeId.Float16:
                context.Writer.WriteUInt16(BitConverter.HalfToUInt16Bits((Half)value!));
                return;
            case TypeId.BFloat16:
                context.Writer.WriteUInt16(((BFloat16)value!).ToBits());
                return;
            case TypeId.Float32:
                context.Writer.WriteFloat32((float)value!);
                return;
            case TypeId.Float64:
                context.Writer.WriteFloat64((double)value!);
                return;
            case TypeId.String:
                StringSerializer.WriteString(context, (string)value!);
                return;
            default:
                throw new InvalidDataException($"unsupported direct field type {typeId}");
        }
    }

    private static object ReadDirectPayload(ReadContext context, TypeId typeId)
    {
        return typeId switch
        {
            TypeId.Bool => context.Reader.ReadUInt8() != 0,
            TypeId.Int8 => context.Reader.ReadInt8(),
            TypeId.Int16 => context.Reader.ReadInt16(),
            TypeId.Int32 => context.Reader.ReadInt32(),
            TypeId.VarInt32 => context.Reader.ReadVarInt32(),
            TypeId.Int64 => context.Reader.ReadInt64(),
            TypeId.VarInt64 => context.Reader.ReadVarInt64(),
            TypeId.TaggedInt64 => context.Reader.ReadTaggedInt64(),
            TypeId.UInt8 => context.Reader.ReadUInt8(),
            TypeId.UInt16 => context.Reader.ReadUInt16(),
            TypeId.UInt32 => context.Reader.ReadUInt32(),
            TypeId.VarUInt32 => context.Reader.ReadVarUInt32(),
            TypeId.UInt64 => context.Reader.ReadUInt64(),
            TypeId.VarUInt64 => context.Reader.ReadVarUInt64(),
            TypeId.TaggedUInt64 => context.Reader.ReadTaggedUInt64(),
            TypeId.Float16 => BitConverter.UInt16BitsToHalf(context.Reader.ReadUInt16()),
            TypeId.BFloat16 => BFloat16.FromBits(context.Reader.ReadUInt16()),
            TypeId.Float32 => context.Reader.ReadFloat32(),
            TypeId.Float64 => context.Reader.ReadFloat64(),
            TypeId.String => StringSerializer.ReadString(context),
            _ => throw new InvalidDataException($"unsupported direct field type {typeId}"),
        };
    }
}
