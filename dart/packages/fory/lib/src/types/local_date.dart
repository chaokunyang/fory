final class LocalDate implements Comparable<LocalDate> {
  final int year;
  final int month;
  final int day;

  const LocalDate(this.year, this.month, this.day);

  factory LocalDate.fromEpochDay(int epochDay) {
    final instant = DateTime.fromMillisecondsSinceEpoch(
      epochDay * Duration.millisecondsPerDay,
      isUtc: true,
    );
    return LocalDate(instant.year, instant.month, instant.day);
  }

  int toEpochDay() => DateTime.utc(year, month, day)
      .millisecondsSinceEpoch ~/
      Duration.millisecondsPerDay;

  @override
  int compareTo(LocalDate other) {
    final yearCompare = year.compareTo(other.year);
    if (yearCompare != 0) {
      return yearCompare;
    }
    final monthCompare = month.compareTo(other.month);
    if (monthCompare != 0) {
      return monthCompare;
    }
    return day.compareTo(other.day);
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is LocalDate &&
          other.year == year &&
          other.month == month &&
          other.day == day;

  @override
  int get hashCode => Object.hash(year, month, day);

  @override
  String toString() =>
      '$year-${month.toString().padLeft(2, '0')}-${day.toString().padLeft(2, '0')}';
}
