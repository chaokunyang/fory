import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/serializer/serializer_support.dart';
import 'package:fory/src/serializer/struct_field_runtime.dart';
import 'package:fory/src/serializer/struct_session.dart';
import 'package:fory/src/util/hash_util.dart';

/// Internal struct-specific runtime orchestration.
///
/// This mirrors the Java/Cython ownership split: schema/version framing,
/// compatible-field staging, and remembered remote metadata live here rather
/// than on generic read/write contexts or on the public serializer API.
final class StructRuntime {
  final Serializer<Object?> _payloadSerializer;
  final StructMetadataInternal _metadata;
  final Object _missingContextValue = Object();

  StructRuntime(this._payloadSerializer, this._metadata);

  List<FieldMetadataInternal> sharedTypeDefFieldsForWrite(
    WriteContext context,
    Object value,
  ) {
    final compatibleFields = _compatibleWriteFieldsForValue(context, value);
    if (compatibleFields == null) {
      return _metadata.fields;
    }
    return compatibleFields
        .map((field) => field.metadata)
        .toList(growable: false);
  }

  void write(
    WriteContext context,
    ResolvedTypeInternal resolved,
    Object value,
  ) {
    if (!context.config.compatible && context.config.checkStructVersion) {
      context.buffer.writeUint32(schemaHashInternal(_metadata));
    }
    final previousCompatibleFields = _pushCompatibleWriteFields(context, value);
    try {
      _payloadSerializer.write(context, value);
    } finally {
      if (!identical(previousCompatibleFields, _missingContextValue)) {
        context.restoreStructWriteSession(
          previousCompatibleFields as StructWriteSession?,
        );
      }
    }
  }

  Object read(
    ReadContext context,
    ResolvedTypeInternal resolved,
  ) {
    if (!context.config.compatible && context.config.checkStructVersion) {
      final expected = schemaHashInternal(_metadata);
      final actual = context.buffer.readUint32();
      if (actual != expected) {
        throw StateError(
          'Struct schema version mismatch for ${resolved.type}: $actual != $expected.',
        );
      }
    }
    if (context.config.compatible && resolved.isCompatibleStruct) {
      return _readCompatible(context, resolved);
    }
    final value = context.readSerializerPayload(_payloadSerializer, resolved);
    _rememberRemoteMetadata(context, resolved, value);
    return value;
  }

  Object? _pushCompatibleWriteFields(
    WriteContext context,
    Object value,
  ) {
    final compatibleFields = _compatibleWriteFieldsForValue(context, value);
    if (compatibleFields == null) {
      return _missingContextValue;
    }
    return context.swapStructWriteSession(
      StructWriteSession(
        List<Object>.unmodifiable(compatibleFields),
      ),
    );
  }

  List<StructFieldRuntime>? _compatibleWriteFieldsForValue(
    WriteContext context,
    Object value,
  ) {
    if (!_metadata.evolving || !context.config.compatible) {
      return null;
    }
    final remoteMetadata = context.compatibleStructMetadataFor(value);
    if (remoteMetadata == null) {
      return null;
    }
    final localByIdentifier = <String, StructFieldRuntime>{};
    for (var slot = 0; slot < _metadata.fields.length; slot += 1) {
      final field = _metadata.fields[slot];
      localByIdentifier[field.identifier] = StructFieldRuntime(slot, field);
    }
    final orderedFields = <StructFieldRuntime>[];
    final usedIdentifiers = <String>{};
    for (final remoteField in remoteMetadata.fields) {
      final localField = localByIdentifier[remoteField.identifier];
      if (localField == null) {
        continue;
      }
      orderedFields.add(
        StructFieldRuntime(
          localField.slot,
          mergeCompatibleWriteField(localField.metadata, remoteField),
        ),
      );
      usedIdentifiers.add(remoteField.identifier);
    }
    for (var slot = 0; slot < _metadata.fields.length; slot += 1) {
      final localField = _metadata.fields[slot];
      if (usedIdentifiers.add(localField.identifier)) {
        orderedFields.add(StructFieldRuntime(slot, localField));
      }
    }
    return orderedFields;
  }

  Object _readCompatible(
    ReadContext context,
    ResolvedTypeInternal resolved,
  ) {
    final remoteFields =
        resolved.remoteStructMetadata?.fields ?? _metadata.fields;
    final localFields = <String, StructFieldRuntime>{};
    for (var slot = 0; slot < _metadata.fields.length; slot += 1) {
      final field = _metadata.fields[slot];
      localFields[field.identifier] = StructFieldRuntime(slot, field);
    }
    final compatibleFields = <int, Object?>{};
    for (final remoteField in remoteFields) {
      final localField = localFields[remoteField.identifier];
      if (localField == null) {
        readCompatibleField(context, remoteField);
        continue;
      }
      compatibleFields[localField.slot] = readCompatibleField(
        context,
        mergeCompatibleReadField(localField.metadata, remoteField),
      );
    }
    final previousCompatibleFields = context.swapStructReadSession(
      StructReadSession(compatibleFields),
    );
    try {
      final value = context.readSerializerPayload(_payloadSerializer, resolved);
      _rememberRemoteMetadata(context, resolved, value);
      return value;
    } finally {
      context.restoreStructReadSession(previousCompatibleFields);
    }
  }

  void _rememberRemoteMetadata(
    ReadContext context,
    ResolvedTypeInternal resolved,
    Object value,
  ) {
    final remoteMetadata = resolved.remoteStructMetadata;
    if (remoteMetadata != null) {
      context.rememberCompatibleStructMetadata(value, remoteMetadata);
    }
  }
}
