package sqlline;

import com.github.vlsi.mat.calcite.cli.MCPApplication;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class MCPSqlLine extends SqlLine {
  void setAppConfig(Application application) {
    super.setAppConfig(new MCPApplication());
  }

  public void setUpConnection(final MCPDatabaseConnection mcpDatabaseConnection) throws SQLException {
    getDatabaseConnections().setConnection(mcpDatabaseConnection);
    Connection connection = getDatabaseConnection().getConnection();
    mcpDatabaseConnection.meta = (DatabaseMetaData) Proxy.newProxyInstance(
        DatabaseMetaData.class.getClassLoader(),
        new Class[]{DatabaseMetaData.class},
        new DatabaseMetaDataHandler(connection.getMetaData()));
  }

  public void removeConnection(final MCPDatabaseConnection mcpDatabaseConnection) {
    getDatabaseConnections().removeConnection(mcpDatabaseConnection);
  }
}
