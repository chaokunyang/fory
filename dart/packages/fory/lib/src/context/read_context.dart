import 'package:meta/meta.dart';
import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/context/compatible_struct_metadata_store.dart';
import 'package:fory/src/context/meta_string_reader.dart';
import 'package:fory/src/context/ref_reader.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/payload_codec.dart';
import 'package:fory/src/serializer/struct_session.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/types/float16.dart';
import 'package:fory/src/meta/meta_string.dart';

/// Read-side runtime state for a single Fory operation.
///
/// Generated and manual serializers receive this object from [Serializer.read].
/// Application code normally interacts with [Fory] instead of preparing
/// contexts directly.
final class ReadContext {
  /// Effective runtime configuration for the active operation.
  final Config config;
  final TypeResolver _typeResolver;
  final RefReader _refReader;
  final MetaStringReader _metaStringReader;
  final CompatibleStructMetadataStore _compatibleStructMetadata;

  late Buffer _buffer;
  final List<ResolvedTypeInternal> _sharedTypes = <ResolvedTypeInternal>[];
  StructReadSession? _structReadSession;
  int _depth = 0;

  @internal
  ReadContext(
    this.config,
    this._typeResolver,
    this._refReader,
    this._metaStringReader,
    this._compatibleStructMetadata,
  );

  /// Prepares the context to read from [buffer].
  ///
  /// This resets all per-operation caches, shared TypeDef state, and Ref state.
  void prepare(Buffer buffer) {
    _buffer = buffer;
    _sharedTypes.clear();
    _refReader.reset();
    _metaStringReader.reset();
    _structReadSession = null;
    _depth = 0;
  }

  /// The active input buffer for the current operation.
  Buffer get buffer => _buffer;

  @internal
  TypeResolver get typeResolver => _typeResolver;

  @internal
  RefReader get refReader => _refReader;

  @internal
  StructReadSession? get structReadSession => _structReadSession;

  @internal
  void rememberCompatibleStructMetadata(
    Object value,
    StructMetadataInternal metadata,
  ) {
    _compatibleStructMetadata.remember(value, metadata);
  }

  @internal
  ResolvedTypeInternal readTypeMetaValue() => _readTypeMeta();

  @internal
  Object readSerializerPayload(
    Serializer<Object?> serializer,
    ResolvedTypeInternal resolved,
  ) {
    final needsSentinel = resolved.supportsRef && !_refReader.hasPreservedRefId;
    if (needsSentinel) {
      _refReader.preserveRefId(-1);
    }
    try {
      return serializer.read(this) as Object;
    } finally {
      if (needsSentinel &&
          _refReader.hasPreservedRefId &&
          _refReader.lastPreservedRefId == -1) {
        _refReader.discardPreservedRefId(-1);
      }
    }
  }

  /// Records entry into one more nested read frame.
  void increaseDepth() {
    _depth += 1;
    if (_depth > config.maxDepth) {
      throw StateError('Deserialization depth exceeded ${config.maxDepth}.');
    }
  }

  /// Records exit from a nested read frame.
  void decreaseDepth() {
    _depth -= 1;
  }

  /// Reads a boolean value.
  bool readBool() => _buffer.readBool();

  /// Reads a signed 8-bit integer.
  int readByte() => _buffer.readByte();

  /// Reads an unsigned 8-bit integer.
  int readUint8() => _buffer.readUint8();

  /// Reads a signed little-endian 16-bit integer.
  int readInt16() => _buffer.readInt16();

  /// Reads a signed little-endian 32-bit integer.
  int readInt32() => _buffer.readInt32();

  /// Reads a signed little-endian 64-bit integer.
  int readInt64() => _buffer.readInt64();

  /// Reads a half-precision floating-point value.
  Float16 readFloat16() => _buffer.readFloat16();

  /// Reads a single-precision floating-point value.
  double readFloat32() => _buffer.readFloat32();

  /// Reads a double-precision floating-point value.
  double readFloat64() => _buffer.readFloat64();

  /// Reads a zig-zag encoded signed 32-bit varint.
  int readVarInt32() => _buffer.readVarInt32();

  /// Reads an unsigned 32-bit varint.
  int readVarUint32() => _buffer.readVarUint32();

  /// Reads a zig-zag encoded signed 64-bit varint.
  int readVarInt64() => _buffer.readVarInt64();

  /// Reads a tagged signed 64-bit integer.
  int readTaggedInt64() => _buffer.readTaggedInt64();

