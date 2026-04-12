import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';

/// Advanced extension point for generated and manual serializers.
///
/// Most application code uses generated registration helpers instead of
/// implementing this interface directly.
abstract class Serializer<T> {
  const Serializer();

  /// Whether values handled by this serializer may participate in Ref
  /// tracking when the active field or root operation requests it.
  ///
  /// Immutable serializers such as enums should override this to `false`.
  bool get supportsRef => true;

  /// Writes [value] to [context].
  void write(WriteContext context, T value);

  /// Reads a value of type [T] from [context].
  T read(ReadContext context);
}

/// Enum-specific serializer base used by generated enum serializers.
abstract class EnumSerializer<T> extends Serializer<T> {
  const EnumSerializer();

  @override
  bool get supportsRef => false;
}

/// Union-specific serializer base used by manual union serializers.
abstract class UnionSerializer<T> extends Serializer<T> {
  const UnionSerializer();
}
