package build.buf.protovalidate.internal.evaluator

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.Type
import com.google.protobuf.Descriptors.OneofDescriptor
import com.google.protobuf.Message
import org.projectnessie.cel.common.ULong
import protokt.v1.KtEnum
import protokt.v1.KtMessage
import protokt.v1.KtProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

interface MessageLike {
    fun newObjectValue(fieldDescriptor: FieldDescriptor, fieldValue: Any): Value

    fun getRepeatedFieldCount(field: FieldDescriptor): Int

    fun hasField(field: FieldDescriptor): Boolean

    fun hasField(oneof: OneofDescriptor): Boolean

    fun getField(field: FieldDescriptor): Any
}

class ProtobufMessageLike(
    val message: Message
) : MessageLike {
    override fun newObjectValue(fieldDescriptor: FieldDescriptor, fieldValue: Any) =
        ProtobufObjectValue(fieldDescriptor, fieldValue)

    override fun getRepeatedFieldCount(field: FieldDescriptor) =
        message.getRepeatedFieldCount(field)

    override fun hasField(field: FieldDescriptor) =
        message.hasField(field)

    override fun hasField(oneof: OneofDescriptor) =
        message.getOneofFieldDescriptor(oneof) != null

    override fun getField(field: FieldDescriptor) =
        message.getField(field)
}

class ProtoktMessageLike(
    val message: KtMessage
) : MessageLike {
    override fun newObjectValue(fieldDescriptor: FieldDescriptor, fieldValue: Any) =
        ProtoktObjectValue(fieldDescriptor, fieldValue)

    override fun getRepeatedFieldCount(field: FieldDescriptor) =
        getTopLevelFieldGetter<Collection<*>>(field).get(message).size

    override fun hasField(field: FieldDescriptor) =
        getTopLevelFieldGetter<Any?>(field).get(message) != null

    override fun hasField(oneof: OneofDescriptor): Boolean {
        val fieldNumbers = oneof.fields.map { it.number }.toSet()

        return message::class
            .nestedClasses
            .filter { it.isSealed && !it.isSubclassOf(KtEnum::class) }
            .flatMap { it.nestedClasses }
            .let { getTopLevelFieldGetters<Any?> { it in fieldNumbers } }
            .mapNotNull { it.get(message) }
            .any()
    }

    override fun getField(field: FieldDescriptor) =
        getTopLevelFieldGetter<Any>(field).get(message)

    private fun <T> getTopLevelFieldGetter(fieldDescriptor: FieldDescriptor): KProperty1<KtMessage, T> =
        getTopLevelFieldGetters<T> { it == fieldDescriptor.number }.single()

    private fun <T> getTopLevelFieldGetters(condition: (Int) -> Boolean): List<KProperty1<KtMessage, T>> =
        message::class
            .declaredMemberProperties
            .filter { condition(it.findAnnotation<KtProperty>()!!.number) }
            .map {
                @Suppress("UNCHECKED_CAST")
                it as KProperty1<KtMessage, T>
            }
}

class ProtoktMessageValue(
    message: KtMessage
) : Value {
    private val message = ProtoktMessageLike(message)

    override fun messageValue() =
        message

    override fun <T : Any> value(clazz: Class<T>) =
        clazz.cast(message.message)

    override fun repeatedValue() =
        emptyList<Value>()

    override fun mapValue() =
        emptyMap<Value, Value>()
}

class ProtoktObjectValue(
    private val fieldDescriptor: FieldDescriptor,
    private val value: Any
) : Value {
    override fun messageValue() =
        if (fieldDescriptor.type == Type.MESSAGE) {
            ProtoktMessageLike(value as KtMessage)
        } else {
            null
        }

    override fun <T : Any> value(clazz: Class<T>): T {
        val type = fieldDescriptor.type

        return if (
            !fieldDescriptor.isRepeated &&
            type in setOf(Type.UINT32, Type.UINT64, Type.FIXED32, Type.FIXED64)
        ) {
            clazz.cast(ULong.valueOf((value as Number).toLong()))
        } else {
            clazz.cast(value)
        }
    }

    override fun repeatedValue() =
        if (fieldDescriptor.isRepeated) {
            (value as List<*>).map { ProtoktObjectValue(fieldDescriptor, it!!) }
        } else {
            emptyList<Value>()
        }

    override fun mapValue(): Map<Value, Value> {
        @Suppress("UNCHECKED_CAST")
        val input = (value as? List<KtMessage>) ?: listOf(value as KtMessage)

        val keyDesc = fieldDescriptor.messageType.findFieldByNumber(1)
        val valDesc = fieldDescriptor.messageType.findFieldByNumber(2)

        return input.associate {
            val keyValue = ProtoktMessageLike(it).getField(keyDesc)
            val keyProtoktValue = ProtoktObjectValue(keyDesc, keyValue)

            val valValue = ProtoktMessageLike(it).getField(valDesc)
            val valProtoktValue = ProtoktObjectValue(valDesc, valValue)

            keyProtoktValue to valProtoktValue
        }
    }
}
