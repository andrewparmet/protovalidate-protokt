package build.buf.protovalidate

import build.buf.protovalidate.internal.celext.ValidateLibrary
import build.buf.protovalidate.internal.evaluator.Evaluator
import build.buf.protovalidate.internal.evaluator.EvaluatorBuilder
import build.buf.protovalidate.internal.evaluator.ProtoktMessageValue
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import org.projectnessie.cel.Env
import org.projectnessie.cel.Library
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import protokt.v1.google.protobuf.FileDescriptor
import java.util.concurrent.ConcurrentHashMap
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

    fun load(descriptor: FileDescriptor) {
        descriptor
            .toProtobufJavaDescriptor()
            .messageTypes
            .forEach {
                evaluatorsByFullTypeName[it.fullName] = evaluatorBuilder.load(it)
            }
    }

    private fun FileDescriptor.toProtobufJavaDescriptor(): Descriptors.FileDescriptor =
        Descriptors.FileDescriptor.buildFrom(
            DescriptorProtos.FileDescriptorProto.parseFrom(proto.serialize()),
            dependencies.map { it.toProtobufJavaDescriptor() }.toTypedArray(),
            true
        )

    fun validate(message: KtMessage): ValidationResult =
        evaluatorsByFullTypeName.getValue(
            message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName
        ).evaluate(ProtoktMessageValue(message), failFast)
}
