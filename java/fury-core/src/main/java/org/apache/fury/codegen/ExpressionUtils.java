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

package org.apache.fury.codegen;

import static org.apache.fury.codegen.CodeGenerator.getSourcePublicAccessibleParentClass;
import static org.apache.fury.codegen.CodeGenerator.sourcePublicAccessible;
import static org.apache.fury.codegen.Expression.Arithmetic;
import static org.apache.fury.codegen.Expression.Comparator;
import static org.apache.fury.codegen.Expression.IsNull;
import static org.apache.fury.codegen.Expression.Literal;
import static org.apache.fury.codegen.Expression.NewArray;
import static org.apache.fury.codegen.Expression.Not;
import static org.apache.fury.codegen.Expression.StaticInvoke;
import static org.apache.fury.type.TypeUtils.getRawType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.fury.codegen.Expression.BitAnd;
import org.apache.fury.codegen.Expression.BitOr;
import org.apache.fury.codegen.Expression.BitShift;
import org.apache.fury.codegen.Expression.Cast;
import org.apache.fury.codegen.Expression.Invoke;
import org.apache.fury.codegen.Expression.ListExpression;
import org.apache.fury.codegen.Expression.LogicalAnd;
import org.apache.fury.codegen.Expression.LogicalOr;
import org.apache.fury.codegen.Expression.Null;
import org.apache.fury.codegen.Expression.Variable;
import org.apache.fury.reflect.ReflectionUtils;
import org.apache.fury.reflect.TypeRef;
import org.apache.fury.util.Preconditions;
import org.apache.fury.util.StringUtils;
import org.apache.fury.util.function.Functions;

/** Expression utils to create expression and code in a more convenient way. */
public class ExpressionUtils {
  public static ListExpression list(Expression... expressions) {
    return new ListExpression(expressions);
  }

  public static Expression newObjectArray(Expression... expressions) {
    return new NewArray(TypeRef.of(Object[].class), expressions);
  }

  public static Expression ofInt(String name, int v) {
    return new Variable(name, Literal.ofInt(v));
  }

  public static Expression valueOf(TypeRef<?> type, Expression value) {
    return new StaticInvoke(getRawType(type), "valueOf", type, false, value);
  }

  public static IsNull isNull(Expression target) {
    return new IsNull(target);
  }

  public static Expression notNull(Expression target) {
    return new Not(new IsNull(target));
  }

  public static Expression eqNull(Expression target) {
    Preconditions.checkArgument(!target.type().isPrimitive());
    return eq(target, new Null(target.type()));
  }

  public static Expression neqNull(Expression target) {
    Preconditions.checkArgument(!target.type().isPrimitive());
    return neq(target, new Null(target.type()));
  }

  public static LogicalAnd and(Expression left, Expression right, String name) {
    return new LogicalAnd(false, left, right);
  }

  public static LogicalAnd and(Expression left, Expression right) {
    return new LogicalAnd(true, left, right);
  }

  public static LogicalOr or(Expression left, Expression right, Expression... expressions) {
    LogicalOr logicalOr = new LogicalOr(left, right);
    for (Expression expression : expressions) {
      logicalOr = new LogicalOr(left, expression);
    }
    return logicalOr;
  }

  public static BitOr bitor(Expression left, Expression right) {
    return new BitOr(left, right);
  }

  public static BitAnd bitand(Expression left, Expression right, String name) {
    BitAnd bitAnd = new BitAnd(left, right);
    bitAnd.inline(false);
    return bitAnd;
  }

  public static BitAnd bitand(Expression left, Expression right) {
    return new BitAnd(left, right);
  }

  public static Not not(Expression target) {
    return new Not(target);
  }

  public static Literal nullValue(TypeRef<?> type) {
    return new Literal(null, type);
  }

  public static Comparator eq(Expression left, Expression right) {
    return eq(left, right, true);
  }

  public static Comparator eq(Expression left, Expression right, boolean inline) {
    return new Comparator("==", left, right, inline);
  }

  public static Comparator eq(Expression left, Expression right, String valuePrefix) {
    Comparator comparator = new Comparator("==", left, right, false);
    comparator.valuePrefix = valuePrefix;
    return comparator;
  }

  public static Comparator neq(Expression left, Expression right) {
    return neq(left, right, true);
  }

  public static Comparator neq(Expression left, Expression right, String valuePrefix) {
    Comparator comparator = new Comparator("!=", left, right, false);
    comparator.valuePrefix = valuePrefix;
    return comparator;
  }

  public static Comparator neq(Expression left, Expression right, boolean inline) {
    return new Comparator("!=", left, right, inline);
  }

  public static Comparator egt(Expression left, Expression right) {
    return new Comparator(">=", left, right, true);
  }

  public static Comparator egt(Expression left, Expression right, String valuePrefix) {
    Comparator comparator = new Comparator(">=", left, right, false);
    comparator.valuePrefix = valuePrefix;
    return comparator;
  }

  public static Comparator gt(Expression left, Expression right) {
    Comparator comparator = new Comparator(">", left, right, true);
    return comparator;
  }

  public static Comparator lessThan(Expression left, Expression right) {
    return new Comparator("<", left, right, true);
  }

