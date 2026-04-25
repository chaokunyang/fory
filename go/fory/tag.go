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
	"fmt"
	"reflect"
	"strconv"
	"strings"
	"unicode"
)

const (
	// TagIDUseFieldName indicates field name should be used instead of tag ID
	TagIDUseFieldName = -1
)

type ForyTag struct {
	ID       int
	Nullable bool
	Ref      bool
	Ignore   bool
	HasTag   bool
	Encoding string

	NullableSet  bool
	RefSet       bool
	IgnoreSet    bool
	EncodingSet  bool
	TypeSet      bool
	TypeValid    bool
	TypeOverride *typeOverrideNode
	ParseError   string
}

type typeOverrideNode struct {
	name         string
	explicitName bool
	encoding     string
	encodingSet  bool
	nullable     *bool
	ref          *bool
	element      *typeOverrideNode
	key          *typeOverrideNode
	value        *typeOverrideNode
}

func parseForyTag(field reflect.StructField) ForyTag {
	tag := ForyTag{
		ID:        TagIDUseFieldName,
		HasTag:    false,
		Encoding:  "varint",
		TypeValid: true,
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

	// Parse comma-separated options while respecting nested type DSL.
	parts := splitTagParts(tagValue)
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
			case "encoding":
				tag.Encoding = strings.ToLower(strings.TrimSpace(value))
				tag.EncodingSet = true
			case "type":
				tag.TypeSet = true
				override, err := parseTypeOverride(value)
				if err != nil {
					tag.TypeValid = false
					tag.ParseError = err.Error()
				} else {
					tag.TypeOverride = override
				}
			case "compress", "nested_ref":
				tag.ParseError = fmt.Sprintf("legacy fory tag key %q is not supported; use encoding=... or type=...", key)
			default:
				tag.ParseError = fmt.Sprintf("unknown fory tag key %q", key)
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
			default:
				tag.ParseError = fmt.Sprintf("unknown fory tag flag %q", part)
			}
		}
	}

	return tag
}

func splitTagParts(tagValue string) []string {
	var parts []string
	bracketDepth := 0
	parenDepth := 0
	start := 0
	for i, r := range tagValue {
		switch r {
		case '[':
			bracketDepth++
		case ']':
			if bracketDepth > 0 {
				bracketDepth--
			}
		case '(':
			parenDepth++
		case ')':
			if parenDepth > 0 {
				parenDepth--
			}
		case ',':
			if bracketDepth == 0 && parenDepth == 0 {
				parts = append(parts, tagValue[start:i])
				start = i + 1
			}
		}
	}
	if start <= len(tagValue) {
		parts = append(parts, tagValue[start:])
	}
	return parts
}

type typeOverrideParser struct {
	input string
	pos   int
}

func parseTypeOverride(value string) (*typeOverrideNode, error) {
	parser := &typeOverrideParser{input: strings.TrimSpace(value)}
	node, err := parser.parseTypeExpr(true)
	if err != nil {
		return nil, err
	}
	parser.skipSpaces()
	if parser.pos != len(parser.input) {
		return nil, fmt.Errorf("unexpected trailing type override input %q", parser.input[parser.pos:])
	}
	return node, nil
}

func (p *typeOverrideParser) parseTypeExpr(root bool) (*typeOverrideNode, error) {
	name := p.parseIdentifier()
	if name == "" {
		return nil, fmt.Errorf("expected type name at offset %d", p.pos)
	}
	if !root && isTypeChildLabel(name) && p.peek() == '(' {
		node := &typeOverrideNode{name: "", explicitName: false}
		p.pos++
		if err := p.parseArgs(node); err != nil {
			return nil, err
		}
		return node, nil
	}
	node := &typeOverrideNode{name: strings.ToLower(name), explicitName: true}
	p.skipSpaces()
	if p.peek() == '(' {
		p.pos++
		if err := p.parseArgs(node); err != nil {
			return nil, err
		}
	}
	return node, nil
}

func (p *typeOverrideParser) parseArgs(node *typeOverrideNode) error {
	for {
		p.skipSpaces()
		if p.peek() == ')' {
			p.pos++
			return nil
		}
		name := p.parseIdentifier()
		if name == "" {
			return fmt.Errorf("expected option or child override at offset %d", p.pos)
		}
		name = strings.ToLower(name)
		p.skipSpaces()
		switch p.peek() {
		case '=':
			p.pos++
			p.skipSpaces()
			if isTypeChildLabel(name) {
				child, err := p.parseTypeExpr(false)
				if err != nil {
					return err
				}
				setTypeChild(node, name, child)
			} else {
				value := p.parseScalarValue()
				if err := setTypeOption(node, name, value); err != nil {
					return err
				}
			}
		case '(':
			if !isTypeChildLabel(name) {
				return fmt.Errorf("unexpected nested override %q at offset %d", name, p.pos)
			}
			p.pos++
			child := &typeOverrideNode{explicitName: false}
			if err := p.parseArgs(child); err != nil {
				return err
			}
			setTypeChild(node, name, child)
		default:
			return fmt.Errorf("expected '=' or '(' after %q at offset %d", name, p.pos)
		}
		p.skipSpaces()
		switch p.peek() {
		case ',':
			p.pos++
		case ')':
			p.pos++
			return nil
		default:
			return fmt.Errorf("expected ',' or ')' at offset %d", p.pos)
		}
	}
}

