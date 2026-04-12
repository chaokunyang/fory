import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/primitive_serializers.dart';
import 'package:fory/src/serializer/scalar_serializers.dart';
import 'package:fory/src/serializer/serializer.dart';

enum _FixedTypePayloadKind {
  primitive,
  string,
  struct,
  serializer,
  generic,
}

final class FixedTypePayload {
  final TypeInfo typeInfo;
  final FieldType? declaredFieldType;
  final Serializer<Object?>? serializer;
  final _FixedTypePayloadKind _kind;

  const FixedTypePayload._(
    this.typeInfo,
    this.declaredFieldType,
    this.serializer,
    this._kind,
  );

  factory FixedTypePayload(
    TypeInfo typeInfo, [
    FieldType? declaredFieldType,
  ]) {
    if (TypeIds.isPrimitive(typeInfo.typeId)) {
      return FixedTypePayload._(
        typeInfo,
        declaredFieldType,
        null,
        _FixedTypePayloadKind.primitive,
      );
    }
    if (typeInfo.typeId == TypeIds.string) {
      return FixedTypePayload._(
        typeInfo,
        declaredFieldType,
        null,
        _FixedTypePayloadKind.string,
      );
    }
    if (typeInfo.kind == RegistrationKind.struct) {
      return FixedTypePayload._(
        typeInfo,
        declaredFieldType,
        null,
        _FixedTypePayloadKind.struct,
      );
    }
    if (typeInfo.typeId == TypeIds.list ||
        typeInfo.typeId == TypeIds.set ||
        typeInfo.typeId == TypeIds.map) {
      return FixedTypePayload._(
        typeInfo,
        declaredFieldType,
        null,
        _FixedTypePayloadKind.generic,
      );
    }
    return FixedTypePayload._(
      typeInfo,
      declaredFieldType,
      typeInfo.serializer,
      _FixedTypePayloadKind.serializer,
    );
  }

  @pragma('vm:prefer-inline')
  void write(WriteContext context, Object value) {
    switch (_kind) {
      case _FixedTypePayloadKind.primitive:
        PrimitiveSerializer.writePayload(context, typeInfo.typeId, value);
        return;
      case _FixedTypePayloadKind.string:
        StringSerializer.writePayload(context, value as String);
        return;
      case _FixedTypePayloadKind.struct:
        typeInfo.structSerializer!.writeValue(context, typeInfo, value);
        return;
      case _FixedTypePayloadKind.serializer:
        serializer!.write(context, value);
        return;
      case _FixedTypePayloadKind.generic:
        context.writeResolvedValue(typeInfo, value, declaredFieldType);
        return;
    }
  }

  @pragma('vm:prefer-inline')
  Object? read(ReadContext context, {bool hasPreservedRef = false}) {
    switch (_kind) {
      case _FixedTypePayloadKind.primitive:
        return PrimitiveSerializer.readPayload(context, typeInfo.typeId);
      case _FixedTypePayloadKind.string:
        return StringSerializer.readPayload(context);
      case _FixedTypePayloadKind.struct:
        return typeInfo.structSerializer!.readValue(
          context,
          typeInfo,
          hasCurrentPreservedRef: hasPreservedRef,
        );
      case _FixedTypePayloadKind.serializer:
        return context.readSerializerPayload(
          serializer!,
          typeInfo,
          hasCurrentPreservedRef: hasPreservedRef,
        );
      case _FixedTypePayloadKind.generic:
        return context.readResolvedValue(
          typeInfo,
          declaredFieldType,
          hasPreservedRef: hasPreservedRef,
        );
    }
  }
}

bool tracksNestedPayloadDepth(TypeInfo typeInfo) {
  if (TypeIds.isContainer(typeInfo.typeId)) {
    return false;
  }
  switch (typeInfo.kind) {
    case RegistrationKind.builtin:
    case RegistrationKind.enumType:
      return false;
    case RegistrationKind.struct:
    case RegistrationKind.ext:
    case RegistrationKind.union:
      return true;
  }
}

bool sameTypeInfo(
  TypeInfo left,
  TypeInfo right,
) {
  if (left.kind != right.kind || left.typeId != right.typeId) {
    return false;
  }
  if (left.userTypeId != null || right.userTypeId != null) {
    return left.userTypeId == right.userTypeId;
  }
  if (left.namespace != null || right.namespace != null) {
    return left.namespace == right.namespace && left.typeName == right.typeName;
  }
  return true;
}

