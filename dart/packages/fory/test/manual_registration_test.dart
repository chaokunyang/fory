import 'package:fory/fory.dart';
import 'package:test/test.dart';

final class ManualValue {
  ManualValue(this.name, this.score);

  final String name;
  final int score;
}

final class ManualValueSerializer extends Serializer<ManualValue> {
  const ManualValueSerializer();

  @override
  void write(WriteContext context, ManualValue value) {
    final buffer = context.buffer;
    buffer.writeUtf8(value.name);
    buffer.writeInt64(value.score);
  }

  @override
  ManualValue read(ReadContext context) {
    final buffer = context.buffer;
    return ManualValue(buffer.readUtf8(), buffer.readInt64());
  }
}

void main() {
  test('registers custom serializer through public register api', () {
    final fory = Fory();
    fory.register(
      ManualValue,
      const ManualValueSerializer(),
      namespace: 'manual',
      typeName: 'ManualValue',
    );

    final value = ManualValue('alpha', 99);
    final bytes = fory.serialize(value);
    final roundTrip = fory.deserialize<ManualValue>(bytes);
    expect(roundTrip.name, equals('alpha'));
    expect(roundTrip.score, equals(99));
  });
}
