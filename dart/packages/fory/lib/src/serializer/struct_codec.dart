import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/serializer/serializer_support.dart';
import 'package:fory/src/serializer/struct_field_binding.dart';
import 'package:fory/src/serializer/struct_slots.dart';
import 'package:fory/src/util/hash_util.dart';

typedef GeneratedStructCompatibleFactory<T> = T Function();

typedef GeneratedStructCompatibleFieldReader<T> = void Function(
  ReadContext context,
  T value,
  Object? rawValue,
);

/// Internal struct-specific serialization orchestration.
///
/// This mirrors the Java/Cython ownership split: schema/version framing,
/// compatible-field staging, and remembered remote metadata live here rather
/// than on generic read/write contexts or on the public serializer API.
final class StructCodec {
  final Serializer<Object?> _payloadSerializer;
  final StructMetadataInternal _metadata;
  final TypeResolver _typeResolver;
  late final List<StructFieldBinding> _localFields =
      List<StructFieldBinding>.unmodifiable(
        List<StructFieldBinding>.generate(
          _metadata.fields.length,
          (slot) => StructFieldBinding(
            slot,
            _metadata.fields[slot],
            _typeResolver.createDeclaredFieldBinding(_metadata.fields[slot]),
          ),
        ),
      );
  late final Map<String, StructFieldBinding> _localFieldsByIdentifier =
      <String, StructFieldBinding>{
        for (final field in _localFields) field.metadata.identifier: field,
      };
  final Expando<_CompatibleWriteLayout> _compatibleWriteLayouts =
      Expando<_CompatibleWriteLayout>('fory_compatible_write_layout');
  final Expando<_CompatibleReadLayout> _compatibleReadLayouts =
      Expando<_CompatibleReadLayout>('fory_compatible_read_layout');
  final GeneratedStructCompatibleFactory<Object>? _compatibleFactory;
  final List<GeneratedStructCompatibleFieldReader<Object>>?
      _compatibleReadersBySlot;

  StructCodec(
    this._payloadSerializer,
    this._metadata,
    this._typeResolver, {
    GeneratedStructCompatibleFactory<Object>? compatibleFactory,
    List<GeneratedStructCompatibleFieldReader<Object>>?
        compatibleReadersBySlot,
  })  : _compatibleFactory = compatibleFactory,
        _compatibleReadersBySlot = compatibleReadersBySlot;

  List<StructFieldBinding> get localFields => _localFields;

  SharedTypeDefInternal sharedTypeDefForWrite(
    WriteContext context,
    ResolvedTypeInternal resolved,
    Object value,
  ) {
    final layout = _compatibleWriteLayoutForValue(context, resolved, value);
    if (layout == null) {
      return resolved.sharedTypeDef!;
    }
    return layout.sharedTypeDef;
  }

  void write(
    WriteContext context,
    ResolvedTypeInternal resolved,
    Object value,
  ) {
    final internal = context;
    if (!context.config.compatible && context.config.checkStructVersion) {
      context.buffer.writeUint32(schemaHashInternal(_metadata));
    }
    final previousCompatibleFields =
        _replaceWriteSlots(internal, resolved, value);
    _payloadSerializer.write(context, value);
    internal.restoreLocalState(
      structWriteSlotsKey,
      previousCompatibleFields,
    );
  }

  Object read(
    ReadContext context,
    ResolvedTypeInternal resolved,
    {bool hasCurrentPreservedRef = false}
  ) {
    final internal = context;
    if (!context.config.compatible && context.config.checkStructVersion) {
      final expected = schemaHashInternal(_metadata);
      final actual = context.buffer.readUint32();
      if (actual != expected) {
        throw StateError(
          'Struct schema version mismatch for ${resolved.type}: $actual != $expected.',
        );
      }
    }
    if (context.config.compatible &&
        resolved.isCompatibleStruct &&
        resolved.remoteStructMetadata != null) {
      return _readCompatible(
        internal,
        resolved,
        hasCurrentPreservedRef: hasCurrentPreservedRef,
      );
    }
    final previousReadSlots = internal.replaceLocalState(
      structReadSlotsKey,
      null,
    );
    final value = internal.readSerializerPayload(
      _payloadSerializer,
      resolved,
      hasCurrentPreservedRef: hasCurrentPreservedRef,
    );
    internal.restoreLocalState(
      structReadSlotsKey,
      previousReadSlots,
    );
    _rememberRemoteMetadata(internal, resolved, value);
    return value;
  }

  Object? _replaceWriteSlots(
    WriteContext context,
    ResolvedTypeInternal resolved,
    Object value,
  ) {
    final layout = _compatibleWriteLayoutForValue(context, resolved, value);
    return context.replaceLocalState(
      structWriteSlotsKey,
      layout == null ? null : StructWriteSlots(layout.fields, _localFields.length),
    );
  }

