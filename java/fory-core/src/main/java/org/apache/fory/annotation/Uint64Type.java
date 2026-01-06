package org.apache.fory.annotation;

import org.apache.fory.config.LongEncoding;

public @interface Uint64Type {
  LongEncoding encoding() default LongEncoding.VARINT64;
}
