import 'package:meta/meta.dart';
import 'package:fory/src/resolver/type_resolver.dart';

@internal
final Object structWriteSlotsKey = Object();

@internal
final Object structReadSlotsKey = Object();

@internal
final class StructWriteSlots {
  final List<FieldInfo> orderedFields;
  final List<FieldInfo?> _fieldsBySlot;

  StructWriteSlots(this.orderedFields, int fieldCount)
      : _fieldsBySlot = List<FieldInfo?>.filled(fieldCount, null) {
    for (final field in orderedFields) {
      _fieldsBySlot[field.slot] = field;
    }
  }

  FieldInfo? fieldForSlot(int slot) => _fieldsBySlot[slot];
}

@internal
final class StructReadSlots {
  final List<Object?> compatibleValues;
  final List<bool> presentSlots;

  const StructReadSlots(this.compatibleValues, this.presentSlots);

  bool containsSlot(int slot) => presentSlots[slot];

  Object? valueForSlot(int slot) => compatibleValues[slot];
}
