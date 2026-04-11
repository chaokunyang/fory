import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';

abstract class Serializer<T> {
  const Serializer();

  bool get isStruct => false;

  bool get isEnum => false;

  bool get isUnion => false;

  bool get evolving => true;

  List<Map<String, Object?>> get fields => const <Map<String, Object?>>[];

  void write(WriteContext context, T value);

  T read(ReadContext context);
}
