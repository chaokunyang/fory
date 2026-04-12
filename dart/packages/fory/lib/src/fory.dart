import 'dart:typed_data';

import 'package:meta/meta.dart';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/context/compatible_struct_metadata_store.dart';
import 'package:fory/src/context/meta_string_reader.dart';
import 'package:fory/src/context/meta_string_writer.dart';
import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_reader.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/serializer.dart';

/// Root facade for Apache Fory xlang serialization in Dart.
///
/// Typical usage is:
/// 1. create a [Fory] instance
/// 2. register generated types or register manual serializers
/// 3. call [serialize] and [deserialize]
///
/// The Dart runtime only supports xlang payloads.
final class Fory {
  static const int _nullHeaderFlag = 0x01;
  static const int _xlangHeaderFlag = 0x02;
  static final Map<Type, _GeneratedBinding> _generatedBindings =
      <Type, _GeneratedBinding>{};

  late final Buffer _buffer;
  late final WriteContext _writeContext;
  late final ReadContext _readContext;
  late final TypeResolver _typeResolver;

  /// Creates a runtime configured by [config].
  ///
  /// The same instance can be reused across many operations. Each operation
  /// resets its transient read/write state before work starts.
  Fory({Config config = const Config()}) {
    _buffer = Buffer();
    _typeResolver = TypeResolver(config);
    final compatibleStructMetadata = CompatibleStructMetadataStore();
    _writeContext = WriteContext(
      config,
      _typeResolver,
      RefWriter(),
      MetaStringWriter(),
      compatibleStructMetadata,
    );
    _readContext = ReadContext(
      config,
      _typeResolver,
      RefReader(),
      MetaStringReader(_typeResolver),
      compatibleStructMetadata,
    );
  }

  /// Serializes [value] into a new byte array.
  ///
  /// Set [trackRef] to `true` only when the root value is a graph or container
  /// that needs shared-reference tracking and there is no field metadata to
  /// request it. Annotated fields should still use `@ForyField(ref: true)`.
  Uint8List serialize(Object? value, {bool trackRef = false}) {
    _buffer.clear();
    serializeTo(value, _buffer, trackRef: trackRef);
    return Uint8List.fromList(_buffer.toBytes());
  }

  /// Serializes [value] into [buffer].
  ///
  /// The target [buffer] is cleared before bytes are written. [trackRef] has
  /// the same root-level semantics as [serialize].
  void serializeTo(Object? value, Buffer buffer, {bool trackRef = false}) {
    buffer.clear();
    _writeContext.prepare(buffer, trackRef: trackRef);
    if (value == null) {
      buffer.writeUint8(_nullHeaderFlag);
      return;
    }
    buffer.writeUint8(_xlangHeaderFlag);
    _writeContext.writeRootValue(value, trackRef: trackRef);
  }

  /// Deserializes [bytes] and then checks that the result is assignable to [T].
  ///
  /// The payload is decoded from its wire metadata first. `T` is used as a
  /// post-read type check, not as an alternate schema.
  T deserialize<T>(Uint8List bytes) {
    final buffer = Buffer.wrap(bytes);
    return deserializeFrom<T>(buffer);
  }

  /// Deserializes a value from [buffer] and checks that it is assignable to
  /// [T].
  ///
  /// Only xlang payloads are supported. This method consumes bytes from the
  /// current reader position of [buffer].
  T deserializeFrom<T>(Buffer buffer) {
    _readContext.prepare(buffer);
    final header = buffer.readUint8();
    if ((header & _nullHeaderFlag) != 0) {
      return null as T;
    }
    if ((header & _xlangHeaderFlag) == 0) {
      throw StateError(
        'Only xlang payloads are supported by the Dart runtime.',
      );
    }
    final value = _readContext.readRef();
    if (value is T) {
      return value;
    }
    throw StateError(
      'Deserialized value has type ${value.runtimeType}, expected $T.',
    );
  }

  /// Binds a generated serializer factory for [type].
  ///
  /// Generated part files call this before they invoke [register]. Normal
  /// application code should use the generated registration helper instead of
  /// calling this method directly.
  @internal
  static void bindGeneratedEnumFactory(
    Type type,
    Serializer<Object?> Function() serializerFactory,
  ) {
    _generatedBindings[type] = _GeneratedBinding(
      kind: RegistrationKindInternal.enumType,
      serializerFactory: serializerFactory,
    );
  }

  /// Binds a generated struct serializer factory for [type].
  ///
  /// Generated part files and package-internal handwritten generated-style
  /// serializers call this before they invoke [register].
  @internal
  static void bindGeneratedStructFactory(
    Type type,
    Serializer<Object?> Function() serializerFactory, {
    required bool evolving,
    required List<Map<String, Object?>> fields,
  }) {
    _generatedBindings[type] = _GeneratedBinding(
      kind: RegistrationKindInternal.struct,
      serializerFactory: serializerFactory,
      evolving: evolving,
      fieldMetadata: List<Map<String, Object?>>.unmodifiable(fields),
    );
  }

  /// Registers a generated type.
  ///
  /// Exactly one registration mode is required:
  /// - pass [id] for id-based registration, or
  /// - pass both [namespace] and [typeName] for name-based registration.
  ///
  /// Normal application code reaches this through a generated registration
  /// helper. For manual serializers, including unions, use
  /// [registerSerializer].
  void register(
    Type type, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    final binding = _generatedBinding(type);
    final serializer = binding.serializerFactory();
    _typeResolver.registerGenerated(
      type,
      serializer,
      kind: binding.kind,
      evolving: binding.evolving,
      fields: binding.fieldMetadata
          .map(FieldMetadataInternal.fromMetadata)
          .toList(growable: false),
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
  }

  /// Registers a manual serializer for [type].
  ///
  /// Exactly one registration mode is required:
  /// - pass [id] for id-based registration, or
  /// - pass both [namespace] and [typeName] for name-based registration.
  ///
  /// This is the advanced escape hatch for external types, manual unions, or
  /// custom wire behavior. Prefer [register] for generated types.
  void registerSerializer(
    Type type,
    Serializer serializer, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    _typeResolver.registerSerializer(
      type,
      serializer,
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
  }

  _GeneratedBinding _generatedBinding(Type type) {
    final binding = _generatedBindings[type];
    if (binding == null) {
      throw StateError(
        'Type $type has no generated serializer binding. Call the generated registration helper for this library or use registerSerializer for manual serializers.',
      );
    }
    return binding;
  }
}

final class _GeneratedBinding {
  final RegistrationKindInternal kind;
  final Serializer<Object?> Function() serializerFactory;
  final bool evolving;
  final List<Map<String, Object?>> fieldMetadata;

  const _GeneratedBinding({
    required this.kind,
    required this.serializerFactory,
    this.evolving = true,
    this.fieldMetadata = const <Map<String, Object?>>[],
  });
}
