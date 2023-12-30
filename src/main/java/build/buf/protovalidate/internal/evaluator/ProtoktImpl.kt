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
import protokt.v1.Bytes
import protokt.v1.KtEnum
import protokt.v1.KtMessage

class ProtoktMessageLike(
    val message: KtMessage,
    val context: ProtoktRuntimeContext,
) : MessageLike {
    override fun hasField(field: FieldDescriptor) = ProtoktReflect.getField(message, field) is ProtoktReflect.Found

    override fun getField(field: FieldDescriptor) =
        ProtoktObjectValue(
            field,
            (ProtoktReflect.getField(message, field) as ProtoktReflect.Found).value,
            context,
        )
}

class ProtoktMessageValue(
    private val message: KtMessage,
    private val context: ProtoktRuntimeContext,
) : Value {
    override fun messageValue() = ProtoktMessageLike(message, context)

    override fun repeatedValue() = emptyList<Value>()

    override fun mapValue() = emptyMap<Value, Value>()

    override fun celValue() = context.protobufJavaValue(message)

    override fun <T : Any> jvmValue(clazz: Class<T>) = null
}

class ProtoktObjectValue(
    private val fieldDescriptor: FieldDescriptor,
    private val value: Any,
    private val context: ProtoktRuntimeContext,
) : Value {
    override fun messageValue() = ProtoktMessageLike(value as KtMessage, context)

    override fun repeatedValue() = (value as List<*>).map { ProtoktObjectValue(fieldDescriptor, it!!, context) }

    override fun mapValue(): Map<Value, Value> {
        val input = value as Map<*, *>

        val keyDesc = fieldDescriptor.messageType.findFieldByNumber(1)
        val valDesc = fieldDescriptor.messageType.findFieldByNumber(2)

        return input.entries.associate { (key, value) ->
            Pair(
                ProtoktObjectValue(keyDesc, key!!, context),
                ProtoktObjectValue(valDesc, value!!, context),
            )
        }
    }

    override fun celValue() =
        when (value) {
            is KtEnum -> value.value
            is UInt -> org.projectnessie.cel.common.ULong.valueOf(value.toLong())
            is ULong -> org.projectnessie.cel.common.ULong.valueOf(value.toLong())
            is KtMessage, is Bytes -> context.protobufJavaValue(value)

            // pray
            else -> value
        }

    override fun <T : Any> jvmValue(clazz: Class<T>): T? = context.protobufJavaValue(value)?.let(clazz::cast)
}
