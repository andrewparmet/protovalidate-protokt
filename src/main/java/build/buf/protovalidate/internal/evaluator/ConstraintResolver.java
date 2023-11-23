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

package build.buf.protovalidate.internal.evaluator;

import build.buf.protovalidate.exceptions.CompilationException;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import kotlin.Unit;
import protokt.v1.KtMessage;
import protokt.v1.buf.validate.FieldConstraints;
import protokt.v1.buf.validate.MessageConstraints;
import protokt.v1.buf.validate.OneofConstraints;
import protokt.v1.google.protobuf.Descriptor;
import protokt.v1.google.protobuf.FieldDescriptor;
import protokt.v1.google.protobuf.FieldOptions;
import protokt.v1.google.protobuf.MessageOptions;
import protokt.v1.google.protobuf.OneofDescriptor;
import protokt.v1.google.protobuf.OneofDescriptorProto;
import protokt.v1.google.protobuf.OneofOptions;

/** Manages the resolution of protovalidate constraints. */
class ConstraintResolver {

  /**
   * Resolves the constraints for a message descriptor.
   *
   * @param desc the message descriptor.
   * @return the resolved {@link MessageConstraints}.
   */
  MessageConstraints resolveMessageConstraints(Descriptor desc, ExtensionRegistry registry)
      throws InvalidProtocolBufferException, CompilationException {
    MessageOptions options = desc.getProto().getOptions();
    if (!options.hasExtension(ValidateProto.message)) {
      return MessageConstraints.getDefaultInstance();
    }
    // Don't use getExtension here to avoid exception if descriptor types don't match.
    // This can occur if the extension is generated to a different Java package.
    Object value = options .getField(ValidateProto.message.getDescriptor());
    if (value instanceof MessageConstraints) {
      return ((MessageConstraints) value);
    }
    if (value instanceof MessageLite) {
      // Possible that this represents the same constraint type, just generated to a different
      // java_package.
      return MessageConstraints.parseFrom(((MessageLite) value).toByteString());
    }
    throw new CompilationException("unexpected message constraint option type: " + value);
  }

  /**
   * Resolves the constraints for a oneof descriptor.
   *
   * @param desc the oneof descriptor.
   * @return the resolved {@link OneofConstraints}.
   */
  OneofConstraints resolveOneofConstraints(OneofDescriptor desc, ExtensionRegistry registry)
      throws CompilationException {
    OneofOptions options = desc.getProto().getOptions();
    // If the protovalidate oneof extension is unknown, reparse using extension registry.
    if (options.getUnknownFields().hasField(ValidateProto.oneof.getNumber())) {
      options = OneofOptions.parseFrom(options.toByteString(), registry);
    }
    if (!options.hasExtension(ValidateProto.oneof)) {
      return OneofConstraints.getDefaultInstance();
    }
    // Don't use getExtension here to avoid exception if descriptor types don't match.
    // This can occur if the extension is generated to a different Java package.
    Object value = options.getField(ValidateProto.oneof.getDescriptor());
    if (value instanceof OneofConstraints) {
      return ((OneofConstraints) value);
    }
    if (value instanceof MessageLite) {
      // Possible that this represents the same constraint type, just generated to a different
      // java_package.
      return OneofConstraints.parseFrom(((MessageLite) value).toByteString());
    }
    throw new CompilationException("unexpected oneof constraint option type: " + value);
  }

  /**
   * Resolves the constraints for a field descriptor.
   *
   * @param desc the field descriptor.
   * @return the resolved {@link FieldConstraints}.
   */
  FieldConstraints resolveFieldConstraints(FieldDescriptor desc, ExtensionRegistry registry)
      throws InvalidProtocolBufferException, CompilationException {
    FieldOptions options = desc.getProto().getOptions();
    // If the protovalidate field option is unknown, reparse using extension registry.
    if (options.getUnknownFields().hasField(ValidateProto.field.getNumber())) {
      options = FieldOptions.parseFrom(options.toByteString(), registry);
    }
    if (!options.hasExtension(ValidateProto.field)) {
      return FieldConstraints.Deserializer.invoke(builder -> Unit.INSTANCE);
    }
    // Don't use getExtension here to avoid exception if descriptor types don't match.
    // This can occur if the extension is generated to a different Java package.
    Object value = options.getField(ValidateProto.field.getDescriptor());
    if (value instanceof FieldConstraints) {
      return ((FieldConstraints) value);
    }
    if (value instanceof KtMessage) {
      // Possible that this represents the same constraint type, just generated to a different
      // java_package.
      return FieldConstraints.deserialize(((KtMessage) value).serialize());
    }
    throw new CompilationException("unexpected field constraint option type: " + value);
  }
}