func (p *typeOverrideParser) parseIdentifier() string {
	p.skipSpaces()
	start := p.pos
	for p.pos < len(p.input) {
		r := rune(p.input[p.pos])
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '_' {
			p.pos++
			continue
		}
		break
	}
	return p.input[start:p.pos]
}

func (p *typeOverrideParser) parseScalarValue() string {
	p.skipSpaces()
	start := p.pos
	for p.pos < len(p.input) {
		switch p.input[p.pos] {
		case ',', ')':
			return strings.TrimSpace(p.input[start:p.pos])
		default:
			p.pos++
		}
	}
	return strings.TrimSpace(p.input[start:])
}

func (p *typeOverrideParser) skipSpaces() {
	for p.pos < len(p.input) && unicode.IsSpace(rune(p.input[p.pos])) {
		p.pos++
	}
}

func (p *typeOverrideParser) peek() byte {
	if p.pos >= len(p.input) {
		return 0
	}
	return p.input[p.pos]
}

func isTypeChildLabel(name string) bool {
	return name == "element" || name == "key" || name == "value"
}

func setTypeChild(node *typeOverrideNode, label string, child *typeOverrideNode) {
	switch label {
	case "element":
		node.element = child
	case "key":
		node.key = child
	case "value":
		node.value = child
	}
}

func setTypeOption(node *typeOverrideNode, name string, value string) error {
	switch name {
	case "encoding":
		node.encoding = strings.ToLower(strings.TrimSpace(value))
		node.encodingSet = true
		return nil
	case "nullable":
		boolVal, ok := parseBoolStrict(value)
		if !ok {
			return fmt.Errorf("invalid nullable value %q", value)
		}
		node.nullable = &boolVal
		return nil
	case "ref":
		boolVal, ok := parseBoolStrict(value)
		if !ok {
			return fmt.Errorf("invalid ref value %q", value)
		}
		node.ref = &boolVal
		return nil
	default:
		return fmt.Errorf("unknown type override option %q", name)
	}
}

func parseBoolStrict(s string) (bool, bool) {
	s = strings.ToLower(strings.TrimSpace(s))
	switch s {
	case "true", "1", "yes":
		return true, true
	case "false", "0", "no":
		return false, true
	default:
		return false, false
	}
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

		if tag.ParseError != "" {
			return InvalidTagErrorf(
				"invalid fory tag on field %s: %s",
				field.Name,
				tag.ParseError,
			)
		}
		if tag.TypeSet && !tag.TypeValid {
			return InvalidTagErrorf(
				"invalid fory tag type override on field %s",
				field.Name,
			)
		}
		if tag.EncodingSet && !isValidTopLevelEncoding(field.Type, tag.Encoding) {
			return InvalidTagErrorf(
				"invalid fory tag encoding=%q on field %s",
				tag.Encoding,
				field.Name,
			)
		}
		if tag.EncodingSet && !isTopLevelNumericField(field.Type) {
			return InvalidTagErrorf("fory tag encoding on field %s requires a top-level numeric scalar field", field.Name)
		}
		if tag.TypeSet && isTopLevelNumericField(field.Type) {
			return InvalidTagErrorf("numeric field %s must use top-level encoding=... instead of type=...", field.Name)
		}
	}

	return nil
}

func isTopLevelNumericField(type_ reflect.Type) bool {
	if info, ok := getOptionalInfo(type_); ok {
		type_ = info.valueType
	}
	if type_.Kind() == reflect.Ptr {
		type_ = type_.Elem()
	}
	switch type_.Kind() {
	case reflect.Int32, reflect.Int64, reflect.Uint32, reflect.Uint64:
		return true
	default:
		return false
	}
}

func isValidTopLevelEncoding(type_ reflect.Type, encoding string) bool {
	if info, ok := getOptionalInfo(type_); ok {
		type_ = info.valueType
	}
	if type_.Kind() == reflect.Ptr {
		type_ = type_.Elem()
	}
	switch type_.Kind() {
	case reflect.Int32, reflect.Uint32:
		return encoding == "fixed" || encoding == "varint"
	case reflect.Int64, reflect.Uint64:
		return encoding == "fixed" || encoding == "varint" || encoding == "tagged"
	default:
		return false
	}
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
