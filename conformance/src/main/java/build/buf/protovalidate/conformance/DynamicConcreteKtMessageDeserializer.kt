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
