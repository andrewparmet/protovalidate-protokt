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

import com.google.protobuf.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import protokt.v1.AbstractKtDeserializer
import protokt.v1.AbstractKtMessage
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessageDeserializer
import protokt.v1.UnknownFieldSet
import protokt.v1.buf.validate.conformance.cases.MessageRequiredOneof
import protokt.v1.buf.validate.conformance.cases.Oneof
import protokt.v1.buf.validate.conformance.cases.SInt64GT
import protokt.v1.buf.validate.conformance.cases.TestMsg
import protokt.v1.buf.validate.conformance.cases.UInt64In
import protokt.v1.buf.validate.conformance.cases.bytes_file_descriptor
import protokt.v1.buf.validate.conformance.cases.messages_file_descriptor
import protokt.v1.buf.validate.conformance.cases.numbers_file_descriptor
import protokt.v1.buf.validate.conformance.cases.oneofs_file_descriptor
import protokt.v1.buf.validate.conformance.cases.repeated_file_descriptor
import protokt.v1.buf.validate.conformance.cases.strings_file_descriptor

class ProtoktValidatorTest {
    private val validator = ProtoktValidator()

    @Test
    fun `test sint64 constraint`() {
        validator.load(numbers_file_descriptor.descriptor)

        val result =
            validator.validate(
                SInt64GT {
                    `val` = 14
                }
            )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.violations).isNotEmpty()
    }

    @Test
    fun `test required oneof constraint`() {
        validator.load(messages_file_descriptor.descriptor)

        val result =
            validator.validate(
                MessageRequiredOneof {
                    one = MessageRequiredOneof.One.Val(
                        TestMsg {
                            const = "foo"
                        }
                    )
                }
            )

        assertThat(result.violations).isEmpty()
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `test oneof constraint`() {
        validator.load(oneofs_file_descriptor.descriptor)

        val result =
            validator.validate(
                Oneof {
                    o = Oneof.O.X("foobar")
                }
            )

        assertThat(result.violations).isEmpty()
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `test uint64 in constraint`() {
        validator.load(numbers_file_descriptor.descriptor)

        val result =
            validator.validate(
                UInt64In {
                    `val` = 4u
                }
            )

        assertThat(result.isSuccess).isFalse()
    }

    @Test
    fun `test message with varint non-uint64 encoded purely as unknown fields (dynamic message without a dedicated type)`() {
        validator.load(numbers_file_descriptor.descriptor)

        val result =
            validator.validate(
                Int64.deserialize(
                    build.buf.validate.conformance.cases.Int64In
                        .newBuilder()
                        .setVal(4)
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result.isSuccess).isFalse()

        val result2 =
            validator.validate(
                Int64.deserialize(
                    build.buf.validate.conformance.cases.Int64In
                        .newBuilder()
                        .setVal(3)
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result2.isSuccess).isTrue()
    }

    @Test
    fun `test message with varint uint64 encoded purely as unknown fields (dynamic message without a dedicated type)`() {
        validator.load(numbers_file_descriptor.descriptor)

        val result =
            validator.validate(
                UInt64.deserialize(
                    build.buf.validate.conformance.cases.UInt64In
                        .newBuilder()
                        .setVal(4)
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result.isSuccess).isFalse()

        val result2 =
            validator.validate(
                UInt64.deserialize(
                    build.buf.validate.conformance.cases.UInt64In
                        .newBuilder()
                        .setVal(3)
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result2.isSuccess).isTrue()
    }

    @Test
    fun `test message with fixed32 encoded purely as unknown fields (dynamic message without a dedicated type)`() {
        validator.load(numbers_file_descriptor.descriptor)

        val result =
            validator.validate(
                Fixed32.deserialize(
                    build.buf.validate.conformance.cases.Fixed32In
                        .newBuilder()
                        .setVal(4)
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result.isSuccess).isFalse()

        val result2 =
            validator.validate(
                Fixed32.deserialize(
                    build.buf.validate.conformance.cases.Fixed32In
                        .newBuilder()
                        .setVal(3)
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result2.isSuccess).isTrue()
    }

    @Test
    fun `test message with fixed64 encoded purely as unknown fields (dynamic message without a dedicated type)`() {
        validator.load(numbers_file_descriptor.descriptor)

        val result =
            validator.validate(
                Fixed64.deserialize(
                    build.buf.validate.conformance.cases.Fixed64In
                        .newBuilder()
                        .setVal(4)
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result.isSuccess).isFalse()

        val result2 =
            validator.validate(
                Fixed64.deserialize(
                    build.buf.validate.conformance.cases.Fixed64In
                        .newBuilder()
                        .setVal(3)
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result2.isSuccess).isTrue()
    }

    @Test
    fun `test message with length delimited string encoded purely as unknown fields (dynamic message without a dedicated type)`() {
        validator.load(strings_file_descriptor.descriptor)

        val result =
            validator.validate(
                LengthDelimitedString.deserialize(
                    build.buf.validate.conformance.cases.StringIn
                        .newBuilder()
                        .setVal("foo")
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result.isSuccess).isFalse()

        val result2 =
            validator.validate(
                LengthDelimitedString.deserialize(
                    build.buf.validate.conformance.cases.StringIn
                        .newBuilder()
                        .setVal("bar")
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result2.isSuccess).isTrue()
    }

    @Test
    fun `test message with length delimited bytes encoded purely as unknown fields (dynamic message without a dedicated type)`() {
        validator.load(bytes_file_descriptor.descriptor)

        val result =
            validator.validate(
                LengthDelimitedBytes.deserialize(
                    build.buf.validate.conformance.cases.BytesIn
                        .newBuilder()
                        .setVal(ByteString.copyFromUtf8("foo"))
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result.isSuccess).isFalse()

        val result2 =
            validator.validate(
                LengthDelimitedBytes.deserialize(
                    build.buf.validate.conformance.cases.BytesIn
                        .newBuilder()
                        .setVal(ByteString.copyFromUtf8("bar"))
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result2.isSuccess).isTrue()
    }

    @Test
    fun `test message with repeated values encoded purely as unknown fields (dynamic message without a dedicated type)`() {
        validator.load(repeated_file_descriptor.descriptor)

        val result =
            validator.validate(
                RepeatedLengthDelimited.deserialize(
                    build.buf.validate.conformance.cases.RepeatedUnique
                        .newBuilder()
                        .addAllVal(listOf("foo", "foo"))
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result.isSuccess).isFalse()

        val result2 =
            validator.validate(
                RepeatedLengthDelimited.deserialize(
                    build.buf.validate.conformance.cases.RepeatedUnique
                        .newBuilder()
                        .addAllVal(listOf("foo", "bar"))
                        .build()
                        .toByteArray()
                )
            )

        assertThat(result2.isSuccess).isTrue()
    }

    abstract class AbstractDynamicMessage : AbstractKtMessage() {
        abstract val unknownFields: UnknownFieldSet

        override val messageSize
            get() = unknownFields.size()

        override fun serialize(serializer: protokt.v1.KtMessageSerializer) {
            serializer.writeUnknown(unknownFields)
        }
    }

    @KtGeneratedMessage("buf.validate.conformance.cases.Int64In")
    class Int64(
        override val unknownFields: UnknownFieldSet
    ) : AbstractDynamicMessage() {
        companion object : AbstractKtDeserializer<Int64>() {
            @JvmStatic
            override fun deserialize(deserializer: KtMessageDeserializer): Int64 {
                val unknownFields = UnknownFieldSet.Builder()

                while (true) {
                    when (deserializer.readTag()) {
                        0 -> return Int64(UnknownFieldSet.from(unknownFields))
                        else -> unknownFields.add(deserializer.readUnknown())
                    }
                }
            }
        }
    }

    @KtGeneratedMessage("buf.validate.conformance.cases.UInt64In")
    class UInt64(
        override val unknownFields: UnknownFieldSet
    ) : AbstractDynamicMessage() {
        companion object : AbstractKtDeserializer<UInt64>() {
            @JvmStatic
            override fun deserialize(deserializer: KtMessageDeserializer): UInt64 {
                val unknownFields = UnknownFieldSet.Builder()

                while (true) {
                    when (deserializer.readTag()) {
                        0 -> return UInt64(UnknownFieldSet.from(unknownFields))
                        else -> unknownFields.add(deserializer.readUnknown())
                    }
                }
            }
        }
    }

    @KtGeneratedMessage("buf.validate.conformance.cases.Fixed32In")
    class Fixed32(
        override val unknownFields: UnknownFieldSet
    ) : AbstractDynamicMessage() {
        companion object : AbstractKtDeserializer<Fixed32>() {
            @JvmStatic
            override fun deserialize(deserializer: KtMessageDeserializer): Fixed32 {
                val unknownFields = UnknownFieldSet.Builder()

                while (true) {
                    when (deserializer.readTag()) {
                        0 -> return Fixed32(UnknownFieldSet.from(unknownFields))
                        else -> unknownFields.add(deserializer.readUnknown())
                    }
                }
            }
        }
    }

    @KtGeneratedMessage("buf.validate.conformance.cases.Fixed64In")
    class Fixed64(
        override val unknownFields: UnknownFieldSet
    ) : AbstractDynamicMessage() {
        companion object : AbstractKtDeserializer<Fixed64>() {
            @JvmStatic
            override fun deserialize(deserializer: KtMessageDeserializer): Fixed64 {
                val unknownFields = UnknownFieldSet.Builder()

                while (true) {
                    when (deserializer.readTag()) {
                        0 -> return Fixed64(UnknownFieldSet.from(unknownFields))
                        else -> unknownFields.add(deserializer.readUnknown())
                    }
                }
            }
        }
    }

    @KtGeneratedMessage("buf.validate.conformance.cases.StringIn")
    class LengthDelimitedString(
        override val unknownFields: UnknownFieldSet
    ) : AbstractDynamicMessage() {
        companion object : AbstractKtDeserializer<LengthDelimitedString>() {
            @JvmStatic
            override fun deserialize(deserializer: KtMessageDeserializer): LengthDelimitedString {
                val unknownFields = UnknownFieldSet.Builder()

                while (true) {
                    when (deserializer.readTag()) {
                        0 -> return LengthDelimitedString(UnknownFieldSet.from(unknownFields))
                        else -> unknownFields.add(deserializer.readUnknown())
                    }
                }
            }
        }
    }

    @KtGeneratedMessage("buf.validate.conformance.cases.BytesIn")
    class LengthDelimitedBytes(
        override val unknownFields: UnknownFieldSet
    ) : AbstractDynamicMessage() {
        companion object : AbstractKtDeserializer<LengthDelimitedBytes>() {
            @JvmStatic
            override fun deserialize(deserializer: KtMessageDeserializer): LengthDelimitedBytes {
                val unknownFields = UnknownFieldSet.Builder()

                while (true) {
                    when (deserializer.readTag()) {
                        0 -> return LengthDelimitedBytes(UnknownFieldSet.from(unknownFields))
                        else -> unknownFields.add(deserializer.readUnknown())
                    }
                }
            }
        }
    }

    @KtGeneratedMessage("buf.validate.conformance.cases.RepeatedUnique")
    class RepeatedLengthDelimited(
        override val unknownFields: UnknownFieldSet
    ) : AbstractDynamicMessage() {
        companion object : AbstractKtDeserializer<RepeatedLengthDelimited>() {
            @JvmStatic
            override fun deserialize(deserializer: KtMessageDeserializer): RepeatedLengthDelimited {
                val unknownFields = UnknownFieldSet.Builder()

                while (true) {
                    when (deserializer.readTag()) {
                        0 -> return RepeatedLengthDelimited(UnknownFieldSet.from(unknownFields))
                        else -> unknownFields.add(deserializer.readUnknown())
                    }
                }
            }
        }
    }
}
