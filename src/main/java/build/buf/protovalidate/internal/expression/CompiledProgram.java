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

package build.buf.protovalidate.internal.expression;

import build.buf.protovalidate.exceptions.ExecutionException;

import javax.annotation.Nullable;

import kotlin.Unit;
import org.projectnessie.cel.Program;
import org.projectnessie.cel.common.types.Err;
import org.projectnessie.cel.common.types.ref.Val;
import protokt.v1.buf.validate.Violation;

/**
 * {@link CompiledProgram} is a parsed and type-checked {@link Program} along with the source {@link
 * Expression}.
 */
public class CompiledProgram {
  /** A compiled CEL program that can be evaluated against a set of variable bindings. */
  private final Program program;

  /** The original expression that was compiled into the program from the proto file. */
  private final Expression source;

  /**
   * Constructs a new {@link CompiledProgram}.
   *
   * @param program The compiled CEL program.
   * @param source The original expression that was compiled into the program.
   */
  public CompiledProgram(Program program, Expression source) {
    this.program = program;
    this.source = source;
  }

  /**
   * Evaluate the compiled program with a given set of {@link Variable} bindings.
   *
   * @param bindings Variable bindings used for the evaluation.
   * @return The {@link protokt.v1.buf.validate.Violation} from the evaluation, or null if there are no
   *     violations.
   * @throws ExecutionException If the evaluation of the CEL program fails with an error.
   */
  @Nullable
  public Violation eval(Variable bindings) throws ExecutionException {
    Program.EvalResult evalResult = program.eval(bindings);
    Val val = evalResult.getVal();
    if (val instanceof Err) {
      throw new ExecutionException(String.format("error evaluating %s: %s", source.id, val));
    }
    Object value = val.value();
    if (value instanceof String) {
      if ("".equals(value)) {
        return null;
      }
      return Violation.Deserializer.invoke(builder -> {
          builder.setConstraintId(this.source.id);
          builder.setMessage(value.toString());
          return Unit.INSTANCE;
      });
    } else if (value instanceof Boolean) {
      if (val.booleanValue()) {
        return null;
      }
      return Violation.Deserializer.invoke(builder -> {
        builder.setConstraintId(this.source.id);
        builder.setMessage(this.source.message);
        return Unit.INSTANCE;
      });
    } else {
      throw new ExecutionException(String.format("resolved to an unexpected type %s", val));
    }
  }
}
