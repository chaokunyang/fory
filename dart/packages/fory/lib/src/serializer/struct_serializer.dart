import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/serializer/serializer_support.dart';
import 'package:fory/src/serializer/struct_slots.dart';
import 'package:fory/src/util/hash_util.dart';

typedef GeneratedStructCompatibleFactory<T> = T Function();

typedef GeneratedStructCompatibleFieldReader<T> = void Function(
  ReadContext context,
  T value,
  Object? rawValue,
);

final class StructSerializer extends Serializer<Object?> {
  final Serializer<Object?> _payloadSerializer;
  final StructMetadataInternal _metadata;
  final TypeResolver _typeResolver;
  late final List<FieldInfoInternal> _localFields =
      List<FieldInfoInternal>.unmodifiable(
        List<FieldInfoInternal>.generate(
          _metadata.fields.length,
          (slot) => _typeResolver.fieldInfo(_metadata.fields[slot], slot: slot),
        ),
      );
  late final Map<String, FieldInfoInternal>
      _localFieldsByIdentifier = <String, FieldInfoInternal>{
        for (final field in _localFields) field.identifier: field,
      };
  final Expando<_CompatibleWriteLayout> _compatibleWriteLayouts =
      Expando<_CompatibleWriteLayout>('fory_compatible_write_layout');
  final Expando<_CompatibleReadLayout> _compatibleReadLayouts =
      Expando<_CompatibleReadLayout>('fory_compatible_read_layout');
  final GeneratedStructCompatibleFactory<Object>? _compatibleFactory;
  final List<GeneratedStructCompatibleFieldReader<Object>>?
      _compatibleReadersBySlot;

  StructSerializer(
    this._payloadSerializer,
    this._metadata,
    this._typeResolver, {
    GeneratedStructCompatibleFactory<Object>? compatibleFactory,
    List<GeneratedStructCompatibleFieldReader<Object>>?
        compatibleReadersBySlot,
  })  : _compatibleFactory = compatibleFactory,
        _compatibleReadersBySlot = compatibleReadersBySlot;

  @override
  bool get supportsRef => _payloadSerializer.supportsRef;

  @override
  void write(WriteContext context, Object? value) {
    throw StateError('StructSerializer.write requires struct dispatch.');
  }

  @override
  Object read(ReadContext context) {
    throw StateError('StructSerializer.read requires struct dispatch.');
  }

  List<FieldInfoInternal> get localFields => _localFields;

  SharedTypeDefInternal sharedTypeDefForWrite(
    WriteContext context,
    TypeInfoInternal resolved,
    Object value,
  ) {
    final layout = _compatibleWriteLayoutForValue(context, resolved, value);
    if (layout == null) {
      return resolved.sharedTypeDef!;
    }
    return layout.sharedTypeDef;
  }

  void writeValue(
    WriteContext context,
    TypeInfoInternal resolved,
    Object value,
  ) {
    final internal = context;
    if (!context.config.compatible && context.config.checkStructVersion) {
      context.buffer.writeUint32(schemaHashInternal(_metadata));
    }
    final previousCompatibleFields =
        _replaceWriteSlots(internal, resolved, value);
    _payloadSerializer.write(context, value);
    internal.restoreContextObject(
      structWriteSlotsKey,
      previousCompatibleFields,
    );
  }

  Object readValue(
    ReadContext context,
    TypeInfoInternal resolved, {
    bool hasCurrentPreservedRef = false,
  }) {
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
    final previousReadSlots = internal.replaceContextObject(
      structReadSlotsKey,
      null,
    );
    final value = internal.readSerializerPayload(
      _payloadSerializer,
      resolved,
      hasCurrentPreservedRef: hasCurrentPreservedRef,
    );
    internal.restoreContextObject(
      structReadSlotsKey,
      previousReadSlots,
    );
    _rememberRemoteMetadata(internal, resolved, value);
    return value;
  }

  Object? _replaceWriteSlots(
    WriteContext context,
    TypeInfoInternal resolved,
    Object value,
  ) {
    final layout = _compatibleWriteLayoutForValue(context, resolved, value);
    return context.replaceContextObject(
      structWriteSlotsKey,
      layout == null ? null : StructWriteSlots(layout.fields, _localFields.length),
    );
  }

