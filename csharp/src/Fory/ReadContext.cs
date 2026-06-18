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

using System.Buffers.Binary;

namespace Apache.Fory;

public sealed class ReadContext
{
    private const int MinRemoteStructSchemaLimit = 8192;

    private readonly ReusableArray<TypeMeta> _readTypeMetas = new();
    private readonly Dictionary<ulong, TypeMeta> _cachedTypeMetasByHeader = [];
    private readonly Dictionary<object, int> _remoteSchemaVersionsByType = [];
    private TypeMeta? _firstReadTypeMeta;
    private bool _hasFirstReadTypeMeta;
    private ulong _lastMetaHeader;
    private TypeMeta? _lastTypeMeta;
    private bool _hasLastMetaHeader;
    private TypeMeta? _pendingTypeMeta;
    private ulong _pendingTypeMetaHeader;
    private int _pendingTypeMetaIndex = -1;

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

    internal bool TryGetCachedReadTypeMeta(ulong header, out TypeMeta typeMeta)
    {
        if (_hasLastMetaHeader && _lastMetaHeader == header && _lastTypeMeta is not null)
        {
            typeMeta = _lastTypeMeta;
            return true;
        }

        if (_cachedTypeMetasByHeader.TryGetValue(header, out TypeMeta? cached) && cached is not null)
        {
            _lastMetaHeader = header;
            _lastTypeMeta = cached;
            _hasLastMetaHeader = true;
            typeMeta = cached;
            return true;
        }

        typeMeta = null!;
        return false;
    }

