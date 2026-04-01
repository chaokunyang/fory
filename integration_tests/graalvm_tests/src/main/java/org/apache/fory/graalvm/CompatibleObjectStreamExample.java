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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.fory.Fory;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.serializer.ObjectStreamSerializer;

public class CompatibleObjectStreamExample {
  public static class TreeSetSubclass extends TreeSet<String> {
    public TreeSetSubclass() {}
  }

  public static class TreeMapSubclass extends TreeMap<String, String> {
    public TreeMapSubclass() {}
  }

  public static class Container implements Serializable {
    private String name;
    private TreeSetSubclass values;
    private TreeMapSubclass attributes;

    public Container() {}

    public Container(String name, TreeSetSubclass values, TreeMapSubclass attributes) {
      this.name = name;
      this.values = values;
      this.attributes = attributes;
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
      s.defaultWriteObject();
    }

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
      s.defaultReadObject();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Container)) {
        return false;
      }
      Container that = (Container) o;
      return Objects.equals(name, that.name)
          && Objects.equals(values, that.values)
          && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, values, attributes);
    }
  }

  private static Fory createFory() {
    Fory fory =
        Fory.builder()
            .requireClassRegistration(true)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .withScopedMetaShare(false)
            .withCodegen(true)
            .build();
    fory.registerSerializer(Container.class, new ObjectStreamSerializer(fory, Container.class));
    fory.registerSerializer(
        TreeSetSubclass.class, new ObjectStreamSerializer(fory, TreeSetSubclass.class));
    fory.registerSerializer(
        TreeMapSubclass.class, new ObjectStreamSerializer(fory, TreeMapSubclass.class));
    fory.ensureSerializersCompiled();
    return fory;
  }

  private static void test(Fory fory) {
    TreeSetSubclass values = new TreeSetSubclass();
    values.add("one");
    values.add("two");
    TreeMapSubclass attributes = new TreeMapSubclass();
    attributes.put("alpha", "A");
    attributes.put("beta", "B");
    Container value = new Container("container", values, attributes);
    byte[] bytes = fory.serialize(value);
    fory.reset();
    Container roundTrip = (Container) fory.deserialize(bytes);
    if (!value.equals(roundTrip)) {
      throw new AssertionError("Compatible object stream round trip mismatch: " + roundTrip);
    }
  }

  public static void main(String[] args) {
    System.out.println("CompatibleObjectStreamExample started");
    Fory fory = createFory();
    test(fory);
    System.out.println("CompatibleObjectStreamExample succeed 1/2");
    fory = createFory();
    test(fory);
    System.out.println("CompatibleObjectStreamExample succeed");
  }
}
