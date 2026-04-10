import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';

abstract class Serializer<T> {
  const Serializer();

  void write(WriteContext context, T value);

  T read(ReadContext context);
}
