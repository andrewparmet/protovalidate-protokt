package build.buf.protovalidate.conformance

import io.github.classgraph.ClassGraph
import protokt.v1.KtDeserializer
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import protokt.v1.google.protobuf.Empty
import java.io.InputStream
import kotlin.reflect.full.findAnnotation

object DynamicConcreteKtMessageDeserializer {
    private val deserializersByFullTypeName: Map<String, KtDeserializer<*>> by lazy {
        ClassGraph()
            .enableAllInfo()
            .scan()
            .getClassesWithAnnotation(KtGeneratedMessage::class.java)
            .asSequence()
            .map { it.loadClass().kotlin }
            .associate { messageClass ->
                messageClass.findAnnotation<KtGeneratedMessage>()!!.fullTypeName to
                    messageClass
                        .nestedClasses
                        .single { it.simpleName == Empty.Deserializer::class.simpleName }
                        .objectInstance as KtDeserializer<*>
            }

    }

    @JvmStatic
    fun parse(fullTypeName: String, bytes: InputStream): KtMessage =
        deserializersByFullTypeName.getValue(fullTypeName).deserialize(bytes)
}
