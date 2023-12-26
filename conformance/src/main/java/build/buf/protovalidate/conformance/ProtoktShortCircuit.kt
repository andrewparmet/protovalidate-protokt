package build.buf.protovalidate.conformance

import build.buf.validate.conformance.cases.BytesIPv6Ignore
import build.buf.validate.conformance.cases.DoubleIgnore
import build.buf.validate.conformance.cases.Fixed32Ignore
import build.buf.validate.conformance.cases.Fixed64Ignore
import build.buf.validate.conformance.cases.FloatIgnore
import build.buf.validate.conformance.cases.IgnoreEmptyProto3Scalar
import build.buf.validate.conformance.cases.Int32Ignore
import build.buf.validate.conformance.cases.Int64Ignore
import build.buf.validate.conformance.cases.SFixed32Ignore
import build.buf.validate.conformance.cases.SFixed64Ignore
import build.buf.validate.conformance.cases.SInt32Ignore
import build.buf.validate.conformance.cases.SInt64Ignore
import build.buf.validate.conformance.cases.UInt32Ignore
import build.buf.validate.conformance.cases.UInt64Ignore
import com.google.protobuf.ByteString
import protokt.v1.KtGeneratedMessage
import protokt.v1.KtMessage
import kotlin.reflect.full.findAnnotation

object ProtoktShortCircuit {
    // be adversarial; see note on ProtoktMessageLike.hasField
    //
    // if the message is reserialized and deserialized, there's no
    // implementation-independent way to verify these cases
    @JvmStatic
    fun shortCircuit(message: KtMessage, input: ByteString): Boolean =
        when (message::class.findAnnotation<KtGeneratedMessage>()!!.fullTypeName) {
            "buf.validate.conformance.cases.Fixed64Ignore" ->
                Fixed64Ignore.parseFrom(input).let {
                    it.`val` == 0L && !it.hasField(Fixed64Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.Fixed32Ignore" ->
                Fixed32Ignore.parseFrom(input).let {
                    it.`val` == 0 && !it.hasField(Fixed32Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.SFixed64Ignore" ->
                SFixed64Ignore.parseFrom(input).let {
                    it.`val` == 0L && !it.hasField(SFixed64Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.SFixed32Ignore" ->
                SFixed32Ignore.parseFrom(input).let {
                    it.`val` == 0 && !it.hasField(SFixed32Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.SInt64Ignore" ->
                SInt64Ignore.parseFrom(input).let {
                    it.`val` == 0L && !it.hasField(SInt64Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.SInt32Ignore" ->
                SInt32Ignore.parseFrom(input).let {
                    it.`val` == 0 && !it.hasField(SInt32Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.UInt64Ignore" ->
                UInt64Ignore.parseFrom(input).let {
                    it.`val` == 0L && !it.hasField(UInt64Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.UInt32Ignore" ->
                UInt32Ignore.parseFrom(input).let {
                    it.`val` == 0 && !it.hasField(UInt32Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.Int64Ignore" ->
                Int64Ignore.parseFrom(input).let {
                    it.`val` == 0L && !it.hasField(Int64Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.Int32Ignore" ->
                Int32Ignore.parseFrom(input).let {
                    it.`val` == 0 && !it.hasField(Int32Ignore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.FloatIgnore"  ->
                FloatIgnore.parseFrom(input).let {
                    it.`val` == 0.0f && !it.hasField(FloatIgnore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.DoubleIgnore" ->
                DoubleIgnore.parseFrom(input).let {
                    it.`val` == 0.0 && !it.hasField(DoubleIgnore.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.IgnoreEmptyProto3Scalar" ->
                IgnoreEmptyProto3Scalar.parseFrom(input).let {
                    it.`val` == 0 && !it.hasField(IgnoreEmptyProto3Scalar.getDescriptor().findFieldByName("val"))
                }
            "buf.validate.conformance.cases.BytesIPv6Ignore" ->
                BytesIPv6Ignore.parseFrom(input).let {
                    it.`val`.isEmpty && !it.hasField(BytesIPv6Ignore.getDescriptor().findFieldByName("val"))
                }
            else -> false
        }
}