void writeFieldTypeValue(
  WriteContext context,
  FieldType fieldType,
  TypeInfo? declaredTypeInfo,
  bool usesDeclaredType,
  Object? value,
) {
  if (fieldType.isDynamic) {
    if (fieldType.ref) {
      context.writeRef(value);
      return;
    }
    if (context.writeNullFlag(value)) {
      return;
    }
    context.buffer.writeByte(RefWriter.notNullValueFlag);
    context.writeNonRef(value as Object);
    return;
  }
  if (fieldType.isPrimitive && !fieldType.nullable) {
    if (value == null) {
      throw StateError('Expected non-null field value.');
    }
    context.writePrimitiveValue(fieldType.typeId, value);
    return;
  }
  if (!usesDeclaredType) {
    if (fieldType.ref) {
      context.writeRef(value);
      return;
    }
    if (fieldType.nullable) {
      if (context.writeNullFlag(value)) {
        return;
      }
      context.buffer.writeByte(RefWriter.notNullValueFlag);
    } else if (value == null) {
      throw StateError('Expected non-null field value.');
    }
    context.writeNonRef(value as Object);
    return;
  }
  final resolved = declaredTypeInfo!;
  if (fieldType.nullable || fieldType.ref) {
    final handled = context.refWriter.writeRefOrNull(
      context.buffer,
      value,
      trackRef: fieldType.ref && resolved.supportsRef,
    );
    if (handled) {
      return;
    }
  }
  if (value == null) {
    throw StateError('Expected non-null field value.');
  }
  context.writeResolvedValue(resolved, value, fieldType);
}

T readFieldTypeValue<T>(
  ReadContext context,
  FieldType fieldType,
  TypeInfo? declaredTypeInfo,
  bool usesDeclaredType, [
  T? fallback,
]) {
  if (fieldType.isDynamic) {
    return context.readRef() as T;
  }
  if (fieldType.isPrimitive && !fieldType.nullable) {
    return context.readPrimitiveValue(fieldType.typeId) as T;
  }
  if (!usesDeclaredType) {
    if (fieldType.ref) {
      return context.readRef() as T;
    }
    if (fieldType.nullable) {
      return context.readNullable() as T;
    }
    return context.readNonRef() as T;
  }
  final resolved = declaredTypeInfo!;
  if (fieldType.nullable || fieldType.ref) {
    final flag = context.refReader.tryPreserveRefId(context.buffer);
    final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
    if (flag == RefWriter.nullFlag) {
      return fallback as T;
    }
    if (flag == RefWriter.refFlag) {
      return context.refReader.getReadRef() as T;
    }
    final value = context.readResolvedValue(
      resolved,
      fieldType,
      hasPreservedRef: preservedRefId != null,
    );
    if (preservedRefId != null &&
        resolved.supportsRef &&
        context.refReader.readRefAt(preservedRefId) == null) {
      context.refReader.setReadRef(preservedRefId, value);
    }
    return value as T;
  }
  return context.readResolvedValue(resolved, fieldType) as T;
}

final class ListSerializer extends Serializer<List> {
  const ListSerializer();

  @override
  void write(WriteContext context, List value) {
    writePayload(
      context,
      value,
      null,
      trackRef: context.rootTrackRef,
    );
  }

  @override
  List read(ReadContext context) {
    return readPayload(context, null);
  }

