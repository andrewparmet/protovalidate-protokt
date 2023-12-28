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

package build.buf.protovalidate.internal.evaluator

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.OneofDescriptor

interface MessageLike {
    fun getRepeatedFieldCount(field: FieldDescriptor): Int

    fun hasField(field: FieldDescriptor): Boolean

    fun hasField(oneof: OneofDescriptor): Boolean

    fun getField(field: FieldDescriptor): Value
}
