import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/serializer/serializer_support.dart';
import 'package:fory/src/serializer/struct_field_runtime.dart';
import 'package:fory/src/serializer/struct_session.dart';
import 'package:fory/src/util/hash_util.dart';

typedef GeneratedStructCompatibleFactory<T> = T Function();

typedef GeneratedStructCompatibleFieldReader<T> = void Function(
  ReadContext context,
  T value,
  Object? rawValue,
);

/// Internal struct-specific runtime orchestration.
///
/// This mirrors the Java/Cython ownership split: schema/version framing,
/// compatible-field staging, and remembered remote metadata live here rather
/// than on generic read/write contexts or on the public serializer API.
final class StructRuntime {
  final Serializer<Object?> _payloadSerializer;
  final StructMetadataInternal _metadata;
  final TypeResolver _typeResolver;
  late final List<StructFieldRuntime> _localFields =
      List<StructFieldRuntime>.unmodifiable(
        List<StructFieldRuntime>.generate(
          _metadata.fields.length,
          (slot) => StructFieldRuntime(
            slot,
            _metadata.fields[slot],
            _typeResolver.createDeclaredFieldRuntime(_metadata.fields[slot]),
          ),
        ),
      );
  late final Map<String, StructFieldRuntime> _localFieldsByIdentifier =
      <String, StructFieldRuntime>{
        for (final field in _localFields) field.metadata.identifier: field,
      };
  final Expando<_CompatibleWritePlan> _compatibleWritePlans =
      Expando<_CompatibleWritePlan>('fory_compatible_write_plan');
  final Expando<_CompatibleReadPlan> _compatibleReadPlans =
      Expando<_CompatibleReadPlan>('fory_compatible_read_plan');
  final GeneratedStructCompatibleFactory<Object>? _compatibleFactory;
  final List<GeneratedStructCompatibleFieldReader<Object>>?
      _compatibleReadersBySlot;

  StructRuntime(
    this._payloadSerializer,
    this._metadata,
    this._typeResolver, {
    GeneratedStructCompatibleFactory<Object>? compatibleFactory,
    List<GeneratedStructCompatibleFieldReader<Object>>?
        compatibleReadersBySlot,
  })  : _compatibleFactory = compatibleFactory,
        _compatibleReadersBySlot = compatibleReadersBySlot;

  List<StructFieldRuntime> get localFields => _localFields;

  SharedTypeDefInternal sharedTypeDefForWrite(
    WriteContext context,
    ResolvedTypeInternal resolved,
    Object value,
  ) {
    final plan = _compatibleWritePlanForValue(context, resolved, value);
    if (plan == null) {
      return resolved.sharedTypeDef!;
    }
    return plan.sharedTypeDef;
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
        _replaceWriteSession(internal, resolved, value);
    try {
      _payloadSerializer.write(context, value);
    } finally {
      internal.restoreLocalState(
        structWriteSessionKey,
        previousCompatibleFields,
      );
    }
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
    final previousReadSession = internal.replaceLocalState(
      structReadSessionKey,
      null,
    );
    try {
      final value = internal.readSerializerPayload(
        _payloadSerializer,
        resolved,
        hasCurrentPreservedRef: hasCurrentPreservedRef,
      );
      _rememberRemoteMetadata(internal, resolved, value);
      return value;
    } finally {
      internal.restoreLocalState(
        structReadSessionKey,
        previousReadSession,
      );
    }
  }

  Object? _replaceWriteSession(
    WriteContext context,
    ResolvedTypeInternal resolved,
    Object value,
  ) {
    final plan = _compatibleWritePlanForValue(context, resolved, value);
    return context.replaceLocalState(
      structWriteSessionKey,
      plan == null ? null : StructWriteSession(plan.fields, _localFields.length),
    );
  }

