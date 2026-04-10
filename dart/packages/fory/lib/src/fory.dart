import 'dart:typed_data';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_reader.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/serializer.dart';

final class Fory {
  static const int _nullHeaderFlag = 0x01;
  static const int _xlangHeaderFlag = 0x02;

  late final Buffer _buffer;
  late final WriteContext _writeContext;
  late final ReadContext _readContext;
  late final TypeResolver _typeResolver;

  Fory({Config config = const Config()}) {
    _buffer = Buffer();
    _typeResolver = TypeResolver(config);
    _writeContext = WriteContext(config, _typeResolver, RefWriter());
    _readContext = ReadContext(config, _typeResolver, RefReader());
  }

  Uint8List serialize(Object? value, {bool trackRef = false}) {
    _buffer.clear();
    serializeTo(value, _buffer, trackRef: trackRef);
    return Uint8List.fromList(_buffer.toBytes());
  }

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

  T deserialize<T>(Uint8List bytes) {
    final buffer = Buffer.wrap(bytes);
    return deserializeFrom<T>(buffer);
  }

  T deserializeFrom<T>(Buffer buffer) {
    _readContext.prepare(buffer);
    final header = buffer.readUint8();
    if ((header & _nullHeaderFlag) != 0) {
      return null as T;
    }
    if ((header & _xlangHeaderFlag) == 0) {
      throw StateError('Only xlang payloads are supported by the Dart runtime.');
    }
    final value = _readContext.readAny();
    if (value is T) {
      return value;
    }
    throw StateError(
      'Deserialized value has type ${value.runtimeType}, expected $T.',
    );
  }

  void register(
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
}
