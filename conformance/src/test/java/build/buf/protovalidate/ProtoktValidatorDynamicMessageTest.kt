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

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import protokt.v1.KtMessage
import protokt.v1.buf.validate.conformance.cases.numbers_file_descriptor

class ProtoktValidatorDynamicMessageTest : AbstractProtoktValidatorTest() {
    override fun validate(message: KtMessage) = validator.validate2(message)

    @Test
    fun `2test message with fixed32 encoded purely as unknown fields (dynamic message without a dedicated type)`() {
        validator.load(numbers_file_descriptor.descriptor)

        val result =
            validate(
                Fixed32.deserialize(
                    build.buf.validate.conformance.cases.Fixed32In
                        .newBuilder()
                        .setVal(4)
                        .build()
                        .toByteArray(),
                ),
            )

        Assertions.assertThat(result.isSuccess).isFalse()

        val result2 =
            validate(
                Fixed32.deserialize(
                    build.buf.validate.conformance.cases.Fixed32In
                        .newBuilder()
                        .setVal(3)
                        .build()
                        .toByteArray(),
                ),
            )

        Assertions.assertThat(result2.isSuccess).isTrue()
    }
}
