// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package fory

import (
	"reflect"
	"strconv"
	"strings"
)

const (
	// TagIDUseFieldName indicates field name should be used instead of tag ID
	TagIDUseFieldName = -1
)

// ForyTag represents parsed fory struct tag options.
//
// Tag format: `fory:"id=N,nullable=bool,ref=bool,ignore=bool,compress=bool,encoding=value"` or `fory:"-"`
//
// Options:
//   - id: Field tag ID. -1 (default) uses field name, >=0 uses numeric tag ID for compact encoding
//   - nullable: Whether to write null flag. Default false (skip null flag for non-nullable fields)
//   - ref: Whether to enable reference tracking. Default false (skip ref tracking overhead)
//   - ignore: Whether to skip this field during serialization. Default false
//   - compress: For int32/uint32 fields: true=varint encoding (default), false=fixed encoding
//   - encoding: For numeric fields:
//   - int32/uint32: "varint" (default) or "fixed"
//   - int64/uint64: "varint" (default), "fixed", or "tagged"
//
// Note: For int32/uint32, use either `compress` or `encoding`, not both.
//
// Examples:
//
//	type Example struct {
//	    Name       string  `fory:"id=0"`                           // Use tag ID 0
//	    Age        int     `fory:"id=1,nullable=false"`            // Explicit nullable=false
//	    Email      *string `fory:"id=2,nullable=true,ref=false"`   // Nullable pointer, no ref tracking
//	    Parent     *Node   `fory:"id=3,ref=true,nullable=true"`    // With reference tracking
//	    FixedI32   int32   `fory:"compress=false"`                 // Use fixed 4-byte INT32
//	    VarI32     int32   `fory:"encoding=varint"`                // Use VARINT32 (default)
//	    FixedU32   uint32  `fory:"encoding=fixed"`                 // Use fixed 4-byte UINT32
//	    TaggedI64  int64   `fory:"encoding=tagged"`                // Use TAGGED_INT64
//	    VarU64     uint64  `fory:"encoding=varint"`                // Use VAR_UINT64 (default)
//	    Secret     string  `fory:"ignore"`                         // Skip this field
//	    Hidden     string  `fory:"-"`                              // Skip this field (shorthand)
//	}
type ForyTag struct {
	ID       int    // Field tag ID (-1 = use field name, >=0 = use tag ID)
	Nullable bool   // Whether to write null flag (default: false)
	Ref      bool   // Whether to enable reference tracking (default: false)
	Ignore   bool   // Whether to ignore this field during serialization (default: false)
	HasTag   bool   // Whether field has fory tag at all
	Compress bool   // For int32/uint32: true=varint, false=fixed (default: true)
	Encoding string // For int64/uint64: "fixed", "varint", "tagged" (default: "varint")

	// Track which options were explicitly set (for override logic)
	NullableSet bool
	RefSet      bool
	IgnoreSet   bool
	CompressSet bool
	EncodingSet bool
}

// parseForyTag parses a fory struct tag from reflect.StructField.Tag.
//
// Tag format: `fory:"id=N,nullable=bool,ref=bool,ignore=bool,compress=bool,encoding=value"` or `fory:"-"`
//
// Supported syntaxes:
//   - Key-value: `nullable=true`, `ref=false`, `ignore=true`, `id=0`
//   - For int32/uint32: `compress=true` (varint) or `compress=false` (fixed), default is true
//   - For int64/uint64: `encoding=fixed`, `encoding=varint`, `encoding=tagged`, default is varint
//   - Standalone flags: `nullable`, `ref`, `ignore` (equivalent to =true)
//   - Shorthand: `-` (equivalent to `ignore=true`)
func parseForyTag(field reflect.StructField) ForyTag {
	tag := ForyTag{
		ID:       TagIDUseFieldName,
		Nullable: false,
		Ref:      false,
		Ignore:   false,
		HasTag:   false,
		Compress: true,     // default: varint encoding
		Encoding: "varint", // default: varint encoding
	}

	tagValue, ok := field.Tag.Lookup("fory")
	if !ok {
		return tag
	}

	tag.HasTag = true

	// Handle "-" shorthand for ignore
	if tagValue == "-" {
		tag.Ignore = true
		tag.IgnoreSet = true
		return tag
	}

	// Parse comma-separated options
	parts := strings.Split(tagValue, ",")
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}

		// Handle key=value pairs and standalone flags
		if idx := strings.Index(part, "="); idx >= 0 {
			key := strings.TrimSpace(part[:idx])
			value := strings.TrimSpace(part[idx+1:])

			switch key {
			case "id":
				if id, err := strconv.Atoi(value); err == nil {
					tag.ID = id
				}
			case "nullable":
				tag.Nullable = parseBool(value)
				tag.NullableSet = true
			case "ref":
				tag.Ref = parseBool(value)
				tag.RefSet = true
			case "ignore":
				tag.Ignore = parseBool(value)
				tag.IgnoreSet = true
			case "compress":
				tag.Compress = parseBool(value)
				tag.CompressSet = true
			case "encoding":
				tag.Encoding = strings.ToLower(strings.TrimSpace(value))
				tag.EncodingSet = true
			}
		} else {
			// Handle standalone flags (presence means true)
			switch part {
			case "nullable":
				tag.Nullable = true
				tag.NullableSet = true
			case "ref":
				tag.Ref = true
				tag.RefSet = true
			case "ignore":
				tag.Ignore = true
				tag.IgnoreSet = true
			}
		}
	}

	return tag
}

// parseBool parses a boolean value from string.
// Accepts: "true", "1", "yes" as true; everything else as false.
func parseBool(s string) bool {
	s = strings.ToLower(strings.TrimSpace(s))
	return s == "true" || s == "1" || s == "yes"
}

// validateForyTags validates all fory tags in a struct type.
// Returns an error if validation fails.
//
// Validation rules:
//   - Tag ID must be >= -1
//   - Tag IDs must be unique within a struct (except -1)
//   - Ignored fields are not validated for ID uniqueness
func validateForyTags(t reflect.Type) error {
	if t.Kind() == reflect.Ptr {
		t = t.Elem()
	}
	if t.Kind() != reflect.Struct {
		return nil
	}

	tagIDs := make(map[int]string) // id -> field name

	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		tag := parseForyTag(field)

		// Skip ignored fields for ID uniqueness validation
		if tag.Ignore {
			continue
		}

		// Validate tag ID range
		if tag.ID < TagIDUseFieldName {
			return InvalidTagErrorf("invalid fory tag id=%d on field %s: id must be >= -1",
				tag.ID, field.Name)
		}

		// Check for duplicate tag IDs (except -1 which means use field name)
		if tag.ID >= 0 {
			if existing, ok := tagIDs[tag.ID]; ok {
				return InvalidTagErrorf("duplicate fory tag id=%d on fields %s and %s",
					tag.ID, existing, field.Name)
			}
			tagIDs[tag.ID] = field.Name
		}
	}

	return nil
}

// shouldIncludeField returns true if the field should be serialized.
// A field is excluded if:
//   - It's unexported (starts with lowercase)
//   - It has `fory:"-"` tag
//   - It has `fory:"ignore"` or `fory:"ignore=true"` tag
func shouldIncludeField(field reflect.StructField) bool {
	// Skip unexported fields
	if field.PkgPath != "" {
		return false
	}

	// Check for ignore tag
	tag := parseForyTag(field)
	return !tag.Ignore
}
