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

public sealed class ReadContext
{
    private const int MinRemoteTypeMetaLimit = 8192;

    private readonly ReusableArray<TypeMeta> _readTypeMetas = new();
    private readonly Dictionary<ulong, TypeMeta> _cachedTypeMetasByHeader = [];
    private TypeMeta? _firstReadTypeMeta;
    private bool _hasFirstReadTypeMeta;

    private readonly List<MetaString> _readMetaStrings = [];

    internal readonly UInt64Map<TypeInfo> _readTypeInfoByType = new();
    internal readonly List<uint> _reservedRefIds = [];
    private readonly int _maxDynamicReadDepth;
    internal Type? _typeMetaType;
    internal TypeMeta? _typeMeta;
    internal UInt64Map<TypeMeta>? _typeMetaByType;
    internal Type? _cachedTypeMetaType;
    internal TypeMeta? _cachedTypeMeta;
    internal int _currentDynamicReadDepth;
    private readonly Dictionary<object, int> _remoteSchemaVersionsByType = [];
    private readonly Config _config;
    private int _totalAcceptedSchemaVersions;

    public ReadContext(
        ByteReader reader,
        TypeResolver typeResolver,
        Config config)
    {
        ArgumentNullException.ThrowIfNull(config);

        Reader = reader;
        TypeResolver = typeResolver;
        TrackRef = config.TrackRef;
        Compatible = config.Compatible;
        CheckStructVersion = config.CheckStructVersion;
        RefReader = new RefReader();
        _maxDynamicReadDepth = config.MaxDepth;
        _config = config;
    }

    public ByteReader Reader { get; private set; }

    public TypeResolver TypeResolver { get; }

    public bool TrackRef { get; }

    public bool Compatible { get; }

    public bool CheckStructVersion { get; }

    internal RefReader RefReader { get; }

