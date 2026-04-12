import 'package:meta/meta.dart';

import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/serializer/declared_value_codec.dart';
import 'package:fory/src/serializer/serializer_support.dart';
import 'package:fory/src/serializer/struct_field_runtime.dart';

@internal
Object generatedField(
  int slot,
  Map<String, Object?> metadata,
) {
  return StructFieldRuntime.fromMetadata(slot, metadata);
}

@internal
int generatedFieldSlot(Object field) => (field as StructFieldRuntime).slot;

@internal
List<Object>? generatedCompatibleWriteFields(WriteContext context) {
  return context.structWriteSession?.compatibleFields;
}

@internal
void writeGeneratedField(
  WriteContext context,
  Object field,
  Object? value,
) {
  writeDeclaredValue(
    context,
    (field as StructFieldRuntime).metadata,
    value,
  );
}

@internal
V readGeneratedField<V>(
  ReadContext context,
  Object field, [
  V? fallback,
]) {
  final compatibleValues = context.structReadSession?.compatibleValues;
  if (compatibleValues != null) {
    final slot = (field as StructFieldRuntime).slot;
    if (!compatibleValues.containsKey(slot)) {
      return fallback as V;
    }
    final value = compatibleValues[slot];
    if (value is DeferredReadRef) {
      return context.getReadRef(value.id) as V;
    }
    return value as V;
  }
  return readDeclaredValue(
    context,
    (field as StructFieldRuntime).metadata,
    fallback,
  );
}
