final class Timestamp implements Comparable<Timestamp> {
  final int seconds;
  final int nanoseconds;

  const Timestamp(this.seconds, this.nanoseconds);

  factory Timestamp.fromDateTime(DateTime value) {
    final utcValue = value.toUtc();
    final microseconds = utcValue.microsecondsSinceEpoch;
    final seconds = microseconds ~/ Duration.microsecondsPerSecond;
    final micros = microseconds % Duration.microsecondsPerSecond;
    return Timestamp(seconds, micros * 1000);
  }

  DateTime toDateTime() => DateTime.fromMicrosecondsSinceEpoch(
    seconds * Duration.microsecondsPerSecond + nanoseconds ~/ 1000,
    isUtc: true,
  );

  @override
  int compareTo(Timestamp other) {
    final secondsCompare = seconds.compareTo(other.seconds);
    if (secondsCompare != 0) {
      return secondsCompare;
    }
    return nanoseconds.compareTo(other.nanoseconds);
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Timestamp &&
          other.seconds == seconds &&
          other.nanoseconds == nanoseconds;

  @override
  int get hashCode => Object.hash(seconds, nanoseconds);

  @override
  String toString() => toDateTime().toIso8601String();
}
