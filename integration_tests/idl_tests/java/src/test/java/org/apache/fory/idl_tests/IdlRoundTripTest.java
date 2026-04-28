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

package org.apache.fory.idl_tests;

import addressbook.AddressBook;
import addressbook.AddressbookForyRegistration;
import addressbook.Animal;
import addressbook.Cat;
import addressbook.Dog;
import addressbook.Person;
import addressbook.Person.PhoneNumber;
import addressbook.Person.PhoneType;
import auto_id.AutoIdForyRegistration;
import auto_id.Envelope;
import auto_id.Wrapper;
import example.ExampleForyRegistration;
import example.ExampleMessage;
import example.ExampleMessageUnion;
import example_common.ExampleCommonForyRegistration;
import example_common.ExampleLeaf;
import example_common.ExampleLeafUnion;
import example_common.ExampleState;
import collection.CollectionForyRegistration;
import collection.NumericCollectionArrayUnion;
import collection.NumericCollectionUnion;
import collection.NumericCollections;
import collection.NumericCollectionsArray;
import complex_pb.ComplexPbForyRegistration;
import complex_pb.PrimitiveTypes;
import any_example.AnyExampleForyRegistration;
import any_example.AnyHolder;
import any_example.AnyInner;
import any_example.AnyUnion;
import complex_fbs.ComplexFbsForyRegistration;
import complex_fbs.Container;
import complex_fbs.Metric;
import complex_fbs.Note;
import complex_fbs.Payload;
import complex_fbs.ScalarPack;
import complex_fbs.Status;
import evolving1.Evolving1ForyRegistration;
import evolving1.EvolvingMessage;
import evolving1.EvolvingSizeMessage;
import evolving1.FixedMessage;
import evolving1.FixedSizeMessage;
import evolving2.Evolving2ForyRegistration;
import graph.Edge;
import graph.Graph;
import graph.GraphForyRegistration;
import graph.Node;
import root.MultiHolder;
import optional_types.AllOptionalTypes;
import optional_types.OptionalHolder;
import optional_types.OptionalTypesForyRegistration;
import optional_types.OptionalUnion;
import tree.TreeForyRegistration;
import tree.TreeNode;
import monster.Color;
import monster.Monster;
import monster.MonsterForyRegistration;
import monster.Vec3;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.fory.Fory;
import org.apache.fory.collection.BFloat16List;
import org.apache.fory.collection.Float16List;
import org.apache.fory.collection.Int16List;
import org.apache.fory.collection.Int32List;
import org.apache.fory.collection.Int64List;
import org.apache.fory.collection.Int8List;
import org.apache.fory.collection.Uint16List;
import org.apache.fory.collection.Uint32List;
import org.apache.fory.collection.Uint64List;
import org.apache.fory.collection.Uint8List;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.config.Language;
import org.apache.fory.type.BFloat16;
import org.apache.fory.type.Float16;
import org.testng.Assert;
import org.testng.annotations.Test;

public class IdlRoundTripTest {

  @Test
  public void testAddressBookRoundTripCompatible() throws Exception {
    runAddressBookRoundTrip(true);
  }

  @Test
  public void testAddressBookRoundTripSchemaConsistent() throws Exception {
    runAddressBookRoundTrip(false);
  }

  @Test
  public void testAutoIdRoundTripCompatible() throws Exception {
    runAutoIdRoundTrip(true);
  }

  @Test
  public void testAutoIdRoundTripSchemaConsistent() throws Exception {
    runAutoIdRoundTrip(false);
  }

  @Test
  public void testEvolvingRoundTrip() {
    runEvolvingRoundTrip();
  }

  private void runAddressBookRoundTrip(boolean compatible) throws Exception {
    Fory fory = buildFory(compatible);
    AddressbookForyRegistration.register(fory);

    AddressBook book = buildAddressBook();
    byte[] bytes = fory.serialize(book);
    Object decoded = fory.deserialize(bytes);

    Assert.assertTrue(decoded instanceof AddressBook);
    Assert.assertEquals(decoded, book);

    Map<String, String> peerSelection = Collections.singletonMap("DATA_FILE", "");
    for (String peer : resolvePeers(peerSelection)) {
      Path dataFile = Files.createTempFile("idl-" + peer + "-", ".bin");
      dataFile.toFile().deleteOnExit();
      Files.write(dataFile, bytes);

      Map<String, String> env = new HashMap<>();
      env.put("DATA_FILE", dataFile.toAbsolutePath().toString());
      PeerCommand command = buildPeerCommand(peer, env, compatible);
      runPeer(command, peer);

      byte[] peerBytes = Files.readAllBytes(dataFile);
      Object roundTrip = fory.deserialize(peerBytes);
      Assert.assertTrue(roundTrip instanceof AddressBook);
      Assert.assertEquals(roundTrip, book);
    }
  }

  private void runAutoIdRoundTrip(boolean compatible) throws Exception {
    Fory fory = buildFory(compatible);
    AutoIdForyRegistration.register(fory);

    Envelope envelope = buildAutoIdEnvelope();
    byte[] bytes = fory.serialize(envelope);
    Object decoded = fory.deserialize(bytes);

    Assert.assertTrue(decoded instanceof Envelope);
    Assert.assertEquals(decoded, envelope);

    Wrapper wrapper = buildAutoIdWrapper(envelope);
    byte[] wrapperBytes = fory.serialize(wrapper);
    Object decodedWrapper = fory.deserialize(wrapperBytes);
    Assert.assertTrue(decodedWrapper instanceof Wrapper);
    Assert.assertEquals(decodedWrapper, wrapper);

    Map<String, String> peerSelection = Collections.singletonMap("DATA_FILE_AUTO_ID", "");
    for (String peer : resolvePeers(peerSelection)) {
      Path dataFile = Files.createTempFile("idl-auto-id-" + peer + "-", ".bin");
      dataFile.toFile().deleteOnExit();
      Files.write(dataFile, bytes);

      Map<String, String> env = new HashMap<>();
      env.put("DATA_FILE_AUTO_ID", dataFile.toAbsolutePath().toString());
      PeerCommand command = buildPeerCommand(peer, env, compatible);
      runPeer(command, peer);

      byte[] peerBytes = Files.readAllBytes(dataFile);
      Object roundTrip = fory.deserialize(peerBytes);
      Assert.assertTrue(roundTrip instanceof Envelope);
      Assert.assertEquals(roundTrip, envelope);
    }
  }

  private void runEvolvingRoundTrip() {
    Fory foryV1 = buildFory(true);
    Fory foryV2 = buildFory(true);
    Evolving1ForyRegistration.register(foryV1);
    Evolving2ForyRegistration.register(foryV2);

    EvolvingMessage messageV1 = new EvolvingMessage();
    messageV1.setId(1);
    messageV1.setName("Alice");
    messageV1.setCity("NYC");

    byte[] bytes = foryV1.serialize(messageV1);
    Object decoded = foryV2.deserialize(bytes);
    Assert.assertTrue(decoded instanceof evolving2.EvolvingMessage);
    evolving2.EvolvingMessage messageV2 = (evolving2.EvolvingMessage) decoded;
    Assert.assertEquals(messageV2.getId(), messageV1.getId());
    Assert.assertEquals(messageV2.getName(), messageV1.getName());
    Assert.assertEquals(messageV2.getCity(), messageV1.getCity());
    messageV2.setEmail("alice@example.com");

    byte[] roundTripBytes = foryV2.serialize(messageV2);
    Object roundTrip = foryV1.deserialize(roundTripBytes);
    Assert.assertTrue(roundTrip instanceof EvolvingMessage);
    Assert.assertEquals(roundTrip, messageV1);

    FixedMessage fixedV1 = new FixedMessage();
    fixedV1.setId(10);
    fixedV1.setName("Bob");
    fixedV1.setScore(90);
    fixedV1.setNote("note");

    byte[] fixedBytes = foryV1.serialize(fixedV1);
    try {
      Object fixedDecoded = foryV2.deserialize(fixedBytes);
      byte[] fixedRoundTripBytes = foryV2.serialize(fixedDecoded);
      Object fixedRoundTrip = foryV1.deserialize(fixedRoundTripBytes);
      Assert.assertNotEquals(fixedRoundTrip, fixedV1);
    } catch (Exception ignored) {
      // Expected failure for non-evolving struct.
    }

    EvolvingSizeMessage evolvingSizeV1 = new EvolvingSizeMessage();
    evolvingSizeV1.setPayload("payload");
    FixedSizeMessage fixedSizeV1 = new FixedSizeMessage();
    fixedSizeV1.setPayload("payload");

    byte[] evolvingSizeBytes = foryV1.serialize(evolvingSizeV1);
    byte[] fixedSizeBytes = foryV1.serialize(fixedSizeV1);
    Assert.assertTrue(fixedSizeBytes.length < evolvingSizeBytes.length);

    Object evolvingSizeDecoded = foryV2.deserialize(evolvingSizeBytes);
    Assert.assertTrue(evolvingSizeDecoded instanceof evolving2.EvolvingSizeMessage);
    Assert.assertEquals(
        ((evolving2.EvolvingSizeMessage) evolvingSizeDecoded).getPayload(),
        evolvingSizeV1.getPayload());
    Object evolvingSizeRoundTrip = foryV1.deserialize(foryV2.serialize(evolvingSizeDecoded));
    Assert.assertTrue(evolvingSizeRoundTrip instanceof EvolvingSizeMessage);
    Assert.assertEquals(evolvingSizeRoundTrip, evolvingSizeV1);

    Object fixedSizeDecoded = foryV2.deserialize(fixedSizeBytes);
    Assert.assertTrue(fixedSizeDecoded instanceof evolving2.FixedSizeMessage);
    Assert.assertEquals(
        ((evolving2.FixedSizeMessage) fixedSizeDecoded).getPayload(),
        fixedSizeV1.getPayload());
    Object fixedSizeRoundTrip = foryV1.deserialize(foryV2.serialize(fixedSizeDecoded));
    Assert.assertTrue(fixedSizeRoundTrip instanceof FixedSizeMessage);
    Assert.assertEquals(fixedSizeRoundTrip, fixedSizeV1);
  }

