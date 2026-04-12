import 'package:meta/meta.dart';
import 'package:fory/src/serializer/struct_field_runtime.dart';

@internal
final Object structWriteSessionKey = Object();

@internal
final Object structReadSessionKey = Object();

@internal
final class StructWriteSession {
  final List<StructFieldRuntime> orderedFields;
  final List<StructFieldRuntime?> _fieldsBySlot;

  StructWriteSession(this.orderedFields, int fieldCount)
      : _fieldsBySlot = List<StructFieldRuntime?>.filled(fieldCount, null) {
    for (final field in orderedFields) {
      _fieldsBySlot[field.slot] = field;
    }
  }

  StructFieldRuntime? fieldForSlot(int slot) => _fieldsBySlot[slot];
}

@internal
final class StructReadSession {
  final List<Object?> compatibleValues;
  final List<bool> presentSlots;

  const StructReadSession(this.compatibleValues, this.presentSlots);

  bool containsSlot(int slot) => presentSlots[slot];

  Object? valueForSlot(int slot) => compatibleValues[slot];
}
