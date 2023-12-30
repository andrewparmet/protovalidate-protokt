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
import com.google.protobuf.Message
import com.google.protobuf.UnknownFieldSet

class ProtobufMessageLike(
    val message: Message
) : MessageLike {
    override fun getRepeatedFieldCount(field: FieldDescriptor) =
        message.getRepeatedFieldCount(field)

    override fun hasField(field: FieldDescriptor) =
        message.hasField(field) ||
            (field.type == FieldDescriptor.Type.ENUM && message.unknownFields.hasField(field.number))

    override fun getField(field: FieldDescriptor) =
        if (field.type == FieldDescriptor.Type.ENUM && message.unknownFields.hasField(field.number)) {
            val enums = (message.unknownFields.getField(field.number) as UnknownFieldSet.Field).varintList
            if (field.isRepeated) {
                ProtobufObjectValue(field, enums.map { java.lang.Long.valueOf(it).toInt() })
            } else {
                ProtobufObjectValue(field, enums.last().let { java.lang.Long.valueOf(it).toInt() })
            }
        } else {
            ProtobufObjectValue(field, message.getField(field))
        }
}