  @Test
  public void testToBytesFromBytes() {
    AddressBook book = buildAddressBook();
    byte[] bookBytes = book.toBytes();
    AddressBook decodedBook = AddressBook.fromBytes(bookBytes);
    Assert.assertEquals(decodedBook, book);

    Dog dog = new Dog();
    dog.setName("Rex");
    dog.setBarkVolume(5);
    Animal animal = Animal.ofDog(dog);
    byte[] animalBytes = animal.toBytes();
    Animal decodedAnimal = Animal.fromBytes(animalBytes);
    Assert.assertEquals(decodedAnimal, animal);

    Person owner = new Person();
    owner.setName("Alice");
    owner.setId(123);
    owner.setEmail("");
    owner.setTags(Collections.emptyList());
    owner.setScores(new HashMap<>());
    owner.setSalary(0.0);
    owner.setPhones(Collections.emptyList());
    Dog rootDog = new Dog();
    rootDog.setName("Rex");
    rootDog.setBarkVolume(5);
    owner.setPet(Animal.ofDog(rootDog));

    AddressBook multiBook = new AddressBook();
    multiBook.setPeople(Arrays.asList(owner));
    Map<String, Person> peopleByName = new HashMap<>();
    peopleByName.put(owner.getName(), owner);
    multiBook.setPeopleByName(peopleByName);

    TreeNode rootNode = new TreeNode();
    rootNode.setId("root");
    rootNode.setName("root");
    rootNode.setChildren(Collections.emptyList());

    MultiHolder multi = new MultiHolder();
    multi.setBook(multiBook);
    multi.setRoot(rootNode);
    multi.setOwner(owner);

    byte[] multiBytes = multi.toBytes();
    MultiHolder decodedMulti = MultiHolder.fromBytes(multiBytes);
    Assert.assertEquals(decodedMulti, multi);
  }

  @Test
  public void testPrimitiveTypesRoundTripCompatible() throws Exception {
    runPrimitiveTypesRoundTrip(true);
  }

  @Test
  public void testPrimitiveTypesRoundTripSchemaConsistent() throws Exception {
    runPrimitiveTypesRoundTrip(false);
  }

  private void runPrimitiveTypesRoundTrip(boolean compatible) throws Exception {
    Fory fory = buildFory(compatible);
    AddressbookForyRegistration.register(fory);
    ComplexPbForyRegistration.register(fory);

    PrimitiveTypes types = buildPrimitiveTypes();
    byte[] bytes = fory.serialize(types);
    Object decoded = fory.deserialize(bytes);

    Assert.assertTrue(decoded instanceof PrimitiveTypes);
    Assert.assertEquals(decoded, types);

    Map<String, String> peerSelection = Collections.singletonMap("DATA_FILE_PRIMITIVES", "");
    for (String peer : resolvePeers(peerSelection)) {
      Path dataFile = Files.createTempFile("idl-primitive-" + peer + "-", ".bin");
      dataFile.toFile().deleteOnExit();
      Files.write(dataFile, bytes);

      Map<String, String> env = new HashMap<>();
      env.put("DATA_FILE_PRIMITIVES", dataFile.toAbsolutePath().toString());
      PeerCommand command = buildPeerCommand(peer, env, compatible);
      runPeer(command, peer);

      byte[] peerBytes = Files.readAllBytes(dataFile);
      Object roundTrip = fory.deserialize(peerBytes);
      Assert.assertTrue(roundTrip instanceof PrimitiveTypes);
      Assert.assertEquals(roundTrip, types);
    }
  }

  @Test
  public void testCollectionRoundTripCompatible() throws Exception {
    runCollectionRoundTrip(true);
  }

  @Test
  public void testCollectionRoundTripSchemaConsistent() throws Exception {
    runCollectionRoundTrip(false);
  }

  private void runCollectionRoundTrip(boolean compatible) throws Exception {
    Fory fory = buildFory(compatible);
    CollectionForyRegistration.register(fory);

    NumericCollections collections = buildNumericCollections();
    NumericCollectionUnion collectionUnion = buildNumericCollectionUnion();
    NumericCollectionsArray collectionsArray = buildNumericCollectionsArray();
    NumericCollectionArrayUnion collectionArrayUnion = buildNumericCollectionArrayUnion();

    byte[] collectionsBytes = fory.serialize(collections);
    Object collectionsDecoded = fory.deserialize(collectionsBytes);
    Assert.assertTrue(collectionsDecoded instanceof NumericCollections);
    Assert.assertEquals(collectionsDecoded, collections);

    byte[] unionBytes = fory.serialize(collectionUnion);
    Object unionDecoded = fory.deserialize(unionBytes);
    assertNumericCollectionUnion(unionDecoded, collectionUnion);

    byte[] arrayBytes = fory.serialize(collectionsArray);
    Object arrayDecoded = fory.deserialize(arrayBytes);
    Assert.assertTrue(arrayDecoded instanceof NumericCollectionsArray);
    Assert.assertEquals(arrayDecoded, collectionsArray);

    byte[] arrayUnionBytes = fory.serialize(collectionArrayUnion);
    Object arrayUnionDecoded = fory.deserialize(arrayUnionBytes);
    assertNumericCollectionArrayUnion(arrayUnionDecoded, collectionArrayUnion);

    Map<String, String> peerSelection = new HashMap<>();
    peerSelection.put("DATA_FILE_COLLECTION", "");
    peerSelection.put("DATA_FILE_COLLECTION_UNION", "");
    peerSelection.put("DATA_FILE_COLLECTION_ARRAY", "");
    peerSelection.put("DATA_FILE_COLLECTION_ARRAY_UNION", "");
    for (String peer : resolvePeers(peerSelection)) {
      Path collectionsFile =
          Files.createTempFile("idl-collections-" + peer + "-", ".bin");
      collectionsFile.toFile().deleteOnExit();
      Files.write(collectionsFile, collectionsBytes);

      Path unionFile =
          Files.createTempFile("idl-collection-union-" + peer + "-", ".bin");
      unionFile.toFile().deleteOnExit();
      Files.write(unionFile, unionBytes);

      Path arrayFile =
          Files.createTempFile("idl-collections-array-" + peer + "-", ".bin");
      arrayFile.toFile().deleteOnExit();
      Files.write(arrayFile, arrayBytes);

      Path arrayUnionFile =
          Files.createTempFile("idl-collection-array-union-" + peer + "-", ".bin");
      arrayUnionFile.toFile().deleteOnExit();
      Files.write(arrayUnionFile, arrayUnionBytes);

      Map<String, String> env = new HashMap<>();
      env.put("DATA_FILE_COLLECTION", collectionsFile.toAbsolutePath().toString());
      env.put("DATA_FILE_COLLECTION_UNION", unionFile.toAbsolutePath().toString());
      env.put("DATA_FILE_COLLECTION_ARRAY", arrayFile.toAbsolutePath().toString());
      env.put(
          "DATA_FILE_COLLECTION_ARRAY_UNION", arrayUnionFile.toAbsolutePath().toString());
      PeerCommand command = buildPeerCommand(peer, env, compatible);
      runPeer(command, peer);

      byte[] peerCollectionsBytes = Files.readAllBytes(collectionsFile);
      Object collectionsRoundTrip = fory.deserialize(peerCollectionsBytes);
      Assert.assertTrue(collectionsRoundTrip instanceof NumericCollections);
      Assert.assertEquals(collectionsRoundTrip, collections);

      byte[] peerUnionBytes = Files.readAllBytes(unionFile);
      Object unionRoundTrip = fory.deserialize(peerUnionBytes);
      assertNumericCollectionUnion(unionRoundTrip, collectionUnion);

      byte[] peerArrayBytes = Files.readAllBytes(arrayFile);
      Object arrayRoundTrip = fory.deserialize(peerArrayBytes);
      Assert.assertTrue(arrayRoundTrip instanceof NumericCollectionsArray);
      Assert.assertEquals(arrayRoundTrip, collectionsArray);

      byte[] peerArrayUnionBytes = Files.readAllBytes(arrayUnionFile);
      Object arrayUnionRoundTrip = fory.deserialize(peerArrayUnionBytes);
      assertNumericCollectionArrayUnion(arrayUnionRoundTrip, collectionArrayUnion);
    }
  }

