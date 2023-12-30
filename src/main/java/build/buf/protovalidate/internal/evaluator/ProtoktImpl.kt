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
import com.google.protobuf.WireFormat
import protokt.v1.Bytes
import protokt.v1.Converter
import protokt.v1.KtEnum
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import kotlin.reflect.full.findAnnotation

class ProtoktMessageLike(
    val message: KtMessage,
    val context: ProtoktRuntimeContext
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
            context
        )
}

class ProtoktMessageValue(
    private val message: KtMessage,
    private val context: ProtoktRuntimeContext
) : Value {
    override fun messageValue() =
        ProtoktMessageLike(message, context)

    override fun repeatedValue() =
        emptyList<Value>()

    override fun mapValue() =
        emptyMap<Value, Value>()

    override fun celValue() =
        context.protobufJavaValue(message)

    override fun <T : Any> jvmValue(clazz: Class<T>) =
        null
}

class ProtoktObjectValue(
    private val fieldDescriptor: FieldDescriptor,
    private val value: Any,
    private val context: ProtoktRuntimeContext
) : Value {
    override fun messageValue() =
        ProtoktMessageLike(value as KtMessage, context)

    override fun repeatedValue() =
        (value as List<*>).map { ProtoktObjectValue(fieldDescriptor, it!!, context) }

    override fun mapValue(): Map<Value, Value> {
        val input = value as Map<*, *>

        val keyDesc = fieldDescriptor.messageType.findFieldByNumber(1)
        val valDesc = fieldDescriptor.messageType.findFieldByNumber(2)

        return input.entries.associate { (key, value) ->
            Pair(
                ProtoktObjectValue(keyDesc, key!!, context),
                ProtoktObjectValue(valDesc, value!!, context)
            )
        }
    }

    override fun celValue() =
        when (value) {
            is KtEnum -> value.value
            is UInt -> org.projectnessie.cel.common.ULong.valueOf(value.toLong())
            is ULong -> org.projectnessie.cel.common.ULong.valueOf(value.toLong())
            is KtMessage -> context.protobufJavaValue(value)

            // todo: support Bytes in CEL
            is Bytes -> ByteString.copyFrom(value.asReadOnlyBuffer())

            // pray
            else -> value
        }

    override fun <T : Any> jvmValue(clazz: Class<T>): T? =
        when (value) {
            is KtEnum -> value.value
            is UInt -> value.toInt()
            is ULong -> value.toLong()
            is KtMessage -> context.protobufJavaValue(value)

            // todo: support Bytes in CEL
            is Bytes -> ByteString.copyFrom(value.asReadOnlyBuffer())

            // pray
            else -> value
        }?.let(clazz::cast)
}

class ProtoktRuntimeContext(
    val descriptorsByFullTypeName: Map<String, Descriptor>,
    val convertersByFullTypeName: Map<String, Converter<*, *>>
) {
    fun protobufJavaValue(value: Any?) =
        when (value) {
            is KtEnum -> value.value
            is UInt -> value.toInt()
            is ULong -> value.toLong()
            is KtMessage -> toDynamicMessage(value, this)

            // todo: support Bytes in CEL
            is Bytes -> ByteString.copyFrom(value.asReadOnlyBuffer())

            // pray
            else -> value
        }

    companion object {
        val DEFAULT_CONVERTERS: Map<String, Converter<*, *>> =
            mapOf(

            )
    }
}

fun toDynamicMessage(message: KtMessage, context: ProtoktRuntimeContext): Message {
    val descriptor =
        context.descriptorsByFullTypeName
            .getValue(message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName)

    return DynamicMessage.newBuilder(descriptor)
        .apply {
            descriptor.fields.forEach { field ->
                ProtoktReflect.getField(message, field)?.let { value ->
                    setField(
                        field,
                        when {
                            field.type == FieldDescriptor.Type.ENUM ->
                                if (field.isRepeated) {
                                    (value as List<*>).map { field.enumType.findValueByNumberCreatingIfUnknown(((it as KtEnum).value)) }
                                } else {
                                    field.enumType.findValueByNumberCreatingIfUnknown(((value as KtEnum).value))
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

                                (value as Map<*, *>).map { (k, v) ->
                                    defaultEntry.toBuilder()
                                        .setKey(context.protobufJavaValue(k))
                                        .setValue(context.protobufJavaValue(v))
                                        .build()
                                }
                            }

                            field.isRepeated ->
                                (value as List<*>).map(context::protobufJavaValue)

                            field.type == FieldDescriptor.Type.MESSAGE -> {
                                /*
                                ProtoktRuntimeContext.DEFAULT_CONVERTERS[field.messageType.fullName]?.let {
                                    @Suppress("UNCHECKED_CAST")
                                    val coerced = it as Converter<Any, Any>
                                    coerced.unwrap(value)
                                } ?: context.protobufJavaValue(value)

                                 */
                                // todo: proper wrapper type registry
                                when (field.messageType.fullName) {
                                    "google.protobuf.DoubleValue" ->
                                        DoubleValue.newBuilder().setValue((value as Double).toDouble()).build()
                                    "google.protobuf.FloatValue" ->
                                        FloatValue.newBuilder().setValue((value as Float).toFloat()).build()
                                    "google.protobuf.Int64Value" ->
                                        Int64Value.newBuilder().setValue((value as Long).toLong()).build()
                                    "google.protobuf.UInt64Value" ->
                                        UInt64Value.newBuilder().setValue((value as ULong).toLong()).build()
                                    "google.protobuf.Int32Value" ->
                                        Int32Value.newBuilder().setValue((value as Int).toInt()).build()
                                    "google.protobuf.UInt32Value" ->
                                        UInt32Value.newBuilder().setValue((value as UInt).toInt()).build()
                                    "google.protobuf.BoolValue" ->
                                        BoolValue.newBuilder().setValue((value as Boolean)).build()
                                    "google.protobuf.StringValue" ->
                                        StringValue.newBuilder().setValue(value as String).build()
                                    "google.protobuf.BytesValue" ->
                                        BytesValue.newBuilder().setValue(context.protobufJavaValue(value) as ByteString).build()
                                    else -> context.protobufJavaValue(value)
                                }
                            }

                            else -> context.protobufJavaValue(value)
                        }
                    )
                }
            }
        }
        // todo: transform unknown fields
        .build()
}
