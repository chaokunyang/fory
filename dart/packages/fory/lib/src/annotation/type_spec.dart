/// Type-level annotations for configuring nested container elements.
///
/// Use [ListType] and [MapType] on fields to override default ref-tracking
/// and nullability for container elements, keys, and values.
library;

/// Option that modifies a type's serialization behavior.
abstract class TypeOption {
  const TypeOption();

  const factory TypeOption.ref([bool tracked]) = RefOption;
  const factory TypeOption.nullable([bool value]) = NullableOption;
}

/// Enables or disables reference tracking for a type.
final class RefOption extends TypeOption {
  final bool tracked;
  const RefOption([this.tracked = true]);
}

/// Overrides nullability for a type.
final class NullableOption extends TypeOption {
  final bool value;
  const NullableOption([this.value = true]);
}

/// Base class for type specifications that carry [TypeOption]s.
abstract class TypeSpec {
  final List<TypeOption> options;
  const TypeSpec([this.options = const []]);
}

/// Specifies options for a scalar or object value type.
final class ValueType extends TypeSpec {
  const ValueType([super.options]);

  const ValueType.ref() : super(const [TypeOption.ref()]);
  const ValueType.noRef() : super(const [TypeOption.ref(false)]);
  const ValueType.nullable() : super(const [TypeOption.nullable()]);
  const ValueType.nonNullable() : super(const [TypeOption.nullable(false)]);
  const ValueType.refNullable()
      : super(const [TypeOption.ref(), TypeOption.nullable()]);
}

/// Specifies options for a list or set field, including its element type.
final class ListType extends TypeSpec {
  final TypeSpec element;

  const ListType({
    this.element = const ValueType(),
    List<TypeOption> options = const [],
  }) : super(options);

  const ListType.ref({
    this.element = const ValueType(),
  }) : super(const [TypeOption.ref()]);

  const ListType.noRef({
    this.element = const ValueType(),
  }) : super(const [TypeOption.ref(false)]);

  const ListType.nullable({
    this.element = const ValueType(),
  }) : super(const [TypeOption.nullable()]);
}

/// Specifies options for a map field, including its key and value types.
final class MapType extends TypeSpec {
  final TypeSpec key;
  final TypeSpec value;

  const MapType({
    this.key = const ValueType(),
    this.value = const ValueType(),
    List<TypeOption> options = const [],
  }) : super(options);

  const MapType.ref({
    this.key = const ValueType(),
    this.value = const ValueType(),
  }) : super(const [TypeOption.ref()]);

  const MapType.noRef({
    this.key = const ValueType(),
    this.value = const ValueType(),
  }) : super(const [TypeOption.ref(false)]);

  const MapType.nullable({
    this.key = const ValueType(),
    this.value = const ValueType(),
  }) : super(const [TypeOption.nullable()]);
}