  @Test
  public void testOptionalTypesRoundTripCompatible() throws Exception {
    runOptionalTypesRoundTrip(true);
  }

  @Test
  public void testOptionalTypesRoundTripSchemaConsistent() throws Exception {
    runOptionalTypesRoundTrip(false);
  }

  @Test
  public void testExampleRoundTripCompatible() throws Exception {
    runExampleRoundTrip(true);
  }

  @Test
  public void testExampleRoundTripSchemaConsistent() throws Exception {
    runExampleRoundTrip(false);
  }

  private void runOptionalTypesRoundTrip(boolean compatible) throws Exception {
    Fory fory = buildFory(compatible);
    OptionalTypesForyRegistration.register(fory);

    OptionalHolder holder = buildOptionalHolder();
    byte[] bytes = fory.serialize(holder);
    Object decoded = fory.deserialize(bytes);

    Assert.assertTrue(decoded instanceof OptionalHolder);
    Assert.assertEquals(decoded, holder);

    Map<String, String> peerSelection = Collections.singletonMap("DATA_FILE_OPTIONAL_TYPES", "");
    for (String peer : resolvePeers(peerSelection)) {
      Path dataFile = Files.createTempFile("idl-optional-" + peer + "-", ".bin");
      dataFile.toFile().deleteOnExit();
      Files.write(dataFile, bytes);

      Map<String, String> env = new HashMap<>();
      env.put("DATA_FILE_OPTIONAL_TYPES", dataFile.toAbsolutePath().toString());
      PeerCommand command = buildPeerCommand(peer, env, compatible);
      runPeer(command, peer);

      byte[] peerBytes = Files.readAllBytes(dataFile);
      Object roundTrip = fory.deserialize(peerBytes);
      Assert.assertTrue(roundTrip instanceof OptionalHolder);
      Assert.assertEquals(roundTrip, holder);
    }
  }

  private void runExampleRoundTrip(boolean compatible) throws Exception {
    List<ExampleSchemaEvolutionTypes.ExampleVariantSpec> variantSpecs =
        ExampleSchemaEvolutionTypes.variantSpecs();
    ExampleMessage message = buildExampleMessage();
    Map<String, Object> expectedValues = buildExampleFieldValues(message, variantSpecs);
    Fory fory = buildExampleFory(compatible);

    ExampleMessage messageRoundTrip =
        fory.deserialize(fory.serialize(message), ExampleMessage.class);
    assertExampleMessageValues(
        messageRoundTrip, expectedValues, variantSpecs, "java local example message");

    ExampleMessageUnion unionValue = buildExampleMessageUnion();
    ExampleMessageUnion unionRoundTrip =
        fory.deserialize(fory.serialize(unionValue), ExampleMessageUnion.class);
    assertExampleUnionEquals(unionRoundTrip, unionValue, "java local example union");
    assertExampleUnionLocalRoundTrips(fory, expectedValues, variantSpecs);

    byte[] messageBytes = fory.serialize(message);
    if (compatible) {
      assertExampleSchemaEvolution(
          messageBytes, expectedValues, variantSpecs, "java example message");
    }

    byte[] unionBytes = fory.serialize(unionValue);

    Map<String, String> peerSelection = new HashMap<>();
    peerSelection.put("DATA_FILE_EXAMPLE_MESSAGE", "");
    peerSelection.put("DATA_FILE_EXAMPLE_UNION", "");
    for (String peer : resolvePeers(peerSelection)) {
      Path messageFile = Files.createTempFile("idl-example-message-" + peer + "-", ".bin");
      messageFile.toFile().deleteOnExit();
      Files.write(messageFile, messageBytes);

      Path unionFile = Files.createTempFile("idl-example-union-" + peer + "-", ".bin");
      unionFile.toFile().deleteOnExit();
      Files.write(unionFile, unionBytes);

      Map<String, String> env = new HashMap<>();
      env.put("DATA_FILE_EXAMPLE_MESSAGE", messageFile.toAbsolutePath().toString());
      env.put("DATA_FILE_EXAMPLE_UNION", unionFile.toAbsolutePath().toString());
      PeerCommand command = buildPeerCommand(peer, env, compatible);
      runPeer(command, peer);

      ExampleMessage peerMessage =
          fory.deserialize(Files.readAllBytes(messageFile), ExampleMessage.class);
      assertExampleMessageValues(
          peerMessage, expectedValues, variantSpecs, peer + " example message");
      if (compatible) {
        assertExampleSchemaEvolution(
            Files.readAllBytes(messageFile),
            expectedValues,
            variantSpecs,
            peer + " example message");
      }

      ExampleMessageUnion peerUnion =
          fory.deserialize(Files.readAllBytes(unionFile), ExampleMessageUnion.class);
      assertExampleUnionEquals(peerUnion, unionValue, peer + " example union");
    }
  }

  @Test
  public void testAnyRoundTripCompatible() {
    runAnyRoundTrip(true);
  }

  @Test
  public void testAnyRoundTripSchemaConsistent() {
    runAnyRoundTrip(false);
  }

  private void runAnyRoundTrip(boolean compatible) {
    Fory fory = buildFory(compatible);
    AnyExampleForyRegistration.register(fory);

    AnyHolder holder = buildAnyHolder();
    byte[] bytes = fory.serialize(holder);
    Object decoded = fory.deserialize(bytes);

    Assert.assertTrue(decoded instanceof AnyHolder);
    Assert.assertEquals(decoded, holder);
  }

  @Test
  public void testTreeRoundTripCompatible() throws Exception {
    runTreeRoundTrip(true);
  }

  @Test
  public void testTreeRoundTripSchemaConsistent() throws Exception {
    runTreeRoundTrip(false);
  }

  private void runTreeRoundTrip(boolean compatible) throws Exception {
    Fory fory = buildRefFory(compatible);
    TreeForyRegistration.register(fory);

    TreeNode tree = buildTree();
    byte[] bytes = fory.serialize(tree);
    Object decoded = fory.deserialize(bytes);

    Assert.assertTrue(decoded instanceof TreeNode);
    TreeNode roundTrip = (TreeNode) decoded;
    assertTree(roundTrip);

    Map<String, String> peerSelection = Collections.singletonMap("DATA_FILE_TREE", "");
    for (String peer : resolvePeers(peerSelection)) {
      Path dataFile = Files.createTempFile("idl-tree-" + peer + "-", ".bin");
      dataFile.toFile().deleteOnExit();
      Files.write(dataFile, bytes);

      Map<String, String> env = new HashMap<>();
      env.put("DATA_FILE_TREE", dataFile.toAbsolutePath().toString());
      PeerCommand command = buildPeerCommand(peer, env, compatible);
      runPeer(command, peer);

      byte[] peerBytes = Files.readAllBytes(dataFile);
      Object peerRoundTrip = fory.deserialize(peerBytes);
      Assert.assertTrue(peerRoundTrip instanceof TreeNode);
      assertTree((TreeNode) peerRoundTrip);
    }
  }

  @Test
  public void testGraphRoundTripCompatible() throws Exception {
    runGraphRoundTrip(true);
  }

  @Test
  public void testGraphRoundTripSchemaConsistent() throws Exception {
    runGraphRoundTrip(false);
  }

  private void runGraphRoundTrip(boolean compatible) throws Exception {
    Fory fory = buildRefFory(compatible);
    GraphForyRegistration.register(fory);

    Graph graph = buildGraph();
    byte[] bytes = fory.serialize(graph);
    Object decoded = fory.deserialize(bytes);

    Assert.assertTrue(decoded instanceof Graph);
    Graph roundTrip = (Graph) decoded;
    assertGraph(roundTrip);

    Map<String, String> peerSelection = Collections.singletonMap("DATA_FILE_GRAPH", "");
    for (String peer : resolvePeers(peerSelection)) {
      Path dataFile = Files.createTempFile("idl-graph-" + peer + "-", ".bin");
      dataFile.toFile().deleteOnExit();
      Files.write(dataFile, bytes);

      Map<String, String> env = new HashMap<>();
      env.put("DATA_FILE_GRAPH", dataFile.toAbsolutePath().toString());
      PeerCommand command = buildPeerCommand(peer, env, compatible);
      runPeer(command, peer);

      byte[] peerBytes = Files.readAllBytes(dataFile);
      Object peerRoundTrip = fory.deserialize(peerBytes);
      Assert.assertTrue(peerRoundTrip instanceof Graph);
      assertGraph((Graph) peerRoundTrip);
    }
  }

  @Test
  public void testFlatbuffersRoundTripCompatible() throws Exception {
    runFlatbuffersRoundTrip(true);
  }

  @Test
  public void testFlatbuffersRoundTripSchemaConsistent() throws Exception {
    runFlatbuffersRoundTrip(false);
  }

