package build.buf.protovalidate

import protokt.v1.KtMessage

class ProtoktValidatorDynamicMessageTest : AbstractProtoktValidatorTest() {
    override fun validate(message: KtMessage) = validator.validate2(message)
}