    internal void CacheReadTypeMeta(ulong header, TypeMeta typeMeta)
    {
        if (_cachedTypeMetasByHeader.TryGetValue(header, out TypeMeta? existing) && existing is not null)
        {
            _lastMetaHeader = header;
            _lastTypeMeta = existing;
            _hasLastMetaHeader = true;
            return;
        }

        object? typeKey = CheckRemoteStructSchemaLimit(typeMeta);
        _lastMetaHeader = header;
        _lastTypeMeta = typeMeta;
        _hasLastMetaHeader = true;
        _cachedTypeMetasByHeader.TryAdd(header, typeMeta);
        RecordRemoteStructSchema(typeKey);
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.NoInlining)]
    private object? CheckRemoteStructSchemaLimit(TypeMeta typeMeta)
    {
        if (typeMeta.TypeId is not uint typeId ||
            typeId is not ((uint)TypeId.Struct or
                (uint)TypeId.CompatibleStruct or
                (uint)TypeId.NamedStruct or
                (uint)TypeId.NamedCompatibleStruct))
        {
            return null;
        }

        object typeKey = typeMeta.RegisterByName
            ? $"{typeMeta.NamespaceName.Value}\0{typeMeta.TypeName.Value}"
            : typeMeta.UserTypeId!.Value;
        _remoteSchemaVersionsByType.TryGetValue(typeKey, out int versionsForType);
        int maxSchemaVersionsPerType = _config.MaxSchemaVersionsPerType;
        if (versionsForType >= maxSchemaVersionsPerType)
        {
            throw new InvalidDataException(
                $"Remote schema version limit exceeded for type {typeKey}: {versionsForType} >= {maxSchemaVersionsPerType}. " +
                "Increase MaxSchemaVersionsPerType if this peer legitimately sends many schema versions for one type.");
        }

        int acceptedStructTypeCount = versionsForType == 0
            ? _remoteSchemaVersionsByType.Count + 1
            : _remoteSchemaVersionsByType.Count;
        int maxAverageSchemaVersionsPerType = _config.MaxAverageSchemaVersionsPerType;
        long globalLimit = Math.Max(
            MinRemoteStructSchemaLimit,
            (long)acceptedStructTypeCount * maxAverageSchemaVersionsPerType);
        if (_totalAcceptedSchemaVersions >= globalLimit)
        {
            throw new InvalidDataException(
                $"Remote schema version limit exceeded: {_totalAcceptedSchemaVersions} schemas for {acceptedStructTypeCount} " +
                $"accepted struct types exceeds the average limit {maxAverageSchemaVersionsPerType}. Increase " +
                "MaxAverageSchemaVersionsPerType if this peer legitimately sends many schema versions across many types.");
        }

        return typeKey;
    }

    private void RecordRemoteStructSchema(object? typeKey)
    {
        if (typeKey is null)
        {
            return;
        }

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

    internal void AcceptReadTypeMeta(TypeMeta typeMeta)
    {
        if (!ReferenceEquals(_pendingTypeMeta, typeMeta))
        {
            return;
        }

        CacheReadTypeMeta(_pendingTypeMetaHeader, typeMeta);
        StoreReadTypeMeta(typeMeta, _pendingTypeMetaIndex);
        _pendingTypeMeta = null;
        _pendingTypeMetaIndex = -1;
    }

    internal bool TryAcceptExactLocalTypeMeta(TypeMeta typeMeta, TypeInfo exactLocal, out TypeMeta localTypeMeta)
    {
        if (!ReferenceEquals(_pendingTypeMeta, typeMeta))
        {
            localTypeMeta = typeMeta;
            return false;
        }

        TypeInfo.TypeMetaCacheEntry local = exactLocal.GetTypeMetaCacheEntry(TrackRef);
        if (!typeMeta.Encode().AsSpan().SequenceEqual(local.EncodedBytes))
        {
            localTypeMeta = typeMeta;
            return false;
        }

        localTypeMeta = local.TypeMeta;
        StoreReadTypeMeta(localTypeMeta, _pendingTypeMetaIndex);
        _pendingTypeMeta = null;
        _pendingTypeMetaIndex = -1;
        return true;
    }

    internal TypeMeta ReadTypeMeta(TypeInfo? exactLocal = null)
    {
        uint indexMarker = Reader.ReadVarUInt32();
        bool isRef = (indexMarker & 1) == 1;
        int index = checked((int)(indexMarker >> 1));
        if (isRef)
        {
            TypeMeta? cached = GetReadTypeMeta(index);
            if (cached is null)
            {
                throw new InvalidDataException($"unknown type meta ref index {index}");
            }

            return cached;
        }

        if (_pendingTypeMeta is not null)
        {
            throw new InvalidDataException("previous type meta was not accepted");
        }

        ulong header = Reader.ReadUInt64();
        if (TryGetCachedReadTypeMeta(header, out TypeMeta cachedTypeMeta))
        {
            // Header-cache hits intentionally skip without rehashing. Entries reach this cache only
            // after a successful TypeMeta parse and 52-bit metadata-hash validation. The current body
            // size still comes from the current header bytes, not from the cached TypeMeta.
            TypeMeta.SkipBody(Reader, header);
            StoreReadTypeMeta(cachedTypeMeta, index);
            return cachedTypeMeta;
        }

        if (exactLocal is not null && TryReadExactLocalTypeMeta(header, exactLocal, out TypeMeta localTypeMeta))
        {
            StoreReadTypeMeta(localTypeMeta, index);
            return localTypeMeta;
        }

        Reader.MoveBack(sizeof(ulong));
        TypeMeta typeMeta = TypeMeta.Decode(Reader, _config.MaxTypeFields, _config.MaxTypeMetaBytes);
        _pendingTypeMeta = typeMeta;
        _pendingTypeMetaHeader = header;
        _pendingTypeMetaIndex = index;
        return typeMeta;
    }

    [System.Runtime.CompilerServices.MethodImpl(System.Runtime.CompilerServices.MethodImplOptions.NoInlining)]
    private bool TryReadExactLocalTypeMeta(ulong header, TypeInfo exactLocal, out TypeMeta typeMeta)
    {
        TypeInfo.TypeMetaCacheEntry local = exactLocal.GetTypeMetaCacheEntry(TrackRef);
        byte[] encoded = local.EncodedBytes;
        if (encoded.Length < sizeof(ulong) ||
            BinaryPrimitives.ReadUInt64LittleEndian(encoded) != header)
        {
            typeMeta = null!;
            return false;
        }

        int bodyBytes = encoded.Length - sizeof(ulong);
        TypeMeta.CheckEncodedBodySize(encoded, _config.MaxTypeMetaBytes);
        Reader.CheckBound(bodyBytes);
        int start = Reader.Cursor - sizeof(ulong);
        if (!Reader.Storage.AsSpan(start, encoded.Length).SequenceEqual(encoded))
        {
            typeMeta = null!;
            return false;
        }

        Reader.Skip(bodyBytes);
        typeMeta = local.TypeMeta;
        return true;
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
        _pendingTypeMeta = null;
        _pendingTypeMetaIndex = -1;
    }
}