  private void runFlatbuffersRoundTrip(boolean compatible) throws Exception {
    Fory fory = buildFory(compatible);
    MonsterForyRegistration.register(fory);
    ComplexFbsForyRegistration.register(fory);

    Monster monster = buildMonster();
    byte[] monsterBytes = fory.serialize(monster);
    Object monsterDecoded = fory.deserialize(monsterBytes);
    Assert.assertTrue(monsterDecoded instanceof Monster);
    Assert.assertEquals(monsterDecoded, monster);

    Container container = buildContainer();
    byte[] containerBytes = fory.serialize(container);
    Object containerDecoded = fory.deserialize(containerBytes);
    Assert.assertTrue(containerDecoded instanceof Container);
    Assert.assertEquals(containerDecoded, container);

    Map<String, String> peerSelection = new HashMap<>();
    peerSelection.put("DATA_FILE_FLATBUFFERS_MONSTER", "");
    peerSelection.put("DATA_FILE_FLATBUFFERS_TEST2", "");
    for (String peer : resolvePeers(peerSelection)) {
      Path monsterFile =
          Files.createTempFile("idl-flatbuffers-monster-" + peer + "-", ".bin");
      monsterFile.toFile().deleteOnExit();
      Files.write(monsterFile, monsterBytes);

      Path containerFile =
          Files.createTempFile("idl-flatbuffers-test2-" + peer + "-", ".bin");
      containerFile.toFile().deleteOnExit();
      Files.write(containerFile, containerBytes);

      Map<String, String> env = new HashMap<>();
      env.put("DATA_FILE_FLATBUFFERS_MONSTER", monsterFile.toAbsolutePath().toString());
      env.put("DATA_FILE_FLATBUFFERS_TEST2", containerFile.toAbsolutePath().toString());
      PeerCommand command = buildPeerCommand(peer, env, compatible);
      runPeer(command, peer);

      byte[] peerMonsterBytes = Files.readAllBytes(monsterFile);
      Object monsterRoundTrip = fory.deserialize(peerMonsterBytes);
      Assert.assertTrue(monsterRoundTrip instanceof Monster);
      Assert.assertEquals(monsterRoundTrip, monster);

      byte[] peerContainerBytes = Files.readAllBytes(containerFile);
      Object containerRoundTrip = fory.deserialize(peerContainerBytes);
      Assert.assertTrue(containerRoundTrip instanceof Container);
      Assert.assertEquals(containerRoundTrip, container);
    }
  }

  private Fory buildFory(boolean compatible) {
    return Fory.builder()
        .withLanguage(Language.XLANG)
        .withCompatibleMode(
            compatible ? CompatibleMode.COMPATIBLE : CompatibleMode.SCHEMA_CONSISTENT)
        .build();
  }

  private Fory buildRefFory(boolean compatible) {
    return Fory.builder()
        .withLanguage(Language.XLANG)
        .withCompatibleMode(
            compatible ? CompatibleMode.COMPATIBLE : CompatibleMode.SCHEMA_CONSISTENT)
        .withRefTracking(true)
        .build();
  }

  private List<String> resolvePeers(Map<String, String> environment) {
    String peerEnv = System.getenv("IDL_PEER_LANG");
    if (peerEnv == null || peerEnv.trim().isEmpty()) {
      return Collections.emptyList();
    }
    List<String> peers =
        Arrays.stream(peerEnv.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toList());
    if (peers.contains("all")) {
      peers =
          new ArrayList<>(
              Arrays.asList("python", "go", "rust", "cpp", "swift", "javascript", "csharp", "dart"));
    }
    return peers.stream()
        .filter(peer -> supportsPeerPayload(peer, environment))
        .collect(Collectors.toList());
  }

  private boolean supportsDartPeerPayload(Map<String, String> environment) {
    return !(environment.containsKey("DATA_FILE_PRIMITIVES")
        || environment.containsKey("DATA_FILE_COLLECTION")
        || environment.containsKey("DATA_FILE_COLLECTION_UNION")
        || environment.containsKey("DATA_FILE_COLLECTION_ARRAY")
        || environment.containsKey("DATA_FILE_COLLECTION_ARRAY_UNION")
        || environment.containsKey("DATA_FILE_OPTIONAL_TYPES")
        || environment.containsKey("DATA_FILE_FLATBUFFERS_MONSTER")
        || environment.containsKey("DATA_FILE_FLATBUFFERS_TEST2"));
  }

  private boolean supportsPeerPayload(String peer, Map<String, String> environment) {
    if ("dart".equals(peer)) {
      return supportsDartPeerPayload(environment);
    }
    return true;
  }

  private PeerCommand buildPeerCommand(
      String peer, Map<String, String> environment, boolean compatible) {
    Path repoRoot = repoRoot();
    Path idlRoot = repoRoot.resolve("integration_tests").resolve("idl_tests");
    Path workDir = idlRoot;
    List<String> command;
    PeerCommand peerCommand = new PeerCommand();
    peerCommand.environment.putAll(environment);
    peerCommand.environment.put("IDL_COMPATIBLE", Boolean.toString(compatible));

    switch (peer) {
      case "python":
        command = Arrays.asList("python", "-m", "idl_tests.roundtrip");
        Path pythonRoot = idlRoot.resolve("python");
        String pythonPath =
            pythonRoot.resolve("idl_tests").resolve("generated")
                + File.pathSeparator
                + pythonRoot
                + File.pathSeparator
                + repoRoot.resolve("python");
        String existingPythonPath = System.getenv("PYTHONPATH");
        if (existingPythonPath != null && !existingPythonPath.isEmpty()) {
          pythonPath = pythonPath + File.pathSeparator + existingPythonPath;
        }
        peerCommand.environment.put("PYTHONPATH", pythonPath);
        peerCommand.environment.put("ENABLE_FORY_CYTHON_SERIALIZATION", "0");
        break;
      case "go":
        workDir = idlRoot.resolve("go");
        String goTest;
        if (usesExamplePeerPayload(environment)) {
          goTest =
              compatible
                  ? "TestExampleRoundTripCompatible"
                  : "TestExampleRoundTripSchemaConsistent";
        } else {
          goTest =
              compatible
                  ? "TestAddressBookRoundTripCompatible"
                  : "TestAddressBookRoundTripSchemaConsistent";
        }
        command = Arrays.asList("go", "test", "-run", goTest, "-v");
        break;
      case "rust":
        workDir = idlRoot.resolve("rust");
        String rustTest =
            compatible
                ? "test_address_book_roundtrip_compatible"
                : "test_address_book_roundtrip_schema_consistent";
        command = Arrays.asList("cargo", "test", "--test", "idl_roundtrip", rustTest);
        break;
      case "cpp":
        command = Collections.singletonList("./cpp/run.sh");
        break;
      case "swift":
        workDir = idlRoot.resolve("swift").resolve("idl_package");
        String swiftTest =
            compatible
                ? "IdlRoundTripTests/testAddressBookRoundTripCompatible"
                : "IdlRoundTripTests/testAddressBookRoundTripSchemaConsistent";
        command = Arrays.asList("swift", "test", "--filter", swiftTest);
        peerCommand.environment.put("ENABLE_FORY_DEBUG_OUTPUT", "1");
        break;
      case "javascript":
        workDir = idlRoot.resolve("javascript");
        command = Arrays.asList("npx", "ts-node", "roundtrip.ts");
        peerCommand.environment.put("ENABLE_FORY_DEBUG_OUTPUT", "1");
        break;
      case "dart":
        if (!supportsDartPeerPayload(environment)) {
          throw new IllegalArgumentException(
              "Dart peer does not support payload set " + environment.keySet());
        }
        workDir = idlRoot.resolve("dart");
        command =
            Arrays.asList(
                "dart",
                "test",
                "test/idl_roundtrip_test.dart",
                "--plain-name",
                "interop file roundtrip hooks when env vars are set");
        peerCommand.environment.put("ENABLE_FORY_DEBUG_OUTPUT", "1");
        break;
      case "csharp":
        workDir = idlRoot.resolve("csharp").resolve("IdlTests");
        command =
            Arrays.asList(
                "dotnet",
                "test",
                "-c",
                "Release",
                "--filter",
                "FullyQualifiedName~" + resolveCsharpPeerTest(environment));
        peerCommand.environment.put("ENABLE_FORY_DEBUG_OUTPUT", "1");
        break;
      default:
        throw new IllegalArgumentException("Unknown peer language: " + peer);
    }

    peerCommand.command = command;
    peerCommand.workDir = workDir;
    return peerCommand;
  }

  private boolean usesExamplePeerPayload(Map<String, String> environment) {
    return environment.containsKey("DATA_FILE_EXAMPLE_MESSAGE")
        || environment.containsKey("DATA_FILE_EXAMPLE_UNION");
  }

