// Copyright 2023 Buf Technologies, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buf.protovalidate.internal.evaluator;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.exceptions.ExecutionException;
import kotlin.Unit;
import protokt.v1.KtMessage;
import protokt.v1.buf.validate.Violation;
import protokt.v1.google.protobuf.FieldDescriptor;

import java.util.Collections;
import java.util.List;

/** Performs validation on a single message field, defined by its descriptor. */
class FieldEvaluator implements Evaluator {
  /** The {@link ValueEvaluator} to apply to the field's value */
  public final ValueEvaluator valueEvaluator;

  /** The {@link FieldDescriptor} targeted by this evaluator */
  private final FieldDescriptor descriptor;

  /** Indicates that the field must have a set value. */
  private final boolean required;

  /**
   * IgnoreEmpty indicates if a field should skip validation on its zero value. This field is
   * generally true for nullable fields or fields with the ignore_empty constraint explicitly set.
   */
  private final boolean ignoreEmpty;

  /** Constructs a new {@link FieldEvaluator} */
  FieldEvaluator(
      ValueEvaluator valueEvaluator,
      FieldDescriptor descriptor,
      boolean required,
      boolean ignoreEmpty) {
    this.valueEvaluator = valueEvaluator;
    this.descriptor = descriptor;
    this.required = required;
    this.ignoreEmpty = ignoreEmpty;
  }

  @Override
  public boolean tautology() {
    return !required && valueEvaluator.tautology();
  }

  @Override
  public ValidationResult evaluate(Value val, boolean failFast) throws ExecutionException {
    KtMessage message = val.messageValue();
    if (message == null) {
      return ValidationResult.EMPTY;
    }
    boolean hasField;
    if (descriptor.isRepeated()) {
      hasField = message.getRepeatedFieldCount(descriptor) != 0;
    } else {
      hasField = message.hasField(descriptor);
    }
    if (required && !hasField) {
      return new ValidationResult(
          Collections.singletonList(
              Violation.Deserializer.invoke(builder -> {
                  builder.setFieldPath(descriptor.getName());
                  builder.setConstraintId("required");
                  builder.setMessage("value is required");
                  return Unit.INSTANCE;
              })
          )
      );
    }
    if (ignoreEmpty && !hasField) {
      return ValidationResult.EMPTY;
    }
    Object fieldValue = message.getField(descriptor);
    ValidationResult evalResult =
        valueEvaluator.evaluate(new ObjectValue(descriptor, fieldValue), failFast);
    List<Violation> violations =
        ErrorPathUtils.prefixErrorPaths(evalResult.getViolations(), "%s", descriptor.getName());
    return new ValidationResult(violations);
  }
}
