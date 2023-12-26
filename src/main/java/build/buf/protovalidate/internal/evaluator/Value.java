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
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * {@link Value} is a wrapper around a protobuf value that provides helper methods for accessing the
 * value.
 */
public interface Value {
  /**
   * Get the underlying value as a {@link Message} type.
   *
   * @return The underlying {@link Message} value. null if the underlying value is not a {@link
   *     Message} type.
   */
  @Nullable
  MessageLike messageValue();

  /**
   * Get the underlying value.
   *
   * @return The value.
   */
  Object value();

  /**
   * Get the underlying value as a list.
   *
   * @return The underlying value as a list. Empty list is returned if the underlying type is not a
   *     list.
   */
  List<Value> repeatedValue();

  /**
   * Get the underlying value as a map.
   *
   * @return The underlying value as a map. Empty map is returned if the underlying type is not a
   *     list.
   */
  Map<Value, Value> mapValue();

  /**
   * Get the underlying value as an enum value.
   *
   * @return The underlying value as an enum value. -1 is returned if the underlying type is not an
   *     enum.
   */
  int enumValue();

  Object bindingValue();
}
