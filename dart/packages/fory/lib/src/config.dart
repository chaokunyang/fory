final class Config {
  static const int defaultMaxDepth = 256;
  static const int defaultMaxCollectionSize = 1 << 20;
  static const int defaultMaxBinarySize = 64 * 1024 * 1024;

  final bool compatible;
  final bool checkStructVersion;
  final int maxDepth;
  final int maxCollectionSize;
  final int maxBinarySize;

  const Config({
    this.compatible = false,
    bool checkStructVersion = true,
    this.maxDepth = defaultMaxDepth,
    this.maxCollectionSize = defaultMaxCollectionSize,
    this.maxBinarySize = defaultMaxBinarySize,
  }) : checkStructVersion = compatible ? false : checkStructVersion,
       assert(maxDepth > 0, 'maxDepth must be positive'),
       assert(
         maxCollectionSize > 0,
         'maxCollectionSize must be positive',
       ),
       assert(maxBinarySize > 0, 'maxBinarySize must be positive');
}
