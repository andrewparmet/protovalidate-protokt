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

package build.buf.protovalidate.internal.evaluator

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.protobuf.Descriptors
import protokt.v1.Fixed32Val
import protokt.v1.Fixed64Val
import protokt.v1.KtEnum
import protokt.v1.KtMessage
import protokt.v1.KtProperty
import protokt.v1.LengthDelimitedVal
import protokt.v1.UnknownFieldSet
import protokt.v1.VarintVal
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

object ProtoktReflect {
    private val reflectedGettersByClass =
        CacheBuilder.newBuilder()
            .build(
                object : CacheLoader<KClass<out KtMessage>, Map<Int, (KtMessage) -> Any?>>() {
                    override fun load(key: KClass<out KtMessage>): Map<Int, (KtMessage) -> Any?> =
                        gettersForClass(key)
                }
            )

    private fun gettersForClass(messageClass: KClass<out KtMessage>) =
        getTopLevelGetters(messageClass) + oneofGetters(messageClass)

    private fun getTopLevelGetters(klass: KClass<*>): Map<Int, (Any) -> Any?> =
        klass.declaredMemberProperties
            .map { it.findAnnotation<KtProperty>()?.number to it }
            .filter { (number, _) -> number != null }
            .associate { (number, getter) ->
                number!! to
                    { msg ->
                        @Suppress("UNCHECKED_CAST")
                        (getter as KProperty1<Any, *>).get(msg)
                    }
            }

    private fun oneofGetters(messageClass: KClass<out KtMessage>) = buildMap {
        val oneofPropertiesSealedClasses =
            messageClass
                .nestedClasses
                .filter { it.isSealed && !it.isSubclassOf(KtEnum::class) }

        oneofPropertiesSealedClasses.forEach { sealedClass ->
            val oneofPropertyGetter =
                messageClass.declaredMemberProperties
                    .single { it.returnType.classifier == sealedClass }
                    .let {
                        @Suppress("UNCHECKED_CAST")
                        it as KProperty1<KtMessage, *>
                    }

            putAll(
                sealedClass.nestedClasses.associate {
                    val (number, getterFromSubtype) = getTopLevelGetters(it).entries.single()
                    number to
                        { msg: KtMessage ->
                            val oneofProperty = oneofPropertyGetter.get(msg)
                            if (it.isInstance(oneofProperty)) {
                                getterFromSubtype(oneofProperty!!)
                            } else {
                                null
                            }
                        }
                }
            )
        }
    }

    private fun getUnknownField(message: KtMessage, field: Descriptors.FieldDescriptor) =
        message::class
            .declaredMemberProperties
            .firstOrNull { it.returnType.classifier == UnknownFieldSet::class }
            .let {
                @Suppress("UNCHECKED_CAST")
                it as KProperty1<KtMessage, UnknownFieldSet>
            }
            .get(message)
            .unknownFields[field.number.toUInt()]
            ?.let { value ->
                when {
                    value.varint.isNotEmpty() ->
                        value.varint
                            .map(VarintVal::value)
                            .map {
                                if (field.type == Descriptors.FieldDescriptor.Type.UINT64) {
                                    it
                                } else {
                                    it.toLong()
                                }
                            }

                    value.fixed32.isNotEmpty() ->
                        value.fixed32.map(Fixed32Val::value)

                    value.fixed64.isNotEmpty() ->
                        value.fixed64.map(Fixed64Val::value)

                    value.lengthDelimited.isNotEmpty() ->
                        value.lengthDelimited
                            .map(LengthDelimitedVal::value)
                            .map {
                                if (field.type == Descriptors.FieldDescriptor.Type.STRING) {
                                    StandardCharsets.UTF_8.decode(it.asReadOnlyBuffer()).toString()
                                } else {
                                    it
                                }
                            }

                    else -> error("unknown field for field number ${field.number} existed but was empty")
                }
            }
            .let {
                if (field.isRepeated) {
                    if (field.isMapField) {
                        it ?: emptyMap<Any, Any>()
                    } else {
                        it ?: emptyList<Any>()
                    }
                } else {
                    it?.first()
                }
            }

    fun getField(message: KtMessage, field: Descriptors.FieldDescriptor): Any? =
        reflectedGettersByClass[message::class][field.number]?.invoke(message)
            ?: getUnknownField(message, field)
}
