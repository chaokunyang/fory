package org.apache.fory.annotation;

import org.apache.fory.config.LongEncoding;

public @interface Int64Type {
  LongEncoding encoding() default LongEncoding.VARINT64;
}