  private String resolveCsharpPeerTest(Map<String, String> environment) {
    if (environment.containsKey("DATA_FILE_COLLECTION")
        || environment.containsKey("DATA_FILE_COLLECTION_UNION")
        || environment.containsKey("DATA_FILE_COLLECTION_ARRAY")
        || environment.containsKey("DATA_FILE_COLLECTION_ARRAY_UNION")) {
      return "Apache.Fory.IdlTests.RoundtripTests.CollectionRoundTrip";
    }
    if (environment.containsKey("DATA_FILE_EXAMPLE_MESSAGE")
        || environment.containsKey("DATA_FILE_EXAMPLE_UNION")) {
      return "Apache.Fory.IdlTests.RoundtripTests.ExampleRoundTrip";
    }
    if (environment.containsKey("DATA_FILE_FLATBUFFERS_MONSTER")
        || environment.containsKey("DATA_FILE_FLATBUFFERS_TEST2")) {
      return "Apache.Fory.IdlTests.RoundtripTests.FlatbuffersRoundTrip";
    }
    if (environment.containsKey("DATA_FILE_AUTO_ID")) {
      return "Apache.Fory.IdlTests.RoundtripTests.AutoIdRoundTrip";
    }
    if (environment.containsKey("DATA_FILE_PRIMITIVES")) {
      return "Apache.Fory.IdlTests.RoundtripTests.PrimitiveTypesRoundTrip";
    }
    if (environment.containsKey("DATA_FILE_OPTIONAL_TYPES")) {
      return "Apache.Fory.IdlTests.RoundtripTests.OptionalTypesRoundTrip";
    }
    if (environment.containsKey("DATA_FILE_TREE")) {
      return "Apache.Fory.IdlTests.RoundtripTests.TreeRoundTrip";
    }
    if (environment.containsKey("DATA_FILE_GRAPH")) {
      return "Apache.Fory.IdlTests.RoundtripTests.GraphRoundTrip";
    }
    if (environment.containsKey("DATA_FILE")) {
      return "Apache.Fory.IdlTests.RoundtripTests.AddressBookRoundTrip";
    }
    throw new IllegalArgumentException("Unsupported C# peer payload set " + environment.keySet());
  }

