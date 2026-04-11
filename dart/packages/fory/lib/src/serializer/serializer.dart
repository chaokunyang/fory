import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';

/// Advanced extension point for generated and manual serializers.
///
/// Most application code uses generated registration helpers instead of
/// implementing this interface directly.
abstract class Serializer<T> {
  const Serializer();

  /// Whether this serializer represents a struct.
  ///
  /// Generated struct serializers override this to `true`.
  bool get isStruct => false;

  /// Whether this serializer represents an enum.
  bool get isEnum => false;

  /// Whether this serializer represents a union.
  bool get isUnion => false;

  /// Whether the struct metadata for this serializer is evolving.
  ///
  /// Only meaningful for struct serializers.
  bool get evolving => true;

  /// Declared field metadata for struct serializers.
  ///
  /// Non-struct serializers normally leave this empty.
  List<Map<String, Object?>> get fields => const <Map<String, Object?>>[];

  /// Writes [value] to [context].
  void write(WriteContext context, T value);

  /// Reads a value of type [T] from [context].
  T read(ReadContext context);
}
