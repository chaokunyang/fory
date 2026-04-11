import 'dart:typed_data';

import 'package:meta/meta.dart';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
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
/// 2. register generated structs or enums, or register manual serializers
/// 3. call [serialize] and [deserialize]
///
/// The Dart runtime only supports xlang payloads.
final class Fory {
  static const int _nullHeaderFlag = 0x01;
  static const int _xlangHeaderFlag = 0x02;
  static final Map<Type, _GeneratedRegistrationBinding> _generatedBindings =
      <Type, _GeneratedRegistrationBinding>{};

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
    _writeContext = WriteContext(config, _typeResolver, RefWriter());
    _readContext = ReadContext(config, _typeResolver, RefReader());
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
    _writeContext.writeAny(value, trackRef: trackRef);
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
          'Only xlang payloads are supported by the Dart runtime.');
    }
    final value = _readContext.readAny();
    if (value is T) {
      return value;
    }
    throw StateError(
      'Deserialized value has type ${value.runtimeType}, expected $T.',
    );
  }

  /// Binds a generated struct serializer factory for [type].
  ///
  /// Generated part files call this before they invoke [registerStruct]. Normal
  /// application code should use the generated registration helper instead of
  /// calling this method directly.
  @internal
  static void bindGeneratedStructFactory(
    Type type,
    Serializer Function() serializerFactory,
  ) {
    _generatedBindings[type] = _GeneratedRegistrationBinding.struct(
      () => serializerFactory() as Serializer<Object?>,
    );
  }

  /// Binds a generated enum serializer factory for [type].
  ///
  /// Generated part files call this before they invoke [registerEnum]. Normal
  /// application code should use the generated registration helper instead of
  /// calling this method directly.
  @internal
  static void bindGeneratedEnumFactory(
    Type type,
    Serializer Function() serializerFactory,
  ) {
    _generatedBindings[type] = _GeneratedRegistrationBinding.enumType(
      () => serializerFactory() as Serializer<Object?>,
    );
  }

  /// Registers a generated struct type.
  ///
  /// Exactly one registration mode is required:
  /// - pass [id] for id-based registration, or
  /// - pass both [namespace] and [typeName] for name-based registration.
  ///
  /// Normal application code reaches this through a generated registration
  /// helper. For manual custom serializers, use [registerSerializer].
  void registerStruct(
    Type type, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    final serializerFactory = _generatedSerializerFactory(type,
        expectedKind: _GeneratedBindingKind.struct);
    _typeResolver.register(
      type,
      serializerFactory(),
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
  }

  /// Registers a generated enum type.
  ///
  /// Exactly one registration mode is required:
  /// - pass [id] for id-based registration, or
  /// - pass both [namespace] and [typeName] for name-based registration.
  ///
  /// Normal application code reaches this through a generated registration
  /// helper. For manual custom serializers, use [registerSerializer].
  void registerEnum(
    Type type, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    final serializerFactory = _generatedSerializerFactory(type,
        expectedKind: _GeneratedBindingKind.enumType);
    _typeResolver.register(
      type,
      serializerFactory(),
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
  /// This is the advanced escape hatch for external types or custom wire
  /// behavior. Prefer generated registration for annotated structs and enums.
  void registerSerializer(
    Type type,
    Serializer serializer, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    _typeResolver.register(
      type,
      serializer,
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
  }

  /// Registers a manual union serializer for [type].
  ///
  /// Exactly one registration mode is required:
  /// - pass [id] for id-based registration, or
  /// - pass both [namespace] and [typeName] for name-based registration.
  ///
  /// Use this only for manual union implementations. Generated struct and enum
  /// code should use [registerStruct] and [registerEnum].
  void registerUnion(
    Type type,
    Serializer serializer, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    _typeResolver.registerUnion(
      type,
      serializer,
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
  }

  Serializer<Object?> Function() _generatedSerializerFactory(
    Type type, {
    required _GeneratedBindingKind expectedKind,
  }) {
    final binding = _generatedBindings[type];
    if (binding == null) {
      throw StateError(
        'Type $type has no generated serializer binding. Call the generated registration helper for this library or use registerSerializer/registerUnion for manual serializers.',
      );
    }
    if (binding.kind != expectedKind) {
      throw StateError(
        'Type $type is bound as ${binding.kind.name}, not ${expectedKind.name}. Use the matching generated registration API.',
      );
    }
    return binding.serializerFactory;
  }
}

enum _GeneratedBindingKind { struct, enumType }

final class _GeneratedRegistrationBinding {
  final _GeneratedBindingKind kind;
  final Serializer<Object?> Function() serializerFactory;

  const _GeneratedRegistrationBinding._(this.kind, this.serializerFactory);

  factory _GeneratedRegistrationBinding.struct(
    Serializer<Object?> Function() serializerFactory,
  ) {
    return _GeneratedRegistrationBinding._(
      _GeneratedBindingKind.struct,
      serializerFactory,
    );
  }

  factory _GeneratedRegistrationBinding.enumType(
    Serializer<Object?> Function() serializerFactory,
  ) {
    return _GeneratedRegistrationBinding._(
      _GeneratedBindingKind.enumType,
      serializerFactory,
    );
  }
}
