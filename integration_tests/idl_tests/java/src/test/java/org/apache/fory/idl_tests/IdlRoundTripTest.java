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
import addressbook.Person;
import addressbook.Person.PhoneNumber;
import addressbook.Person.PhoneType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fory.Fory;
import org.apache.fory.config.Language;
import org.testng.Assert;
import org.testng.annotations.Test;

public class IdlRoundTripTest {

  @Test
  public void testAddressBookRoundTrip() {
    Fory fory = Fory.builder().withLanguage(Language.XLANG).build();
    AddressbookForyRegistration.register(fory);

    AddressBook book = buildAddressBook();
    byte[] bytes = fory.serialize(book);
    Object decoded = fory.deserialize(bytes);

    Assert.assertTrue(decoded instanceof AddressBook);
    Assert.assertEquals(decoded, book);
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

    AddressBook book = new AddressBook();
    List<Person> people = new ArrayList<>();
    people.add(person);
    book.setPeople(people);

    Map<String, Person> peopleByName = new HashMap<>();
    peopleByName.put(person.getName(), person);
    book.setPeopleByName(peopleByName);

    return book;
  }
}
