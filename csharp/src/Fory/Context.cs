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

internal sealed class TypeMetaWriteState
{
    private Dictionary<Type, uint>? _typeIndexByType;
    private Type? _firstType;
    private uint _nextIndex;

    internal (uint Index, bool IsNew) AssignIndexIfAbsent(Type type)
    {
        if (_nextIndex == 0)
        {
            _firstType = type;
            _nextIndex = 1;
            return (0, true);
        }

        if (ReferenceEquals(_firstType, type))
        {
            return (0, false);
        }

        if (_typeIndexByType is null)
        {
            _typeIndexByType = new Dictionary<Type, uint>();
        }
        else if (_typeIndexByType.TryGetValue(type, out uint existing))
        {
            return (existing, false);
        }

        uint index = _nextIndex;
        _nextIndex += 1;
        _typeIndexByType[type] = index;
        return (index, true);
    }

    internal void Reset()
    {
        _firstType = null;
        _typeIndexByType?.Clear();
        _nextIndex = 0;
    }
}

internal sealed class TypeMetaReadState
{
    private const int MaxParsedTypeMetaEntries = 8192;
    private readonly List<TypeMeta> _typeMetas = [];
    private readonly Dictionary<ulong, CachedTypeMetaEntry> _cachedTypeMetasByHeader = [];
    private TypeMeta? _firstTypeMeta;
    private bool _hasFirstTypeMeta;
    private ulong _lastMetaHeader;
    private CachedTypeMetaEntry _lastTypeMeta;
    private bool _hasLastMetaHeader;

    private readonly record struct CachedTypeMetaEntry(TypeMeta TypeMeta, int SkipBytesAfterHeader);

    internal TypeMeta? TypeMetaAt(int index)
    {
        if (index < 0)
        {
            return null;
        }

        if (index == 0)
        {
            return _hasFirstTypeMeta ? _firstTypeMeta : null;
        }

        int listIndex = index - 1;
        return listIndex >= 0 && listIndex < _typeMetas.Count ? _typeMetas[listIndex] : null;
    }

    internal void StoreTypeMeta(TypeMeta typeMeta, int index)
    {
        if (index < 0)
        {
            throw new InvalidDataException("negative type meta index");
        }

        if (index == 0)
        {
            _firstTypeMeta = typeMeta;
            _hasFirstTypeMeta = true;
            return;
        }

        if (!_hasFirstTypeMeta)
        {
            throw new InvalidDataException(
                $"type meta index gap: index={index}, missing index 0");
        }

        int listIndex = index - 1;
        if (listIndex == _typeMetas.Count)
        {
            _typeMetas.Add(typeMeta);
            return;
        }

        if (listIndex < _typeMetas.Count)
        {
            _typeMetas[listIndex] = typeMeta;
            return;
        }

        throw new InvalidDataException(
            $"type meta index gap: index={index}, count={_typeMetas.Count + 1}");
    }

    internal bool TryGetCachedTypeMeta(ulong header, out TypeMeta typeMeta, out int skipBytesAfterHeader)
    {
        if (_hasLastMetaHeader && _lastMetaHeader == header)
        {
            typeMeta = _lastTypeMeta.TypeMeta;
            skipBytesAfterHeader = _lastTypeMeta.SkipBytesAfterHeader;
            return true;
        }

        if (_cachedTypeMetasByHeader.TryGetValue(header, out CachedTypeMetaEntry cached))
        {
            _lastMetaHeader = header;
            _lastTypeMeta = cached;
            _hasLastMetaHeader = true;
            typeMeta = cached.TypeMeta;
            skipBytesAfterHeader = cached.SkipBytesAfterHeader;
            return true;
        }

        typeMeta = null!;
        skipBytesAfterHeader = 0;
        return false;
    }

    internal void CacheTypeMeta(ulong header, TypeMeta typeMeta, int skipBytesAfterHeader)
    {
        CachedTypeMetaEntry cached = new(typeMeta, skipBytesAfterHeader);
        _lastMetaHeader = header;
        _lastTypeMeta = cached;
        _hasLastMetaHeader = true;
        if (_cachedTypeMetasByHeader.Count < MaxParsedTypeMetaEntries)
        {
            _cachedTypeMetasByHeader.TryAdd(header, cached);
        }
    }

    internal void Reset()
    {
        _firstTypeMeta = null;
        _hasFirstTypeMeta = false;
        _typeMetas.Clear();
    }
}

internal sealed class MetaStringWriteState
{
    private readonly Dictionary<MetaString, uint> _stringIndexByKey = [];
    private uint _nextIndex;

    internal (uint Index, bool IsNew) AssignIndexIfAbsent(MetaString value)
    {
        if (_stringIndexByKey.TryGetValue(value, out uint existing))
        {
            return (existing, false);
        }

        uint index = _nextIndex;
        _nextIndex += 1;
        _stringIndexByKey[value] = index;
        return (index, true);
    }

    internal void Reset()
    {
        _stringIndexByKey.Clear();
        _nextIndex = 0;
    }
}

