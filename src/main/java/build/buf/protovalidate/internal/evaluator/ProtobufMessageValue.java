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

import com.google.protobuf.Message;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The {@link build.buf.protovalidate.internal.evaluator.Value} type that contains a {@link
 * com.google.protobuf.Message}.
 */
public final class ProtobufMessageValue implements Value {

  private final ProtobufMessageLike value;

  /**
   * Constructs a {@link ProtobufMessageValue} with the provided message value.
   *
   * @param value The message value.
   */
  public ProtobufMessageValue(Message value) {
    this.value = new ProtobufMessageLike(value);
  }

  @Override
  public MessageLike messageValue() {
    return value;
  }

  @Nullable
  @Override
  public <T> T jvmValue(Class<T> clazz) {
    return null;
  }

  @Override
  public Object celValue() {
    return value.getMessage();
  }

  @Override
  public List<Value> repeatedValue() {
    return Collections.emptyList();
  }

  @Override
  public Map<Value, Value> mapValue() {
    return Collections.emptyMap();
  }
}
