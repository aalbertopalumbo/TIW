package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import beans.Utente;

public class UtenteDAO {
	
	private Connection connection;
	
	public UtenteDAO(Connection connection) {
		this.connection = connection;
	}

	
	public Utente checkLogin(String username, String password) throws SQLException {
		
		Utente user = null;
		String query = "SELECT username, nome, cognome, password, ruolo FROM Utente WHERE username = ? AND password = ?";
		
		try (PreparedStatement pstatement = connection.prepareStatement(query);) {
			pstatement.setString(1, username);
			pstatement.setString(2, password);
			try (ResultSet result = pstatement.executeQuery();) {
				// if result.next serve a dire che c'è una riga e prendiamo la prima
				if (result.next()) {
					user = new Utente();
					user.setUsername(result.getString("username"));
					user.setPassword(result.getString("password"));
					user.setNome(result.getString("nome"));
					user.setCognome(result.getString("cognome"));
					user.setRuolo(result.getString("ruolo").toUpperCase().equals("CLIENTE") ? Utente.RuoloUtente.CLIENTE : Utente.RuoloUtente.FORNITORE);
				}
			}
		}
		return user;
	}
	
	
	public boolean registraUtente(Utente user) throws SQLException {
		
		String checkQuery = "SELECT * FROM Utente WHERE username = ?";
		try (PreparedStatement pstatement = connection.prepareStatement(checkQuery)) {
			pstatement.setString(1, user.getUsername());
			try (ResultSet result = pstatement.executeQuery()) {
				if (result.next()) {
					return false; // utente già presente
				}
			}
		}
			
		String query = "INSERT INTO Utente (username, nome, cognome, password, ruolo) VALUES (?, ?, ?, ?, ?)";
		
		try (PreparedStatement pstatement = connection.prepareStatement(query)) {
			
			pstatement.setString(1, user.getUsername());
			pstatement.setString(2, user.getNome());
			pstatement.setString(3, user.getCognome());
			pstatement.setString(4, user.getPassword());
			
			pstatement.setString(5, user.getRuolo().name()); 

			int success = pstatement.executeUpdate();
			
			// se ha modificato almeno una riga la registrazione è andata
			return success > 0;
		}
	}
}
