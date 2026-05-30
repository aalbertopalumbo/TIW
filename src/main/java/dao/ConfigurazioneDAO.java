package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public class ConfigurazioneDAO {
	
	private Connection con;

	public ConfigurazioneDAO(Connection connection) {
		this.con = connection;
	}

	// configurazione con id per fare redirect a dettaglio configurazione
	public int salvaConfigurazione(String nomeConfig, String username, int codiceRadice, double prezzoTotale, Map<Integer, Integer> scelteSku) throws SQLException {
		int idConfigurazione = -1;
		
		String insertConfig = "INSERT INTO Configurazione (nome, username_cliente, codice_prodotto_radice, data_creazione, prezzo_totale) VALUES (?, ?, ?, CURDATE(), ?)";
		String insertDettaglio = "INSERT INTO Dettaglio (id_config, cod_prod_s, cod_sku) VALUES (?, ?, ?)";

		try {
			// transazione
			con.setAutoCommit(false);

			// salviamo la configurazione e ne prendiamo l'id (il cliente non sa l'id, serve per redirect)
			try (PreparedStatement psConfig = con.prepareStatement(insertConfig, Statement.RETURN_GENERATED_KEYS)) {
				psConfig.setString(1, nomeConfig);
				psConfig.setString(2, username);
				psConfig.setInt(3, codiceRadice);
				psConfig.setDouble(4, prezzoTotale);
				psConfig.executeUpdate();

				try (ResultSet rsKeys = psConfig.getGeneratedKeys()) {
					if (rsKeys.next()) {
						idConfigurazione = rsKeys.getInt(1);
					} else {
						throw new SQLException("Creazione configurazione fallita.");
					}
				}
			}

			// salviamo le sku
			// per ogni prodotto semplice nel prodotto composto, associo le sku
			try (PreparedStatement psDettaglio = con.prepareStatement(insertDettaglio)) {
				for (Map.Entry<Integer, Integer> entry : scelteSku.entrySet()) {
					psDettaglio.setInt(1, idConfigurazione);
					psDettaglio.setInt(2, entry.getKey());   // codice del prodotto semplice
					psDettaglio.setInt(3, entry.getValue()); // codice della sku associata
					psDettaglio.addBatch();
				}
				psDettaglio.executeBatch();
			}

			con.commit();

			// fine transazione
			con.commit();
			
		} catch (SQLException e) {
			con.rollback();
			throw e;
		} finally {
			con.setAutoCommit(true);
		}
		
		return idConfigurazione;
	}
}