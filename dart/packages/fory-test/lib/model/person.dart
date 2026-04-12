library;

import 'package:fory/fory.dart';

import 'person.fory.dart' as generated;

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
  List<String?> tags = <String?>[];
  Map<String, Int32> scores = <String, Int32>{};
}

@ForyStruct()
class RefNode {
  RefNode();

  String name = '';

  @ForyField(ref: true)
  RefNode? self;
}

@ForyStruct()
class EvolvingPayload {
  EvolvingPayload();

  String value = '';
}

@ForyStruct(evolving: false)
class FixedPayload {
  FixedPayload();

  String value = '';
}

void registerPersonTypes(Fory fory) {
  generated.registerPersonForyTypes(fory);
}

void registerPersonType(
  Fory fory,
  Type type, {
  int? id,
  String? namespace,
  String? typeName,
}) {
  generated.registerPersonForyType(
    fory,
    type,
    id: id,
    namespace: namespace,
    typeName: typeName,
  );
}
