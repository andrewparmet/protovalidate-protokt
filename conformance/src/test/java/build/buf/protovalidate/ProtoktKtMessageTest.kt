package build.buf.protovalidate

import protokt.v1.KtMessage

class ProtoktKtMessageTest : AbstractProtoktValidatorTest() {
    override fun validate(message: KtMessage) = validator.validate(message)
}
