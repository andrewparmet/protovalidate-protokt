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
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

// todo: guava caches with expiration; one per call that uses reflection.
// todo: utilities that remove all need for casting to access the caches
// private val gettersByMessageAndDescriptor =
//    ConcurrentHashMap<Pair<KClass<out KtMessage>, FieldDescriptor>, KProperty1<out KtMessage, Any>>()

object ProtoktReflect {
    private data class MessageField(
        val messageClass: KClass<out KtMessage>,
        val field: FieldDescriptor
    )

    private sealed interface PropertyLookup

    data object NotFound : PropertyLookup

    class Found(
        val getter: PropertyGetter
    ) : PropertyLookup

    data class PropertyGetter(
        val returnType: KClassifier,
        val getter: (Any) -> Any?
    )

    private val topLevelGettersByClass =
        CacheBuilder.newBuilder()
            .build(
                object : CacheLoader<KClass<*>, SortedMap<Int, PropertyGetter>>() {
                    override fun load(key: KClass<*>): SortedMap<Int, PropertyGetter> =
                        key
                            .declaredMemberProperties
                            .map { it.findAnnotation<KtProperty>()?.number to it }
                            .filter { (number, _) -> number != null }
                            .associateTo(TreeMap()) {
                                it.first!! to
                                    PropertyGetter(
                                        it.second.returnType.classifier!!,
                                        { msg ->
                                            try {
                                                @Suppress("UNCHECKED_CAST")
                                                (it.second as KProperty1<Any, *>).get(msg)
                                            } catch (ex: Exception) {
                                                throw ex
                                            }
                                        }
                                    )
                            }
                }
            )

    private val topLevelGettersByMessageField =
        CacheBuilder.newBuilder()
            .build(
                object : CacheLoader<MessageField, PropertyLookup>() {
                    override fun load(key: MessageField): PropertyLookup =
                        topLevelGettersByClass[key.messageClass][key.field.number]?.let(::Found) ?: NotFound
                }
            )

    fun getTopLevelGetters(klass: KClass<*>): Map<Int, PropertyGetter> =
        topLevelGettersByClass[klass]

    fun <T> getTopLevelField(message: KtMessage, field: FieldDescriptor) =
        topLevelGettersByMessageField[MessageField(message::class, field)]
            .let { (it as? Found)?.getter }
            ?.getter
            ?.invoke(message)

    private data class SealedClassAndGetter(
        val oneofPropertyGetter: KProperty1<KtMessage, *>,
        val number: Int,
        val getterFromSealedClassSubtype: PropertyGetter
    )

    private val gettersByNumber =
        CacheBuilder.newBuilder()
            .build(
                object : CacheLoader<KClass<out KtMessage>, Map<Int, (KtMessage, Int) -> Any?>>() {
                    override fun load(key: KClass<out KtMessage>): Map<Int, (KtMessage, Int) -> Any?> =
                        loadAll(listOf(key)).values.single()

                    override fun loadAll(keys: Iterable<KClass<out KtMessage>>) =
                        keys.associateWith(::gettersForClass)

                    private fun gettersForClass(messageClass: KClass<out KtMessage>) =
                        getTopLevelGetters(messageClass).mapValues { (_, v) -> { msg: KtMessage, _: Int -> v.getter(msg) } } + oneofGetters(messageClass)

                    private fun oneofGetters(messageClass: KClass<out KtMessage>): Map<Int, (KtMessage, Int) -> Any?> {
                        val oneofPropertiesSealedClasses =
                            messageClass
                                .nestedClasses
                                .filter { it.isSealed && !it.isSubclassOf(KtEnum::class) }

                        val oneofPropertySubtypeGetters =
                            oneofPropertiesSealedClasses.flatMap { sealedClass ->
                                val oneofPropertyGetter =
                                    messageClass.declaredMemberProperties
                                        .single { it.returnType.classifier == sealedClass }
                                        .let {
                                            @Suppress("UNCHECKED_CAST")
                                            it as KProperty1<KtMessage, *>
                                        }

                                sealedClass.nestedClasses.map {
                                    val (number, getterFromSubtype) = getTopLevelGetters(it).entries.single()
                                    SealedClassAndGetter(
                                        oneofPropertyGetter,
                                        number,
                                        PropertyGetter(it, getterFromSubtype.getter)
                                    )
                                }
                            }

                        return oneofPropertySubtypeGetters.associate { (oneofPropertyGetter, number, getterFromSubtype) ->
                            data class PropGetter(
                                val dataClassGetter: KProperty1<KtMessage, *>
                            ) : (KtMessage, Int) -> Any? {
                                override fun invoke(msg: KtMessage, requestedNumber: Int): Any? {
                                    val oneofProperty = oneofPropertyGetter.get(msg)
                                    return if ((getterFromSubtype.returnType as KClass<*>).isInstance(oneofProperty)) {
                                        getterFromSubtype.getter(oneofProperty!!)
                                    } else {
                                        null
                                    }
                                }
                            }

                            number to PropGetter(oneofPropertyGetter)
                        }
                    }
                }
            )

    fun getGetters(klass: KClass<out KtMessage>): Map<Int, (KtMessage, Int) -> Any?> =
        gettersByNumber[klass] as Map<Int, (KtMessage, Int) -> Any?>
}

// todo: cache field lookup paths for message class/descriptor pairs
// todo: or just cache general reflection info globally
class ProtoktMessageLike(
    val message: KtMessage,
    private val descriptorsByFullTypeName: Map<String, Descriptor>
) : MessageLike {
    override fun getRepeatedFieldCount(field: FieldDescriptor): Int {
        val value = ProtoktReflect.getTopLevelField<Any>(message, field) ?: (getUnknownField(field) as List<*>)
        return if (value is List<*>) {
            value.size
        } else {
            (value as Map<*, *>).size
        }
    }

    // todo: protokt needs to track presence of scalars with a private backing field
    // bring this up with buf team, as this is not required by the proto3 presence tracking spec:
    // https://github.com/protocolbuffers/protobuf/blob/a4576cb8208ea8f1c4b05b9bb35533201e301171/docs/field_presence.md?plain=1#L103
    /*
        class Foo(
            private val _possiblyNotThere: Int?
        ) {
            // presence tracking via checking property `_possiblyNotThere`.
            // idk what happens on later JDKs if the backing property is null
            val possiblyNotThere = _possiblyNotThere ?: 0
        }
     */
    override fun hasField(field: FieldDescriptor) =
        (getStandardField(field) ?: getOneofField(field)) != null

    private fun getStandardField(field: FieldDescriptor) =
        ProtoktReflect.getTopLevelField<Any?>(message, field)

    override fun hasField(oneof: OneofDescriptor): Boolean {
        val getters = ProtoktReflect.getGetters(message::class)
        return oneof.fields.map { getters.getValue(it.number)(message, it.number) }.any { it != null }
    }

    private fun getOneofField(field: FieldDescriptor): Any? {
        return ProtoktReflect.getGetters(message::class).getValue(field.number)(message, field.number)
/*
        val dataClassProperty =
            dataClassInstance::class
                .declaredMemberProperties
                .single()
                .let {
                    @Suppress("UNCHECKED_CAST")
                    it as KProperty1<Any, Any?>
                }

        return if (dataClassProperty.findAnnotation<KtProperty>()!!.number == field.number) {
            dataClassProperty.get(dataClassInstance)
        } else {
            null
        } */
    }


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
            getStandardField(field) ?: getOneofField(field) ?: getUnknownField(field)!!,
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
