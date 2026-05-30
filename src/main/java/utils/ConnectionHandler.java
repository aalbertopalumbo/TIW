package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionHandler {

	public static Connection getConnection(String address, String port, String dbName, String user, String password) throws SQLException {
		
		Connection connection = null;
		
		try {
			// carico il driver di MySQL
			Class.forName("com.mysql.cj.jdbc.Driver");
			
			// costruisce l'URL di connessione
			String url = "jdbc:mysql://" + address + ":" + port + "/" + dbName + "?serverTimezone=UTC";
			
			// apre la connessione
			connection = DriverManager.getConnection(url, user, password);
			
		} catch (ClassNotFoundException e) {
			throw new SQLException("driver del database non trovato", e);
		}
		
		return connection;
	}

	public static void closeConnection(Connection connection) {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
}