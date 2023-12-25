package build.buf.protovalidate.internal.evaluator

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.OneofDescriptor
import com.google.protobuf.Message
import protokt.v1.KtMessage

interface MessageLike {
    fun newObjectValue(fieldDescriptor: FieldDescriptor, fieldValue: Any): Value

    fun getRepeatedFieldCount(field: FieldDescriptor): Int

    fun hasField(field: FieldDescriptor): Boolean

    fun getField(field: FieldDescriptor): Any

    fun getOneofFieldDescriptor(oneof: OneofDescriptor): FieldDescriptor?
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

    override fun getField(field: FieldDescriptor) =
        message.getField(field)

    override fun getOneofFieldDescriptor(oneof: OneofDescriptor) =
        message.getOneofFieldDescriptor(oneof)
}

class ProtoktMessageLike(
    val message: KtMessage
) : MessageLike {
    override fun newObjectValue(fieldDescriptor: FieldDescriptor, fieldValue: Any): Value {
        TODO("Not yet implemented")
    }

    override fun getRepeatedFieldCount(field: FieldDescriptor): Int {
        TODO("Not yet implemented")
    }

    override fun hasField(field: FieldDescriptor): Boolean {
        TODO("Not yet implemented")
    }

    override fun getField(field: FieldDescriptor): Any {
        TODO("Not yet implemented")
    }

    override fun getOneofFieldDescriptor(oneof: OneofDescriptor): FieldDescriptor? {
        TODO("Not yet implemented")
    }
}

class ProtoktMessageValue(
    message: KtMessage
) : Value {
    private val message = ProtoktMessageLike(message)
    
    override fun messageValue() =
        message

    override fun <T : Any?> value(clazz: Class<T>) =
        clazz.cast(message.message)

    override fun repeatedValue() =
        emptyList<Value>()

    override fun mapValue() =
        emptyMap<Value, Value>()
}

class ProtoktObjectValue(
    private val field: FieldDescriptor,
    private val value: Any
) : Value {
    override fun messageValue(): MessageLike? {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> value(clazz: Class<T>?): T {
        TODO("Not yet implemented")
    }

    override fun repeatedValue(): MutableList<Value> {
        TODO("Not yet implemented")
    }

    override fun mapValue(): MutableMap<Value, Value> {
        TODO("Not yet implemented")
    }
}
