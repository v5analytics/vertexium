package org.vertexium.sql;

import org.vertexium.VertexiumException;
import org.vertexium.util.VertexiumLogger;
import org.vertexium.util.VertexiumLoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SqlGraphDDL {
    private static final VertexiumLogger LOGGER = VertexiumLoggerFactory.getLogger(SqlGraphDDL.class);
    private static final String BIG_BIN_COLUMN_TYPE = "LONGBLOB";
    private static final String GET_TABLES_TABLE_NAME_COLUMN = "TABLE_NAME";

    // MySQL's limit is 767 (http://dev.mysql.com/doc/refman/5.7/en/innodb-restrictions.html)
    private static final int ID_VARCHAR_SIZE = 767;

    // Oracle's limit is 4000 (https://docs.oracle.com/cd/B28359_01/server.111/b28320/limits001.htm)
    // MySQL's limit is 65,535 (http://dev.mysql.com/doc/refman/5.7/en/char.html)
    // H2's limit is Integer.MAX_VALUE (http://www.h2database.com/html/datatypes.html#varchar_type)
    private static final int VARCHAR_SIZE = 4000;

    public static void create(DataSource dataSource, SqlGraphConfiguration graphConfig) {
        createMapTable(
                dataSource,
                graphConfig.tableNameWithPrefix(SqlGraphConfiguration.VERTEX_TABLE_NAME),
                BIG_BIN_COLUMN_TYPE
        );
        createMapTable(
                dataSource,
                graphConfig.tableNameWithPrefix(SqlGraphConfiguration.EDGE_TABLE_NAME),
                BIG_BIN_COLUMN_TYPE,
                "in_vertex_id varchar(" + ID_VARCHAR_SIZE + "), out_vertex_id varchar(" + ID_VARCHAR_SIZE + ")"
        );
        createColumnIndexes(
                dataSource,
                graphConfig.tableNameWithPrefix(SqlGraphConfiguration.EDGE_TABLE_NAME),
                "in_vertex_id", "out_vertex_id"
        );
        createMapTable(
                dataSource,
                graphConfig.tableNameWithPrefix(SqlGraphConfiguration.METADATA_TABLE_NAME),
                BIG_BIN_COLUMN_TYPE
        );
        createStreamingPropertiesTable(
                dataSource,
                graphConfig.tableNameWithPrefix(SqlGraphConfiguration.STREAMING_PROPERTIES_TABLE_NAME)
        );
        createExtendedDataTable(
                dataSource,
                graphConfig.tableNameWithPrefix(SqlGraphConfiguration.EXTENDED_DATA_TABLE_NAME)
        );
    }

    private static void createMapTable(DataSource dataSource, String tableName, String valueColumnType) {
        createMapTable(dataSource, tableName, valueColumnType, "");
    }

    private static void createMapTable(DataSource dataSource, String tableName, String valueColumnType, String additionalColumns) {
        if (!additionalColumns.isEmpty()) {
            additionalColumns = ", " + additionalColumns;
        }
        String sql = String.format("CREATE TABLE IF NOT EXISTS %s (%s varchar(" + ID_VARCHAR_SIZE + ") primary key, %s %s not null %s)",
                tableName,
                SqlGraphConfiguration.KEY_COLUMN_NAME,
                SqlGraphConfiguration.VALUE_COLUMN_NAME,
                valueColumnType,
                additionalColumns);
        runSql(dataSource, sql, tableName);
    }

    private static void createStreamingPropertiesTable(DataSource dataSource, String tableName) {
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s (" +
                        "%s varchar(" + ID_VARCHAR_SIZE + ") primary key," +
                        "%s %s not null," +
                        "%s varchar(" + VARCHAR_SIZE + ") not null," +
                        "%s bigint not null" +
                        ")",
                tableName,
                SqlStreamingPropertyTable.KEY_COLUMN_NAME,
                SqlStreamingPropertyTable.VALUE_COLUMN_NAME,
                BIG_BIN_COLUMN_TYPE,
                SqlStreamingPropertyTable.VALUE_TYPE_COLUMN_NAME,
                SqlStreamingPropertyTable.VALUE_LENGTH_COLUMN_NAME
        );
        runSql(dataSource, sql, tableName);
    }

    private static void createExtendedDataTable(DataSource dataSource, String tableName) {
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s (" +
                        "%s varchar(" + VARCHAR_SIZE + ") not null," +
                        "%s varchar(" + VARCHAR_SIZE + ") not null," +
                        "%s varchar(" + VARCHAR_SIZE + ") not null," +
                        "%s varchar(" + VARCHAR_SIZE + ") not null," +
                        "%s varchar(" + VARCHAR_SIZE + ") not null," +
                        "%s varchar(" + VARCHAR_SIZE + ")," +
                        "%s %s not null," +
                        "%s bigint not null," +
                        "%s varchar(" + VARCHAR_SIZE + ") not null" +
                        ")",
                tableName,
                SqlExtendedDataTable.ELEMENT_TYPE_COLUMN_NAME,
                SqlExtendedDataTable.ELEMENT_ID_COLUMN_NAME,
                SqlExtendedDataTable.TABLE_NAME_COLUMN_NAME,
                SqlExtendedDataTable.ROW_ID_COLUMN_NAME,
                SqlExtendedDataTable.COLUMN_COLUMN_NAME,
                SqlExtendedDataTable.KEY_COLUMN_NAME,
                SqlExtendedDataTable.VALUE_COLUMN_NAME,
                BIG_BIN_COLUMN_TYPE,
                SqlExtendedDataTable.TIMESTAMP_COLUMN_NAME,
                SqlExtendedDataTable.VISIBILITY_COLUMN_NAME
        );
        runSql(dataSource, sql, tableName);
    }

    private static void createColumnIndexes(DataSource dataSource, String tableName, String... columnNames) {
        for (String columnName : columnNames) {
            String sql = String.format(
                    "create index idx_%s_%s on %s (%s);", tableName, columnName, tableName, columnName);
            runSql(dataSource, sql, tableName);
        }
    }

    private static void runSql(DataSource dataSource, String sql, String tableName) {
        try {
            try (Connection connection = dataSource.getConnection()) {
                if (!doesTableExist(connection, tableName)) {
                    LOGGER.info("creating table %s (sql: %s)", tableName, sql);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new VertexiumException("Could not create SQL table: " + tableName + " (sql: " + sql + ")", ex);
        }
    }

    private static boolean doesTableExist(Connection connection, String tableName) throws SQLException {
        ResultSet tables = connection.getMetaData().getTables(null, null, "%", null);
        while (tables.next()) {
            if (tableName.equalsIgnoreCase(tables.getString(GET_TABLES_TABLE_NAME_COLUMN))) {
                return true;
            }
        }
        return false;
    }
}