  _CompatibleWriteLayout? _compatibleWriteLayoutForValue(
    WriteContext context,
    ResolvedTypeInternal resolved,
    Object value,
  ) {
    if (!_metadata.evolving || !context.config.compatible) {
      return null;
    }
    final remoteMetadata = context.compatibleStructMetadataFor(value);
    if (remoteMetadata == null) {
      return null;
    }
    final cached = _compatibleWriteLayouts[remoteMetadata];
    if (cached != null) {
      return cached;
    }
    final orderedFields = <StructFieldBinding>[];
    final usedIdentifiers = <String>{};
    for (final remoteField in remoteMetadata.fields) {
      final localField = _localFieldsByIdentifier[remoteField.identifier];
      if (localField == null) {
        continue;
      }
      final mergedField = mergeCompatibleWriteField(
        localField.metadata,
        remoteField,
      );
      orderedFields.add(
        StructFieldBinding(
          localField.slot,
          mergedField,
          _typeResolver.createDeclaredFieldBinding(mergedField),
        ),
      );
      usedIdentifiers.add(remoteField.identifier);
    }
    for (final localField in _localFields) {
      if (usedIdentifiers.add(localField.metadata.identifier)) {
        orderedFields.add(localField);
      }
    }
    final fields = List<StructFieldBinding>.unmodifiable(orderedFields);
    final sharedTypeDef = context.typeResolver.sharedTypeDefForResolved(
      resolved,
      fields: fields
          .map((field) => field.metadata)
          .toList(growable: false),
    );
    final layout = _CompatibleWriteLayout(fields, sharedTypeDef);
    _compatibleWriteLayouts[remoteMetadata] = layout;
    return layout;
  }

  Object _readCompatible(
    ReadContext context,
    ResolvedTypeInternal resolved,
    {required bool hasCurrentPreservedRef}
  ) {
    final layout = _compatibleReadLayoutForResolved(resolved);
    final compatibleFactory = _compatibleFactory;
    final compatibleReadersBySlot = _compatibleReadersBySlot;
    if (compatibleFactory != null && compatibleReadersBySlot != null) {
      final int? sentinelId;
      final needsSentinel =
          resolved.supportsRef && !hasCurrentPreservedRef;
      if (needsSentinel) {
        sentinelId = context.refReader.preserveSentinel();
      } else {
        sentinelId = null;
      }
      final value = compatibleFactory();
      context.reference(value);
      for (var index = 0; index < layout.fields.length; index += 1) {
        final localField = layout.fields[index];
        if (localField == null) {
          readCompatibleField(context, layout.remoteFields[index]);
          continue;
        }
        compatibleReadersBySlot[localField.slot](
          context,
          value,
          readCompatibleFieldBinding(
            context,
            localField.valueBinding,
          ),
        );
      }
      if (needsSentinel &&
          context.refReader.hasPreservedRefId &&
          context.refReader.lastPreservedRefId == sentinelId) {
        context.refReader.discardPreservedRefId(sentinelId!);
      }
      _rememberRemoteMetadata(context, resolved, value);
      return value;
    }
    final compatibleValues = List<Object?>.filled(_localFields.length, null);
    final presentSlots = List<bool>.filled(_localFields.length, false);
    for (var index = 0; index < layout.fields.length; index += 1) {
      final localField = layout.fields[index];
      if (localField == null) {
        readCompatibleField(context, layout.remoteFields[index]);
        continue;
      }
      final slot = localField.slot;
      compatibleValues[slot] = readCompatibleFieldBinding(
        context,
        localField.valueBinding,
      );
      presentSlots[slot] = true;
    }
    final previousCompatibleFields = context.replaceLocalState(
      structReadSlotsKey,
      StructReadSlots(compatibleValues, presentSlots),
    );
    final value = context.readSerializerPayload(
      _payloadSerializer,
      resolved,
      hasCurrentPreservedRef: hasCurrentPreservedRef,
    );
    context.restoreLocalState(
      structReadSlotsKey,
      previousCompatibleFields,
    );
    _rememberRemoteMetadata(context, resolved, value);
    return value;
  }

  _CompatibleReadLayout _compatibleReadLayoutForResolved(
    ResolvedTypeInternal resolved,
  ) {
    final remoteMetadata = resolved.remoteStructMetadata;
    if (remoteMetadata == null) {
      return _CompatibleReadLayout(_metadata.fields, _localFields);
    }
    final cached = _compatibleReadLayouts[remoteMetadata];
    if (cached != null) {
      return cached;
    }
    final fields = <StructFieldBinding?>[];
    for (final remoteField in remoteMetadata.fields) {
      final localField = _localFieldsByIdentifier[remoteField.identifier];
      if (localField == null) {
        fields.add(null);
        continue;
      }
      final mergedField = mergeCompatibleReadField(
        localField.metadata,
        remoteField,
      );
      fields.add(
        StructFieldBinding(
          localField.slot,
          mergedField,
          _typeResolver.createDeclaredFieldBinding(mergedField),
        ),
      );
    }
    final layout = _CompatibleReadLayout(
      remoteMetadata.fields,
      List<StructFieldBinding?>.unmodifiable(fields),
    );
    _compatibleReadLayouts[remoteMetadata] = layout;
    return layout;
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

final class _CompatibleWriteLayout {
  final List<StructFieldBinding> fields;
  final SharedTypeDefInternal sharedTypeDef;

  const _CompatibleWriteLayout(this.fields, this.sharedTypeDef);
}

final class _CompatibleReadLayout {
  final List<FieldMetadataInternal> remoteFields;
  final List<StructFieldBinding?> fields;

  const _CompatibleReadLayout(this.remoteFields, this.fields);
}