  private void runPeer(PeerCommand command, String peer) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command.command);
    // Keep peer output off the forked JVM stdio so Surefire's control channel
    // cannot be corrupted by child process logs.
    builder.redirectErrorStream(true);
    builder.directory(command.workDir.toFile());
    builder.environment().putAll(command.environment);

    Process process = builder.start();
    PeerOutputCollector outputCollector = new PeerOutputCollector(process.getInputStream(), peer);
    outputCollector.start();
    boolean finished = process.waitFor(180, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(10, TimeUnit.SECONDS);
      String output = outputCollector.awaitOutput();
      Assert.fail(
          "Peer process timed out for "
              + peer
              + (output.isEmpty() ? "" : "\noutput:\n" + output));
    }

    int exitCode = process.exitValue();
    String output = outputCollector.awaitOutput();
    if (exitCode != 0) {
      Assert.fail(
          "Peer process failed for "
              + peer
              + " with exit code "
              + exitCode
              + (output.isEmpty() ? "" : "\noutput:\n" + output));
    }
  }

  private static final class PeerOutputCollector extends Thread {
    private final InputStream inputStream;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private IOException readFailure;

    private PeerOutputCollector(InputStream inputStream, String peer) {
      super("idl-peer-output-" + peer);
      setDaemon(true);
      this.inputStream = inputStream;
    }

    @Override
    public void run() {
      byte[] buffer = new byte[4096];
      int bytesRead;
      try {
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
        }
      } catch (IOException e) {
        readFailure = e;
      } finally {
        try {
          inputStream.close();
        } catch (IOException ignored) {
        }
      }
    }

    private String awaitOutput() throws IOException, InterruptedException {
      join();
      if (readFailure != null) {
        throw readFailure;
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private Path repoRoot() {
    Path moduleDir = java.nio.file.Paths.get("").toAbsolutePath();
    return moduleDir.getParent().getParent().getParent();
  }

  private AddressBook buildAddressBook() {
    PhoneNumber mobile = new PhoneNumber();
    mobile.setNumber("555-0100");
    mobile.setPhoneType(PhoneType.MOBILE);

    PhoneNumber work = new PhoneNumber();
    work.setNumber("555-0111");
    work.setPhoneType(PhoneType.WORK);

    List<PhoneNumber> phones = new ArrayList<>();
    phones.add(mobile);
    phones.add(work);

    List<String> tags = Arrays.asList("friend", "colleague");

    Map<String, Integer> scores = new HashMap<>();
    scores.put("math", 100);
    scores.put("science", 98);

    Person person = new Person();
    person.setName("Alice");
    person.setId(123);
    person.setEmail("alice@example.com");
    person.setTags(tags);
    person.setScores(scores);
    person.setSalary(120000.5);
    person.setPhones(phones);
    Dog dog = new Dog();
    dog.setName("Rex");
    dog.setBarkVolume(5);
    Animal pet = Animal.ofDog(dog);
    Cat cat = new Cat();
    cat.setName("Mimi");
    cat.setLives(9);
    pet.setCat(cat);
    person.setPet(pet);

    AddressBook book = new AddressBook();
    List<Person> people = new ArrayList<>();
    people.add(person);
    book.setPeople(people);

    Map<String, Person> peopleByName = new HashMap<>();
    peopleByName.put(person.getName(), person);
    book.setPeopleByName(peopleByName);

    return book;
  }

  private Envelope buildAutoIdEnvelope() {
    Envelope.Payload payload = new Envelope.Payload();
    payload.setValue(42);

    Envelope.Detail detail = Envelope.Detail.ofPayload(payload);

    Envelope envelope = new Envelope();
    envelope.setId("env-1");
    envelope.setPayload(payload);
    envelope.setDetail(detail);
    envelope.setStatus(auto_id.Status.OK);
    return envelope;
  }

  private Wrapper buildAutoIdWrapper(Envelope envelope) {
    return Wrapper.ofEnvelope(envelope);
  }

  private PrimitiveTypes buildPrimitiveTypes() {
    PrimitiveTypes types = new PrimitiveTypes();
    types.setBoolValue(true);
    types.setInt8Value((byte) 12);
    types.setInt16Value((short) 1234);
    types.setInt32Value(-123456);
    types.setVarint32Value(-12345);
    types.setInt64Value(-123456789L);
    types.setVarint64Value(-987654321L);
    types.setTaggedInt64Value(123456789L);
    types.setUint8Value((byte) 200);
    types.setUint16Value((short) 60000);
    types.setUint32Value(1234567890);
    types.setVarUint32Value(1234567890);
    types.setUint64Value(9876543210L);
    types.setVarUint64Value(12345678901L);
    types.setTaggedUint64Value(2222222222L);
    types.setFloat32Value(2.5f);
    types.setFloat64Value(3.5d);
    PrimitiveTypes.Contact contact = PrimitiveTypes.Contact.ofEmail("alice@example.com");
    contact.setPhone(12345);
    types.setContact(contact);
    return types;
  }

  private NumericCollections buildNumericCollections() {
    NumericCollections collections = new NumericCollections();
    collections.setInt8Values(new Int8List(new byte[] {1, -2, 3}));
    collections.setInt16Values(new Int16List(new short[] {100, -200, 300}));
    collections.setInt32Values(new Int32List(new int[] {1000, -2000, 3000}));
    collections.setInt64Values(new Int64List(new long[] {10000L, -20000L, 30000L}));
    collections.setUint8Values(new Uint8List(new byte[] {(byte) 200, (byte) 250}));
    collections.setUint16Values(new Uint16List(new short[] {(short) 50000, (short) 60000}));
    collections.setUint32Values(new Uint32List(new int[] {2000000000, 2100000000}));
    collections.setUint64Values(new Uint64List(new long[] {9000000000L, 12000000000L}));
    collections.setFloat32Values(new float[] {1.5f, 2.5f});
    collections.setFloat64Values(new double[] {3.5d, 4.5d});
    return collections;
  }

  private NumericCollectionUnion buildNumericCollectionUnion() {
    return NumericCollectionUnion.ofInt32Values(new Int32List(new int[] {7, 8, 9}));
  }

  private NumericCollectionsArray buildNumericCollectionsArray() {
    NumericCollectionsArray collections = new NumericCollectionsArray();
    collections.setInt8Values(new byte[] {1, -2, 3});
    collections.setInt16Values(new short[] {100, -200, 300});
    collections.setInt32Values(new int[] {1000, -2000, 3000});
    collections.setInt64Values(new long[] {10000L, -20000L, 30000L});
    collections.setUint8Values(new byte[] {(byte) 200, (byte) 250});
    collections.setUint16Values(new short[] {(short) 50000, (short) 60000});
    collections.setUint32Values(new int[] {2000000000, 2100000000});
    collections.setUint64Values(new long[] {9000000000L, 12000000000L});
    collections.setFloat32Values(new float[] {1.5f, 2.5f});
    collections.setFloat64Values(new double[] {3.5d, 4.5d});
    return collections;
  }

  private NumericCollectionArrayUnion buildNumericCollectionArrayUnion() {
    return NumericCollectionArrayUnion.ofUint16Values(new short[] {1000, 2000, 3000});
  }

  private void assertNumericCollectionUnion(
      Object decoded, NumericCollectionUnion expected) {
    Assert.assertTrue(decoded instanceof NumericCollectionUnion);
    NumericCollectionUnion union = (NumericCollectionUnion) decoded;
    Assert.assertEquals(union.getNumericCollectionUnionCase(), expected.getNumericCollectionUnionCase());
    switch (union.getNumericCollectionUnionCase()) {
      case INT32_VALUES:
        Assert.assertEquals(union.getInt32Values(), expected.getInt32Values());
        break;
      default:
        Assert.fail("Unexpected union case: " + union.getNumericCollectionUnionCase());
    }
  }

  private void assertNumericCollectionArrayUnion(
      Object decoded, NumericCollectionArrayUnion expected) {
    Assert.assertTrue(decoded instanceof NumericCollectionArrayUnion);
    NumericCollectionArrayUnion union = (NumericCollectionArrayUnion) decoded;
    Assert.assertEquals(
        union.getNumericCollectionArrayUnionCase(),
        expected.getNumericCollectionArrayUnionCase());
    switch (union.getNumericCollectionArrayUnionCase()) {
      case UINT16_VALUES:
        Assert.assertTrue(
            Arrays.equals(union.getUint16Values(), expected.getUint16Values()));
        break;
      default:
        Assert.fail(
            "Unexpected array union case: " + union.getNumericCollectionArrayUnionCase());
    }
  }

  private Monster buildMonster() {
    Vec3 pos = new Vec3();
    pos.setX(1.0f);
    pos.setY(2.0f);
    pos.setZ(3.0f);

    Monster monster = new Monster();
    monster.setPos(pos);
    monster.setMana((short) 200);
    monster.setHp((short) 80);
    monster.setName("Orc");
    monster.setFriendly(true);
    monster.setInventory(new Uint8List(new byte[] {(byte) 1, (byte) 2, (byte) 3}));
    monster.setColor(Color.Blue);
    return monster;
  }

  private OptionalHolder buildOptionalHolder() {
    AllOptionalTypes allTypes = new AllOptionalTypes();
    allTypes.setBoolValue(true);
    allTypes.setInt8Value((byte) 12);
    allTypes.setInt16Value((short) 1234);
    allTypes.setInt32Value(-123456);
    allTypes.setFixedInt32Value(-123456);
    allTypes.setVarint32Value(-12345);
    allTypes.setInt64Value(-123456789L);
    allTypes.setFixedInt64Value(-123456789L);
    allTypes.setVarint64Value(-987654321L);
    allTypes.setTaggedInt64Value(123456789L);
    allTypes.setUint8Value((byte) 200);
    allTypes.setUint16Value((short) 60000);
    allTypes.setUint32Value(1234567890);
    allTypes.setFixedUint32Value(1234567890);
    allTypes.setVarUint32Value(1234567890);
    allTypes.setUint64Value(9876543210L);
    allTypes.setFixedUint64Value(9876543210L);
    allTypes.setVarUint64Value(12345678901L);
    allTypes.setTaggedUint64Value(2222222222L);
    allTypes.setFloat32Value(2.5f);
    allTypes.setFloat64Value(3.5);
    allTypes.setStringValue("optional");
    allTypes.setBytesValue(new byte[] {1, 2, 3});
    allTypes.setDateValue(LocalDate.of(2024, 1, 2));
    allTypes.setTimestampValue(Instant.parse("2024-01-02T03:04:05Z"));
    allTypes.setInt32List(new Int32List(new int[] {1, 2, 3}));
    allTypes.setStringList(Arrays.asList("alpha", "beta"));
    Map<String, Long> int64Map = new HashMap<>();
    int64Map.put("alpha", 10L);
    int64Map.put("beta", 20L);
    allTypes.setInt64Map(int64Map);

    OptionalHolder holder = new OptionalHolder();
    holder.setAllTypes(allTypes);
    holder.setChoice(OptionalUnion.ofNote("optional"));
    return holder;
  }

  private Fory buildExampleFory(boolean compatible) {
    Fory fory = buildFory(compatible);
    ExampleCommonForyRegistration.register(fory);
    ExampleForyRegistration.register(fory);
    return fory;
  }

  private Fory buildExampleEmptyFory(boolean compatible) {
    Fory fory = buildFory(compatible);
    ExampleCommonForyRegistration.register(fory);
    ExampleSchemaEvolutionTypes.registerEmptyType(fory);
    return fory;
  }

  private Fory buildExampleVariantFory(
      boolean compatible, ExampleSchemaEvolutionTypes.ExampleVariantSpec spec) {
    Fory fory = buildFory(compatible);
    ExampleCommonForyRegistration.register(fory);
    ExampleSchemaEvolutionTypes.registerVariantType(fory, spec);
    return fory;
  }

  private Map<String, Object> buildExampleFieldValues(
      ExampleMessage message, List<ExampleSchemaEvolutionTypes.ExampleVariantSpec> variantSpecs)
      throws Exception {
    Map<String, Object> values = new LinkedHashMap<>();
    for (ExampleSchemaEvolutionTypes.ExampleVariantSpec spec : variantSpecs) {
      values.put(spec.javaProperty, readBeanProperty(message, spec.javaProperty));
    }
    return values;
  }

  private ExampleMessage buildExampleMessage() {
    ExampleLeaf leafA = buildExampleLeaf("leaf-a", 7);
    ExampleLeaf leafB = buildExampleLeaf("leaf-b", -3);
    ExampleLeafUnion leafUnion = ExampleLeafUnion.ofLeaf(buildExampleLeaf("leaf-b", -3));
    LocalDate dateValue = LocalDate.of(2024, 2, 29);
    Instant timestampValue = Instant.parse("2024-02-29T12:34:56.789123Z");
    Duration durationValue = Duration.ofSeconds(3723).plusNanos(456_789_000);
    BigDecimal decimalValue = new BigDecimal("123456789012345.6789");

    ExampleMessage message = new ExampleMessage();
    message.setBoolValue(true);
    message.setInt8Value((byte) -12);
    message.setInt16Value((short) 1234);
    message.setFixedInt32Value(123456789);
    message.setVarint32Value(-1234567);
    message.setFixedInt64Value(1234567890123456789L);
    message.setVarint64Value(-1234567890123456789L);
    message.setTaggedInt64Value(1073741824L);
    message.setUint8Value((byte) 200);
    message.setUint16Value((short) 60000);
    message.setFixedUint32Value(2000000000);
    message.setVarUint32Value(2100000000);
    message.setFixedUint64Value(9000000000L);
    message.setVarUint64Value(12000000000L);
    message.setTaggedUint64Value(2222222222L);
    message.setFloat16Value(exampleFloat16(1.5f));
    message.setBfloat16Value(exampleBFloat16(-2.75f));
    message.setFloat32Value(3.25f);
    message.setFloat64Value(-4.5d);
    message.setStringValue("example-string");
    message.setBytesValue(new byte[] {1, 2, 3, 4});
    message.setDateValue(dateValue);
    message.setTimestampValue(timestampValue);
    message.setDurationValue(durationValue);
    message.setDecimalValue(decimalValue);
    message.setEnumValue(ExampleState.READY);
    message.setMessageValue(leafA);
    message.setUnionValue(leafUnion);
    message.setBoolList(new boolean[] {true, false});
    message.setInt8List(new Int8List(new byte[] {-12, 7}));
    message.setInt16List(new Int16List(new short[] {1234, -2345}));
    message.setFixedInt32List(new Int32List(new int[] {123456789, -123456789}));
    message.setVarint32List(new Int32List(new int[] {-1234567, 7654321}));
    message.setFixedInt64List(
        new Int64List(new long[] {1234567890123456789L, -123456789012345678L}));
    message.setVarint64List(
        new Int64List(new long[] {-1234567890123456789L, 123456789012345678L}));
    message.setTaggedInt64List(new Int64List(new long[] {1073741824L, -1073741824L}));
    message.setUint8List(new Uint8List(new byte[] {(byte) 200, 42}));
    message.setUint16List(new Uint16List(new short[] {(short) 60000, (short) 12345}));
    message.setFixedUint32List(new Uint32List(new int[] {2000000000, 1234567890}));
    message.setVarUint32List(new Uint32List(new int[] {2100000000, 1234567890}));
    message.setFixedUint64List(new Uint64List(new long[] {9000000000L, 4000000000L}));
    message.setVarUint64List(new Uint64List(new long[] {12000000000L, 5000000000L}));
    message.setTaggedUint64List(new Uint64List(new long[] {2222222222L, 3333333333L}));
    message.setFloat16List(
        new Float16List(
            new short[] {exampleFloat16(1.5f).toBits(), exampleFloat16(-0.5f).toBits()}));
    message.setBfloat16List(
        new BFloat16List(
            new short[] {
              exampleBFloat16(-2.75f).toBits(),
              exampleBFloat16(2.25f).toBits()
            }));
    message.setMaybeFloat16List(
        Arrays.asList(exampleFloat16(1.5f), null, exampleFloat16(-0.5f)));
    message.setMaybeBfloat16List(
        Arrays.asList(null, exampleBFloat16(2.25f), exampleBFloat16(-1.0f)));
    message.setFloat32List(new float[] {3.25f, -0.5f});
    message.setFloat64List(new double[] {-4.5d, 6.75d});
    message.setStringList(Arrays.asList("example-string", "secondary"));
    message.setBytesList(Arrays.asList(new byte[] {1, 2, 3, 4}, new byte[] {5, 6}));
    message.setDateList(Arrays.asList(dateValue, LocalDate.of(2024, 3, 1)));
    message.setTimestampList(
        Arrays.asList(timestampValue, Instant.parse("2024-03-01T00:00:00.123456Z")));
    message.setDurationList(
        Arrays.asList(durationValue, Duration.ofSeconds(1).plusNanos(234_567_000)));
    message.setDecimalList(Arrays.asList(decimalValue, new BigDecimal("-0.5")));
    message.setEnumList(Arrays.asList(ExampleState.READY, ExampleState.FAILED));
    message.setMessageList(Arrays.asList(leafA, leafB));
    message.setUnionList(Arrays.asList(ExampleLeafUnion.ofLeaf(leafA), leafUnion));

    Map<Boolean, String> stringValuesByBool = new LinkedHashMap<>();
    stringValuesByBool.put(Boolean.TRUE, "true-value");
    stringValuesByBool.put(Boolean.FALSE, "false-value");
    message.setStringValuesByBool(stringValuesByBool);

    Map<Byte, String> stringValuesByInt8 = new LinkedHashMap<>();
    stringValuesByInt8.put((byte) -12, "minus-twelve");
    message.setStringValuesByInt8(stringValuesByInt8);

    Map<Short, String> stringValuesByInt16 = new LinkedHashMap<>();
    stringValuesByInt16.put((short) 1234, "twelve-thirty-four");
    message.setStringValuesByInt16(stringValuesByInt16);

    Map<Integer, String> stringValuesByFixedInt32 = new LinkedHashMap<>();
    stringValuesByFixedInt32.put(123456789, "fixed-int32");
    message.setStringValuesByFixedInt32(stringValuesByFixedInt32);

    Map<Integer, String> stringValuesByVarint32 = new LinkedHashMap<>();
    stringValuesByVarint32.put(-1234567, "varint32");
    message.setStringValuesByVarint32(stringValuesByVarint32);

    Map<Long, String> stringValuesByFixedInt64 = new LinkedHashMap<>();
    stringValuesByFixedInt64.put(1234567890123456789L, "fixed-int64");
    message.setStringValuesByFixedInt64(stringValuesByFixedInt64);

    Map<Long, String> stringValuesByVarint64 = new LinkedHashMap<>();
    stringValuesByVarint64.put(-1234567890123456789L, "varint64");
    message.setStringValuesByVarint64(stringValuesByVarint64);

    Map<Long, String> stringValuesByTaggedInt64 = new LinkedHashMap<>();
    stringValuesByTaggedInt64.put(1073741824L, "tagged-int64");
    message.setStringValuesByTaggedInt64(stringValuesByTaggedInt64);

    Map<Byte, String> stringValuesByUint8 = new LinkedHashMap<>();
    stringValuesByUint8.put((byte) 200, "uint8");
    message.setStringValuesByUint8(stringValuesByUint8);

    Map<Short, String> stringValuesByUint16 = new LinkedHashMap<>();
    stringValuesByUint16.put((short) 60000, "uint16");
    message.setStringValuesByUint16(stringValuesByUint16);

    Map<Integer, String> stringValuesByFixedUint32 = new LinkedHashMap<>();
    stringValuesByFixedUint32.put(2000000000, "fixed-uint32");
    message.setStringValuesByFixedUint32(stringValuesByFixedUint32);

    Map<Integer, String> stringValuesByVarUint32 = new LinkedHashMap<>();
    stringValuesByVarUint32.put(2100000000, "var-uint32");
    message.setStringValuesByVarUint32(stringValuesByVarUint32);

    Map<Long, String> stringValuesByFixedUint64 = new LinkedHashMap<>();
    stringValuesByFixedUint64.put(9000000000L, "fixed-uint64");
    message.setStringValuesByFixedUint64(stringValuesByFixedUint64);

    Map<Long, String> stringValuesByVarUint64 = new LinkedHashMap<>();
    stringValuesByVarUint64.put(12000000000L, "var-uint64");
    message.setStringValuesByVarUint64(stringValuesByVarUint64);

    Map<Long, String> stringValuesByTaggedUint64 = new LinkedHashMap<>();
    stringValuesByTaggedUint64.put(2222222222L, "tagged-uint64");
    message.setStringValuesByTaggedUint64(stringValuesByTaggedUint64);

    Map<String, String> stringValuesByString = new LinkedHashMap<>();
    stringValuesByString.put("example-string", "string");
    message.setStringValuesByString(stringValuesByString);

    Map<Instant, String> stringValuesByTimestamp = new LinkedHashMap<>();
    stringValuesByTimestamp.put(timestampValue, "timestamp");
    message.setStringValuesByTimestamp(stringValuesByTimestamp);

    Map<Duration, String> stringValuesByDuration = new LinkedHashMap<>();
    stringValuesByDuration.put(durationValue, "duration");
    message.setStringValuesByDuration(stringValuesByDuration);

    Map<ExampleState, String> stringValuesByEnum = new LinkedHashMap<>();
    stringValuesByEnum.put(ExampleState.READY, "ready");
    message.setStringValuesByEnum(stringValuesByEnum);

    Map<String, Float16> float16ValuesByName = new LinkedHashMap<>();
    float16ValuesByName.put("primary", exampleFloat16(1.5f));
    message.setFloat16ValuesByName(float16ValuesByName);

    Map<String, Float16> maybeFloat16ValuesByName = new LinkedHashMap<>();
    maybeFloat16ValuesByName.put("primary", exampleFloat16(1.5f));
    maybeFloat16ValuesByName.put("missing", null);
    message.setMaybeFloat16ValuesByName(maybeFloat16ValuesByName);

    Map<String, BFloat16> bfloat16ValuesByName = new LinkedHashMap<>();
    bfloat16ValuesByName.put("primary", exampleBFloat16(-2.75f));
    message.setBfloat16ValuesByName(bfloat16ValuesByName);

    Map<String, BFloat16> maybeBfloat16ValuesByName = new LinkedHashMap<>();
    maybeBfloat16ValuesByName.put("missing", null);
    maybeBfloat16ValuesByName.put("secondary", exampleBFloat16(2.25f));
    message.setMaybeBfloat16ValuesByName(maybeBfloat16ValuesByName);

    Map<String, byte[]> bytesValuesByName = new LinkedHashMap<>();
    bytesValuesByName.put("payload", new byte[] {1, 2, 3, 4});
    message.setBytesValuesByName(bytesValuesByName);

    Map<String, LocalDate> dateValuesByName = new LinkedHashMap<>();
    dateValuesByName.put("leap-day", dateValue);
    message.setDateValuesByName(dateValuesByName);

    Map<String, BigDecimal> decimalValuesByName = new LinkedHashMap<>();
    decimalValuesByName.put("amount", decimalValue);
    message.setDecimalValuesByName(decimalValuesByName);

    Map<String, ExampleLeaf> messageValuesByName = new LinkedHashMap<>();
    messageValuesByName.put("leaf-a", leafA);
    messageValuesByName.put("leaf-b", leafB);
    message.setMessageValuesByName(messageValuesByName);

    Map<String, ExampleLeafUnion> unionValuesByName = new LinkedHashMap<>();
    unionValuesByName.put("leaf-b", leafUnion);
    message.setUnionValuesByName(unionValuesByName);
    return message;
  }

  private ExampleMessageUnion buildExampleMessageUnion() {
    return ExampleMessageUnion.ofUnionValue(
        ExampleLeafUnion.ofLeaf(buildExampleLeaf("leaf-b", -3)));
  }

  private ExampleLeaf buildExampleLeaf(String label, int count) {
    ExampleLeaf leaf = new ExampleLeaf();
    leaf.setLabel(label);
    leaf.setCount(count);
    return leaf;
  }

  private Float16 exampleFloat16(float value) {
    return Float16.valueOf(value);
  }

  private BFloat16 exampleBFloat16(float value) {
    return BFloat16.valueOf(value);
  }

  private void assertExampleSchemaEvolution(
      byte[] bytes,
      Map<String, Object> expectedValues,
      List<ExampleSchemaEvolutionTypes.ExampleVariantSpec> variantSpecs,
      String sourceLabel)
      throws Exception {
    ExampleMessage decoded = buildExampleFory(true).deserialize(bytes, ExampleMessage.class);
    assertExampleMessageValues(
        decoded, expectedValues, variantSpecs, sourceLabel + " full-schema decode");

    Object emptyDecoded = buildExampleEmptyFory(true).deserialize(bytes);
    Assert.assertEquals(
        emptyDecoded.getClass(),
        ExampleSchemaEvolutionTypes.emptyType(),
        sourceLabel + " empty-schema decode");

    for (ExampleSchemaEvolutionTypes.ExampleVariantSpec spec : variantSpecs) {
      Object variantDecoded = buildExampleVariantFory(true, spec).deserialize(bytes);
      Assert.assertEquals(
          variantDecoded.getClass(),
          spec.variantClass,
          sourceLabel + " variant decode type for " + spec.javaProperty);
      assertNormalizedEquals(
          sourceLabel + " variant field " + spec.javaProperty,
          readBeanProperty(variantDecoded, spec.javaProperty),
          expectedValues.get(spec.javaProperty));
    }
  }

  private void assertExampleUnionLocalRoundTrips(
      Fory fory,
      Map<String, Object> expectedValues,
      List<ExampleSchemaEvolutionTypes.ExampleVariantSpec> variantSpecs)
      throws Exception {
    for (ExampleSchemaEvolutionTypes.ExampleVariantSpec spec : variantSpecs) {
      ExampleMessageUnion expected =
          buildExampleUnionCase(spec.javaProperty, expectedValues.get(spec.javaProperty));
      ExampleMessageUnion actual =
          fory.deserialize(fory.serialize(expected), ExampleMessageUnion.class);
      assertExampleUnionEquals(actual, expected, "java union case " + spec.javaProperty);
    }
  }

  private ExampleMessageUnion buildExampleUnionCase(String javaProperty, Object value)
      throws Exception {
    Method factory = findSingleArgumentMethod(ExampleMessageUnion.class, "of" + accessorSuffix(javaProperty));
    return (ExampleMessageUnion) factory.invoke(null, value);
  }

  private void assertExampleMessageValues(
      ExampleMessage actual,
      Map<String, Object> expectedValues,
      List<ExampleSchemaEvolutionTypes.ExampleVariantSpec> variantSpecs,
      String sourceLabel)
      throws Exception {
    for (ExampleSchemaEvolutionTypes.ExampleVariantSpec spec : variantSpecs) {
      assertNormalizedEquals(
          sourceLabel + "." + spec.javaProperty,
          readBeanProperty(actual, spec.javaProperty),
          expectedValues.get(spec.javaProperty));
    }
  }

  private void assertExampleUnionEquals(
      ExampleMessageUnion actual, ExampleMessageUnion expected, String sourceLabel) throws Exception {
    Assert.assertEquals(
        actual.getExampleMessageUnionCaseId(),
        expected.getExampleMessageUnionCaseId(),
        sourceLabel + " case id");
    assertNormalizedEquals(
        sourceLabel + "." + unionCaseProperty(actual),
        readExampleUnionValue(actual),
        readExampleUnionValue(expected));
  }

  private Object readExampleUnionValue(ExampleMessageUnion union) throws Exception {
    return readBeanProperty(union, unionCaseProperty(union));
  }

  private String unionCaseProperty(ExampleMessageUnion union) {
    return enumNameToJavaProperty(union.getExampleMessageUnionCase().name());
  }

  private Object readBeanProperty(Object bean, String javaProperty) throws Exception {
    Method getter = bean.getClass().getMethod("get" + accessorSuffix(javaProperty));
    return getter.invoke(bean);
  }

  private Method findSingleArgumentMethod(Class<?> type, String methodName) {
    for (Method method : type.getMethods()) {
      if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
        return method;
      }
    }
    throw new IllegalArgumentException("Missing method " + type.getName() + "." + methodName);
  }

  private String accessorSuffix(String javaProperty) {
    return Character.toUpperCase(javaProperty.charAt(0)) + javaProperty.substring(1);
  }

  private String enumNameToJavaProperty(String enumName) {
    StringBuilder builder = new StringBuilder();
    boolean uppercaseNext = false;
    for (int i = 0; i < enumName.length(); i++) {
      char ch = enumName.charAt(i);
      if (ch == '_') {
        uppercaseNext = true;
        continue;
      }
      char lower = Character.toLowerCase(ch);
      if (builder.length() == 0) {
        builder.append(lower);
      } else if (uppercaseNext) {
        builder.append(Character.toUpperCase(lower));
        uppercaseNext = false;
      } else {
        builder.append(lower);
      }
    }
    return builder.toString();
  }

  private void assertNormalizedEquals(String message, Object actual, Object expected) {
    Assert.assertEquals(normalizeValue(actual), normalizeValue(expected), message);
  }

  private Object normalizeValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Float16) {
      return ((Float16) value).toBits();
    }
    if (value instanceof BFloat16) {
      return ((BFloat16) value).toBits();
    }
    if (value instanceof BigDecimal) {
      return ((BigDecimal) value).toPlainString();
    }
    if (value instanceof Instant || value instanceof Duration || value instanceof LocalDate) {
      return value.toString();
    }
    if (value instanceof Enum<?>) {
      return ((Enum<?>) value).name();
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      List<Object> normalized = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        normalized.add(normalizeValue(Array.get(value, i)));
      }
      return normalized;
    }
    if (value instanceof List<?>) {
      List<?> list = (List<?>) value;
      List<Object> normalized = new ArrayList<>(list.size());
      for (Object element : list) {
        normalized.add(normalizeValue(element));
      }
      return normalized;
    }
    if (value instanceof Map<?, ?>) {
      Map<?, ?> map = (Map<?, ?>) value;
      List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
      entries.sort(Comparator.comparing(entry -> normalizeMapKey(entry.getKey())));
      Map<String, Object> normalized = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : entries) {
        normalized.put(normalizeMapKey(entry.getKey()), normalizeValue(entry.getValue()));
      }
      return normalized;
    }
    return value;
  }

  private String normalizeMapKey(Object key) {
    if (key == null) {
      return "null";
    }
    Object normalized = normalizeValue(key);
    return key.getClass().getName() + ":" + normalized;
  }

  private AnyHolder buildAnyHolder() {
    AnyInner inner = new AnyInner();
    inner.setName("inner");

    AnyHolder holder = new AnyHolder();
    holder.setBoolValue(Boolean.TRUE);
    holder.setStringValue("hello");
    holder.setDateValue(LocalDate.of(2024, 1, 2));
    holder.setTimestampValue(Instant.ofEpochSecond(1704164645L));
    holder.setMessageValue(inner);
    holder.setUnionValue(AnyUnion.ofText("union"));
    holder.setListValue(Arrays.asList("alpha", "beta"));
    holder.setMapValue(new HashMap<>(Map.of("k1", "v1", "k2", "v2")));
    return holder;
  }

  private TreeNode buildTree() {
    TreeNode childA = new TreeNode();
    childA.setId("child-a");
    childA.setName("child-a");
    childA.setChildren(Collections.emptyList());

    TreeNode childB = new TreeNode();
    childB.setId("child-b");
    childB.setName("child-b");
    childB.setChildren(Collections.emptyList());

    childA.setParent(childB);
    childB.setParent(childA);

    TreeNode root = new TreeNode();
    root.setId("root");
    root.setName("root");
    root.setChildren(Arrays.asList(childA, childA, childB));
    return root;
  }

  private void assertTree(TreeNode root) {
    List<TreeNode> children = root.getChildren();
    Assert.assertNotNull(children);
    Assert.assertEquals(children.size(), 3);
    Assert.assertSame(children.get(0), children.get(1));
    Assert.assertNotSame(children.get(0), children.get(2));
    Assert.assertSame(children.get(0).getParent(), children.get(2));
    Assert.assertSame(children.get(2).getParent(), children.get(0));
  }

  private Graph buildGraph() {
    Node nodeA = new Node();
    nodeA.setId("node-a");
    Node nodeB = new Node();
    nodeB.setId("node-b");

    Edge edge = new Edge();
    edge.setId("edge-1");
    edge.setWeight(1.5f);
    edge.setFrom(nodeA);
    edge.setTo(nodeB);

    nodeA.setOutEdges(Collections.singletonList(edge));
    nodeA.setInEdges(Collections.singletonList(edge));
    nodeB.setInEdges(Collections.singletonList(edge));
    nodeB.setOutEdges(Collections.emptyList());

    Graph graph = new Graph();
    graph.setNodes(Arrays.asList(nodeA, nodeB));
    graph.setEdges(Collections.singletonList(edge));
    return graph;
  }

  private void assertGraph(Graph graph) {
    Assert.assertNotNull(graph.getNodes());
    Assert.assertNotNull(graph.getEdges());
    Assert.assertEquals(graph.getNodes().size(), 2);
    Assert.assertEquals(graph.getEdges().size(), 1);
    Node nodeA = graph.getNodes().get(0);
    Node nodeB = graph.getNodes().get(1);
    Edge edge = graph.getEdges().get(0);
    Assert.assertSame(edge, nodeA.getOutEdges().get(0));
    Assert.assertSame(edge, nodeA.getInEdges().get(0));
    Assert.assertSame(edge.getFrom(), nodeA);
    Assert.assertSame(edge.getTo(), nodeB);
  }

  private Container buildContainer() {
    ScalarPack pack = new ScalarPack();
    pack.setB((byte) -8);
    pack.setUb((byte) 200);
    pack.setS((short) -1234);
    pack.setUs((short) 40000);
    pack.setI(-123456);
    pack.setUi(123456);
    pack.setL(-123456789L);
    pack.setUl(987654321L);
    pack.setF(1.5f);
    pack.setD(2.5d);
    pack.setOk(true);

    Container container = new Container();
    container.setId(9876543210L);
    container.setStatus(Status.STARTED);
    container.setBytes(new Int8List(new byte[] {(byte) 1, (byte) 2, (byte) 3}));
    container.setNumbers(new Int32List(new int[] {10, 20, 30}));
    container.setScalars(pack);
    container.setNames(Arrays.asList("alpha", "beta"));
    container.setFlags(new boolean[] {true, false});
    Note note = new Note();
    note.setText("alpha");
    Payload payload = Payload.ofNote(note);
    Metric metric = new Metric();
    metric.setValue(42.0d);
    payload.setMetric(metric);
    container.setPayload(payload);
    return container;
  }

  private static final class PeerCommand {
    private List<String> command;
    private Path workDir;
    private final Map<String, String> environment = new HashMap<>();
  }
}