  static void writePayload(
    WriteContext context,
    Iterable values,
    FieldType? elementFieldType, {
    required bool trackRef,
  }) {
    final size = values.length;
    if (size > context.config.maxCollectionSize) {
      throw StateError(
        'Collection size $size exceeds ${context.config.maxCollectionSize}.',
      );
    }
    context.buffer.writeVarUint32(size);
    if (size == 0) {
      return;
    }
    final declaredTypeInfo = elementFieldType == null ||
            elementFieldType.isDynamic ||
            elementFieldType.typeId == TypeIds.unknown
        ? null
        : context.typeResolver.resolveFieldType(elementFieldType);
    final usesDeclaredType = declaredTypeInfo != null &&
        usesDeclaredTypeInfo(
          context.config.compatible,
          elementFieldType!,
          declaredTypeInfo,
        );
    final analysis = _analyzeListHeader(
      context,
      values,
      usesDeclaredType: usesDeclaredType,
    );
    final elementTrackRef = (elementFieldType?.ref ?? false) ||
        (elementFieldType == null && trackRef);
    var header = 0;
    if (elementTrackRef) {
      header |= 0x01;
    }
    if (analysis.hasNull) {
      header |= 0x02;
    }
    if (usesDeclaredType) {
      header |= 0x04;
    }
    if (analysis.sameType) {
      header |= 0x08;
    }
    context.buffer.writeUint8(header);
    final declaredPayload = declaredTypeInfo == null
        ? null
        : FixedTypePayload(
            declaredTypeInfo,
            elementFieldType,
          );
    final sameTypeInfo =
        !usesDeclaredType && analysis.sameType ? analysis.sameTypeInfo : null;
    final samePayload =
        sameTypeInfo == null ? null : FixedTypePayload(sameTypeInfo);
    if (!usesDeclaredType &&
        sameTypeInfo != null &&
        analysis.firstNonNull != null) {
      context.writeTypeMetaValue(
        sameTypeInfo,
        analysis.firstNonNull!,
      );
    }
    if (declaredPayload != null) {
      final tracksDepth = tracksNestedPayloadDepth(declaredTypeInfo!);
      if (tracksDepth) {
        context.increaseDepth();
      }
      for (final value in values) {
        if (value == null) {
          context.buffer.writeByte(RefWriter.nullFlag);
          continue;
        }
        if (elementTrackRef) {
          writeFixedTypeValue(
            context,
            declaredPayload,
            value as Object,
            trackRef: true,
          );
        } else if (analysis.hasNull) {
          context.buffer.writeByte(RefWriter.notNullValueFlag);
          declaredPayload.write(context, value as Object);
        } else {
          declaredPayload.write(context, value as Object);
        }
      }
      if (tracksDepth) {
        context.decreaseDepth();
      }
      return;
    }
    if (samePayload != null) {
      final tracksDepth = tracksNestedPayloadDepth(sameTypeInfo!);
      if (tracksDepth) {
        context.increaseDepth();
      }
      for (final value in values) {
        if (value == null) {
          context.buffer.writeByte(RefWriter.nullFlag);
        } else if (elementTrackRef) {
          writeFixedTypeValue(
            context,
            samePayload,
            value as Object,
            trackRef: true,
          );
        } else if (analysis.hasNull) {
          context.buffer.writeByte(RefWriter.notNullValueFlag);
          samePayload.write(context, value as Object);
        } else {
          samePayload.write(context, value as Object);
        }
      }
      if (tracksDepth) {
        context.decreaseDepth();
      }
      return;
    }
    for (final value in values) {
      if (analysis.sameType && analysis.sameTypeInfo != null) {
        if (value == null) {
          context.buffer.writeByte(RefWriter.nullFlag);
        } else if (elementTrackRef) {
          final handled = context.refWriter.writeRefOrNull(
            context.buffer,
            value,
            trackRef: analysis.sameTypeInfo!.supportsRef,
          );
          if (!handled) {
            context.writeResolvedValue(
              analysis.sameTypeInfo!,
              value as Object,
              null,
            );
          }
        } else if (analysis.hasNull) {
          context.buffer.writeByte(RefWriter.notNullValueFlag);
          context.writeResolvedValue(
            analysis.sameTypeInfo!,
            value as Object,
            null,
          );
        } else {
          context.writeResolvedValue(
            analysis.sameTypeInfo!,
            value as Object,
            null,
          );
        }
        continue;
      }
      if (elementTrackRef) {
        context.writeRef(value);
      } else if (analysis.hasNull) {
        if (value == null) {
          context.buffer.writeByte(RefWriter.nullFlag);
        } else {
          context.buffer.writeByte(RefWriter.notNullValueFlag);
          context.writeNonRef(value as Object);
        }
      } else {
        context.writeNonRef(value as Object);
      }
    }
  }

  static List<Object?> readPayload(
    ReadContext context,
    FieldType? elementFieldType,
  ) {
    final state = _prepareListRead(context, elementFieldType);
    final result = List<Object?>.filled(state.size, null, growable: false);
    if (state.size == 0) {
      return result;
    }
    if (state.tracksDepth) {
      context.increaseDepth();
    }
    for (var index = 0; index < state.size; index += 1) {
      result[index] = _readPreparedListItem(context, state);
    }
    if (state.tracksDepth) {
      context.decreaseDepth();
    }
    return result;
  }
}

final class SetSerializer extends Serializer<Set> {
  const SetSerializer();

  @override
  void write(WriteContext context, Set value) {
    ListSerializer.writePayload(
      context,
      value,
      null,
      trackRef: context.rootTrackRef,
    );
  }

