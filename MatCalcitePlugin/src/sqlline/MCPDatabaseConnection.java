package sqlline;

import com.github.vlsi.mat.calcite.CalciteDataSource;

import org.eclipse.mat.snapshot.ISnapshot;

import java.sql.SQLException;

public class MCPDatabaseConnection extends DatabaseConnection {
  private final ISnapshot snapshot;

  public MCPDatabaseConnection(MCPSqlLine sqlLine, String filename, ISnapshot snapshot) {
    super(sqlLine, null, filename, "username", "password", null);
    this.snapshot = snapshot;
  }

  @Override
  boolean connect() throws SQLException {
    connection = CalciteDataSource.getConnection(snapshot);
    return true;
  }
}
