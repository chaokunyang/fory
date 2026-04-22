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

/**
 * Integration tests for JavaScript IDL-generated code.
 *
 * These tests verify that:
 * 1. Generated JavaScript types compile correctly
 * 2. Java writes IDL payloads, JavaScript roundtrips them, and Java verifies
 */

import * as path from 'path';
import { spawnSync } from 'child_process';
import {
  AddressBook,
  Person,
  Animal,
  AnimalCase,
  Dog,
  Cat,
} from '../generated/addressbook';
import {
  AllOptionalTypes,
} from '../generated/optional_types';
import { TreeNode } from '../generated/tree';
import {
  Envelope,
  Status,
  WrapperCase,
} from '../generated/auto_id';

// ---------------------------------------------------------------------------
// Helper: build test objects that conform to generated interfaces
// ---------------------------------------------------------------------------

function builddog(): Dog {
  return { name: 'Rex', barkVolume: 5 };
}

function buildcat(): Cat {
  return { name: 'Mimi', lives: 9 };
}

function buildPersonPhoneNumber(num: string, pt: Person.PhoneType): Person.PhoneNumber {
  return { number_: num, phoneType: pt };
}

function buildperson(): Person {
  return {
    name: 'Alice',
    id: 123,
    email: 'alice@example.com',
    tags: ['friend', 'colleague'],
    scores: new Map([['math', 100], ['science', 98]]),
    salary: 120000.5,
    phones: [
      buildPersonPhoneNumber('555-0100', Person.PhoneType.MOBILE),
      buildPersonPhoneNumber('555-0111', Person.PhoneType.WORK),
    ],
    pet: { case: AnimalCase.CAT, value: buildcat() },
  };
}

function buildaddressBook(): AddressBook {
  const person = buildperson();
  return {
    people: [person],
    peopleByName: new Map([[person.name, person]]),
  };
}

function buildtreeNode(): TreeNode {
  const child1: TreeNode = {
    id: 'child-1',
    name: 'Child 1',
    children: [],
    parent: undefined,
  };
  const child2: TreeNode = {
    id: 'child-2',
    name: 'Child 2',
    children: [],
    parent: undefined,
  };
  return {
    id: 'root',
    name: 'Root',
    children: [child1, child2],
    parent: undefined,
  };
}

function buildAutoIdenvelope(): Envelope {
  const payload: Envelope.Payload = { value: 42 };
  return {
    id: 'env-1',
    payload,
    detail: { case: Envelope.DetailCase.PAYLOAD, value: payload },
    status: Status.OK,
  };
}

// ---------------------------------------------------------------------------
// 1. Compilation & type-construction tests
//    (If these tests run at all, the generated types compile correctly.)
// ---------------------------------------------------------------------------

