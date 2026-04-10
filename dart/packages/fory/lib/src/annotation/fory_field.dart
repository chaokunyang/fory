final class ForyField {
  final bool skip;
  final int? id;
  final bool? nullable;
  final bool ref;
  final bool? dynamic;

  const ForyField({
    this.skip = false,
    this.id,
    this.nullable,
    this.ref = false,
    this.dynamic,
  });
}
