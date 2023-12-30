package build.buf.protovalidate.internal.evaluator

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.BytesValue
import com.google.protobuf.Descriptors
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

class ProtoktRuntimeContext(
    val descriptorsByFullTypeName: Map<String, Descriptors.Descriptor>,
    val convertersByFullTypeName: Map<String, Converter<*, *>>,
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
            mapOf()
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

                            field.isMapField -> {
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

                                (value as Map<*, *>).map { (k, v) ->
                                    defaultEntry.toBuilder()
                                        .setKey(context.protobufJavaValue(k))
                                        .setValue(context.protobufJavaValue(v))
                                        .build()
                                }
                            }

                            field.isRepeated ->
                                (value as List<*>).map(context::protobufJavaValue)

                            field.type == Descriptors.FieldDescriptor.Type.MESSAGE -> {
                                /*
                                ProtoktRuntimeContext.DEFAULT_CONVERTERS[field.messageType.fullName]?.let {
                                    @Suppress("UNCHECKED_CAST")
                                    val coerced = it as Converter<Any, Any>
                                    coerced.unwrap(value)
                                } ?: context.protobufJavaValue(value)

                                todo: proper wrapper type registry
                                 */
                                when (field.messageType.fullName) {
                                    "google.protobuf.DoubleValue" ->
                                        DoubleValue.newBuilder().setValue(value as Double).build()
                                    "google.protobuf.FloatValue" ->
                                        FloatValue.newBuilder().setValue(value as Float).build()
                                    "google.protobuf.Int64Value" ->
                                        Int64Value.newBuilder().setValue(value as Long).build()
                                    "google.protobuf.UInt64Value" ->
                                        UInt64Value.newBuilder().setValue((value as ULong).toLong()).build()
                                    "google.protobuf.Int32Value" ->
                                        Int32Value.newBuilder().setValue(value as Int).build()
                                    "google.protobuf.UInt32Value" ->
                                        UInt32Value.newBuilder().setValue((value as UInt).toInt()).build()
                                    "google.protobuf.BoolValue" ->
                                        BoolValue.newBuilder().setValue(value as Boolean).build()
                                    "google.protobuf.StringValue" ->
                                        StringValue.newBuilder().setValue(value as String).build()
                                    "google.protobuf.BytesValue" ->
                                        BytesValue.newBuilder().setValue(context.protobufJavaValue(value) as ByteString).build()
                                    else -> context.protobufJavaValue(value)
                                }
                            }

                            else -> context.protobufJavaValue(value)
                        },
                    )
                }
            }
        }
        // todo: transform unknown fields
        .build()
}
