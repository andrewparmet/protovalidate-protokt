package build.buf.protovalidate.internal.evaluator

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.Type
import com.google.protobuf.Descriptors.OneofDescriptor
import com.google.protobuf.Message
import org.projectnessie.cel.common.ULong
import protokt.v1.KtEnum
import protokt.v1.KtMessage
import protokt.v1.KtProperty
import protokt.v1.google.protobuf.Empty
import kotlin.reflect.KClass
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

    override fun getRepeatedFieldCount(field: FieldDescriptor): Int {
        val value = getTopLevelFieldGetter<Any>(field)!!.get(message)
        return if (value is List<*>) {
            value.size
        } else {
            (value as Map<*, *>).size
        }
    }

    override fun hasField(field: FieldDescriptor) =
        (getStandardField(field) ?: getOneofField(field)) != null

    override fun hasField(oneof: OneofDescriptor): Boolean {
        val fieldNumbers = oneof.fields.map { it.number }.toSet()

        val oneofSealedClasses =
            message::class
                .nestedClasses
                .filter { it.isSealed && !it.isSubclassOf(KtEnum::class) }

        val classWithProperty =
            oneofSealedClasses.single { sealedClass ->
                val dataClasses = sealedClass.nestedClasses
                dataClasses.flatMap { getTopLevelFieldGetters<Any>(it, fieldNumbers::contains) }.any()
            }

        return message::class
            .declaredMemberProperties
            .single { it.returnType.classifier == classWithProperty }
            .let {
                @Suppress("UNCHECKED_CAST")
                it as KProperty1<KtMessage, Any?>
            }
            .get(message) != null
    }

    private fun getStandardField(field: FieldDescriptor) =
        getTopLevelFieldGetter<Any?>(field)?.get(message)

    private fun getOneofField(field: FieldDescriptor): Any? {
        val oneofSealedClasses =
            message::class
                .nestedClasses
                .filter { it.isSealed && !it.isSubclassOf(KtEnum::class) }

        val classWithProperty =
            oneofSealedClasses.singleOrNull { sealedClass ->
                val dataClasses = sealedClass.nestedClasses
                dataClasses.flatMap { getTopLevelFieldGetters<Any>(it, field.number::equals) }.any()
            } ?: return null

        return message::class
            .declaredMemberProperties
            .single { it.returnType.classifier == classWithProperty }
            .let {
                @Suppress("UNCHECKED_CAST")
                it as KProperty1<KtMessage, Any?>
            }
            .get(message) != null
    }

    override fun getField(field: FieldDescriptor) =
        getStandardField(field) ?: getOneofField(field)!!

    private fun <T> getTopLevelFieldGetter(fieldDescriptor: FieldDescriptor): KProperty1<KtMessage, T>? =
        getTopLevelFieldGetters<T>(message::class, fieldDescriptor.number::equals).singleOrNull()

    private fun <T> getTopLevelFieldGetters(klass: KClass<*>, condition: (Int) -> Boolean): List<KProperty1<KtMessage, T>> =
        klass
            .declaredMemberProperties
            .filter {
                val annotation = it.findAnnotation<KtProperty>()
                annotation != null && condition(annotation.number)
            }
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

    override fun enumValue() =
        -1
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
            clazz.cast(
                ULong.valueOf(
                    when (type) {
                        Type.UINT32 -> (value as UInt).toLong()
                        Type.UINT64 -> (value as kotlin.ULong).toLong()
                        Type.FIXED32 -> (value as UInt).toLong()
                        Type.FIXED64 -> (value as kotlin.ULong).toLong()
                        else -> error("unsupported unsigned conversion: $type")
                    }
                )
            )
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
        val input = value as Map<*, *>

        val keyDesc = fieldDescriptor.messageType.findFieldByNumber(1)
        val valDesc = fieldDescriptor.messageType.findFieldByNumber(2)

        return input.entries.associate { (key, value) ->
            ProtoktObjectValue(keyDesc, key!!) to ProtoktObjectValue(valDesc, value!!)
        }
    }

    override fun enumValue() =
        (value as KtEnum).value
}
