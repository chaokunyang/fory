import 'package:fory/fory.dart';
import 'package:fory_test/model/person.dart';
import 'package:test/test.dart';

void main() {
  group('generated registration', () {
    test('round-trips struct and enum data', () {
      final fory = Fory();
      registerPersonTypes(fory);

      final person = Person()
        ..name = 'Ada'
        ..age = Int32(36)
        ..favoriteColor = Color.blue
        ..tags = <String?>['engineer', null]
        ..scores = <String, Int32>{
          'math': Int32(100),
          'logic': Int32(99),
        };

      final bytes = fory.serialize(person);
      final roundTrip = fory.deserialize<Person>(bytes);

      expect(roundTrip.name, equals('Ada'));
      expect(roundTrip.age, equals(Int32(36)));
      expect(roundTrip.favoriteColor, equals(Color.blue));
      expect(roundTrip.tags, equals(<String?>['engineer', null]));
      expect(roundTrip.scores['math'], equals(Int32(100)));
      expect(roundTrip.scores['logic'], equals(Int32(99)));
    });

    test('supports root trackRef for top-level graphs', () {
      final fory = Fory();
      registerPersonTypes(fory);

      final node = RefNode()..name = 'root';
      final bytes = fory.serialize(<Object?>[node, node], trackRef: true);
      final roundTrip = fory.deserialize<Object?>(bytes) as List<Object?>;

      expect(roundTrip, hasLength(2));
      expect(identical(roundTrip[0], roundTrip[1]), isTrue);
    });

    test('preserves self reference on annotated ref fields', () {
      final fory = Fory();
      registerPersonTypes(fory);

      final node = RefNode()
        ..name = 'self'
        ..self = null;
      node.self = node;

      final bytes = fory.serialize(node);
      final roundTrip = fory.deserialize<RefNode>(bytes);
      expect(identical(roundTrip, roundTrip.self), isTrue);
    });

    test('fixed payload stays smaller than evolving payload in compatible mode', () {
      final fory = Fory(config: const Config(compatible: true));
      registerPersonTypes(fory);

      final evolving = EvolvingPayload()..value = 'payload';
      final fixed = FixedPayload()..value = 'payload';

      final evolvingBytes = fory.serialize(evolving);
      final fixedBytes = fory.serialize(fixed);

      expect(fixedBytes.length, lessThan(evolvingBytes.length));
      expect(fory.deserialize<EvolvingPayload>(evolvingBytes).value, equals('payload'));
      expect(fory.deserialize<FixedPayload>(fixedBytes).value, equals('payload'));
    });
  });
}