    internal void ResetFor(ByteReader reader)
    {
        Reader = reader;
        Reset();
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.AggressiveInlining)]
    internal TypeMeta? GetReadTypeMeta(int index)
    {
        if (index < 0)
        {
            return null;
        }

        if (index == 0)
        {
            return _hasFirstReadTypeMeta ? _firstReadTypeMeta : null;
        }

        return _readTypeMetas.Get(index - 1);
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.AggressiveInlining)]
    internal void StoreReadTypeMeta(TypeMeta typeMeta, int index)
    {
        if (index < 0)
        {
            throw new InvalidDataException("negative type meta index");
        }

        if (index == 0)
        {
            _firstReadTypeMeta = typeMeta;
            _hasFirstReadTypeMeta = true;
            return;
        }

        if (!_hasFirstReadTypeMeta)
        {
            throw new InvalidDataException(
                $"type meta index gap: index={index}, missing index 0");
        }

        int listIndex = index - 1;
        if (listIndex == _readTypeMetas.Count)
        {
            _readTypeMetas.Add(typeMeta);
            return;
        }

        if (listIndex < _readTypeMetas.Count)
        {
            _readTypeMetas.Set(listIndex, typeMeta);
            return;
        }

        throw new InvalidDataException(
            $"type meta index gap: index={index}, count={_readTypeMetas.Count + 1}");
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.AggressiveInlining)]
    internal bool TryGetCachedReadTypeMeta(ulong header, out TypeMeta typeMeta)
    {
        if (_cachedTypeMetasByHeader.TryGetValue(header, out TypeMeta? cached) && cached is not null)
        {
            typeMeta = cached;
            return true;
        }

        typeMeta = null!;
        return false;
    }

    internal void CacheReadTypeMeta(ulong header, TypeMeta typeMeta)
    {
        if (_cachedTypeMetasByHeader.ContainsKey(header))
        {
            return;
        }

        object typeKey = CheckRemoteTypeMetaLimit(typeMeta);
        _cachedTypeMetasByHeader.Add(header, typeMeta);
        RecordRemoteTypeMeta(typeKey);
    }

    internal void CacheExactLocalTypeMeta(ulong header, TypeMeta typeMeta)
    {
        _cachedTypeMetasByHeader.TryAdd(header, typeMeta);
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.NoInlining)]
    private object CheckRemoteTypeMetaLimit(TypeMeta typeMeta)
    {
        object typeKey;
        if (typeMeta.RegisterByName)
        {
            typeKey = $"{typeMeta.NamespaceName.Value}\0{typeMeta.TypeName.Value}";
        }
        else if (typeMeta.UserTypeId.HasValue)
        {
            typeKey = typeMeta.UserTypeId.Value;
        }
        else
        {
            throw new InvalidDataException("remote metadata is missing type identity");
        }
        _remoteSchemaVersionsByType.TryGetValue(typeKey, out int versionsForType);
        int maxSchemaVersionsPerType = _config.MaxSchemaVersionsPerType;
        if (versionsForType >= maxSchemaVersionsPerType)
        {
            throw new InvalidDataException(
                $"Remote schema version limit exceeded for type {typeKey}: {versionsForType} >= {maxSchemaVersionsPerType}. " +
                "The data may be malicious. If the data is not malicious, please increase MaxSchemaVersionsPerType.");
        }

        int acceptedTypeCount = versionsForType == 0
            ? _remoteSchemaVersionsByType.Count + 1
            : _remoteSchemaVersionsByType.Count;
        int maxAverageSchemaVersionsPerType = _config.MaxAverageSchemaVersionsPerType;
        long globalLimit = Math.Max(
            MinRemoteTypeMetaLimit,
            (long)acceptedTypeCount * maxAverageSchemaVersionsPerType);
        if (_totalAcceptedSchemaVersions >= globalLimit)
        {
            throw new InvalidDataException(
                $"Remote schema version limit exceeded: {_totalAcceptedSchemaVersions} metadata versions for " +
                $"{acceptedTypeCount} accepted remote types exceeds the average limit {maxAverageSchemaVersionsPerType}. " +
                "The data may be malicious. If the data is not malicious, please increase MaxAverageSchemaVersionsPerType.");
        }

        return typeKey;
    }

    private void RecordRemoteTypeMeta(object typeKey)
    {
        _remoteSchemaVersionsByType.TryGetValue(typeKey, out int versionsForType);
        _remoteSchemaVersionsByType[typeKey] = versionsForType + 1;
        _totalAcceptedSchemaVersions++;
    }

    internal MetaString? GetReadMetaString(int index)
    {
        return index >= 0 && index < _readMetaStrings.Count ? _readMetaStrings[index] : null;
    }

    internal void AppendReadMetaString(MetaString value)
    {
        _readMetaStrings.Add(value);
    }

    internal TypeMeta ReadTypeMeta()
    {
        if (TryReadTypeMetaRef(out int index, out TypeMeta typeMeta))
        {
            return typeMeta;
        }

        ulong header = Reader.ReadUInt64();
        if (TryGetCachedReadTypeMeta(header, out TypeMeta cachedTypeMeta))
        {
            TypeMeta.SkipBody(Reader, header);
            StoreReadTypeMeta(cachedTypeMeta, index);
            return cachedTypeMeta;
        }

        Reader.MoveBack(sizeof(ulong));
        typeMeta = DecodeReadTypeMeta();
        CacheReadTypeMeta(header, typeMeta);
        StoreReadTypeMeta(typeMeta, index);
        return typeMeta;
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.AggressiveInlining)]
    internal bool TryReadTypeMetaRef(out int index, out TypeMeta typeMeta)
    {
        uint indexMarker = Reader.ReadVarUInt32();
        bool isRef = (indexMarker & 1) == 1;
        index = checked((int)(indexMarker >> 1));
        if (isRef)
        {
            TypeMeta? cached = GetReadTypeMeta(index);
            if (cached is null)
            {
                throw new InvalidDataException($"unknown type meta ref index {index}");
            }

            typeMeta = cached;
            return true;
        }

        typeMeta = null!;
        return false;
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.NoInlining)]
    internal bool IsExactLocalTypeMeta(int start, int end, TypeInfo exactLocal)
    {
        TypeInfo.TypeMetaCacheEntry local = exactLocal.GetTypeMetaCacheEntry(TrackRef);
        if (!IsStructMeta(local.TypeMeta))
        {
            return false;
        }

        byte[] encoded = local.EncodedBytes;
        if (end - start != encoded.Length ||
            !Reader.Storage.AsSpan(start, encoded.Length).SequenceEqual(encoded))
        {
            return false;
        }

        TypeMeta.CheckFieldCount(local.TypeMeta.Fields.Count, _config.MaxTypeFields);
        return true;
    }

    private static bool IsStructMeta(TypeMeta typeMeta)
    {
        return typeMeta.TypeId is uint typeId &&
               (TypeId)typeId is TypeId.Struct or
                   TypeId.CompatibleStruct or
                   TypeId.NamedStruct or
                   TypeId.NamedCompatibleStruct;
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.NoInlining)]
    internal TypeMeta DecodeReadTypeMeta()
    {
        return TypeMeta.Decode(Reader, _config.MaxTypeFields, _config.MaxTypeMetaBytes);
    }

    internal void StoreTypeMeta(Type type, TypeMeta typeMeta)
    {
        ulong typeKey = TypeMapKey.Get(type);
        if (_cachedTypeMetaType == type && ReferenceEquals(_cachedTypeMeta, typeMeta))
        {
            return;
        }

        if (ReferenceEquals(_typeMetaType, type))
        {
            if (ReferenceEquals(_typeMeta, typeMeta))
            {
                _cachedTypeMetaType = type;
                _cachedTypeMeta = typeMeta;
                return;
            }

            _typeMeta = typeMeta;
            _cachedTypeMetaType = type;
            _cachedTypeMeta = typeMeta;
            return;
        }

        if (_typeMetaType is null)
        {
            _typeMetaType = type;
            _typeMeta = typeMeta;
            _cachedTypeMetaType = type;
            _cachedTypeMeta = typeMeta;
            return;
        }

        if (_typeMetaByType is null)
        {
            _typeMetaByType = new UInt64Map<TypeMeta>();
            if (_typeMeta is not null)
            {
                _typeMetaByType.Set(TypeMapKey.Get(_typeMetaType!), _typeMeta);
            }
        }
        else if (_typeMetaByType.TryGetValue(typeKey, out TypeMeta? existing) &&
                 ReferenceEquals(existing, typeMeta))
        {
            _cachedTypeMetaType = type;
            _cachedTypeMeta = typeMeta;
            return;
        }

        _typeMetaByType.Set(typeKey, typeMeta);
        _cachedTypeMetaType = type;
        _cachedTypeMeta = typeMeta;
    }

    public TypeMeta? GetTypeMeta<T>()
    {
        return GetTypeMeta(typeof(T));
    }

    private TypeMeta? GetTypeMeta(Type type)
    {
        ulong typeKey = TypeMapKey.Get(type);
        if (_cachedTypeMetaType == type && _cachedTypeMeta is not null)
        {
            return _cachedTypeMeta;
        }

        if (ReferenceEquals(_typeMetaType, type) &&
            _typeMeta is not null)
        {
            _cachedTypeMetaType = type;
            _cachedTypeMeta = _typeMeta;
            return _typeMeta;
        }

        if (_typeMetaByType is null ||
            !_typeMetaByType.TryGetValue(typeKey, out TypeMeta? typeMeta) ||
            typeMeta is null)
        {
            return null;
        }

        _cachedTypeMetaType = type;
        _cachedTypeMeta = typeMeta;
        return typeMeta;
    }

    internal void SetReadTypeInfo(Type type, TypeInfo typeInfo)
    {
        _readTypeInfoByType.Set(TypeMapKey.Get(type), typeInfo);
    }

    internal TypeInfo? GetReadTypeInfo(Type type)
    {
        return _readTypeInfoByType.TryGetValue(TypeMapKey.Get(type), out TypeInfo? typeInfo) ? typeInfo : null;
    }

    internal void ClearReadTypeInfo(Type type)
    {
        _readTypeInfoByType.Remove(TypeMapKey.Get(type));
    }

    public void StoreRef(object? value)
    {
        if (_reservedRefIds.Count == 0)
        {
            return;
        }

        RefReader.StoreRefAt(_reservedRefIds[^1], value);
    }

    internal void SetReservedRefId(uint refId)
    {
        _reservedRefIds.Add(refId);
    }

    internal void ClearReservedRefId()
    {
        if (_reservedRefIds.Count > 0)
        {
            _reservedRefIds.RemoveAt(_reservedRefIds.Count - 1);
        }
    }

    internal void IncreaseReadDepth()
    {
        _currentDynamicReadDepth += 1;
        if (_currentDynamicReadDepth > _maxDynamicReadDepth)
        {
            throw new InvalidDataException(
                $"maximum dynamic object nesting depth ({_maxDynamicReadDepth}) exceeded. current depth: {_currentDynamicReadDepth}");
        }
    }

    internal void DecreaseReadDepth()
    {
        if (_currentDynamicReadDepth > 0)
        {
            _currentDynamicReadDepth -= 1;
        }
    }

    internal void Reset()
    {
        RefReader.Reset();
        _typeMetaType = null;
        _typeMeta = null;
        _typeMetaByType?.ClearKeys();
        _readTypeInfoByType.ClearKeys();
        _reservedRefIds.Clear();
        _cachedTypeMetaType = null;
        _cachedTypeMeta = null;
        _currentDynamicReadDepth = 0;
        _firstReadTypeMeta = null;
        _hasFirstReadTypeMeta = false;
        _readTypeMetas.Clear();
        _readMetaStrings.Clear();
    }
}
