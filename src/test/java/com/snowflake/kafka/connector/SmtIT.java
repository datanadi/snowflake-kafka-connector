package com.snowflake.kafka.connector;

import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC;
import static com.snowflake.kafka.connector.SnowflakeSinkConnectorConfig.NAME;
import static org.apache.kafka.connect.runtime.ConnectorConfig.CONNECTOR_CLASS_CONFIG;
import static org.apache.kafka.connect.runtime.ConnectorConfig.KEY_CONVERTER_CLASS_CONFIG;
import static org.apache.kafka.connect.runtime.ConnectorConfig.TASKS_MAX_CONFIG;
import static org.apache.kafka.connect.runtime.ConnectorConfig.TRANSFORMS_CONFIG;
import static org.apache.kafka.connect.runtime.ConnectorConfig.VALUE_CONVERTER_CLASS_CONFIG;
import static org.apache.kafka.connect.sink.SinkConnector.TOPICS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.snowflake.kafka.connector.internal.InternalUtils;
import com.snowflake.kafka.connector.internal.TestUtils;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.storage.StringConverter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SmtIT extends ConnectClusterBaseIT {

  private static final String SMT_TOPIC = TestUtils.randomTableName();
  private static final String SMT_CONNECTOR = "SMT_CONNECTOR";

  @BeforeAll
  public void createConnector() {
    connectCluster.kafka().createTopic(SMT_TOPIC);
    connectCluster.configureConnector(SMT_CONNECTOR, smtProperties());
    waitForConnectorRunning(SMT_CONNECTOR);
  }

  @AfterAll
  public void deleteConnector() {
    connectCluster.deleteConnector(SMT_CONNECTOR);
    connectCluster.kafka().deleteTopic(SMT_TOPIC);

    TestUtils.dropTable(SMT_TOPIC);
  }

  private Map<String, String> smtProperties() {
    Map<String, String> config = TestUtils.getConf();

    config.put(CONNECTOR_CLASS_CONFIG, SnowflakeSinkConnector.class.getName());
    config.put(NAME, SMT_CONNECTOR);
    config.put(TOPICS_CONFIG, SMT_TOPIC);
    config.put(BUFFER_FLUSH_TIME_SEC, "10");

    config.put(TASKS_MAX_CONFIG, TASK_NUMBER.toString());
    config.put(KEY_CONVERTER_CLASS_CONFIG, StringConverter.class.getName());
    config.put(VALUE_CONVERTER_CLASS_CONFIG, JsonConverter.class.getName());
    config.put("value.converter.schemas.enable", "false");

    config.put(TRANSFORMS_CONFIG, "extractField");
    config.put(
        "transforms.extractField.type", "org.apache.kafka.connect.transforms.ExtractField$Value");
    config.put("transforms.extractField.field", "message");

    return config;
  }

  @Test
  void testIfSmtRetuningNullsIngestDataCorrectly() {
    Stream.iterate(0, x -> x + 1)
        .limit(10)
        .flatMap(v -> Stream.of("{}", String.format("{\"message\":\"value_%d\"}", v)))
        .forEach(message -> connectCluster.kafka().produce(SMT_TOPIC, message));

    await()
        .timeout(Duration.ofMinutes(5))
        .untilAsserted(
            () -> {
              assertThat(numberOfRecordsIn(SMT_TOPIC)).isEqualTo(20);
            });
  }

  private static int numberOfRecordsIn(String smtTopic) throws SQLException {
    try {
      return InternalUtils.resultSize(TestUtils.showTable(smtTopic));
    } catch (IllegalStateException e) {
      return 0;
    }
  }
}