internal sealed class MetaStringReadState
{
    private readonly List<MetaString> _values = [];

    internal MetaString? ValueAt(int index)
    {
        return index >= 0 && index < _values.Count ? _values[index] : null;
    }

    internal void Append(MetaString value)
    {
        _values.Add(value);
    }

    internal void Reset()
    {
        _values.Clear();
    }
}

public sealed class WriteContext
{
    public WriteContext(
        ByteWriter writer,
        TypeResolver typeResolver,
        bool trackRef,
        bool compatible = false,
        bool checkStructVersion = false)
    {
        Writer = writer;
        TypeResolver = typeResolver;
        TrackRef = trackRef;
        Compatible = compatible;
        CheckStructVersion = checkStructVersion;
        RefWriter = new RefWriter();
        TypeMetaState = new TypeMetaWriteState();
        MetaStringWriteState = new MetaStringWriteState();
    }

    public ByteWriter Writer { get; private set; }

    public TypeResolver TypeResolver { get; }

    public bool TrackRef { get; }

    public bool Compatible { get; }

    public bool CheckStructVersion { get; }

    internal RefWriter RefWriter { get; }

    internal TypeMetaWriteState TypeMetaState { get; }

    internal MetaStringWriteState MetaStringWriteState { get; }

    internal void ResetFor(ByteWriter writer)
    {
        Writer = writer;
        Reset();
    }

    internal void WriteTypeMeta(Type type, TypeMeta typeMeta)
    {
        WriteTypeMeta(type, typeMeta.Encode());
    }

    internal void WriteTypeMeta(Type type, ReadOnlySpan<byte> encodedTypeMeta)
    {
        (uint index, bool isNew) = TypeMetaState.AssignIndexIfAbsent(type);
        if (isNew)
        {
            Writer.WriteVarUInt32(index << 1);
            Writer.WriteBytes(encodedTypeMeta);
        }
        else
        {
            Writer.WriteVarUInt32((index << 1) | 1);
        }
    }

    internal void ResetObjectState()
    {
        RefWriter.Reset();
    }

    internal void Reset()
    {
        ResetObjectState();
        TypeMetaState.Reset();
        MetaStringWriteState.Reset();
    }
}

internal readonly record struct CanonicalRefSignature(
    Type Type,
    ulong HashLo,
    ulong HashHi,
    int Length);

internal sealed class CanonicalRefEntry
{
    public required byte[] Bytes { get; init; }
    public required object Object { get; init; }
}

public sealed class ReadContext
{
    private readonly Dictionary<Type, TypeInfo> _pendingTypeInfo = [];
    private readonly Dictionary<CanonicalRefSignature, List<CanonicalRefEntry>> _canonicalRefCache = [];
    private readonly List<uint> _readRefIds = [];
    private readonly int _maxDynamicReadDepth;
    private Type? _typeMetaType;
    private TypeMeta? _typeMeta;
    private Dictionary<Type, TypeMeta>? _typeMetaByType;
    private Type? _cachedTypeMetaType;
    private TypeMeta? _cachedTypeMeta;
    private int _currentDynamicReadDepth;

    public ReadContext(
        ByteReader reader,
        TypeResolver typeResolver,
        bool trackRef,
        bool compatible = false,
        bool checkStructVersion = false,
        int maxDynamicReadDepth = 20)
    {
        if (maxDynamicReadDepth <= 0)
        {
            throw new ArgumentOutOfRangeException(nameof(maxDynamicReadDepth), "MaxDepth must be greater than 0.");
        }

        Reader = reader;
        TypeResolver = typeResolver;
        TrackRef = trackRef;
        Compatible = compatible;
        CheckStructVersion = checkStructVersion;
        RefReader = new RefReader();
        TypeMetaState = new TypeMetaReadState();
        MetaStringReadState = new MetaStringReadState();
        _maxDynamicReadDepth = maxDynamicReadDepth;
    }

    public ByteReader Reader { get; private set; }

    public TypeResolver TypeResolver { get; }

    public bool TrackRef { get; }

    public bool Compatible { get; }

    public bool CheckStructVersion { get; }

    internal RefReader RefReader { get; }

    internal TypeMetaReadState TypeMetaState { get; }

    internal MetaStringReadState MetaStringReadState { get; }

    internal void ResetFor(ByteReader reader)
    {
        Reader = reader;
        Reset();
    }

