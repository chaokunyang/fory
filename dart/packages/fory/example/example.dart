import 'package:fory/fory.dart';

part 'example.fory.dart';

enum Color {
  red,
  blue,
}

@ForyStruct()
class Person {
  Person();

  String name = '';
  Int32 age = Int32(0);
  Color favoriteColor = Color.red;
  List<String> tags = <String>[];
}

void registerExampleTypes(Fory fory) {
  _installExampleForyRegistrations(fory);
  fory.register(Color);
  fory.register(Person);
}

void registerExampleType(
  Fory fory,
  Type type, {
  int? id,
  String? namespace,
  String? typeName,
}) {
  _installExampleForyRegistration(fory, type);
  fory.register(
    type,
    id: id,
    namespace: namespace,
    typeName: typeName,
  );
}

void main() {
  final fory = Fory();
  registerExampleTypes(fory);

  final person = Person()
    ..name = 'Ada'
    ..age = Int32(36)
    ..favoriteColor = Color.blue
    ..tags = <String>['engineer', 'mathematician'];

  final bytes = fory.serialize(person);
  final roundTrip = fory.deserialize<Person>(bytes);

  print('${roundTrip.name} ${roundTrip.age} ${roundTrip.favoriteColor}');
  print(roundTrip.tags);
}
