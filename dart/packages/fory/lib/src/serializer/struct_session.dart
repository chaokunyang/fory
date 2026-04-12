import 'package:meta/meta.dart';

@internal
final class StructWriteSession {
  final List<Object> compatibleFields;

  const StructWriteSession(this.compatibleFields);
}

@internal
final class StructReadSession {
  final Map<int, Object?> compatibleValues;

  const StructReadSession(this.compatibleValues);
}