  public static Arithmetic add(Expression left, Expression right) {
    return new Arithmetic(true, "+", left, right);
  }

  public static Arithmetic add(Expression left, Expression right, String valuePrefix) {
    Arithmetic arithmetic = new Arithmetic(true, "+", left, right);
    arithmetic.valuePrefix = valuePrefix;
    return arithmetic;
  }

  public static Arithmetic subtract(Expression left, Expression right) {
    return new Arithmetic(true, "-", left, right);
  }

  public static Arithmetic subtract(Expression left, Expression right, String valuePrefix) {
    Arithmetic arithmetic = new Arithmetic(false, "-", left, right);
    arithmetic.valuePrefix = valuePrefix;
    return arithmetic;
  }

  public static BitShift shift(String op, Expression target, int numBits) {
    return new BitShift(op, target, numBits);
  }

  public static BitShift leftShift(Expression target, int numBits) {
    return new BitShift("<<", target, numBits);
  }

  public static BitShift arithRightShift(Expression target, int numBits) {
    return new BitShift(">>", target, numBits);
  }

  public static BitShift logicalRightShift(Expression target, int numBits) {
    return new BitShift(">>", target, numBits);
  }

  public static Expression cast(Expression value, TypeRef<?> typeRef) {
    if ((value.type().equals(typeRef) || value.type().isSubtypeOf(typeRef))) {
      return value;
    }
    return new Cast(value, typeRef);
  }

  public static Expression cast(Expression value, TypeRef<?> typeRef, String namePrefix) {
    return new Cast(value, typeRef, namePrefix);
  }

  public static Expression invokeInline(
      Expression targetObject, String functionName, TypeRef type) {
    return inline(invoke(targetObject, functionName, null, type));
  }

  public static Expression invoke(
      Expression targetObject, String functionName, String returnNamePrefix, TypeRef type) {
    Class<?> rawType = type.getRawType();
    if (!sourcePublicAccessible(rawType)) {
      rawType = getSourcePublicAccessibleParentClass(rawType);
      type = type.getSupertype(rawType);
    }
    Class<?> returnType =
        ReflectionUtils.getReturnType(getRawType(targetObject.type()), functionName);
    if (!rawType.isAssignableFrom(returnType)) {
      if (!sourcePublicAccessible(returnType)) {
        returnType = getSourcePublicAccessibleParentClass(returnType);
      }
      return new Cast(
          new Invoke(targetObject, functionName, TypeRef.of(returnType)).inline(),
          type,
          returnNamePrefix);
    } else {
      return new Invoke(targetObject, functionName, returnNamePrefix, type);
    }
  }

  public static Expression inline(Expression expression) {
    return inline(expression, true);
  }

  private static Expression inline(Expression expression, boolean inline) {
    if (expression instanceof Expression.Inlineable) {
      ((Expression.Inlineable) (expression)).inline(inline);
    }
    return expression;
  }

  public static Expression uninline(Expression expression) {
    return inline(expression, false);
  }

  public static StaticInvoke invokeStaticInline(
      Class<?> staticObject, String functionName, TypeRef<?> type, Expression... arguments) {
    return new StaticInvoke(staticObject, functionName, "", type, false, true, arguments);
  }

  static String callFunc(
      String type,
      String resultVal,
      String target,
      String functionName,
      String args,
      boolean needTryCatch) {
    if (needTryCatch) {
      return StringUtils.format(
          "${type} ${value};\n"
              + "try {\n"
              + "   ${value} = ${target}.${functionName}(${args});\n"
              + "} catch (Exception e) {\n"
              + "   throw new RuntimeException(e);\n"
              + "}",
          "type",
          type,
          "value",
          resultVal,
          "target",
          target,
          "functionName",
          functionName,
          "args",
          args);
    } else {
      return StringUtils.format(
          "${type} ${value} = ${target}.${functionName}(${args});",
          "type",
          type,
          "value",
          resultVal,
          "target",
          target,
          "functionName",
          functionName,
          "args",
          args);
    }
  }

  static String callFunc(String target, String functionName, String args, boolean needTryCatch) {
    if (needTryCatch) {
      return StringUtils.format(
          "try {\n"
              + "   ${target}.${functionName}(${args});\n"
              + "} catch (Exception e) {\n"
              + "   throw new RuntimeException(e);\n"
              + "}",
          "target",
          target,
          "functionName",
          functionName,
          "args",
          args);
    } else {
      return StringUtils.format(
          "${target}.${functionName}(${args});",
          "target",
          target,
          "functionName",
          functionName,
          "args",
          args);
    }
  }

  public static List<Expression> extractCapturedExpressions(Serializable closure) {
    List<Expression> expressions = new ArrayList<>();
    Functions.extractCapturedVariables(
        closure,
        capturedArg -> {
          if (capturedArg instanceof Expression) {
            // FIXME may need to check list/container values types?
            expressions.add((Expression) capturedArg);
          } else if (capturedArg instanceof Expression[]) {
            Collections.addAll(Arrays.asList((Expression[]) capturedArg));
          }
          return false;
        });
    return expressions;
  }
}
