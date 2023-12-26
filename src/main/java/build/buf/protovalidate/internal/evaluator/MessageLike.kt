package build.buf.protovalidate.internal.evaluator

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.Type
import com.google.protobuf.Descriptors.OneofDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import org.projectnessie.cel.common.ULong
import protokt.v1.Bytes
import protokt.v1.KtEnum
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import protokt.v1.KtProperty
import protokt.v1.google.protobuf.Duration
import protokt.v1.google.protobuf.Timestamp
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
    val message: KtMessage,
    private val descriptorsByFullTypeName: Map<String, Descriptor>
) : MessageLike {
    override fun newObjectValue(fieldDescriptor: FieldDescriptor, fieldValue: Any) =
        ProtoktObjectValue(fieldDescriptor, fieldValue, descriptorsByFullTypeName)

    override fun getRepeatedFieldCount(field: FieldDescriptor): Int {
        val value = getTopLevelFieldGetter<Any>(field)!!.get(message)
        return if (value is List<*>) {
            value.size
        } else {
            (value as Map<*, *>).size
        }
    }

    // todo: protokt needs to track presence of scalars with a private backing field
    // bring this up with buf team, as this is not required by the proto3 presence tracking spec:
    // https://github.com/protocolbuffers/protobuf/blob/a4576cb8208ea8f1c4b05b9bb35533201e301171/docs/field_presence.md?plain=1#L103
    /*
        class Foo(
            private val _possiblyNotThere: Int?
        ) {
            // presence tracking via checking property `_possiblyNotThere`.
            // idk what happens on later JDKs if the backing property is null
            val possiblyNotThere = _possiblyNotThere ?: 0
        }
     */
    override fun hasField(field: FieldDescriptor) =
        (getStandardField(field) ?: getOneofField(field)) != null

    // todo: can maybe be unified with getOneofField
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
            oneofSealedClasses.firstOrNull { sealedClass ->
                val dataClasses = sealedClass.nestedClasses
                dataClasses.flatMap { getTopLevelFieldGetters<Any>(it, field.number::equals) }.any()
            } ?: return null

        val dataClassInstance =
            message::class
                .declaredMemberProperties
                .single { it.returnType.classifier == classWithProperty }
                .let {
                    @Suppress("UNCHECKED_CAST")
                    it as KProperty1<KtMessage, Any?>
                }
                .get(message) ?: return null

        val dataClassProperty =
            dataClassInstance::class
                .declaredMemberProperties
                .single()
                .let {
                    @Suppress("UNCHECKED_CAST")
                    it as KProperty1<Any, Any?>
                }

        return if (dataClassProperty.findAnnotation<KtProperty>()!!.number == field.number) {
            dataClassProperty.get(dataClassInstance)
        } else {
            null
        }
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
    message: KtMessage,
    private val descriptorsByFullTypeName: Map<String, Descriptor>
) : Value {
    private val message = ProtoktMessageLike(message, descriptorsByFullTypeName)

    override fun messageValue() =
        message

    override fun value() =
        message.message

    override fun repeatedValue() =
        emptyList<Value>()

    override fun mapValue() =
        emptyMap<Value, Value>()

    override fun enumValue() =
        -1

    override fun bindingValue() =
        dynamic(message.message, descriptorsByFullTypeName)
}

class ProtoktObjectValue(
    private val fieldDescriptor: FieldDescriptor,
    private val value: Any,
    private val descriptorsByFullTypeName: Map<String, Descriptor>
) : Value {
    override fun messageValue() =
        if (fieldDescriptor.type == Type.MESSAGE) {
            ProtoktMessageLike(value as KtMessage, descriptorsByFullTypeName)
        } else {
            null
        }

    override fun value() =
        bindingValue()

    override fun repeatedValue() =
        if (fieldDescriptor.isRepeated) {
            (value as List<*>).map { ProtoktObjectValue(fieldDescriptor, it!!, descriptorsByFullTypeName) }
        } else {
            emptyList<Value>()
        }

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

    override fun enumValue() =
        (value as KtEnum).value

    override fun bindingValue() =
        when (value) {
            is KtEnum -> value.value
            is UInt -> ULong.valueOf(value.toLong())
            is kotlin.ULong -> ULong.valueOf(value.toLong())
            is Bytes -> ByteString.copyFrom(value.asReadOnlyBuffer())
            is Timestamp -> com.google.protobuf.Timestamp.newBuilder().setSeconds(value.seconds).setNanos(value.nanos).build()
            is Duration -> com.google.protobuf.Duration.newBuilder().setSeconds(value.seconds).setNanos(value.nanos).build()
            is KtMessage -> dynamic(value, descriptorsByFullTypeName)

            // pray
            else -> value
        }
}

// todo: implement protokt support for CEL
private fun dynamic(message: KtMessage, descriptorsByFullTypeName: Map<String, Descriptor>): Message {
    System.err.println("dynamically rebuilding $message")
    return DynamicMessage.newBuilder(descriptorsByFullTypeName.getValue(message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName))
        .mergeFrom(message.serialize())
        .build()
}
