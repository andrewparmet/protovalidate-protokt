package build.buf.protovalidate.internal.evaluator

import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FieldDescriptor
import protokt.v1.KtMessage

interface MessageLike {
    fun getRepeatedFieldCount(descriptor: Descriptors.FieldDescriptor): Int {
        TODO()
    }

    fun hasField(descriptor: Descriptors.FieldDescriptor): Boolean {
        TODO()
    }

    fun getField(descriptor: Descriptors.FieldDescriptor): Any {
        TODO()
    }

    fun getOneofFieldDescriptor(descriptor: Descriptors.OneofDescriptor): FieldDescriptor? {
        TODO()
    }
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
