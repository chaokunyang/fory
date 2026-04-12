import 'package:meta/meta.dart';
import 'package:fory/src/serializer/struct_field_binding.dart';

@internal
final Object structWriteSlotsKey = Object();

@internal
final Object structReadSlotsKey = Object();

@internal
final class StructWriteSlots {
  final List<StructFieldBinding> orderedFields;
  final List<StructFieldBinding?> _fieldsBySlot;

  StructWriteSlots(this.orderedFields, int fieldCount)
      : _fieldsBySlot = List<StructFieldBinding?>.filled(fieldCount, null) {
    for (final field in orderedFields) {
      _fieldsBySlot[field.slot] = field;
    }
  }

  StructFieldBinding? fieldForSlot(int slot) => _fieldsBySlot[slot];
}

@internal
final class StructReadSlots {
  final List<Object?> compatibleValues;
  final List<bool> presentSlots;

  const StructReadSlots(this.compatibleValues, this.presentSlots);

  bool containsSlot(int slot) => presentSlots[slot];

  Object? valueForSlot(int slot) => compatibleValues[slot];
}
