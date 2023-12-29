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
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.Type
import com.google.protobuf.Descriptors.OneofDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import org.projectnessie.cel.common.ULong
import protokt.v1.Bytes
import protokt.v1.Fixed32Val
import protokt.v1.Fixed64Val
import protokt.v1.KtEnum
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import protokt.v1.KtProperty
import protokt.v1.LengthDelimitedVal
import protokt.v1.UnknownFieldSet
import protokt.v1.VarintVal
import protokt.v1.google.protobuf.Duration
import protokt.v1.google.protobuf.Timestamp
import java.nio.charset.StandardCharsets
import java.util.SortedMap
import java.util.TreeMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

object ProtoktReflect {
    private val gettersByNumber =
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

    fun getField(message: KtMessage, field: FieldDescriptor): Any? =
        (gettersByNumber[message::class] as Map<Int, (KtMessage) -> Any?>).getValue(field.number)(message)
}

class ProtoktMessageLike(
    val message: KtMessage,
    private val descriptorsByFullTypeName: Map<String, Descriptor>
) : MessageLike {
    override fun getRepeatedFieldCount(field: FieldDescriptor): Int {
        val value = ProtoktReflect.getField(message, field) ?: (getUnknownField(field) as List<*>)
        return if (value is List<*>) {
            value.size
        } else {
            (value as Map<*, *>).size
        }
    }

    override fun hasField(field: FieldDescriptor) =
        ProtoktReflect.getField(message, field) != null

    override fun hasField(oneof: OneofDescriptor) =
        oneof.fields.mapNotNull { ProtoktReflect.getField(message, it) }.any()

    private fun getUnknownField(field: FieldDescriptor) =
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
                                if (field.type == Type.UINT64) {
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
                                if (field.type == Type.STRING) {
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

    override fun getField(field: FieldDescriptor) =
        ProtoktObjectValue(
            field,
            ProtoktReflect.getField(message, field) ?: getUnknownField(field)!!,
            descriptorsByFullTypeName
        )
}

class ProtoktMessageValue(
    message: KtMessage,
    private val descriptorsByFullTypeName: Map<String, Descriptor>
) : Value {
    private val message = ProtoktMessageLike(message, descriptorsByFullTypeName)

    override fun messageValue() =
        message

    override fun repeatedValue() =
        emptyList<Value>()

    override fun mapValue() =
        emptyMap<Value, Value>()

    override fun celValue() =
        dynamic(message.message, descriptorsByFullTypeName)

    override fun <T : Any> jvmValue(clazz: Class<T>) =
        null
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

    override fun celValue() =
        when (value) {
            is KtEnum -> value.value
            is UInt -> ULong.valueOf(value.toLong())
            is kotlin.ULong -> ULong.valueOf(value.toLong())

            // todo: support Bytes in CEL
            is Bytes -> ByteString.copyFrom(value.asReadOnlyBuffer())

            is Timestamp -> com.google.protobuf.Timestamp.newBuilder().setSeconds(value.seconds).setNanos(value.nanos).build()
            is Duration -> com.google.protobuf.Duration.newBuilder().setSeconds(value.seconds).setNanos(value.nanos).build()
            is KtMessage -> dynamic(value, descriptorsByFullTypeName)

            // pray
            else -> value
        }

    override fun <T : Any> jvmValue(clazz: Class<T>): T =
        when (value) {
            is KtEnum -> value.value
            is UInt -> value.toInt()
            is kotlin.ULong -> value.toLong()

            // pray
            else -> value
        }.let(clazz::cast)
}

// todo: implement protokt support for CEL
private fun dynamic(message: KtMessage, descriptorsByFullTypeName: Map<String, Descriptor>): Message {
    System.err.println("dynamically rebuilding $message")
    return DynamicMessage.newBuilder(descriptorsByFullTypeName.getValue(message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName))
        .mergeFrom(message.serialize())
        .build()
}
