library;

import 'package:fory/fory.dart';

part 'person.g.dart';

enum Color {
  red,
  blue,
}

@ForyObject()
class Person {
  Person();

  String name = '';
  Int32 age = Int32(0);
  Color favoriteColor = Color.red;
  List<String?> tags = <String?>[];
  Map<String, Int32> scores = <String, Int32>{};
}

@ForyObject()
class RefNode {
  RefNode();

  String name = '';

  @ForyField(ref: true)
  RefNode? self;
}

@ForyObject()
class EvolvingPayload {
  EvolvingPayload();

  String value = '';
}

@ForyObject(evolving: false)
class FixedPayload {
  FixedPayload();

  String value = '';
}

void registerPersonTypes(Fory fory) {
  _registerPersonForyTypes(fory);
}
