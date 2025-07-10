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

enum RefFlag{
  NULL(-3),
  TRACKED_ALREADY(-2),
  UNTRACKED_NOT_NULL(-1),
  TRACK_FIRST(0);

  final int id;

  const RefFlag(this.id);

  // static bool checkAllow(int id){
  //   return id >= NULL.id && id <= TRACK_FIRST.id;
  // }

  bool get noNeedToSer => (this == NULL || this == TRACKED_ALREADY);
}