describe('Generated types compile and construct correctly', () => {
  test('addressBook type construction', () => {
    const book = buildaddressBook();
    expect(book.people).toHaveLength(1);
    expect(book.people[0].name).toBe('Alice');
    expect(book.people[0].id).toBe(123);
    expect(book.people[0].email).toBe('alice@example.com');
    expect(book.people[0].tags).toEqual(['friend', 'colleague']);
    expect(book.people[0].salary).toBe(120000.5);
    expect(book.people[0].phones).toHaveLength(2);
    expect(book.people[0].phones[0].phoneType).toBe(Person.PhoneType.MOBILE);
    expect(book.people[0].phones[1].phoneType).toBe(Person.PhoneType.WORK);
    expect(book.peopleByName.get('Alice')).toBe(book.people[0]);
  });

  test('Union (animal) type construction', () => {
    const doganimal: Animal = {
      case: AnimalCase.DOG,
      value: builddog(),
    };
    expect(doganimal.case).toBe(AnimalCase.DOG);
    expect((doganimal.value as Dog).name).toBe('Rex');

    const catanimal: Animal = {
      case: AnimalCase.CAT,
      value: buildcat(),
    };
    expect(catanimal.case).toBe(AnimalCase.CAT);
    expect((catanimal.value as Cat).lives).toBe(9);
  });

  test('Enum values are correct', () => {
    expect(Person.PhoneType.MOBILE).toBe(0);
    expect(Person.PhoneType.HOME).toBe(1);
    expect(Person.PhoneType.WORK).toBe(2);

    expect(AnimalCase.DOG).toBe(1);
    expect(AnimalCase.CAT).toBe(2);
  });

  test('treeNode type construction with optional parent', () => {
    const tree = buildtreeNode();
    expect(tree.id).toBe('root');
    expect(tree.children).toHaveLength(2);
    expect(tree.parent).toBeUndefined();
    expect(tree.children[0].name).toBe('Child 1');
  });

  test('AutoId types type construction', () => {
    const myEnvelope = buildAutoIdenvelope();
    expect(myEnvelope.id).toBe('env-1');
    expect(myEnvelope.payload?.value).toBe(42);
    expect(myEnvelope.status).toBe(Status.OK);

    expect(Status.UNKNOWN).toBe(4096);
    expect(Status.OK).toBe(8192);

    expect(WrapperCase.ENVELOPE).toBe(1);
    expect(WrapperCase.RAW).toBe(2);

    expect(Envelope.DetailCase.PAYLOAD).toBe(1);
    expect(Envelope.DetailCase.NOTE).toBe(2);
  });
});

test('allOptionalTypes type construction', () => {
  const full: AllOptionalTypes = {
    boolValue: true,
    int32Value: 42,
    stringValue: 'hello',
  };
  const sparse: AllOptionalTypes = {};
  expect(full.stringValue).toBe('hello');
  expect(sparse.stringValue).toBeUndefined();
});

function runJavaRoundTripTests(testPattern: string) {
  const idlRoot = path.resolve(__dirname, '..', '..');
  const script = path.join(idlRoot, 'run_java_tests.sh');
  const result = spawnSync(
    'bash',
    [script],
    {
      cwd: idlRoot,
      encoding: 'utf8',
      maxBuffer: 20 * 1024 * 1024,
      env: {
        ...process.env,
        ENABLE_FORY_DEBUG_OUTPUT: '1',
        IDL_PEER_LANG: 'javascript',
        IDL_JAVA_TEST_PATTERN: testPattern,
      },
    },
  );

  if (result.status !== 0) {
    const output = [result.stdout, result.stderr].filter(Boolean).join('\n');
    throw new Error(`Java IDL roundtrip tests failed for pattern ${testPattern}\n${output}`);
  }
}

const JAVA_JS_ROUNDTRIP_GROUPS: Array<{ title: string; testPattern: string }> = [
  {
    title: 'covers schema and compatible value cases through IdlRoundTripTest',
    testPattern: [
      'IdlRoundTripTest#testAddressBookRoundTripCompatible',
      'testAddressBookRoundTripSchemaConsistent',
      'testAutoIdRoundTripCompatible',
      'testAutoIdRoundTripSchemaConsistent',
      'testPrimitiveTypesRoundTripCompatible',
      'testPrimitiveTypesRoundTripSchemaConsistent',
      'testCollectionRoundTripCompatible',
      'testCollectionRoundTripSchemaConsistent',
      'testOptionalTypesRoundTripCompatible',
      'testOptionalTypesRoundTripSchemaConsistent',
      'testFlatbuffersRoundTripCompatible',
      'testFlatbuffersRoundTripSchemaConsistent',
    ].join('+'),
  },
  {
    title: 'covers ref-tracking cases through IdlRoundTripTest',
    testPattern: [
      'IdlRoundTripTest#testTreeRoundTripCompatible',
      'testTreeRoundTripSchemaConsistent',
      'testGraphRoundTripCompatible',
      'testGraphRoundTripSchemaConsistent',
    ].join('+'),
  },
];

describe('Java-JavaScript roundtrip', () => {
  test.each(JAVA_JS_ROUNDTRIP_GROUPS)('$title', ({ testPattern }) => {
    runJavaRoundTripTests(testPattern);
  }, 240000);
});
