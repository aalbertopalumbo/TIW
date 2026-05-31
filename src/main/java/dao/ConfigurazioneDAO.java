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
		
		String insertConfig = "INSERT INTO Configurazione (nome, username_cliente, cod_prodotto_radice, data_creazione, prezzo_totale) VALUES (?, ?, ?, CURDATE(), ?)";
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
		String qConfig = "SELECT c.id, c.nome, c.username_cliente, c.cod_prodotto_radice, c.data_creazione, c.prezzo_totale, p.nome AS nome_padre " +
		                 "FROM Configurazione c JOIN Prodotto p ON c.cod_prodotto_radice = p.codice " +
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
					radice.setCodice(rs.getInt("cod_prodotto_radice"));
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
	
	
	public List<Configurazione> getConfigurazioniByUtente(String username) throws SQLException {
		List<Configurazione> lista = new ArrayList<>();
		
		// prendiamo tutte le configurazioni di un utente
		String query = "SELECT c.id, c.nome, c.data_creazione, c.data_ultima_modifica, c.prezzo_totale, p.nome AS nome_padre " +
		               "FROM Configurazione c " +
		               "JOIN Prodotto p ON c.cod_prodotto_radice = p.codice " +
		               "WHERE c.username_cliente = ? " +
		               "ORDER BY c.data_creazione DESC";
		
		try (PreparedStatement ps = con.prepareStatement(query)) {
			ps.setString(1, username);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Configurazione config = new Configurazione();
					config.setId(rs.getInt("id"));
					config.setNome(rs.getString("nome"));
					config.setDataCreazione(rs.getDate("data_creazione"));
					config.setDataUltimaModifica(rs.getDate("data_ultima_modifica"));
					config.setPrezzoTotale(rs.getDouble("prezzo_totale"));
					
					ProdottoComposto radice = new ProdottoComposto();
					radice.setNome(rs.getString("nome_padre"));
					config.setProdotto(radice);
					
					lista.add(config);
				}
			}
		}
		return lista;
	}
	
	
	public void eliminaConfigurazione(int idConfig) throws SQLException {
	    String query = "DELETE FROM Configurazione WHERE id = ?";
	    try (PreparedStatement ps = con.prepareStatement(query)) {
	        ps.setInt(1, idConfig);
	        ps.executeUpdate();
	    }
	}

	public int clonaConfigurazione(int idConfig) throws SQLException {
	    int nuovoId = -1;

	    String qConfig = "SELECT nome, username_cliente, cod_prodotto_radice, prezzo_totale FROM Configurazione WHERE id = ?";
	    String insConfig = "INSERT INTO Configurazione (nome, username_cliente, cod_prodotto_radice, data_creazione, prezzo_totale) VALUES (?, ?, ?, CURDATE(), ?)";
	    String qDettagli = "SELECT cod_prod_s, cod_sku FROM Dettaglio WHERE id_config = ?";
	    String insDettaglio = "INSERT INTO Dettaglio (id_config, cod_prod_s, cod_sku) VALUES (?, ?, ?)";

	    try {
	        con.setAutoCommit(false);

	        try (PreparedStatement psConfig = con.prepareStatement(qConfig)) {
	            psConfig.setInt(1, idConfig);
	            try (ResultSet rs = psConfig.executeQuery()) {
	                if (!rs.next()) throw new SQLException("Configurazione non trovata");

	                try (PreparedStatement psIns = con.prepareStatement(insConfig, Statement.RETURN_GENERATED_KEYS)) {
	                    psIns.setString(1, rs.getString("nome") + " (copia)");
	                    psIns.setString(2, rs.getString("username_cliente"));
	                    psIns.setInt(3, rs.getInt("cod_prodotto_radice"));
	                    psIns.setDouble(4, rs.getDouble("prezzo_totale"));
	                    psIns.executeUpdate();

	                    try (ResultSet keys = psIns.getGeneratedKeys()) {
	                        if (keys.next()) nuovoId = keys.getInt(1);
	                        else throw new SQLException("Clonazione fallita");
	                    }
	                }
	            }
	        }

	        try (PreparedStatement psDett = con.prepareStatement(qDettagli)) {
	            psDett.setInt(1, idConfig);
	            try (ResultSet rs = psDett.executeQuery()) {
	                try (PreparedStatement psInsDett = con.prepareStatement(insDettaglio)) {
	                    while (rs.next()) {
	                        psInsDett.setInt(1, nuovoId);
	                        psInsDett.setInt(2, rs.getInt("cod_prod_s"));
	                        psInsDett.setInt(3, rs.getInt("cod_sku"));
	                        psInsDett.addBatch();
	                    }
	                    psInsDett.executeBatch();
	                }
	            }
	        }

	        con.commit();
	    } catch (SQLException e) {
	        con.rollback();
	        throw e;
	    } finally {
	        con.setAutoCommit(true);
	    }

	    return nuovoId;
	}
	
	
}
	
	
	
