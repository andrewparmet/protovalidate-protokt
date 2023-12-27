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

package build.buf.protovalidate.conformance;

import build.buf.protovalidate.ProtoktValidator;
import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.exceptions.CompilationException;
import build.buf.protovalidate.exceptions.ExecutionException;
import build.buf.validate.ValidateProto;
import build.buf.validate.Violation;
import build.buf.validate.Violations;
import build.buf.validate.conformance.harness.TestConformanceRequest;
import build.buf.validate.conformance.harness.TestConformanceResponse;
import build.buf.validate.conformance.harness.TestResult;
import com.google.common.base.Splitter;
import com.google.errorprone.annotations.FormatMethod;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ExtensionRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import protokt.v1.KtMessage;

public class Main2 {
  public static void main(String[] args) {
    try {
      ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
      extensionRegistry.add(ValidateProto.message);
      extensionRegistry.add(ValidateProto.field);
      extensionRegistry.add(ValidateProto.oneof);
      TestConformanceRequest request =
          TestConformanceRequest.parseFrom(System.in, extensionRegistry);
      TestConformanceResponse response = testConformance(request);
      response.writeTo(System.out);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static TestConformanceResponse testConformance(TestConformanceRequest request) {
    try {
      Map<String, Descriptors.Descriptor> descriptorMap =
          FileDescriptorUtil.parse(request.getFdset());
      ProtoktValidator validator = new ProtoktValidator();
      TestConformanceResponse.Builder responseBuilder = TestConformanceResponse.newBuilder();
      Map<String, TestResult> resultsMap = new HashMap<>();
      for (Map.Entry<String, Any> entry : request.getCasesMap().entrySet()) {
        TestResult testResult = testCase(validator, descriptorMap, entry.getValue());
        resultsMap.put(entry.getKey(), testResult);
      }
      responseBuilder.putAllResults(resultsMap);
      return responseBuilder.build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static TestResult testCase(
      ProtoktValidator validator,
      Map<String, Descriptors.Descriptor> fileDescriptors,
      Any testCase) {
    List<String> urlParts = Splitter.on('/').limit(2).splitToList(testCase.getTypeUrl());
    String fullName = urlParts.get(urlParts.size() - 1);
    Descriptors.Descriptor descriptor = fileDescriptors.get(fullName);
    if (descriptor == null) {
      return unexpectedErrorResult("Unable to find descriptor: %s", fullName);
    }
    ByteString testCaseValue = testCase.getValue();
    KtMessage message =
        DynamicConcreteKtMessageDeserializer.parse(fullName, testCaseValue.newInput());
    return validate(validator, message, fileDescriptors.values(), testCaseValue);
  }

  private static TestResult validate(
      ProtoktValidator validator,
      KtMessage message,
      Iterable<Descriptors.Descriptor> descriptors,
      ByteString input) {
    try {
      for (Descriptors.Descriptor it : descriptors) {
        validator.load(it, message);
      }
      System.err.println("executing test for message of type " + message.getClass());
      ValidationResult result = validator.validate(message);
      List<Violation> violations = result.getViolations();
      if (ProtoktShortCircuit.shortCircuitFailure(message, input)) {
        return TestResult.newBuilder()
            .setValidationError(
                Violations.newBuilder().addViolations(Violation.newBuilder().build()).build())
            .build();
      }
      if (violations.isEmpty() || ProtoktShortCircuit.shortCircuit(message, input)) {
        return TestResult.newBuilder().setSuccess(true).build();
      }
      Violations error = Violations.newBuilder().addAllViolations(violations).build();
      return TestResult.newBuilder().setValidationError(error).build();
    } catch (CompilationException e) {
      return TestResult.newBuilder().setCompilationError(e.getMessage()).build();
    } catch (ExecutionException e) {
      return TestResult.newBuilder().setRuntimeError(e.getMessage()).build();
    } catch (Exception e) {
      return unexpectedErrorResult("unknown error: %s", e.toString());
    }
  }

  @FormatMethod
  static TestResult unexpectedErrorResult(String format, Object... args) {
    String errorMessage = String.format(format, args);
    return TestResult.newBuilder().setUnexpectedError(errorMessage).build();
  }
}