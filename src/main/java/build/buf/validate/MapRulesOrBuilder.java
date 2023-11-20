// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: buf/validate/validate.proto

// Protobuf Java Version: 3.25.1
package build.buf.validate;

public interface MapRulesOrBuilder extends
    // @@protoc_insertion_point(interface_extends:buf.validate.MapRules)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   *Specifies the minimum number of key-value pairs allowed. If the field has
   * fewer key-value pairs than specified, an error message is generated.
   *
   * ```proto
   * message MyMap {
   *   // The field `value` must have at least 2 key-value pairs.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.min_pairs = 2];
   * }
   * ```
   * </pre>
   *
   * <code>optional uint64 min_pairs = 1 [json_name = "minPairs", (.buf.validate.priv.field) = { ... }</code>
   * @return Whether the minPairs field is set.
   */
  boolean hasMinPairs();
  /**
   * <pre>
   *Specifies the minimum number of key-value pairs allowed. If the field has
   * fewer key-value pairs than specified, an error message is generated.
   *
   * ```proto
   * message MyMap {
   *   // The field `value` must have at least 2 key-value pairs.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.min_pairs = 2];
   * }
   * ```
   * </pre>
   *
   * <code>optional uint64 min_pairs = 1 [json_name = "minPairs", (.buf.validate.priv.field) = { ... }</code>
   * @return The minPairs.
   */
  long getMinPairs();

  /**
   * <pre>
   *Specifies the maximum number of key-value pairs allowed. If the field has
   * more key-value pairs than specified, an error message is generated.
   *
   * ```proto
   * message MyMap {
   *   // The field `value` must have at most 3 key-value pairs.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.max_pairs = 3];
   * }
   * ```
   * </pre>
   *
   * <code>optional uint64 max_pairs = 2 [json_name = "maxPairs", (.buf.validate.priv.field) = { ... }</code>
   * @return Whether the maxPairs field is set.
   */
  boolean hasMaxPairs();
  /**
   * <pre>
   *Specifies the maximum number of key-value pairs allowed. If the field has
   * more key-value pairs than specified, an error message is generated.
   *
   * ```proto
   * message MyMap {
   *   // The field `value` must have at most 3 key-value pairs.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.max_pairs = 3];
   * }
   * ```
   * </pre>
   *
   * <code>optional uint64 max_pairs = 2 [json_name = "maxPairs", (.buf.validate.priv.field) = { ... }</code>
   * @return The maxPairs.
   */
  long getMaxPairs();

  /**
   * <pre>
   *Specifies the constraints to be applied to each key in the field.
   *
   * ```proto
   * message MyMap {
   *   // The keys in the field `value` must follow the specified constraints.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.keys = {
   *     string: {
   *       min_len: 3
   *       max_len: 10
   *     }
   *   }];
   * }
   * ```
   * </pre>
   *
   * <code>optional .buf.validate.FieldConstraints keys = 4 [json_name = "keys"];</code>
   * @return Whether the keys field is set.
   */
  boolean hasKeys();
  /**
   * <pre>
   *Specifies the constraints to be applied to each key in the field.
   *
   * ```proto
   * message MyMap {
   *   // The keys in the field `value` must follow the specified constraints.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.keys = {
   *     string: {
   *       min_len: 3
   *       max_len: 10
   *     }
   *   }];
   * }
   * ```
   * </pre>
   *
   * <code>optional .buf.validate.FieldConstraints keys = 4 [json_name = "keys"];</code>
   * @return The keys.
   */
  build.buf.validate.FieldConstraints getKeys();
  /**
   * <pre>
   *Specifies the constraints to be applied to each key in the field.
   *
   * ```proto
   * message MyMap {
   *   // The keys in the field `value` must follow the specified constraints.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.keys = {
   *     string: {
   *       min_len: 3
   *       max_len: 10
   *     }
   *   }];
   * }
   * ```
   * </pre>
   *
   * <code>optional .buf.validate.FieldConstraints keys = 4 [json_name = "keys"];</code>
   */
  build.buf.validate.FieldConstraintsOrBuilder getKeysOrBuilder();

  /**
   * <pre>
   *Specifies the constraints to be applied to the value of each key in the
   * field. Message values will still have their validations evaluated unless
   *skip is specified here.
   *
   * ```proto
   * message MyMap {
   *   // The values in the field `value` must follow the specified constraints.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.values = {
   *     string: {
   *       min_len: 5
   *       max_len: 20
   *     }
   *   }];
   * }
   * ```
   * </pre>
   *
   * <code>optional .buf.validate.FieldConstraints values = 5 [json_name = "values"];</code>
   * @return Whether the values field is set.
   */
  boolean hasValues();
  /**
   * <pre>
   *Specifies the constraints to be applied to the value of each key in the
   * field. Message values will still have their validations evaluated unless
   *skip is specified here.
   *
   * ```proto
   * message MyMap {
   *   // The values in the field `value` must follow the specified constraints.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.values = {
   *     string: {
   *       min_len: 5
   *       max_len: 20
   *     }
   *   }];
   * }
   * ```
   * </pre>
   *
   * <code>optional .buf.validate.FieldConstraints values = 5 [json_name = "values"];</code>
   * @return The values.
   */
  build.buf.validate.FieldConstraints getValues();
  /**
   * <pre>
   *Specifies the constraints to be applied to the value of each key in the
   * field. Message values will still have their validations evaluated unless
   *skip is specified here.
   *
   * ```proto
   * message MyMap {
   *   // The values in the field `value` must follow the specified constraints.
   *   map&lt;string, string&gt; value = 1 [(buf.validate.field).map.values = {
   *     string: {
   *       min_len: 5
   *       max_len: 20
   *     }
   *   }];
   * }
   * ```
   * </pre>
   *
   * <code>optional .buf.validate.FieldConstraints values = 5 [json_name = "values"];</code>
   */
  build.buf.validate.FieldConstraintsOrBuilder getValuesOrBuilder();
}
