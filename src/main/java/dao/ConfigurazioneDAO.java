package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import beans.Configurazione;
import beans.DettaglioConfigurazione;
import beans.ProdottoComposto;
import beans.ProdottoSemplice;
import beans.Sku;
import beans.Utente;

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
	
	
	public Configurazione getConfigurazioneCompleta(int idConfig) throws SQLException {
		Configurazione config = null;
		
		// estraiamo la configurazione
		String qConfig = "SELECT c.id, c.nome, c.username_cliente, c.codice_prodotto_radice, c.data_creazione, c.prezzo_totale, p.nome AS nome_padre " +
		                 "FROM Configurazione c JOIN Prodotto p ON c.codice_prodotto_radice = p.codice " +
		                 "WHERE c.id = ?";
		
		try (PreparedStatement ps = con.prepareStatement(qConfig)) {
			ps.setInt(1, idConfig);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					config = new Configurazione();
					config.setId(rs.getInt("id"));
					config.setNome(rs.getString("nome"));
					config.setDataCreazione(rs.getDate("data_creazione"));
					config.setPrezzoTotale(rs.getDouble("prezzo_totale"));
					
					// mettiamo utente nel bean
					Utente cliente = new Utente();
					cliente.setUsername(rs.getString("username_cliente"));
					config.setCliente(cliente);
					
					// mettiamo utente padre
					ProdottoComposto radice = new ProdottoComposto();
					radice.setCodice(rs.getInt("codice_prodotto_radice"));
					radice.setNome(rs.getString("nome_padre"));
					config.setProdotto(radice);
				}
			}
		}

		// se la configurazione base esiste, andiamo a cercare tutte le SKU scelte
		if (config != null) {
			List<DettaglioConfigurazione> listaDettagli = new ArrayList<>();
			String qDettagli = "SELECT p.codice AS id_ps, p.nome AS nome_ps, s.codice AS id_sku, s.nome AS nome_sku, s.foto, s.descrizione_tecnica, s.prezzo " +
			                   "FROM Dettaglio d " +
			                   "JOIN Prodotto p ON d.cod_prod_s = p.codice " +
			                   "JOIN Sku s ON d.cod_sku = s.codice " +
			                   "WHERE d.id_config = ?";
			
			try (PreparedStatement ps = con.prepareStatement(qDettagli)) {
				ps.setInt(1, idConfig);
				try (ResultSet rs = ps.executeQuery()) {
					// ccorriamo tutte le scelte fatte dall'utente
					while (rs.next()) {
						DettaglioConfigurazione dett = new DettaglioConfigurazione();
						
						// ricostruiamo il prodotto semplice
						ProdottoSemplice psObj = new ProdottoSemplice();
						psObj.setCodice(rs.getInt("id_ps"));
						psObj.setNome(rs.getString("nome_ps"));
						dett.setComponente(psObj);
						
						// ricostruiamo anche le sku del prod
						Sku skuObj = new Sku();
						skuObj.setCodice(rs.getInt("id_sku"));
						skuObj.setNome(rs.getString("nome_sku"));
						skuObj.setFoto(rs.getString("foto"));
						skuObj.setDescrizione(rs.getString("descrizione_tecnica"));
						skuObj.setPrezzo(rs.getInt("prezzo"));
						dett.setSkuSelezionata(skuObj);
						
						listaDettagli.add(dett);
					}
				}
			}
			// inseriamo la lista di dettagli dentro la configurazione principale
			config.setDettagli(listaDettagli);
		}
		
		return config;
	}
	
	
	
}