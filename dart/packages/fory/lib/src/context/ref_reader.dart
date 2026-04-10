import 'package:fory/src/buffer.dart';
import 'package:fory/src/context/ref_writer.dart';

final class RefReader {
  final List<Object?> _refs = <Object?>[];
  final List<int> _preservedIds = <int>[];
  Object? _resolved;

  int readRefHeader(Buffer buffer) {
    final flag = buffer.readByte();
    if (flag == RefWriter.refFlag) {
      _resolved = _refs[buffer.readVarUint32()];
      return flag;
    }
    if (flag == RefWriter.refValueFlag) {
      preserveRefId();
    } else {
      _resolved = null;
    }
    return flag;
  }

  int preserveRefId() {
    final id = _refs.length;
    _refs.add(null);
    _preservedIds.add(id);
    return id;
  }

  bool get hasPreservedRefId => _preservedIds.isNotEmpty;

  int get lastPreservedRefId => _preservedIds.last;

  Object? get readRef => _resolved;

  Object? readRefAt(int id) => _refs[id];

  void reference(Object? value) {
    if (_preservedIds.isEmpty) {
      _refs.add(value);
      return;
    }
    final id = _preservedIds.removeLast();
    _refs[id] = value;
  }

  void setReadRef(int id, Object? value) {
    _refs[id] = value;
  }

  void reset() {
    _refs.clear();
    _preservedIds.clear();
    _resolved = null;
  }
}
