import 'package:meta/meta.dart';
import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/context/compatible_struct_metadata_store.dart';
import 'package:fory/src/context/meta_string_writer.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/payload_codec.dart';
import 'package:fory/src/serializer/struct_session.dart';
import 'package:fory/src/meta/meta_string.dart';
import 'package:fory/src/types/float16.dart';

/// Write-side runtime state for a single Fory operation.
///
/// Generated and manual serializers receive this object from [Serializer.write].
/// Application code normally interacts with [Fory] instead of constructing or
/// preparing contexts directly.
final class WriteContext {
  /// Effective runtime configuration for the active operation.
  final Config config;
  final TypeResolver _typeResolver;
  final RefWriter _refWriter;
  final MetaStringWriter _metaStringWriter;
  final CompatibleStructMetadataStore _compatibleStructMetadata;

  late Buffer _buffer;
  final Map<String, int> _typeDefIds = <String, int>{};
  StructWriteSession? _structWriteSession;
  int _nextTypeDefId = 0;
  bool _rootTrackRef = false;
  int _depth = 0;

  @internal
  WriteContext(
    this.config,
    this._typeResolver,
    this._refWriter,
    this._metaStringWriter,
    this._compatibleStructMetadata,
  );

  /// Prepares the context to write into [buffer].
  ///
  /// This resets all per-operation caches, meta-string tables, and Ref state.
  void prepare(Buffer buffer, {required bool trackRef}) {
    _buffer = buffer;
    _rootTrackRef = trackRef;
    _nextTypeDefId = 0;
    _typeDefIds.clear();
    _refWriter.reset();
    _metaStringWriter.reset();
    _structWriteSession = null;
    _depth = 0;
  }

  /// The active output buffer for the current operation.
  Buffer get buffer => _buffer;

  @internal
  TypeResolver get typeResolver => _typeResolver;

  @internal
  RefWriter get refWriter => _refWriter;

  @internal
  StructWriteSession? get structWriteSession => _structWriteSession;

  @internal
  bool get rootTrackRef => _rootTrackRef;

  @internal
  StructMetadataInternal? compatibleStructMetadataFor(Object value) {
    return _compatibleStructMetadata.metadataFor(value);
  }

  /// Records entry into one more nested write frame.
  void increaseDepth() {
    _depth += 1;
    if (_depth > config.maxDepth) {
      throw StateError('Serialization depth exceeded ${config.maxDepth}.');
    }
  }

  /// Records exit from a nested write frame.
  void decreaseDepth() {
    _depth -= 1;
  }

  /// Writes a boolean value.
  void writeBool(bool value) => _buffer.writeBool(value);

  /// Writes a signed 8-bit integer.
  void writeByte(int value) => _buffer.writeByte(value);

  /// Writes an unsigned 8-bit integer.
  void writeUint8(int value) => _buffer.writeUint8(value);

  /// Writes a signed little-endian 16-bit integer.
  void writeInt16(int value) => _buffer.writeInt16(value);

  /// Writes a signed little-endian 32-bit integer.
  void writeInt32(int value) => _buffer.writeInt32(value);

  /// Writes a signed little-endian 64-bit integer.
  void writeInt64(int value) => _buffer.writeInt64(value);

  /// Writes a half-precision floating-point value.
  void writeFloat16(Float16 value) => _buffer.writeFloat16(value);

  /// Writes a single-precision floating-point value.
  void writeFloat32(double value) => _buffer.writeFloat32(value);

  /// Writes a double-precision floating-point value.
  void writeFloat64(double value) => _buffer.writeFloat64(value);

  /// Writes a zig-zag encoded signed 32-bit varint.
  void writeVarInt32(int value) => _buffer.writeVarInt32(value);

  /// Writes an unsigned 32-bit varint.
  void writeVarUint32(int value) => _buffer.writeVarUint32(value);

  /// Writes a zig-zag encoded signed 64-bit varint.
  void writeVarInt64(int value) => _buffer.writeVarInt64(value);

  /// Writes a tagged signed 64-bit integer.
  void writeTaggedInt64(int value) => _buffer.writeTaggedInt64(value);

