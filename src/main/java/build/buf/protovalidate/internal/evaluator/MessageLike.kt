package build.buf.protovalidate.internal.evaluator

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.OneofDescriptor
import com.google.protobuf.Message
import protokt.v1.KtMessage

interface MessageLike {
    fun getRepeatedFieldCount(field: FieldDescriptor): Int

    fun hasField(field: FieldDescriptor): Boolean

    fun getField(field: FieldDescriptor): Any

    fun getOneofFieldDescriptor(oneof: OneofDescriptor): FieldDescriptor?
}

class ProtobufMessageLike(
    val message: Message
) : MessageLike {
    override fun getRepeatedFieldCount(field: FieldDescriptor) =
        message.getRepeatedFieldCount(field)

    override fun hasField(field: FieldDescriptor) =
        message.hasField(field)

    override fun getField(field: FieldDescriptor) =
        message.getField(field)

    override fun getOneofFieldDescriptor(oneof: OneofDescriptor) =
        message.getOneofFieldDescriptor(oneof)
}

class ProtoktMessageValue(
    val message: KtMessage
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