  @override
  Set read(ReadContext context) {
    return readPayload(context, null);
  }

  static Set<Object?> readPayload(
    ReadContext context,
    FieldType? elementFieldType,
  ) {
    return Set<Object?>.of(
        ListSerializer.readPayload(context, elementFieldType));
  }
}

const ListSerializer listSerializer = ListSerializer();
const SetSerializer setSerializer = SetSerializer();

List<T> readTypedListPayload<T>(
  ReadContext context,
  FieldType? elementFieldType,
  T Function(Object? value) convert,
) {
  final state = _prepareListRead(context, elementFieldType);
  if (state.size == 0) {
    return List<T>.empty(growable: false);
  }
  if (state.tracksDepth) {
    context.increaseDepth();
  }
  final result = List<T>.generate(
    state.size,
    (_) => convert(_readPreparedListItem(context, state)),
    growable: false,
  );
  if (state.tracksDepth) {
    context.decreaseDepth();
  }
  return result;
}

Set<T> readTypedSetPayload<T>(
  ReadContext context,
  FieldType? elementFieldType,
  T Function(Object? value) convert,
) {
  return Set<T>.of(readTypedListPayload(context, elementFieldType, convert));
}

final class _PreparedListRead {
  final int size;
  final bool trackRef;
  final bool hasNull;
  final bool usesDeclaredType;
  final FieldType? elementFieldType;
  final TypeInfo? declaredTypeInfo;
  final FixedTypePayload? declaredPayload;
  final TypeInfo? sameTypeInfo;
  final FixedTypePayload? samePayload;
  final bool tracksDepth;

  const _PreparedListRead({
    required this.size,
    required this.trackRef,
    required this.hasNull,
    required this.usesDeclaredType,
    required this.elementFieldType,
    required this.declaredTypeInfo,
    required this.declaredPayload,
    required this.sameTypeInfo,
    required this.samePayload,
    required this.tracksDepth,
  });
}

_PreparedListRead _prepareListRead(
  ReadContext context,
  FieldType? elementFieldType,
) {
  final size = context.buffer.readVarUint32();
  if (size > context.config.maxCollectionSize) {
    throw StateError(
      'Collection size $size exceeds ${context.config.maxCollectionSize}.',
    );
  }
  if (size == 0) {
    return _PreparedListRead(
      size: 0,
      trackRef: false,
      hasNull: false,
      usesDeclaredType: false,
      elementFieldType: elementFieldType,
      declaredTypeInfo: null,
      declaredPayload: null,
      sameTypeInfo: null,
      samePayload: null,
      tracksDepth: false,
    );
  }
  final header = context.buffer.readUint8();
  final trackRef = (header & 0x01) == 1;
  final hasNull = (header & 0x02) != 0;
  final usesDeclaredType = (header & 0x04) != 0;
  final sameType = (header & 0x08) != 0;
  final declaredTypeInfo = usesDeclaredType && elementFieldType != null
      ? context.typeResolver.resolveFieldType(
          elementFieldType.withRootOverrides(nullable: hasNull, ref: trackRef),
        )
      : null;
  final sameTypeInfo =
      (!usesDeclaredType && sameType) ? context.readTypeMetaValue() : null;
  final declaredPayload = declaredTypeInfo == null
      ? null
      : FixedTypePayload(
          declaredTypeInfo,
          elementFieldType,
        );
  final samePayload =
      sameTypeInfo == null ? null : FixedTypePayload(sameTypeInfo);
  final tracksDepth = (declaredPayload != null &&
          tracksNestedPayloadDepth(declaredTypeInfo!)) ||
      (samePayload != null && tracksNestedPayloadDepth(sameTypeInfo!));
  return _PreparedListRead(
    size: size,
    trackRef: trackRef,
    hasNull: hasNull,
    usesDeclaredType: usesDeclaredType,
    elementFieldType: elementFieldType,
    declaredTypeInfo: declaredTypeInfo,
    declaredPayload: declaredPayload,
    sameTypeInfo: sameTypeInfo,
    samePayload: samePayload,
    tracksDepth: tracksDepth,
  );
}