  /// Writes an unsigned 64-bit varint.
  void writeVarUint64(int value) => _buffer.writeVarUint64(value);

  /// Writes a tagged unsigned 64-bit integer.
  void writeTaggedUint64(int value) => _buffer.writeTaggedUint64(value);

  /// Writes a non-null string payload without adding type metadata.
  void writeString(String value) => writeStringPayload(this, value);

  @internal
  StructWriteSession? swapStructWriteSession(StructWriteSession? session) {
    final previous = _structWriteSession;
    _structWriteSession = session;
    return previous;
  }

  @internal
  void restoreStructWriteSession(StructWriteSession? previous) {
    _structWriteSession = previous;
  }

  /// Writes a nullable value with Ref tracking and type metadata.
  void writeRef(Object? value) {
    _writeRef(value, trackRef: true);
  }

  /// Writes a non-null value together with its type metadata.
  void writeNonRef(Object value) {
    final resolved = _typeResolver.resolveValue(value);
    _writeTypeMeta(resolved, value);
    writeResolvedValue(resolved, value, null);
  }

  /// Writes only the ref-or-null header for [value].
  bool writeRefOrNull(Object? value) {
    return _refWriter.writeRefOrNull(_buffer, value);
  }

  /// Writes the fresh-value or back-reference header for non-null [value].
  ///
  /// Returns `true` when the caller should continue writing payload bytes for
  /// [value], or `false` when an existing back-reference fully handled it.
  bool writeRefValueFlag(Object value) {
    return _refWriter.writeRefValueFlag(_buffer, value);
  }

  /// Writes the null flag when [value] is `null`.
  bool writeNullFlag(Object? value) {
    return _refWriter.writeNullFlag(_buffer, value);
  }

  @internal
  void writeRootValue(Object? value, {required bool trackRef}) {
    if (value == null) {
      _writeRef(value, trackRef: trackRef);
      return;
    }
    final resolved = _typeResolver.resolveValue(value);
    if (trackRef) {
      _writeRef(value, trackRef: true);
      return;
    }
    _refWriter.writeRefOrNull(_buffer, value, trackRef: false);
    if (resolved.supportsRef) {
      _refWriter.reference(value);
    }
    _writeTypeMeta(resolved, value);
    writeResolvedValue(resolved, value, null);
  }

  void _writeRef(Object? value, {required bool trackRef}) {
    final resolved = value == null ? null : _typeResolver.resolveValue(value);
    final effectiveTrackRef =
        trackRef && value != null && resolved!.supportsRef;
    if (_refWriter.writeRefOrNull(_buffer, value,
        trackRef: effectiveTrackRef)) {
      return;
    }
    if (value == null) {
      return;
    }
    _writeTypeMeta(resolved!, value);
    writeResolvedValue(resolved, value, null);
  }

  @internal
  void writePrimitiveValue(int typeId, Object value) =>
      writePayloadPrimitive(this, typeId, value);

  @internal
  void writeResolvedValue(
    ResolvedTypeInternal resolved,
    Object value,
    TypeShapeInternal? declaredShape,
  ) {
    writePayloadValue(this, resolved, value, declaredShape);
  }

  void _writeTypeMeta(ResolvedTypeInternal resolved, Object value) {
    _typeResolver.writeTypeMeta(
      _buffer,
      resolved,
      sharedTypeDefFields:
          resolved.structRuntime?.sharedTypeDefFieldsForWrite(this, value),
      lookupSharedTypeDefId: (identity) => _typeDefIds[identity],
      reserveSharedTypeDefId: (identity) {
        final newIndex = _nextTypeDefId++;
        _typeDefIds[identity] = newIndex;
        return newIndex;
      },
      writePackageMetaString: _writeEncodedMetaString,
      writeTypeNameMetaString: _writeEncodedMetaString,
    );
  }

  void _writeEncodedMetaString(EncodedMetaStringInternal encoded) {
    _metaStringWriter.writeMetaString(_buffer, encoded);
  }

  @internal
  void writeTypeMetaValue(ResolvedTypeInternal resolved, Object value) {
    _writeTypeMeta(resolved, value);
  }
}
