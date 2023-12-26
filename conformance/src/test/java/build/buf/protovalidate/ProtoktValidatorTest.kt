package build.buf.protovalidate

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import protokt.v1.buf.validate.conformance.cases.SInt64GT
import protokt.v1.buf.validate.conformance.cases.numbers_file_descriptor

class ProtoktValidatorTest {
    private val validator = ProtoktValidator()

    @Test
    fun `test a constraint`() {
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
}
