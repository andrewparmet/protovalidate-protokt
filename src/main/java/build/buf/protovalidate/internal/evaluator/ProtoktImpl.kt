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

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import org.projectnessie.cel.common.ULong
import protokt.v1.Bytes
import protokt.v1.KtEnum
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import kotlin.reflect.full.findAnnotation

class ProtoktMessageLike(
    val message: KtMessage,
    internal val descriptorsByFullTypeName: Map<String, Descriptor>
) : MessageLike {
    override fun getRepeatedFieldCount(field: FieldDescriptor): Int {
        val value = ProtoktReflect.getField(message, field)
        return if (value is List<*>) {
            value.size
        } else {
            (value as Map<*, *>).size
        }
    }

    override fun hasField(field: FieldDescriptor) =
        ProtoktReflect.getField(message, field) != null

    override fun getField(field: FieldDescriptor) =
        ProtoktObjectValue(
            field,
            ProtoktReflect.getField(message, field)!!,
            descriptorsByFullTypeName
        )
}

class ProtoktMessageValue(
    message: KtMessage,
    private val descriptorsByFullTypeName: Map<String, Descriptor>
) : Value {
    private val message = ProtoktMessageLike(message, descriptorsByFullTypeName)

    override fun messageValue() =
        message

    override fun repeatedValue() =
        emptyList<Value>()

    override fun mapValue() =
        emptyMap<Value, Value>()

    override fun celValue() =
        dynamic(message)

    override fun <T : Any> jvmValue(clazz: Class<T>) =
        null
}

class ProtoktObjectValue(
    private val fieldDescriptor: FieldDescriptor,
    private val value: Any,
    private val descriptorsByFullTypeName: Map<String, Descriptor>
) : Value {
    override fun messageValue() =
        ProtoktMessageLike(value as KtMessage, descriptorsByFullTypeName)

    override fun repeatedValue() =
        (value as List<*>).map { ProtoktObjectValue(fieldDescriptor, it!!, descriptorsByFullTypeName) }

    override fun mapValue(): Map<Value, Value> {
        val input = value as Map<*, *>

        val keyDesc = fieldDescriptor.messageType.findFieldByNumber(1)
        val valDesc = fieldDescriptor.messageType.findFieldByNumber(2)

        return input.entries.associate { (key, value) ->
            Pair(
                ProtoktObjectValue(keyDesc, key!!, descriptorsByFullTypeName),
                ProtoktObjectValue(valDesc, value!!, descriptorsByFullTypeName)
            )
        }
    }

    override fun celValue() =
        when (value) {
            is KtEnum -> value.value
            is UInt -> ULong.valueOf(value.toLong())
            is kotlin.ULong -> ULong.valueOf(value.toLong())
            is KtMessage -> dynamic(ProtoktMessageLike(value, descriptorsByFullTypeName))

            // todo: support Bytes in CEL
            is Bytes -> ByteString.copyFrom(value.asReadOnlyBuffer())

            // pray
            else -> value
        }

    override fun <T : Any> jvmValue(clazz: Class<T>): T =
        when (value) {
            is KtEnum -> value.value
            is UInt -> value.toInt()
            is kotlin.ULong -> value.toLong()

            // pray
            else -> value
        }.let(clazz::cast)
}

private fun dynamic(message: ProtoktMessageLike): Message {
    val descriptor =
        message.descriptorsByFullTypeName
            .getValue(message.message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName)

    return DynamicMessage.newBuilder(descriptor)
        .apply {
            descriptor.fields.forEach { field ->
                if (message.hasField(field)) {
                    setField(
                        field,
                        message.getField(field).jvmValue(Any::class.java).let {
                            when (field.type) {
                                FieldDescriptor.Type.ENUM ->
                                    field.enumType.findValueByNumber(it as Int)

                                FieldDescriptor.Type.MESSAGE ->
                                    dynamic(ProtoktMessageLike(it as KtMessage, message.descriptorsByFullTypeName))

                                else -> it
                            }
                        }
                    )
                }
            }
        }
        .build()
}
