/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fory.graalvm;

import java.util.Arrays;
import java.util.Objects;
import org.apache.fory.Fory;
import org.apache.fory.util.Preconditions;

/**
 * Test for abstract class corner cases in GraalVM native image. This tests the fix for issue #2695:
 * Abstract enums (enums with abstract methods) and arrays of abstract types.
 */
public class AbstractClassExample {

  // Abstract enum with abstract methods - each enum value is an anonymous inner class
  public enum AbstractEnum {
    VALUE1 {
      @Override
      public int getValue() {
        return 1;
      }
    },
    VALUE2 {
      @Override
      public int getValue() {
        return 2;
      }
    };

    public abstract int getValue();
  }

  // Abstract base class for testing abstract object arrays
  public abstract static class AbstractBase {
    public int id;

    public abstract String getType();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AbstractBase that = (AbstractBase) o;
      return id == that.id;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  // Concrete implementation 1
  public static class ConcreteA extends AbstractBase {
    public String name;

    @Override
    public String getType() {
      return "A";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      ConcreteA concreteA = (ConcreteA) o;
      return Objects.equals(name, concreteA.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), name);
    }
  }

  // Concrete implementation 2
  public static class ConcreteB extends AbstractBase {
    public long value;

    @Override
    public String getType() {
      return "B";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      ConcreteB concreteB = (ConcreteB) o;
      return value == concreteB.value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), value);
    }
  }

  // Container class that holds abstract enum and abstract array
  public static class Container {
    public AbstractEnum enumValue;
    public AbstractEnum[] enumArray;
    public AbstractBase[] baseArray;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Container container = (Container) o;
      return enumValue == container.enumValue
          && Arrays.equals(enumArray, container.enumArray)
          && Arrays.equals(baseArray, container.baseArray);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(enumValue);
      result = 31 * result + Arrays.hashCode(enumArray);
      result = 31 * result + Arrays.hashCode(baseArray);
      return result;
    }
  }

  private static Fory createFory() {
    Fory fory =
        Fory.builder()
            .withName(AbstractClassExample.class.getName())
            .registerGuavaTypes(false)
            .withCodegen(false)
            .build();
    fory.register(AbstractEnum.class);
    fory.register(ConcreteA.class);
    fory.register(ConcreteB.class);
    fory.register(Container.class);
    fory.register(AbstractBase[].class);
    fory.register(AbstractEnum[].class);
    fory.ensureSerializersCompiled();
    return fory;
  }

  public static void main(String[] args) {
    Fory fory = createFory();
    fory.reset();
    testAbstractEnum(fory);
    testAbstractEnumArray(fory);
    testAbstractObjectArray(fory);
    testContainer(fory);

    System.out.println("AbstractClassExample succeed");
  }

  private static void testAbstractEnum(Fory fory) {
    byte[] bytes1 = fory.serialize(AbstractEnum.VALUE1);
    AbstractEnum result1 = fory.deserialize(bytes1, AbstractEnum.class);
    Preconditions.checkArgument(result1 == AbstractEnum.VALUE1, "VALUE1 should match");
    Preconditions.checkArgument(result1.getValue() == 1, "VALUE1.getValue() should be 1");

    byte[] bytes2 = fory.serialize(AbstractEnum.VALUE2);
    AbstractEnum result2 = fory.deserialize(bytes2, AbstractEnum.class);
    Preconditions.checkArgument(result2 == AbstractEnum.VALUE2, "VALUE2 should match");
    Preconditions.checkArgument(result2.getValue() == 2, "VALUE2.getValue() should be 2");
  }

  private static void testAbstractEnumArray(Fory fory) {
    AbstractEnum[] array = new AbstractEnum[] {AbstractEnum.VALUE1, AbstractEnum.VALUE2};
    byte[] bytes = fory.serialize(array);
    AbstractEnum[] result = fory.deserialize(bytes, AbstractEnum[].class);
    Preconditions.checkArgument(Arrays.equals(array, result), "Enum arrays should match");
    Preconditions.checkArgument(result[0].getValue() == 1, "result[0].getValue() should be 1");
    Preconditions.checkArgument(result[1].getValue() == 2, "result[1].getValue() should be 2");
  }

  private static void testAbstractObjectArray(Fory fory) {
    ConcreteA a = new ConcreteA();
    a.id = 1;
    a.name = "test";

    ConcreteB b = new ConcreteB();
    b.id = 2;
    b.value = 100L;

    AbstractBase[] array = new AbstractBase[] {a, b};
    byte[] bytes = fory.serialize(array);
    AbstractBase[] result = fory.deserialize(bytes, AbstractBase[].class);

    Preconditions.checkArgument(result.length == 2, "Array length should be 2");
    Preconditions.checkArgument(result[0] instanceof ConcreteA, "result[0] should be ConcreteA");
    Preconditions.checkArgument(result[1] instanceof ConcreteB, "result[1] should be ConcreteB");
    Preconditions.checkArgument(result[0].equals(a), "result[0] should equal a");
    Preconditions.checkArgument(result[1].equals(b), "result[1] should equal b");
    Preconditions.checkArgument(
        "A".equals(result[0].getType()), "result[0].getType() should be 'A'");
    Preconditions.checkArgument(
        "B".equals(result[1].getType()), "result[1].getType() should be 'B'");
  }

  private static void testContainer(Fory fory) {
    ConcreteA a = new ConcreteA();
    a.id = 10;
    a.name = "containerTest";

    ConcreteB b = new ConcreteB();
    b.id = 20;
    b.value = 200L;

    Container container = new Container();
    container.enumValue = AbstractEnum.VALUE1;
    container.enumArray = new AbstractEnum[] {AbstractEnum.VALUE2, AbstractEnum.VALUE1};
    container.baseArray = new AbstractBase[] {a, b};

    byte[] bytes = fory.serialize(container);
    Container result = fory.deserialize(bytes, Container.class);

    Preconditions.checkArgument(container.equals(result), "Container should match");
    Preconditions.checkArgument(
        result.enumValue.getValue() == 1, "container.enumValue.getValue() should be 1");
  }
}
