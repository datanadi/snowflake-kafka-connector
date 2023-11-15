package com.snowflake.kafka.connector;

import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.ERRORS_LOG_ENABLE_CONFIG;
import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.ERRORS_TOLERANCE_CONFIG;
import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.NAME;
import static com.snowflake.kafka.connector.Utils.HTTP_NON_PROXY_HOSTS;
import static com.snowflake.kafka.connector.internal.TestUtils.getConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.snowflake.kafka.connector.internal.SnowflakeErrors;
import com.snowflake.kafka.connector.internal.SnowflakeKafkaConnectorException;
import com.snowflake.kafka.connector.internal.streaming.IngestionMethodConfig;
import com.snowflake.kafka.connector.internal.streaming.StreamingUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.connect.storage.Converter;
import org.junit.Assert;
import org.junit.Test;

public class ConnectorConfigTest {
  // subset of valid community converters
  public static final List<Converter> COMMUNITY_CONVERTER_SUBSET =
      Arrays.asList(
          new org.apache.kafka.connect.storage.StringConverter(),
          new org.apache.kafka.connect.json.JsonConverter(),
          new io.confluent.connect.avro.AvroConverter());

  // custom snowflake converters, not currently allowed for streaming
  public static final List<Converter> CUSTOM_SNOWFLAKE_CONVERTERS =
      Arrays.asList(
          new com.snowflake.kafka.connector.records.SnowflakeJsonConverter(),
          new com.snowflake.kafka.connector.records.SnowflakeAvroConverterWithoutSchemaRegistry(),
          new com.snowflake.kafka.connector.records.SnowflakeAvroConverter());

  @Test
  public void testConfig() {
    Map<String, String> config = getConfig();
    Utils.validateConfig(config);
  }

  @Test
  public void testConfig_ConvertedInvalidAppName() {
    Map<String, String> config = getConfig();
    config.put(NAME, "testConfig.snowflake-connector");

    Utils.convertAppName(config);

    Utils.validateConfig(config);
  }

  @Test
  public void testEmptyFlushTime() {
    Map<String, String> config = getConfig();
    config.remove(SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC);
  }

