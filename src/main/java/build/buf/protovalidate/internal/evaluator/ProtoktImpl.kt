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

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.BytesValue
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.DoubleValue
import com.google.protobuf.DynamicMessage
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.google.protobuf.MapEntry
import com.google.protobuf.Message
import com.google.protobuf.StringValue
import com.google.protobuf.UInt32Value
import com.google.protobuf.UInt64Value
import com.google.protobuf.UnknownFieldSet
import com.google.protobuf.WireFormat
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

    override fun <T : Any> jvmValue(clazz: Class<T>): T? =
        when (value) {
            is KtEnum -> value.value
            is UInt -> value.toInt()
            is kotlin.ULong -> value.toLong()
            is KtMessage -> dynamic(ProtoktMessageLike(value, descriptorsByFullTypeName))

            // todo: support Bytes in CEL
            is Bytes -> ByteString.copyFrom(value.asReadOnlyBuffer())

            // pray
            else -> value
        }?.let(clazz::cast)
}

private fun dynamic(message: ProtoktMessageLike): Message {
    val descriptor =
        message.descriptorsByFullTypeName
            .getValue(message.message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName)

    return DynamicMessage.newBuilder(descriptor)
        .apply {
            val unknownFields = UnknownFieldSet.newBuilder()

            descriptor.fields.forEach { field ->
                if (message.hasField(field)) {
                    val valueToSet =
                        message.getField(field).let { value ->
                            when {
                                field.type == FieldDescriptor.Type.ENUM -> {
                                    if (field.isRepeated) {
                                        val atLeastOneIsUnknown = value.repeatedValue().any { it.jvmValue(Integer::class.java) == null }
                                        if (atLeastOneIsUnknown) {
                                            // DynamicMessage insists that you use an EnumValueDescriptor to set an enum, but if the
                                            // value is unknown then a descriptor doesn't exist.
                                            //
                                            // To preserve list order we have to treat all enums as unknown. Some libraries that use
                                            // reflection won't check unknown fields, e.g. projectnessie's CEL implementation. That
                                            // could be contributed.
                                            value.repeatedValue().forEach {
                                                unknownFields.mergeField(
                                                    field.number,
                                                    UnknownFieldSet.Field.newBuilder()
                                                        .addVarint(it.jvmValue(Integer::class.java)!!.toLong()).build()
                                                )
                                            }

                                            null
                                        } else {
                                            value.repeatedValue().map { field.enumType.findValueByNumber(it.jvmValue(Integer::class.java)!!.toInt()) }
                                        }
                                    } else {
                                        // Some libraries that use reflection won't check unknown fields, e.g.
                                        // projectnessie's CEL implementation. That could be contributed.
                                        val valueIsUnknown = value.jvmValue(Integer::class.java) == null
                                        if (valueIsUnknown) {
                                            unknownFields.addField(
                                                field.number,
                                                UnknownFieldSet.Field.newBuilder()
                                                    .addVarint(value.jvmValue(Integer::class.java)!!.toLong()).build()
                                            )
                                            null
                                        } else {
                                            field.enumType.findValueByNumber(value.jvmValue(Integer::class.java)!!.toInt())
                                        }
                                    }
                                }

                                field.isMapField -> {
                                    val keyDesc = field.messageType.findFieldByNumber(1)
                                    val valDesc = field.messageType.findFieldByNumber(2)
                                    val keyDefault =
                                        if (keyDesc.type == FieldDescriptor.Type.MESSAGE) {
                                            null
                                        } else {
                                            keyDesc.defaultValue
                                        }

                                    val valDefault =
                                        if (valDesc.type == FieldDescriptor.Type.MESSAGE) {
                                            null
                                        } else {
                                            valDesc.defaultValue
                                        }

                                    val defaultEntry =
                                        MapEntry.newDefaultInstance(
                                            field.messageType,
                                            WireFormat.FieldType.valueOf(keyDesc.type.name),
                                            keyDefault,
                                            WireFormat.FieldType.valueOf(valDesc.type.name),
                                            valDefault,
                                        ) as MapEntry<Any?, Any?>

                                    (value.mapValue()).map { (k, v) ->
                                        defaultEntry.toBuilder()
                                            .setKey(k.jvmValue(Any::class.java))
                                            .setValue(v.jvmValue(Any::class.java))
                                            .build()
                                    }
                                }

                                field.isRepeated ->
                                    value.repeatedValue().map { it.jvmValue(Any::class.java) }

                                field.type == FieldDescriptor.Type.MESSAGE -> {
                                    // todo: proper wrapper type registry
                                    when (field.messageType.fullName) {
                                        "google.protobuf.DoubleValue" ->
                                            DoubleValue.newBuilder().setValue(value.jvmValue(java.lang.Double::class.java)!!.toDouble()).build()
                                        "google.protobuf.FloatValue" ->
                                            FloatValue.newBuilder().setValue(value.jvmValue(java.lang.Float::class.java)!!.toFloat()).build()
                                        "google.protobuf.Int64Value" ->
                                            Int64Value.newBuilder().setValue(value.jvmValue(java.lang.Long::class.java)!!.toLong()).build()
                                        "google.protobuf.UInt64Value" ->
                                            UInt64Value.newBuilder().setValue(value.jvmValue(java.lang.Long::class.java)!!.toLong()).build()
                                        "google.protobuf.Int32Value" ->
                                            Int32Value.newBuilder().setValue(value.jvmValue(Integer::class.java)!!.toInt()).build()
                                        "google.protobuf.UInt32Value" ->
                                            UInt32Value.newBuilder().setValue(value.jvmValue(Integer::class.java)!!.toInt()).build()
                                        "google.protobuf.BoolValue" ->
                                            BoolValue.newBuilder().setValue(value.jvmValue(java.lang.Boolean::class.java)!!.booleanValue()).build()
                                        "google.protobuf.StringValue" ->
                                            StringValue.newBuilder().setValue(value.jvmValue(String::class.java)).build()
                                        "google.protobuf.BytesValue" ->
                                            BytesValue.newBuilder().setValue(value.jvmValue(ByteString::class.java)).build()
                                        else -> value.jvmValue(Any::class.java)
                                    }
                                }

                                else -> value.jvmValue(Any::class.java)
                            }
                        }

                    if (valueToSet != null) {
                        setField(field, valueToSet)
                    }
                }
            }

            setUnknownFields(unknownFields.build())
        }
        .build()
}

fun toDynamicMessage(message: KtMessage, descriptorsByFullTypeName: Map<String, Descriptor>) =
    dynamic(ProtoktMessageLike(message, descriptorsByFullTypeName))