@pragma('vm:prefer-inline')
Object? _readPreparedListItem(
  ReadContext context,
  _PreparedListRead state,
) {
  final declaredPayload = state.declaredPayload;
  if (declaredPayload != null) {
    if (state.hasNull || state.trackRef) {
      final flag = context.refReader.tryPreserveRefId(context.buffer);
      final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
      if (flag == RefWriter.nullFlag) {
        return null;
      }
      if (flag == RefWriter.refFlag) {
        return context.refReader.getReadRef();
      }
      final value = declaredPayload.read(
        context,
        hasPreservedRef: preservedRefId != null,
      );
      if (preservedRefId != null &&
          state.declaredTypeInfo!.supportsRef &&
          context.refReader.readRefAt(preservedRefId) == null) {
        context.refReader.setReadRef(preservedRefId, value);
      }
      return value;
    }
    return declaredPayload.read(context);
  }
  final samePayload = state.samePayload;
  if (samePayload != null) {
    if (state.hasNull || state.trackRef) {
      final flag = context.refReader.tryPreserveRefId(context.buffer);
      final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
      if (flag == RefWriter.nullFlag) {
        return null;
      }
      if (flag == RefWriter.refFlag) {
        return context.refReader.getReadRef();
      }
      final value = samePayload.read(
        context,
        hasPreservedRef: preservedRefId != null,
      );
      if (preservedRefId != null &&
          state.sameTypeInfo!.supportsRef &&
          context.refReader.readRefAt(preservedRefId) == null) {
        context.refReader.setReadRef(preservedRefId, value);
      }
      return value;
    }
    return samePayload.read(context);
  }
  if (state.usesDeclaredType && state.elementFieldType != null) {
    return readFieldTypeValue<Object?>(
      context,
      state.elementFieldType!,
      state.declaredTypeInfo,
      state.usesDeclaredType,
    );
  }
  if (state.sameTypeInfo != null) {
    if (state.hasNull || state.trackRef) {
      final flag = context.refReader.tryPreserveRefId(context.buffer);
      final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
      if (flag == RefWriter.nullFlag) {
        return null;
      }
      if (flag == RefWriter.refFlag) {
        return context.refReader.getReadRef();
      }
      final value = context.readResolvedValue(
        state.sameTypeInfo!,
        null,
        hasPreservedRef: preservedRefId != null,
      );
      if (preservedRefId != null &&
          state.sameTypeInfo!.supportsRef &&
          context.refReader.readRefAt(preservedRefId) == null) {
        context.refReader.setReadRef(preservedRefId, value);
      }
      return value;
    }
    return context.readResolvedValue(state.sameTypeInfo!, null);
  }
  if (state.trackRef) {
    return context.readRef();
  }
  if (state.hasNull) {
    return context.readNullable();
  }
  return context.readNonRef();
}

@pragma('vm:prefer-inline')
void writeFixedTypeValue(
  WriteContext context,
  FixedTypePayload payload,
  Object value, {
  required bool trackRef,
}) {
  if (!trackRef) {
    payload.write(context, value);
    return;
  }
  final handled = context.refWriter.writeRefOrNull(
    context.buffer,
    value,
    trackRef: payload.typeInfo.supportsRef,
  );
  if (!handled) {
    payload.write(context, value);
  }
}

_ListHeaderAnalysis _analyzeListHeader(
  WriteContext context,
  Iterable values, {
  required bool usesDeclaredType,
}) {
  var hasNull = false;
  if (usesDeclaredType) {
    for (final value in values) {
      if (value == null) {
        hasNull = true;
        break;
      }
    }
    return _ListHeaderAnalysis(
      hasNull: hasNull,
      sameType: true,
      sameTypeInfo: null,
      firstNonNull: null,
    );
  }
  Object? firstNonNull;
  TypeInfo? sameTypeInfo;
  Type? firstRuntimeType;
  var sameType = true;
  for (final value in values) {
    if (value == null) {
      hasNull = true;
      continue;
    }
    if (firstNonNull == null) {
      firstNonNull = value;
      firstRuntimeType = value.runtimeType;
      sameTypeInfo = context.typeResolver.resolveValue(value as Object);
      continue;
    }
    if (!sameType) {
      continue;
    }
    if (value.runtimeType != firstRuntimeType) {
      sameType = false;
    }
  }
  return _ListHeaderAnalysis(
    hasNull: hasNull,
    sameType: sameType,
    sameTypeInfo: sameTypeInfo,
    firstNonNull: firstNonNull,
  );
}

final class _ListHeaderAnalysis {
  final bool hasNull;
  final bool sameType;
  final TypeInfo? sameTypeInfo;
  final Object? firstNonNull;

  const _ListHeaderAnalysis({
    required this.hasNull,
    required this.sameType,
    required this.sameTypeInfo,
    required this.firstNonNull,
  });
}