  @Test
  public void testFlushTimeSmall() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC,
        (SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC_MIN - 1) + "");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC);
  }

  @Test
  public void testFlushTimeNotNumber() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC, "fdas");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC);
  }

  @Test
  public void testEmptyName() {
    Map<String, String> config = getConfig();
    config.remove(SnowflakeSinkConnectorConfig.NAME);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.NAME);
  }

  @Test
  public void testURL() {
    Map<String, String> config = getConfig();
    config.remove(SnowflakeSinkConnectorConfig.SNOWFLAKE_URL);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.SNOWFLAKE_URL);
  }

  @Test
  public void testEmptyUser() {
    Map<String, String> config = getConfig();
    config.remove(SnowflakeSinkConnectorConfig.SNOWFLAKE_USER);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.SNOWFLAKE_USER);
  }

  @Test
  public void testEmptyDatabase() {
    Map<String, String> config = getConfig();
    config.remove(SnowflakeSinkConnectorConfig.SNOWFLAKE_DATABASE);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.SNOWFLAKE_DATABASE);
  }

  @Test
  public void testEmptySchema() {
    Map<String, String> config = getConfig();
    config.remove(SnowflakeSinkConnectorConfig.SNOWFLAKE_SCHEMA);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.SNOWFLAKE_SCHEMA);
  }

  @Test
  public void testEmptyPrivateKey() {
    Map<String, String> config = getConfig();
    config.remove(SnowflakeSinkConnectorConfig.SNOWFLAKE_PRIVATE_KEY);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.SNOWFLAKE_PRIVATE_KEY);
  }

  @Test
  public void testCorrectProxyHost() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.JVM_PROXY_HOST, "127.0.0.1");
    config.put(SnowflakeSinkConnectorConfig.JVM_PROXY_PORT, "3128");
    Utils.validateConfig(config);
  }

  @Test
  public void testEmptyPort() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.JVM_PROXY_HOST, "127.0.0.1");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.JVM_PROXY_HOST);
  }

  @Test
  public void testEmptyHost() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.JVM_PROXY_PORT, "3128");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.JVM_PROXY_HOST);
  }

  @Test
  public void testNonProxyHosts() {
    String oldNonProxyHosts =
        (System.getProperty(HTTP_NON_PROXY_HOSTS) != null)
            ? System.getProperty(HTTP_NON_PROXY_HOSTS)
            : null;

    System.setProperty(HTTP_NON_PROXY_HOSTS, "host1.com|host2.com|localhost");
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.JVM_PROXY_HOST, "127.0.0.1");
    config.put(SnowflakeSinkConnectorConfig.JVM_PROXY_PORT, "3128");
    config.put(
        SnowflakeSinkConnectorConfig.JVM_NON_PROXY_HOSTS,
        "*.snowflakecomputing.com|*.amazonaws.com");
    Utils.enableJVMProxy(config);
    String mergedNonProxyHosts = System.getProperty(HTTP_NON_PROXY_HOSTS);
    Assert.assertTrue(
        mergedNonProxyHosts.equals(
            "host1.com|host2.com|localhost|*.snowflakecomputing.com|*.amazonaws.com"));

    if (oldNonProxyHosts != null) {
      System.setProperty(HTTP_NON_PROXY_HOSTS, oldNonProxyHosts);
    } else {
      System.clearProperty(HTTP_NON_PROXY_HOSTS);
    }
  }

  @Test
  public void testIllegalTopicMap() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.TOPICS_TABLES_MAP, "$@#$#@%^$12312");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.TOPICS_TABLES_MAP);
  }

  @Test
  public void testIllegalTableName() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.TOPICS_TABLES_MAP, "topic1:!@#@!#!@");
    validateConfigWithExceptionCode(config, SnowflakeErrors.ERROR_0021.getCode());
  }

  @Test
  public void testDuplicatedTopic() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.TOPICS_TABLES_MAP, "topic1:table1,topic1:table2");
    validateConfigWithExceptionCode(config, SnowflakeErrors.ERROR_0021.getCode());
  }

  @Test
  public void testDuplicatedTableName() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.TOPICS_TABLES_MAP, "topic1:table1,topic2:table1");
    Utils.validateConfig(config);
  }

  @Test
  public void testNameMapCovered() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.TOPICS, "!@#,$%^,test");
    config.put(SnowflakeSinkConnectorConfig.TOPICS_TABLES_MAP, "!@#:table1,$%^:table2");
    Utils.validateConfig(config);
  }

  @Test
  public void testBufferSizeRange() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES,
        SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES_MIN - 1 + "");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES);
  }

  @Test
  public void testBufferSizeValue() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES, "afdsa");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES);
  }

  @Test
  public void testEmptyBufferSize() {
    Map<String, String> config = getConfig();
    config.remove(SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES);
  }

  @Test
  public void testEmptyBufferCount() {
    Map<String, String> config = getConfig();
    config.remove(SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS);
  }

  @Test
  public void testEmptyBufferCountNegative() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS, "-1");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS);
  }

  @Test
  public void testBufferCountValue() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS, "adssadsa");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS);
  }

  @Test
  public void testKafkaProviderConfigValue_valid_null() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.PROVIDER_CONFIG, null);
    Utils.validateConfig(config);
  }

  @Test
  public void testKafkaProviderConfigValue_valid_empty() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.PROVIDER_CONFIG, "");
    Utils.validateConfig(config);
  }

  @Test
  public void testKafkaProviderConfigValue_valid_provider() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.PROVIDER_CONFIG, "self_hosted");
    Utils.validateConfig(config);

    config.put(SnowflakeSinkConnectorConfig.PROVIDER_CONFIG, "CONFLUENT");
    Utils.validateConfig(config);

    config.put(SnowflakeSinkConnectorConfig.PROVIDER_CONFIG, "UNKNOWN");
    Utils.validateConfig(config);
  }

  @Test
  public void testKafkaProviderConfigValue_invalid_value() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.PROVIDER_CONFIG, "Something_which_is_not_supported");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.PROVIDER_CONFIG);
  }

  @Test
  public void testBehaviorOnNullValuesConfig_valid_value() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.BEHAVIOR_ON_NULL_VALUES_CONFIG, "IGNORE");
    Utils.validateConfig(config);

    config.put(SnowflakeSinkConnectorConfig.BEHAVIOR_ON_NULL_VALUES_CONFIG, "DEFAULT");
    Utils.validateConfig(config);
  }

  @Test
  public void testBehaviorOnNullValuesConfig_invalid_value() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.BEHAVIOR_ON_NULL_VALUES_CONFIG, "INVALID");
    validateConfigWithErrorContaining(
        config, SnowflakeSinkConnectorConfig.BEHAVIOR_ON_NULL_VALUES_CONFIG);
  }

  @Test
  public void testJMX_valid_value() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.JMX_OPT, "true");
    Utils.validateConfig(config);

    config.put(SnowflakeSinkConnectorConfig.JMX_OPT, "False");
    Utils.validateConfig(config);
  }

  @Test
  public void testJMX_invalid_value() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.JMX_OPT, "INVALID");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.JMX_OPT);
  }

  @Test
  public void testIngestionTypeConfig_valid_value_snowpipe() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE.toString());
    Utils.validateConfig(config);
  }

  @Test
  public void testIngestionTypeConfig_valid_value_snowpipe_streaming() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    Utils.validateConfig(config);
  }

  @Test
  public void testIngestionTypeConfig_invalid_value() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT, "INVALID_VALUE");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT);
  }

  @Test
  public void testDetermineIngestionMethod_nullOrEmptyInput() {
    Map<String, String> config = getConfig();
    assertEquals(
        IngestionMethodConfig.SNOWPIPE, IngestionMethodConfig.determineIngestionMethod(config));

    assertEquals(
        IngestionMethodConfig.SNOWPIPE, IngestionMethodConfig.determineIngestionMethod(null));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDetermineIngestionMethod_invalidIngestionMethod() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT, "INVALID_VALUE");

    IngestionMethodConfig.determineIngestionMethod(config);
  }

  @Test
  public void testDetermineIngestionLoadMethod_validIngestionMethod() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT, "SNOWPIPE_STREAMING");
    assertEquals(
        IngestionMethodConfig.SNOWPIPE_STREAMING,
        IngestionMethodConfig.determineIngestionMethod(config));
  }

  @Test
  public void testDetermineIngestionLoadMethod_validIngestionMethod_lowercase() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT, "snowpipe_stREAMING");
    assertEquals(
        IngestionMethodConfig.SNOWPIPE_STREAMING,
        IngestionMethodConfig.determineIngestionMethod(config));
  }

  /** These error tests are not going to enforce errors if they are not passed as configs. */
  @Test
  public void testErrorTolerance_AllowedValues() {
    Map<String, String> config = getConfig();
    config.put(ERRORS_TOLERANCE_CONFIG, SnowflakeSinkConnectorConfig.ErrorTolerance.ALL.toString());
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    Utils.validateConfig(config);

    config.put(
        ERRORS_TOLERANCE_CONFIG, SnowflakeSinkConnectorConfig.ErrorTolerance.NONE.toString());
    Utils.validateConfig(config);

    config.put(ERRORS_TOLERANCE_CONFIG, "all");
    Utils.validateConfig(config);
  }

  @Test
  public void testErrorTolerance_DisallowedValues() {
    Map<String, String> config = getConfig();
    config.put(ERRORS_TOLERANCE_CONFIG, "INVALID");
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT);
  }

  @Test
  public void testErrorLog_AllowedValues() {
    Map<String, String> config = getConfig();
    config.put(ERRORS_LOG_ENABLE_CONFIG, "true");
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    Utils.validateConfig(config);

    config.put(ERRORS_LOG_ENABLE_CONFIG, "FALSE");
    Utils.validateConfig(config);

    config.put(ERRORS_LOG_ENABLE_CONFIG, "TRUE");
    Utils.validateConfig(config);
  }

  @Test
  public void testErrorLog_DisallowedValues() {
    Map<String, String> config = getConfig();
    config.put(ERRORS_LOG_ENABLE_CONFIG, "INVALID");
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    validateConfigWithErrorContaining(
        config, SnowflakeSinkConnectorConfig.ERRORS_LOG_ENABLE_CONFIG);
  }

  // ---------- Streaming Buffer tests ---------- //
  @Test
  public void testStreamingEmptyFlushTime() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.remove(SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC);
  }

  @Test
  public void testStreamingFlushTimeSmall() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.put(
        SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC,
        (StreamingUtils.STREAMING_BUFFER_FLUSH_TIME_MINIMUM_SEC - 1) + "");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC);
  }

  @Test
  public void testStreamingFlushTimeNotNumber() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.put(SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC, "fdas");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC);
  }

  @Test
  public void testStreamingEmptyBufferSize() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.remove(SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES);
  }

  @Test
  public void testStreamingEmptyBufferCount() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.remove(SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS);
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS);
  }

  @Test
  public void testStreamingBufferCountNegative() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.put(SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS, "-1");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS);
  }

  @Test
  public void testStreamingBufferCountValue() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.put(SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS, "adssadsa");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS);
  }

  @Test
  public void testValidKeyAndValueConvertersForStreamingSnowpipe() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());

    COMMUNITY_CONVERTER_SUBSET.forEach(
        converter -> {
          config.put(
              SnowflakeSinkConnectorConfig.KEY_CONVERTER_CONFIG_FIELD,
              converter.getClass().toString());
          Utils.validateConfig(config);
        });

    COMMUNITY_CONVERTER_SUBSET.forEach(
        converter -> {
          config.put(
              SnowflakeSinkConnectorConfig.VALUE_CONVERTER_CONFIG_FIELD,
              converter.getClass().toString());
          Utils.validateConfig(config);
        });
  }

  @Test
  public void testInvalidKeyConvertersForStreamingSnowpipe() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());

    CUSTOM_SNOWFLAKE_CONVERTERS.forEach(
        converter -> {
          try {
            config.put(
                SnowflakeSinkConnectorConfig.KEY_CONVERTER_CONFIG_FIELD,
                converter.getClass().toString());
            config.put(
                SnowflakeSinkConnectorConfig.VALUE_CONVERTER_CONFIG_FIELD,
                "org.apache.kafka.connect.storage.StringConverter");

            Utils.validateConfig(config);
          } catch (SnowflakeKafkaConnectorException exception) {
            assert exception
                .getMessage()
                .contains(SnowflakeSinkConnectorConfig.KEY_CONVERTER_CONFIG_FIELD);
          }
        });
  }

  @Test
  public void testInvalidValueConvertersForStreamingSnowpipe() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());

    CUSTOM_SNOWFLAKE_CONVERTERS.forEach(
        converter -> {
          try {
            config.put(
                SnowflakeSinkConnectorConfig.KEY_CONVERTER_CONFIG_FIELD,
                "org.apache.kafka.connect.storage.StringConverter");
            config.put(
                SnowflakeSinkConnectorConfig.VALUE_CONVERTER_CONFIG_FIELD,
                converter.getClass().toString());

            Utils.validateConfig(config);
          } catch (SnowflakeKafkaConnectorException exception) {
            assert exception
                .getMessage()
                .contains(SnowflakeSinkConnectorConfig.VALUE_CONVERTER_CONFIG_FIELD);
          }
        });
  }

  @Test
  public void testInValidConfigFileTypeForSnowpipe() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.SNOWPIPE_STREAMING_FILE_VERSION, "3");
    validateConfigWithErrorContaining(
        config, SnowflakeSinkConnectorConfig.SNOWPIPE_STREAMING_FILE_VERSION);
  }

  @Test
  public void testValidFileTypesForSnowpipeStreaming() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());

    config.put(SnowflakeSinkConnectorConfig.SNOWPIPE_STREAMING_FILE_VERSION, "3");
    Utils.validateConfig(config);

    config.put(SnowflakeSinkConnectorConfig.SNOWPIPE_STREAMING_FILE_VERSION, "1");
    Utils.validateConfig(config);

    // lower case
    config.put(SnowflakeSinkConnectorConfig.SNOWPIPE_STREAMING_FILE_VERSION, "abcd");
    Utils.validateConfig(config);
  }

  @Test
  public void testInvalidSchematizationForSnowpipe() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE.toString());
    config.put(SnowflakeSinkConnectorConfig.ENABLE_SCHEMATIZATION_CONFIG, "true");
    validateConfigWithErrorContaining(config, IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
  }

  @Test
  public void testValidSchematizationForStreamingSnowpipe() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.put(SnowflakeSinkConnectorConfig.ENABLE_SCHEMATIZATION_CONFIG, "true");
    Utils.validateConfig(config);
  }

  @Test
  public void testSchematizationWithUnsupportedConverter() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.put(SnowflakeSinkConnectorConfig.ENABLE_SCHEMATIZATION_CONFIG, "true");
    config.put(
        SnowflakeSinkConnectorConfig.VALUE_CONVERTER_CONFIG_FIELD,
        "org.apache.kafka.connect.storage.StringConverter");
    validateConfigWithErrorContaining(config, "org.apache.kafka.connect.storage.StringConverter");
  }

  @Test
  public void testDisabledSchematizationWithUnsupportedConverter() {
    try {
      Map<String, String> config = getConfig();
      config.put(
          SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
          IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
      config.put(SnowflakeSinkConnectorConfig.ENABLE_SCHEMATIZATION_CONFIG, "false");
      config.put(
          SnowflakeSinkConnectorConfig.VALUE_CONVERTER_CONFIG_FIELD,
          "org.apache.kafka.connect.storage.StringConverter");
      Utils.validateConfig(config);
    } catch (SnowflakeKafkaConnectorException exception) {
      assert exception
          .getMessage()
          .contains(SnowflakeSinkConnectorConfig.ENABLE_SCHEMATIZATION_CONFIG);
    }
  }

  @Test
  public void testEnableOptimizeStreamingClientConfig() {
    Map<String, String> config = getConfig();
    config.put(
        SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
        IngestionMethodConfig.SNOWPIPE_STREAMING.toString());
    config.put(SnowflakeSinkConnectorConfig.ENABLE_STREAMING_CLIENT_OPTIMIZATION_CONFIG, "true");

    Utils.validateConfig(config);
  }

  @Test
  public void testInvalidEnableOptimizeStreamingClientConfig() {
    try {
      Map<String, String> config = getConfig();
      config.put(
          SnowflakeSinkConnectorConfig.INGESTION_METHOD_OPT,
          IngestionMethodConfig.SNOWPIPE.toString());
      config.put(SnowflakeSinkConnectorConfig.ENABLE_STREAMING_CLIENT_OPTIMIZATION_CONFIG, "true");

      Utils.validateConfig(config);
    } catch (SnowflakeKafkaConnectorException exception) {
      assert exception
          .getMessage()
          .contains(SnowflakeSinkConnectorConfig.ENABLE_STREAMING_CLIENT_OPTIMIZATION_CONFIG);
    }
  }

  @Test
  public void testInvalidEmptyConfig() {
    try {
      Map<String, String> config = new HashMap<>();
      Utils.validateConfig(config);
    } catch (SnowflakeKafkaConnectorException exception) {
      assert exception.getMessage().contains(SnowflakeSinkConnectorConfig.SNOWFLAKE_DATABASE);
      assert exception.getMessage().contains(SnowflakeSinkConnectorConfig.SNOWFLAKE_SCHEMA);
      assert exception.getMessage().contains(SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS);
      assert exception.getMessage().contains(SnowflakeSinkConnectorConfig.SNOWFLAKE_PRIVATE_KEY);
      assert exception.getMessage().contains(SnowflakeSinkConnectorConfig.SNOWFLAKE_USER);
      assert exception.getMessage().contains(SnowflakeSinkConnectorConfig.NAME);
      assert exception.getMessage().contains(SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC);
      assert exception.getMessage().contains(SnowflakeSinkConnectorConfig.SNOWFLAKE_URL);
      assert exception.getMessage().contains(SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES);
    }
  }

  // removes each of the following params iteratively to test if the log/exception has all the
  // expected removed params
  @Test
  public void testMultipleInvalidConfigs() {
    List<String> emptyParams =
        Arrays.asList(
            SnowflakeSinkConnectorConfig.SNOWFLAKE_DATABASE,
            SnowflakeSinkConnectorConfig.SNOWFLAKE_SCHEMA,
            SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS,
            SnowflakeSinkConnectorConfig.SNOWFLAKE_PRIVATE_KEY,
            SnowflakeSinkConnectorConfig.SNOWFLAKE_USER,
            SnowflakeSinkConnectorConfig.NAME,
            SnowflakeSinkConnectorConfig.SNOWFLAKE_URL,
            SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES);
    List<String> paramsToRemove = new ArrayList<String>();

    for (String param : emptyParams) {
      paramsToRemove.add(param);
      invalidConfigRunner(paramsToRemove);
    }
  }

  @Test
  public void testOAuthAuthenticator() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.AUTHENTICATOR_TYPE, Utils.OAUTH);
    config.put(SnowflakeSinkConnectorConfig.OAUTH_CLIENT_ID, "client_id");
    config.put(SnowflakeSinkConnectorConfig.OAUTH_CLIENT_SECRET, "client_secret");
    config.put(SnowflakeSinkConnectorConfig.OAUTH_REFRESH_TOKEN, "refresh_token");
    Utils.validateConfig(config);
  }

  @Test
  public void testInvalidAuthenticator() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.AUTHENTICATOR_TYPE, "invalid_authenticator");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.AUTHENTICATOR_TYPE);
  }

  @Test
  public void testEmptyClientId() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.AUTHENTICATOR_TYPE, Utils.OAUTH);
    config.put(SnowflakeSinkConnectorConfig.OAUTH_CLIENT_SECRET, "client_secret");
    config.put(SnowflakeSinkConnectorConfig.OAUTH_REFRESH_TOKEN, "refresh_token");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.OAUTH_CLIENT_ID);
  }

  @Test
  public void testEmptyClientSecret() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.AUTHENTICATOR_TYPE, Utils.OAUTH);
    config.put(SnowflakeSinkConnectorConfig.OAUTH_CLIENT_ID, "client_id");
    config.put(SnowflakeSinkConnectorConfig.OAUTH_REFRESH_TOKEN, "refresh_token");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.OAUTH_CLIENT_SECRET);
  }

  @Test
  public void testEmptyRefreshToken() {
    Map<String, String> config = getConfig();
    config.put(SnowflakeSinkConnectorConfig.AUTHENTICATOR_TYPE, Utils.OAUTH);
    config.put(SnowflakeSinkConnectorConfig.OAUTH_CLIENT_ID, "client_id");
    config.put(SnowflakeSinkConnectorConfig.OAUTH_CLIENT_SECRET, "client_secret");
    validateConfigWithErrorContaining(config, SnowflakeSinkConnectorConfig.OAUTH_REFRESH_TOKEN);
  }

  private void invalidConfigRunner(List<String> paramsToRemove) {
    Map<String, String> config = getConfig();
    for (String configParam : paramsToRemove) {
      config.remove(configParam);
      validateConfigWithErrorContaining(config, configParam);
    }
  }

  private void validateConfigWithErrorContaining(Map<String, String> config, String error) {
    Exception exception =
        assertThrows(SnowflakeKafkaConnectorException.class, () -> Utils.validateConfig(config));
    assert exception.getMessage().contains(error);
  }

  private void validateConfigWithExceptionCode(Map<String, String> config, String code) {
    SnowflakeKafkaConnectorException exception =
        assertThrows(SnowflakeKafkaConnectorException.class, () -> Utils.validateConfig(config));
    assert exception.getCode().equals(code);
  }
}
