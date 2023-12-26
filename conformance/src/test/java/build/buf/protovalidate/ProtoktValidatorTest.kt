package build.buf.protovalidate

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import protokt.v1.buf.validate.conformance.cases.Int64Ignore
import protokt.v1.buf.validate.conformance.cases.MessageRequiredOneof
import protokt.v1.buf.validate.conformance.cases.Oneof
import protokt.v1.buf.validate.conformance.cases.SInt64GT
import protokt.v1.buf.validate.conformance.cases.TestMsg
import protokt.v1.buf.validate.conformance.cases.messages_file_descriptor
import protokt.v1.buf.validate.conformance.cases.numbers_file_descriptor
import protokt.v1.buf.validate.conformance.cases.oneofs_file_descriptor

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
    fun `test int64 ignore constraint`() {
        validator.load(numbers_file_descriptor.descriptor)

        val result =
            validator.validate(
                Int64Ignore {}
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
}
