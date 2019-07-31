/*
 * Copyright (c) 2019 Snowflake Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.snowflake.kafka.connector.internal;

import com.snowflake.client.jdbc.SnowflakeDriver;
import com.snowflake.kafka.connector.Utils;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.databind
    .ObjectMapper;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.databind.node
    .ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;


class TestUtils
{
  //test profile properties
  private static final String USER = "user";
  private static final String DATABASE = "database";
  private static final String SCHEMA = "schema";
  private static final String HOST = "host";
  private static final String WAREHOUSE = "warehouse";
  private static final String PRIVATE_KEY = "private_key";
  final static String TEST_CONNECTOR_NAME = "TEST_CONNECTOR";


  //profile path
  private final static String PROFILE_PATH = "profile.json";

  private final static ObjectMapper mapper = new ObjectMapper();

  private static Connection conn = null;

  private static Map<String, String> conf = null;

  private static SnowflakeURL url = null;


  /**
   * load all login info from profile
   */
  private static void init()
  {

    ObjectNode profile;
    try
    {
      profile = (ObjectNode) mapper.readTree(
          new String(Files.readAllBytes(Paths.get(PROFILE_PATH)))
      );
    } catch (IOException e)
    {
      throw new RuntimeException(e);
    }

    conf = new HashMap<>();

    conf.put(Utils.SF_USER, profile.get(USER).asText());
    conf.put(Utils.SF_DATABASE, profile.get(DATABASE).asText());
    conf.put(Utils.SF_SCHEMA, profile.get(SCHEMA).asText());
    conf.put(Utils.SF_URL, profile.get(HOST).asText());
    conf.put(Utils.SF_WAREHOUSE, profile.get(WAREHOUSE).asText());
    conf.put(Utils.SF_PRIVATE_KEY, profile.get(PRIVATE_KEY).asText());
    conf.put(Utils.NAME, TEST_CONNECTOR_NAME);

    //enable test query mark
    conf.put(Utils.TASK_ID, "");
  }


  /**
   * read private key string from test profile
   * @return a string value represents private key
   */
  static String getKeyString()
  {
    return getConf().get(Utils.SF_PRIVATE_KEY);
  }

  /**
   * Create snowflake jdbc connection
   *
   * @return jdbc connection
   * @throws Exception when meeting error
   */
  private static Connection getConnection() throws Exception
  {
    if (conn != null)
    {
      return conn;
    }

    Properties properties = InternalUtils.createProperties(getConf());

    SnowflakeURL url = new SnowflakeURL(getConf().get(Utils.SF_URL));

    conn = new SnowflakeDriver().connect(url.getJdbcUrl(), properties);

    return conn;
  }

  /**
   * read conf file
   *
   * @return a map of parameters
   */
  static Map<String, String> getConf()
  {
    if (conf == null)
    {
      init();
    }
    return new HashMap<>(conf);
  }

  /**
   * execute sql query
   *
   * @param query sql query string
   * @return result set
   */
  static ResultSet executeQuery(String query)
  {
    try
    {
      Statement statement = getConnection().createStatement();

      return statement.executeQuery(query);
    }
    //if ANY exceptions occur, an illegal state has been reached
    catch (Exception e)
    {
      throw new IllegalStateException(e);
    }
  }

  /**
   * drop a table
   *
   * @param tableName table name
   */
  static void dropTable(String tableName)
  {
    String query = "drop table if exists " + tableName;

    executeQuery(query);
  }

  /**
   * Select * from table
   */
  static ResultSet showTable(String tableName)
  {
    String query = "select * from " + tableName;

    return executeQuery(query);
  }

  /**
   * create a random name for test
   *
   * @param objectName e.g. table, stage, pipe
   * @return kafka_connector_test_objectName_randomNum
   */
  private static String randomName(String objectName)
  {
    long num = Math.abs(new Random().nextLong());
    return "kafka_connector_test_" + objectName + "_" + num;
  }

  /**
   * @return a random table name
   */
  static String randomTableName()
  {
    return randomName("table");
  }

  /**
   * @return a random stage name
   */
  static String randomStageName()
  {
    return randomName("stage");
  }

  /**
   * @return a random pipe name
   */
  static String randomPipeName()
  {
    return randomName("pipe");
  }

  /**
   * retrieve one properties
   *
   * @param name property name
   * @return property value
   */
  private static String get(String name)
  {
    Map<String, String> properties = getConf();

    return properties.get(name);
  }

  static SnowflakeURL getUrl()
  {
    if (url == null)
    {
      url = new SnowflakeURL(get(Utils.SF_URL));
    }
    return url;
  }

  /**
   * Check Snowflake Error Code in test
   * @param error Snowflake error
   * @param func function throwing exception
   * @return true is error code is correct, otherwise, false
   */
  static boolean assertError(SnowflakeErrors error, Runnable func)
  {
    try
    {
      func.run();
    }
    catch (SnowflakeKafkaConnectorException e)
    {
      return e.checkErrorCode(error);
    }
    return false;
  }


  /**
   *
   * @return snowflake connection for test
   */
  static SnowflakeConnectionService getConnectionService()
  {
    return SnowflakeConnectionServiceFactory.builder().setProperties(getConf()).build();
  }

  /**
   * retrieve table size from snowflake
   * @param tableName table name
   * @return size of table
   * @throws SQLException if meet connection issue
   */
  static int tableSize(String tableName) throws SQLException
  {
    String query = "show tables like '" + tableName + "'";
    ResultSet result = executeQuery(query);

    if(result.next())
    {
      return result.getInt("rows");
    }
    return 0;
  }
}