  /// Reads an unsigned 64-bit varint.
  int readVarUint64() => _buffer.readVarUint64();

  /// Reads a tagged unsigned 64-bit integer.
  int readTaggedUint64() => _buffer.readTaggedUint64();

  /// Binds [value] to the most recently preserved Ref slot.
  ///
  /// Manual serializers call this when they need to publish a newly created
  /// object before reading its recursive fields.
  void reference(Object? value) {
    _refReader.reference(value);
  }

  @internal
  StructReadSession? swapStructReadSession(StructReadSession? session) {
    final previous = _structReadSession;
    _structReadSession = session;
    return previous;
  }

  @internal
  void restoreStructReadSession(StructReadSession? previous) {
    _structReadSession = previous;
  }

  /// Reads a non-null string payload without ref/null handling.
  String readString() => readStringPayload(this);

  /// Reads a ref-or-null header and resolves back-references immediately.
  int readRefOrNull() => _refReader.readRefOrNull(_buffer);

  /// Reserves the next read ref id or reuses [refId] when provided.
  int preserveRefId([int? refId]) => _refReader.preserveRefId(refId);

  /// Reads a ref/value header and preserves a new id only for fresh values.
  int tryPreserveRefId() => _refReader.tryPreserveRefId(_buffer);

  /// Returns the last reserved read ref id.
  int get lastPreservedRefId => _refReader.lastPreservedRefId;

  /// Returns whether a reserved read ref id is waiting to be bound.
  bool get hasPreservedRefId => _refReader.hasPreservedRefId;

  /// Returns the resolved read ref or the read ref stored at [id].
  Object? getReadRef([int? id]) => _refReader.getReadRef(id);

  /// Stores [value] under a previously preserved read ref [id].
  void setReadRef(int id, Object? value) => _refReader.setReadRef(id, value);

  /// Reads a nullable value using Ref semantics and wire type metadata.
  Object? readRef() {
    final flag = _refReader.tryPreserveRefId(_buffer);
    final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
    if (flag == RefWriter.nullFlag) {
      return null;
    }
    if (flag == RefWriter.refFlag) {
      return _refReader.getReadRef();
    }
    final resolved = _readTypeMeta();
    final rootPreservedRefId = preservedRefId == null &&
            flag == RefWriter.notNullValueFlag &&
            _depth == 0 &&
            resolved.supportsRef
        ? _refReader.preserveRefId()
        : null;
    final value = readResolvedValue(resolved, null);
    if (preservedRefId != null &&
        resolved.supportsRef &&
        _refReader.readRefAt(preservedRefId) == null) {
      _refReader.setReadRef(preservedRefId, value);
    }
    if (rootPreservedRefId != null &&
        _refReader.readRefAt(rootPreservedRefId) == null) {
      _refReader.setReadRef(rootPreservedRefId, value);
    }
    return value;
  }

  /// Reads a non-null value using the type metadata stored in the payload.
  Object readNonRef() {
    final resolved = _readTypeMeta();
    return readResolvedValue(resolved, null) as Object;
  }

  /// Reads a nullable value using the standard Fory nullable framing.
  Object? readNullable() {
    final flag = _buffer.readByte();
    if (flag == RefWriter.nullFlag) {
      return null;
    }
    if (flag != RefWriter.notNullValueFlag) {
      throw StateError('Unexpected nullable flag $flag.');
    }
    return readNonRef();
  }

  @internal
  Object readPrimitiveValue(int typeId) => readPayloadPrimitive(this, typeId);

  @internal
  Object? readResolvedValue(
    ResolvedTypeInternal resolved,
    TypeShapeInternal? declaredShape,
  ) {
    return readPayloadValue(this, resolved, declaredShape);
  }

  ResolvedTypeInternal _readTypeMeta() {
    return _typeResolver.readTypeMeta(
      _buffer,
      sharedTypeAt: (index) => _sharedTypes[index],
      addSharedType: (resolved) => _sharedTypes.add(resolved),
      readPackageMetaString: _readPackageMetaStringEncoded,
      readTypeNameMetaString: _readTypeNameMetaStringEncoded,
    );
  }

  EncodedMetaStringInternal _readPackageMetaStringEncoded() =>
      _metaStringReader.readMetaString(_buffer);

  EncodedMetaStringInternal _readTypeNameMetaStringEncoded() =>
      _metaStringReader.readMetaString(_buffer);
}
