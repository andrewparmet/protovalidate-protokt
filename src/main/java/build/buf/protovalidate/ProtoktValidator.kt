// Copyright 2023 Buf Technologies, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
    fun load(descriptor: Descriptor, message: KtMessage? = null) {
        descriptorsByFullTypeName[descriptor.fullName] = descriptor
        try {
            evaluatorsByFullTypeName[descriptor.fullName] = evaluatorBuilder.load(descriptor)
        } catch (ex: Exception) {
            // idiosyncrasy of the conformance suite runner requires this particular exception is rethrown rather than a lookup failure later
            if (message != null) {
                if (message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName == descriptor.fullName) {
                    throw ex
                }
            }
        }
        descriptor.nestedTypes.forEach { load(it, message) }
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
