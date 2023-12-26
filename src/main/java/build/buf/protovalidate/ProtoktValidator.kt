package build.buf.protovalidate

import build.buf.protovalidate.exceptions.ValidationException
import build.buf.protovalidate.internal.celext.ValidateLibrary
import build.buf.protovalidate.internal.evaluator.Evaluator
import build.buf.protovalidate.internal.evaluator.EvaluatorBuilder
import build.buf.protovalidate.internal.evaluator.ProtoktMessageValue
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.Descriptor
import org.projectnessie.cel.Env
import org.projectnessie.cel.Library
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import protokt.v1.google.protobuf.FileDescriptor
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Throws
import kotlin.reflect.full.findAnnotation

class ProtoktValidator(
    config: Config = Config.newBuilder().build()
) {
    private val evaluatorBuilder =
        EvaluatorBuilder(
            Env.newEnv(Library.Lib(ValidateLibrary())),
            config.isDisableLazy
        )

    private val failFast = config.isFailFast

    private val evaluatorsByFullTypeName = ConcurrentHashMap<String, Evaluator>()
    private val descriptorsByFullTypeName = ConcurrentHashMap<String, Descriptor>()

    fun load(descriptor: FileDescriptor) {
        descriptor
            .toProtobufJavaDescriptor()
            .messageTypes
            .forEach(::load)
    }

    @Throws(ValidationException::class)
    fun load(descriptor: Descriptor) {
        try {
            evaluatorsByFullTypeName[descriptor.fullName] = evaluatorBuilder.load(descriptor)
            descriptorsByFullTypeName[descriptor.fullName] = descriptor
        } catch (ex: Exception) {
            System.err.println("error while loading ${descriptor.fullName}; this may be deliberate")
        }
        descriptor.nestedTypes.forEach(::load)
    }

    private fun FileDescriptor.toProtobufJavaDescriptor(): Descriptors.FileDescriptor =
        Descriptors.FileDescriptor.buildFrom(
            DescriptorProtos.FileDescriptorProto.parseFrom(proto.serialize()),
            dependencies.map { it.toProtobufJavaDescriptor() }.toTypedArray(),
            true
        )

    @Throws(ValidationException::class)
    fun validate(message: KtMessage): ValidationResult =
        evaluatorsByFullTypeName.getValue(
            message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName
        ).evaluate(
            ProtoktMessageValue(
                message,
                descriptorsByFullTypeName
            ),
            failFast
        )
}
