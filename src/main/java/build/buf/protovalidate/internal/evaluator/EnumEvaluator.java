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
import protokt.v1.buf.validate.Violation;
import protokt.v1.google.protobuf.EnumValueDescriptorProto;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link EnumEvaluator} checks an enum value being a member of the defined values exclusively. This
 * check is handled outside CEL as enums are completely type erased to integers.
 */
class EnumEvaluator implements Evaluator {
  /** Captures all the defined values for this enum */
  private final Set<Integer> values;

  /**
   * Constructs a new evaluator for enum values.
   *
   * @param valueDescriptors the list of {@link EnumValueDescriptorProto} for the enum.
   */
  EnumEvaluator(List<EnumValueDescriptorProto> valueDescriptors) {
    if (valueDescriptors.isEmpty()) {
      this.values = Collections.emptySet();
    } else {
      this.values =
          valueDescriptors.stream()
              .map(EnumValueDescriptorProto::getNumber)
              .collect(Collectors.toSet());
    }
  }

  @Override
  public boolean tautology() {
    return false;
  }

  /**
   * Evaluates an enum value.
   *
   * @param val the value to evaluate.
   * @param failFast indicates if the evaluation should stop on the first violation.
   * @return the {@link ValidationResult} of the evaluation.
   * @throws ExecutionException if an error occurs during the evaluation.
   */
  @Override
  public ValidationResult evaluate(Value val, boolean failFast) throws ExecutionException {
    EnumValueDescriptorProto enumValue = val.value(EnumValueDescriptorProto.class);
    if (enumValue == null) {
      return ValidationResult.EMPTY;
    }
    if (!values.contains(enumValue.getNumber())) {
      return new ValidationResult(
          Collections.singletonList(
              Violation.Deserializer.invoke(builder -> {
                  builder.setConstraintId("enum.defined_only");
                  builder.setMessage("value must be one of the defined enum values");
                  return Unit.INSTANCE;
              })
          )
      );
    }
    return ValidationResult.EMPTY;
  }
}
