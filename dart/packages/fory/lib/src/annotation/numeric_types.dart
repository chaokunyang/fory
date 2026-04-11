enum LongEncoding { fixed, varint, tagged }

final class Int32Type {
  final bool compress;

  const Int32Type({this.compress = true});
}

final class Int64Type {
  final LongEncoding encoding;

  const Int64Type({this.encoding = LongEncoding.varint});
}

final class Uint8Type {
  const Uint8Type();
}

final class Uint16Type {
  const Uint16Type();
}

final class Uint32Type {
  final bool compress;

  const Uint32Type({this.compress = true});
}

final class Uint64Type {
  final LongEncoding encoding;

  const Uint64Type({this.encoding = LongEncoding.varint});
}