  _CompatibleWriteLayout? _compatibleWriteLayoutForValue(
    WriteContext context,
    TypeInfoInternal resolved,
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
    final orderedFields = <FieldInfoInternal>[];
    final usedIdentifiers = <String>{};
    for (final remoteField in remoteMetadata.fields) {
      final localField = _localFieldsByIdentifier[remoteField.identifier];
      if (localField == null) {
        continue;
      }
      final mergedField = _typeResolver.fieldInfo(
        mergeCompatibleWriteField(
          localField,
          remoteField,
        ),
        slot: localField.slot,
      );
      orderedFields.add(mergedField);
      usedIdentifiers.add(remoteField.identifier);
    }
    for (final localField in _localFields) {
      if (usedIdentifiers.add(localField.identifier)) {
        orderedFields.add(localField);
      }
    }
    final fields =
        List<FieldInfoInternal>.unmodifiable(orderedFields);
    final sharedTypeDef = context.typeResolver.sharedTypeDefForResolved(
      resolved,
      fields: fields,
    );
    final layout = _CompatibleWriteLayout(fields, sharedTypeDef);
    _compatibleWriteLayouts[remoteMetadata] = layout;
    return layout;
  }

  Object _readCompatible(
    ReadContext context,
    TypeInfoInternal resolved, {
    required bool hasCurrentPreservedRef,
  }) {
    final layout = _compatibleReadLayoutForResolved(resolved);
    final compatibleFactory = _compatibleFactory;
    final compatibleReadersBySlot = _compatibleReadersBySlot;
    if (compatibleFactory != null && compatibleReadersBySlot != null) {
      final int? sentinelId;
      final needsSentinel = resolved.supportsRef && !hasCurrentPreservedRef;
      if (needsSentinel) {
        sentinelId = context.refReader.preserveRefId(-1);
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
          readCompatibleField(context, localField),
        );
      }
      if (needsSentinel &&
          context.refReader.hasPreservedRefId &&
          context.refReader.lastPreservedRefId == sentinelId) {
        context.refReader.reference(null);
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
      compatibleValues[slot] = readCompatibleField(
        context,
        localField,
      );
      presentSlots[slot] = true;
    }
    final previousCompatibleFields = context.replaceContextObject(
      structReadSlotsKey,
      StructReadSlots(compatibleValues, presentSlots),
    );
    final value = context.readSerializerPayload(
      _payloadSerializer,
      resolved,
      hasCurrentPreservedRef: hasCurrentPreservedRef,
    );
    context.restoreContextObject(
      structReadSlotsKey,
      previousCompatibleFields,
    );
    _rememberRemoteMetadata(context, resolved, value);
    return value;
  }

  _CompatibleReadLayout _compatibleReadLayoutForResolved(
    TypeInfoInternal resolved,
  ) {
    final remoteMetadata = resolved.remoteStructMetadata;
    if (remoteMetadata == null) {
      return _CompatibleReadLayout(_metadata.fields, _localFields);
    }
    final cached = _compatibleReadLayouts[remoteMetadata];
    if (cached != null) {
      return cached;
    }
    final fields = <FieldInfoInternal?>[];
    for (final remoteField in remoteMetadata.fields) {
      final localField = _localFieldsByIdentifier[remoteField.identifier];
      if (localField == null) {
        fields.add(null);
        continue;
      }
      final mergedField = _typeResolver.fieldInfo(
        mergeCompatibleReadField(
          localField,
          remoteField,
        ),
        slot: localField.slot,
      );
      fields.add(mergedField);
    }
    final layout = _CompatibleReadLayout(
      remoteMetadata.fields,
      List<FieldInfoInternal?>.unmodifiable(fields),
    );
    _compatibleReadLayouts[remoteMetadata] = layout;
    return layout;
  }

  void _rememberRemoteMetadata(
    ReadContext context,
    TypeInfoInternal resolved,
    Object value,
  ) {
    final remoteMetadata = resolved.remoteStructMetadata;
    if (remoteMetadata != null) {
      context.rememberCompatibleStructMetadata(value, remoteMetadata);
    }
  }
}

final class _CompatibleWriteLayout {
  final List<FieldInfoInternal> fields;
  final SharedTypeDefInternal sharedTypeDef;

  const _CompatibleWriteLayout(this.fields, this.sharedTypeDef);
}

final class _CompatibleReadLayout {
  final List<FieldInfoInternal> remoteFields;
  final List<FieldInfoInternal?> fields;

  const _CompatibleReadLayout(this.remoteFields, this.fields);
}
