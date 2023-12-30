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

import build.buf.protovalidate.internal.evaluator.ProtoktRuntimeContext.Companion.DEFAULT_CONVERTERS
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.MapEntry
import com.google.protobuf.Message
import com.google.protobuf.UnknownFieldSet
import com.google.protobuf.UnknownFieldSet.Field
import com.google.protobuf.UnsafeByteOperations
import com.google.protobuf.WireFormat
import com.toasttab.protokt.v1.ProtoktProtos
import protokt.v1.Bytes
import protokt.v1.Converter
import protokt.v1.KtEnum
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import protokt.v1.google.protobuf.BoolValueConverter
import protokt.v1.google.protobuf.BytesValueConverter
import protokt.v1.google.protobuf.DoubleValueConverter
import protokt.v1.google.protobuf.FloatValueConverter
import protokt.v1.google.protobuf.Int32ValueConverter
import protokt.v1.google.protobuf.Int64ValueConverter
import protokt.v1.google.protobuf.StringValueConverter
import protokt.v1.google.protobuf.UInt32ValueConverter
import protokt.v1.google.protobuf.UInt64ValueConverter
import kotlin.reflect.full.findAnnotation

class ProtoktRuntimeContext(
    val descriptorsByFullTypeName: Map<String, Descriptors.Descriptor>,
    converters: Iterable<Converter<*, *>>
) {
    private val convertersByWrappedType = converters.associateBy { it.wrapper }


    fun protobufJavaValue(value: Any?) =
        when (value) {
            is KtEnum -> value.value
            is UInt -> value.toInt()
            is ULong -> value.toLong()
            is KtMessage -> toDynamicMessage(value, this)
            is Bytes -> UnsafeByteOperations.unsafeWrap(value.asReadOnlyBuffer())

            // pray
            else -> value
        }

    @Suppress("UNCHECKED_CAST")
    fun unwrap(
        value: Any,
        field: FieldDescriptor,
    ) = ((DEFAULT_CONVERTERS[field.messageType.fullName] ?: convertersByWrappedType.getValue(value::class)) as Converter<Any, Any>).unwrap(value)

    companion object {
        val DEFAULT_CONVERTERS: Map<String, Converter<*, *>> =
            mapOf(
                "google.protobuf.DoubleValue" to DoubleValueConverter,
                "google.protobuf.FloatValue" to FloatValueConverter,
                "google.protobuf.Int64Value" to Int64ValueConverter,
                "google.protobuf.UInt64Value" to UInt64ValueConverter,
                "google.protobuf.Int32Value" to Int32ValueConverter,
                "google.protobuf.UInt32Value" to UInt32ValueConverter,
                "google.protobuf.BoolValue" to BoolValueConverter,
                "google.protobuf.StringValue" to StringValueConverter,
                "google.protobuf.BytesValue" to BytesValueConverter,
            )
    }
}

private fun toDynamicMessage(
    message: KtMessage,
    context: ProtoktRuntimeContext,
): Message {
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
                            field.type == Descriptors.FieldDescriptor.Type.ENUM ->
                                if (field.isRepeated) {
                                    (value as List<*>).map { field.enumType.findValueByNumberCreatingIfUnknown(((it as KtEnum).value)) }
                                } else {
                                    field.enumType.findValueByNumberCreatingIfUnknown(((value as KtEnum).value))
                                }

                            field.isMapField ->
                                convertMap(value, field, context)

                            field.isRepeated ->
                                (value as List<*>).map(context::protobufJavaValue)

                            isWrapped(field) ->
                                context.protobufJavaValue(context.unwrap(value, field))

                            else -> context.protobufJavaValue(value)
                        },
                    )
                }
            }
        }
        .setUnknownFields(mapUnknownFields(message))
        .build()
}

private fun isWrapped(field: FieldDescriptor): Boolean {
    val options = field.toProto().options.getExtension(ProtoktProtos.property)
    return options.wrap.isNotEmpty() ||
        options.keyWrap.isNotEmpty() ||
        options.valueWrap.isNotEmpty() ||
        (field.type == FieldDescriptor.Type.MESSAGE && field.messageType.fullName in DEFAULT_CONVERTERS)
}

private fun convertMap(
    value: Any,
    field: FieldDescriptor,
    context: ProtoktRuntimeContext,
): List<MapEntry<*, *>> {
    val keyDesc = field.messageType.findFieldByNumber(1)
    val valDesc = field.messageType.findFieldByNumber(2)
    val keyDefault =
        if (keyDesc.type == Descriptors.FieldDescriptor.Type.MESSAGE) {
            null
        } else {
            keyDesc.defaultValue
        }

    val valDefault =
        if (valDesc.type == Descriptors.FieldDescriptor.Type.MESSAGE) {
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

    return (value as Map<*, *>).map { (k, v) ->
        defaultEntry.toBuilder()
            .setKey(context.protobufJavaValue(k))
            .setValue(context.protobufJavaValue(v))
            .build()
    }
}

private fun mapUnknownFields(message: KtMessage): UnknownFieldSet {
    val unknownFields = UnknownFieldSet.newBuilder()

    getUnknownFields(message).forEach { (number, field) ->
        unknownFields.mergeField(
            number.toInt(),
            Field.newBuilder()
                .apply {
                    field.varint.forEach { addVarint(it.value.toLong()) }
                    field.fixed32.forEach { addFixed32(it.value.toInt()) }
                    field.fixed64.forEach { addFixed64(it.value.toLong()) }
                    field.lengthDelimited.forEach {
                        addLengthDelimited(
                            UnsafeByteOperations.unsafeWrap(it.value.asReadOnlyBuffer()),
                        )
                    }
                }
                .build(),
        )
    }

    return unknownFields.build()
}
