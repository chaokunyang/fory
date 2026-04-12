import 'dart:collection';

import 'package:meta/meta.dart';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/context/compatible_struct_metadata_store.dart';
import 'package:fory/src/context/meta_string_writer.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/payload_codec.dart';
import 'package:fory/src/serializer/struct_slots.dart';
import 'package:fory/src/types/float16.dart';

/// Write-side serializer context.
///
/// Generated and manual serializers receive this during a single serialization
/// operation. Application code normally interacts with [Fory] instead of
/// constructing contexts directly.
final class WriteContext {
  /// Effective runtime configuration for the active operation.
  final Config config;
  final TypeResolver _typeResolver;
  final RefWriter _refWriter;
  final MetaStringWriter _metaStringWriter;
  final CompatibleStructMetadataStore _compatibleStructMetadata;

  late Buffer _buffer;
  final LinkedHashMap<SharedTypeDefInternal, int> _typeDefIds =
      LinkedHashMap<SharedTypeDefInternal, int>.identity();
  StructWriteSlots? _structWriteSlots;
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

  @internal
  void prepare(Buffer buffer, {required bool trackRef}) {
    _buffer = buffer;
    _rootTrackRef = trackRef;
  }

  @internal
  void reset() {
    _typeDefIds.clear();
    _refWriter.reset();
    _metaStringWriter.reset();
    _structWriteSlots = null;
    _rootTrackRef = false;
    _depth = 0;
  }

  /// The active output buffer for the current operation.
  Buffer get buffer => _buffer;

  @internal
  TypeResolver get typeResolver => _typeResolver;

  @internal
  RefWriter get refWriter => _refWriter;

  @internal
  bool get rootTrackRef => _rootTrackRef;

  @internal
  T? localStateAs<T>(Object key) {
    if (!identical(key, structWriteSlotsKey)) {
      throw StateError('Unknown write local state key: $key.');
    }
    return _structWriteSlots as T?;
  }

  @internal
  Object? replaceLocalState(Object key, Object? next) {
    if (!identical(key, structWriteSlotsKey)) {
      throw StateError('Unknown write local state key: $key.');
    }
    final previous = _structWriteSlots;
    _structWriteSlots = next as StructWriteSlots?;
    return previous;
  }

  @internal
  void restoreLocalState(Object key, Object? previous) {
    if (!identical(key, structWriteSlotsKey)) {
      throw StateError('Unknown write local state key: $key.');
    }
    _structWriteSlots = previous as StructWriteSlots?;
  }

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
    if (_refWriter.writeRefOrNull(
      _buffer,
      value,
      trackRef: effectiveTrackRef,
    )) {
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
    if (!_tracksDepth(resolved)) {
      writePayloadValue(this, resolved, value, declaredShape);
      return;
    }
    increaseDepth();
    writePayloadValue(this, resolved, value, declaredShape);
    decreaseDepth();
  }

  void _writeTypeMeta(ResolvedTypeInternal resolved, Object value) {
    _typeResolver.writeTypeMeta(
      _buffer,
      resolved,
      sharedTypeDef: resolved.structCodec?.sharedTypeDefForWrite(
        this,
        resolved,
        value,
      ),
      sharedTypeDefIds: _typeDefIds,
      metaStringWriter: _metaStringWriter,
    );
  }

  @internal
  void writeTypeMetaValue(ResolvedTypeInternal resolved, Object value) {
    _writeTypeMeta(resolved, value);
  }

  bool _tracksDepth(ResolvedTypeInternal resolved) {
    if (TypeIds.isContainer(resolved.typeId)) {
      return true;
    }
    switch (resolved.kind) {
      case RegistrationKindInternal.builtin:
      case RegistrationKindInternal.enumType:
        return false;
      case RegistrationKindInternal.struct:
      case RegistrationKindInternal.ext:
      case RegistrationKindInternal.union:
        return true;
    }
  }
}
