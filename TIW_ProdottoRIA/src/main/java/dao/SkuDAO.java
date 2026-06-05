package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import beans.Sku;

public class SkuDAO {
	private Connection con;

	public SkuDAO(Connection connection) {
		this.con = connection;
	}


	public List<Sku> trovaTutteLeSku() throws SQLException {
		
		List<Sku> skus = new ArrayList<>();
		
		// prendiamo tutto in ordine dec
		String query = "SELECT codice, nome, foto, descrizione_tecnica, prezzo FROM Sku ORDER BY codice DESC";
		
		try (PreparedStatement pstatement = con.prepareStatement(query);
		     ResultSet result = pstatement.executeQuery()) {
			
			while (result.next()) {
				Sku sku = new Sku();
				sku.setCodice(result.getInt("codice"));
				sku.setNome(result.getString("nome"));
				sku.setFoto(result.getString("foto"));
				sku.setDescrizione(result.getString("descrizione_tecnica")); 
				sku.setPrezzo(result.getInt("prezzo"));
				
				skus.add(sku);
			}
		}
		return skus;
	}
	
	public Sku findSkuById(int codice) throws SQLException {
		Sku sku = null;
		
		String query = "SELECT codice, nome, foto, descrizione_tecnica, prezzo FROM Sku WHERE codice = ?";
		
		try (PreparedStatement pstatement = con.prepareStatement(query)) {
			pstatement.setInt(1, codice);
			
			try (ResultSet result = pstatement.executeQuery()) {
				if (result.next()) {
					sku = new Sku();
					sku.setCodice(result.getInt("codice"));
					sku.setNome(result.getString("nome"));
					sku.setFoto(result.getString("foto"));
					sku.setDescrizione(result.getString("descrizione_tecnica")); 
					sku.setPrezzo(result.getInt("prezzo"));
				}
			}
		}
		return sku;
	}
	
	// per trovare tutte le Sku associate a un prodotto semplice
		public List<Sku> findSkuByProdotto(int codiceProdottoSemplice) throws SQLException {
			List<Sku> skusAssociate = new ArrayList<>();
			
			// prendiamo tutte le info delle sku per un prodotto
			String query = "SELECT s.codice, s.nome, s.foto, s.descrizione_tecnica, s.prezzo " +
			               "FROM Sku s JOIN Realizzazione r ON s.codice = r.cod_sku " +
			               "WHERE r.cod_prod_s = ? " +
			               "ORDER BY s.codice DESC";
			
			try (PreparedStatement pstatement = con.prepareStatement(query)) {
				pstatement.setInt(1, codiceProdottoSemplice);
				
				try (ResultSet result = pstatement.executeQuery()) {
					while (result.next()) {
						Sku sku = new Sku();
						sku.setCodice(result.getInt("codice"));
						sku.setNome(result.getString("nome"));
						sku.setFoto(result.getString("foto"));
						sku.setDescrizione(result.getString("descrizione_tecnica"));
						sku.setPrezzo(result.getInt("prezzo"));
						
						skusAssociate.add(sku);
					}
				}
			}
			
			return skusAssociate;
		}
	
	public void inserisciSku(int codice, String nome, String foto, String descrizione, int prezzo) throws SQLException {
		
		String query = "INSERT INTO Sku (codice, nome, foto, descrizione_tecnica, prezzo) VALUES (?, ?, ?, ?, ?)";
		
		try (PreparedStatement pstatement = con.prepareStatement(query)) {
			pstatement.setInt(1, codice);
			pstatement.setString(2, nome);
			pstatement.setString(3, foto);
			pstatement.setString(4, descrizione);
			pstatement.setInt(5, prezzo);
			
			pstatement.executeUpdate();
		}
	}
	
	
	public List<Sku> cercaSku(String keyword) throws SQLException {
		
		List<Sku> risultati = new ArrayList<>();
		String query = "SELECT codice, nome, foto, descrizione_tecnica, prezzo " +
		               "FROM Sku " +
		               "WHERE LOWER(nome) LIKE LOWER(?) OR LOWER(descrizione_tecnica) LIKE LOWER(?) " +
		               "ORDER BY codice DESC";
		
		try (PreparedStatement pstatement = con.prepareStatement(query)) {
			String searchPattern = "%" + keyword + "%";
			pstatement.setString(1, searchPattern);
			pstatement.setString(2, searchPattern);
			
			try (ResultSet result = pstatement.executeQuery()) {
				while (result.next()) {
					Sku sku = new Sku();
					sku.setCodice(result.getInt("codice"));
					sku.setNome(result.getString("nome"));
					sku.setFoto(result.getString("foto"));
					sku.setDescrizione(result.getString("descrizione_tecnica")); 
					sku.setPrezzo(result.getInt("prezzo"));
					risultati.add(sku);
				}
			}
		}
		return risultati;
	}
	
	
	public void eliminaSku(int codice) throws SQLException {
		// controlliamo se è l'unica sku di un prodotto semplice
		String checkQuery = "SELECT r.cod_prod_s, p.nome " +
		                    "FROM Realizzazione r " +
		                    "JOIN Prodotto p ON r.cod_prod_s = p.codice " +
		                    "WHERE r.cod_sku = ? AND (SELECT COUNT(*) FROM Realizzazione r2 WHERE r2.cod_prod_s = r.cod_prod_s) = 1";
		
		try (PreparedStatement ps = con.prepareStatement(checkQuery)) {
			ps.setInt(1, codice);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					String nomeProd = rs.getString("nome");
					// impediamo la cancellazione
					throw new SQLException("Impossibile eliminare: la SKU è l'unica rimasta per il prodotto '" + nomeProd + "'.");
				}
			}
		}
		
		// se no elimina
		
		// elimina le configurazioni che contengono questa SKU
		String deleteConfig = "DELETE FROM Configurazione WHERE id IN " +
		                      "(SELECT id_config FROM Dettaglio WHERE cod_sku = ?)";
		try (PreparedStatement ps = con.prepareStatement(deleteConfig)) {
		    ps.setInt(1, codice);
		    ps.executeUpdate();
		}
		
		
		String deleteQuery = "DELETE FROM Sku WHERE codice = ?";
		try (PreparedStatement ps = con.prepareStatement(deleteQuery)) {
			ps.setInt(1, codice);
			ps.executeUpdate();
		}
	}
	
	public void updateValoreSku(int codice, String campo, String valore) throws SQLException {
		String query = "UPDATE Sku SET " + campo + " = ? WHERE codice = ?";
		
		try (PreparedStatement ps = con.prepareStatement(query)) {
			if (campo.equals("prezzo")) {
				ps.setInt(1, Integer.parseInt(valore));
			} else {
				ps.setString(1, valore);
			}
			ps.setInt(2, codice);
			ps.executeUpdate();
		}
	}
	
	
}