    internal TypeMeta ReadTypeMeta()
    {
        uint indexMarker = Reader.ReadVarUInt32();
        bool isRef = (indexMarker & 1) == 1;
        int index = checked((int)(indexMarker >> 1));
        if (isRef)
        {
            TypeMeta? cached = TypeMetaState.TypeMetaAt(index);
            if (cached is null)
            {
                throw new InvalidDataException($"unknown type meta ref index {index}");
            }

            return cached;
        }

        ulong header = Reader.ReadUInt64();
        if (TypeMetaState.TryGetCachedTypeMeta(
                header,
                out TypeMeta cachedTypeMeta,
                out int skipBytesAfterHeader))
        {
            Reader.Skip(skipBytesAfterHeader);
            TypeMetaState.StoreTypeMeta(cachedTypeMeta, index);
            return cachedTypeMeta;
        }

        int headerStartCursor = Reader.Cursor - sizeof(ulong);
        Reader.MoveBack(sizeof(ulong));
        TypeMeta typeMeta = TypeMeta.Decode(Reader);
        int consumedTypeMetaBytes = Reader.Cursor - headerStartCursor;
        int parsedSkipBytesAfterHeader = consumedTypeMetaBytes - sizeof(ulong);
        TypeMetaState.StoreTypeMeta(typeMeta, index);
        TypeMetaState.CacheTypeMeta(header, typeMeta, parsedSkipBytesAfterHeader);
        return typeMeta;
    }

    internal void StoreTypeMeta(Type type, TypeMeta typeMeta)
    {
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
            _typeMetaByType = new Dictionary<Type, TypeMeta>();
            if (_typeMeta is not null)
            {
                _typeMetaByType[_typeMetaType!] = _typeMeta;
            }
        }
        else if (_typeMetaByType.TryGetValue(type, out TypeMeta? existing) &&
                 ReferenceEquals(existing, typeMeta))
        {
            _cachedTypeMetaType = type;
            _cachedTypeMeta = typeMeta;
            return;
        }

        _typeMetaByType[type] = typeMeta;
        _cachedTypeMetaType = type;
        _cachedTypeMeta = typeMeta;
    }

    public TypeMeta? GetTypeMeta<T>()
    {
        return GetTypeMeta(typeof(T));
    }

    private TypeMeta? GetTypeMeta(Type type)
    {
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
            !_typeMetaByType.TryGetValue(type, out TypeMeta? typeMeta) ||
            typeMeta is null)
        {
            return null;
        }

        _cachedTypeMetaType = type;
        _cachedTypeMeta = typeMeta;
        return typeMeta;
    }

    internal void SetPendingTypeInfo(Type type, TypeInfo typeInfo)
    {
        _pendingTypeInfo[type] = typeInfo;
    }

    internal TypeInfo? PendingTypeInfo(Type type)
    {
        return _pendingTypeInfo.TryGetValue(type, out TypeInfo? typeInfo) ? typeInfo : null;
    }

    internal void ClearPendingTypeInfo(Type type)
    {
        _pendingTypeInfo.Remove(type);
    }

    public void StoreReadRef(object? value)
    {
        if (_readRefIds.Count == 0)
        {
            return;
        }

        RefReader.StoreRefAt(_readRefIds[^1], value);
    }

    internal void EnterReadRefId(uint refId)
    {
        _readRefIds.Add(refId);
    }

    internal void ExitReadRefId()
    {
        if (_readRefIds.Count > 0)
        {
            _readRefIds.RemoveAt(_readRefIds.Count - 1);
        }
    }

    internal void IncreaseDynamicReadDepth()
    {
        _currentDynamicReadDepth += 1;
        if (_currentDynamicReadDepth > _maxDynamicReadDepth)
        {
            throw new InvalidDataException(
                $"maximum dynamic object nesting depth ({_maxDynamicReadDepth}) exceeded. current depth: {_currentDynamicReadDepth}");
        }
    }

    internal void DecreaseDynamicReadDepth()
    {
        if (_currentDynamicReadDepth > 0)
        {
            _currentDynamicReadDepth -= 1;
        }
    }

    internal T CanonicalizeNonTrackingRef<T>(T value, int start, int end)
    {
        if (!TrackRef || end <= start || value is null || value is not object obj)
        {
            return value;
        }

        byte[] bytes = new byte[end - start];
        Array.Copy(Reader.Storage, start, bytes, 0, bytes.Length);
        (ulong hashLo, ulong hashHi) = MurmurHash3.X64_128(bytes, 47);
        CanonicalRefSignature signature = new(obj.GetType(), hashLo, hashHi, bytes.Length);

        if (_canonicalRefCache.TryGetValue(signature, out List<CanonicalRefEntry>? bucket))
        {
            foreach (CanonicalRefEntry entry in bucket)
            {
                if (entry.Bytes.AsSpan().SequenceEqual(bytes))
                {
                    return (T)entry.Object;
                }
            }

            bucket.Add(new CanonicalRefEntry { Bytes = bytes, Object = obj });
            return value;
        }

        _canonicalRefCache[signature] =
        [
            new CanonicalRefEntry { Bytes = bytes, Object = obj },
        ];
        return value;
    }

    internal void ResetObjectState()
    {
        RefReader.Reset();
        _typeMetaType = null;
        _typeMeta = null;
        _typeMetaByType?.Clear();
        _pendingTypeInfo.Clear();
        _canonicalRefCache.Clear();
        _readRefIds.Clear();
        _cachedTypeMetaType = null;
        _cachedTypeMeta = null;
        _currentDynamicReadDepth = 0;
    }

    internal void Reset()
    {
        ResetObjectState();
        TypeMetaState.Reset();
        MetaStringReadState.Reset();
    }
}