  _CompatibleWritePlan? _compatibleWritePlanForValue(
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
    final cached = _compatibleWritePlans[remoteMetadata];
    if (cached != null) {
      return cached;
    }
    final orderedFields = <StructFieldRuntime>[];
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
        StructFieldRuntime(
          localField.slot,
          mergedField,
          _typeResolver.createDeclaredFieldRuntime(mergedField),
        ),
      );
      usedIdentifiers.add(remoteField.identifier);
    }
    for (final localField in _localFields) {
      if (usedIdentifiers.add(localField.metadata.identifier)) {
        orderedFields.add(localField);
      }
    }
    final fields = List<StructFieldRuntime>.unmodifiable(orderedFields);
    final sharedTypeDef = context.typeResolver.sharedTypeDefForResolved(
      resolved,
      fields: fields
          .map((field) => field.metadata)
          .toList(growable: false),
    );
    final plan = _CompatibleWritePlan(fields, sharedTypeDef);
    _compatibleWritePlans[remoteMetadata] = plan;
    return plan;
  }

  Object _readCompatible(
    ReadContext context,
    ResolvedTypeInternal resolved,
    {required bool hasCurrentPreservedRef}
  ) {
    final plan = _compatibleReadPlanForResolved(resolved);
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
      try {
        final value = compatibleFactory();
        context.reference(value);
        for (var index = 0; index < plan.fields.length; index += 1) {
          final localField = plan.fields[index];
          if (localField == null) {
            readCompatibleField(context, plan.remoteFields[index]);
            continue;
          }
          compatibleReadersBySlot[localField.slot](
            context,
            value,
            readCompatibleFieldRuntime(
              context,
              localField.runtime,
            ),
          );
        }
        _rememberRemoteMetadata(context, resolved, value);
        return value;
      } finally {
        if (needsSentinel &&
            context.refReader.hasPreservedRefId &&
            context.refReader.lastPreservedRefId == sentinelId) {
          context.refReader.discardPreservedRefId(sentinelId!);
        }
      }
    }
    final compatibleValues = List<Object?>.filled(_localFields.length, null);
    final presentSlots = List<bool>.filled(_localFields.length, false);
    for (var index = 0; index < plan.fields.length; index += 1) {
      final localField = plan.fields[index];
      if (localField == null) {
        readCompatibleField(context, plan.remoteFields[index]);
        continue;
      }
      final slot = localField.slot;
      compatibleValues[slot] = readCompatibleFieldRuntime(
        context,
        localField.runtime,
      );
      presentSlots[slot] = true;
    }
    final previousCompatibleFields = context.replaceLocalState(
      structReadSessionKey,
      StructReadSession(compatibleValues, presentSlots),
    );
    try {
      final value = context.readSerializerPayload(
        _payloadSerializer,
        resolved,
        hasCurrentPreservedRef: hasCurrentPreservedRef,
      );
      _rememberRemoteMetadata(context, resolved, value);
      return value;
    } finally {
      context.restoreLocalState(
        structReadSessionKey,
        previousCompatibleFields,
      );
    }
  }

  _CompatibleReadPlan _compatibleReadPlanForResolved(
    ResolvedTypeInternal resolved,
  ) {
    final remoteMetadata = resolved.remoteStructMetadata;
    if (remoteMetadata == null) {
      return _CompatibleReadPlan(_metadata.fields, _localFields);
    }
    final cached = _compatibleReadPlans[remoteMetadata];
    if (cached != null) {
      return cached;
    }
    final fields = <StructFieldRuntime?>[];
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
        StructFieldRuntime(
          localField.slot,
          mergedField,
          _typeResolver.createDeclaredFieldRuntime(mergedField),
        ),
      );
    }
    final plan = _CompatibleReadPlan(
      remoteMetadata.fields,
      List<StructFieldRuntime?>.unmodifiable(fields),
    );
    _compatibleReadPlans[remoteMetadata] = plan;
    return plan;
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

final class _CompatibleWritePlan {
  final List<StructFieldRuntime> fields;
  final SharedTypeDefInternal sharedTypeDef;

  const _CompatibleWritePlan(this.fields, this.sharedTypeDef);
}

final class _CompatibleReadPlan {
  final List<FieldMetadataInternal> remoteFields;
  final List<StructFieldRuntime?> fields;

  const _CompatibleReadPlan(this.remoteFields, this.fields);
}
