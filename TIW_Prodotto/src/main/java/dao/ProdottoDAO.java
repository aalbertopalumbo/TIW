package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import beans.Prodotto;
import beans.ProdottoComposto;
import beans.ProdottoSemplice;

public class ProdottoDAO {
	private Connection con;

	public ProdottoDAO(Connection connection) {
		this.con = connection;
	}

	// usato per trovare i prodotti composti
	public List<Prodotto> trovaProdottiDisponibili() throws SQLException {
		
		List<Prodotto> prodotti = new ArrayList<>();
		
		// prende quelli che non sono figli di altri prodotti
		String query = "SELECT codice, nome FROM Prodotto " +
		               "WHERE codice NOT IN (SELECT codice_figlio FROM Composizione) " +
		               "ORDER BY nome DESC";
		
		try (PreparedStatement pstatement = con.prepareStatement(query);
		     ResultSet result = pstatement.executeQuery()) {
			
			while (result.next()) {
				int codice = result.getInt("codice");
				
				// togliamo quelli che hanno già profondità massima
				if (calcolaProfondita(codice) <= 3) {
					Prodotto p = new Prodotto();
					p.setCodice(codice);
					p.setNome(result.getString("nome"));
					prodotti.add(p);
				}
			}
		}
		return prodotti;
	}
	
	public Prodotto findProdottoSempliceById(int codice) throws SQLException {
		Prodotto prodotto = null;
		
		String query = "SELECT codice, nome FROM Prodotto WHERE codice = ?";
		
		try (PreparedStatement pstatement = con.prepareStatement(query)) {
			pstatement.setInt(1, codice);
			
			try (ResultSet result = pstatement.executeQuery()) {
				if (result.next()) {
					prodotto = new Prodotto();
					prodotto.setCodice(result.getInt("codice"));
					prodotto.setNome(result.getString("nome"));
				}
			}
		}
		return prodotto;
	}
	
	// fatto per inserire un prod semplice e le sue SKU
	public void inserisciProdottoSemplice(int codice, String nome, String[] codiciSku) throws SQLException {
		
		String queryProdotto = "INSERT INTO Prodotto (codice, nome) VALUES (?, ?)";
		String querySemplice = "INSERT INTO ProdottoSemplice (codice) VALUES (?)";
		String querySku = "INSERT INTO Realizzazione (cod_prod_s, cod_sku) VALUES (?, ?)";

		try {
			// togliamo autocommit per fare le transazioni tutte insieme per fare un revert unico
			con.setAutoCommit(false);

			// inseriamo il prodotto
			try (PreparedStatement psProdotto = con.prepareStatement(queryProdotto)) {
				psProdotto.setInt(1, codice);
				psProdotto.setString(2, nome);
				psProdotto.executeUpdate();
			}

			// inseriamo in prodotto semplice
			try (PreparedStatement psSemplice = con.prepareStatement(querySemplice)) {
				psSemplice.setInt(1, codice);
				psSemplice.executeUpdate();
			}

			// in realizzazione mettiamo tutte le sku legate al prodotto
			try (PreparedStatement psSku = con.prepareStatement(querySku)) {
				for (String idSkuStr : codiciSku) {
					int idSku = Integer.parseInt(idSkuStr);
					psSku.setInt(1, codice);
					psSku.setInt(2, idSku);
					
					// accumuliamo tutte le query in una
					psSku.addBatch(); 
				}
				psSku.executeBatch();
			}

			// committiamo tutte le operazioni insieme
			con.commit();

		} catch (SQLException e) {
			// rollback se errore
			con.rollback();
			throw e;
		} finally {
			// riattiviamo autocommit alla fine
			con.setAutoCommit(true);
		}
	}
	
	
	public ProdottoComposto findProdottoCompostoById(int codice) throws SQLException {
		ProdottoComposto prodotto = null;
		
		// join per prendere anche il nome in prodotto e i dati in composto
		String query = "SELECT p.codice, p.nome, pc.descrizione, pc.p_min, pc.p_max " +
	               "FROM Prodotto p " +
	               "JOIN ProdottoComposto pc ON p.codice = pc.codice " +
	               "WHERE p.codice = ?";
	
		try (PreparedStatement pstatement = con.prepareStatement(query)) {
			pstatement.setInt(1, codice);
			
			try (ResultSet result = pstatement.executeQuery()) {
				if (result.next()) {
					prodotto = new ProdottoComposto();
					prodotto.setCodice(result.getInt("codice"));
					prodotto.setNome(result.getString("nome"));
					prodotto.setDescrizione(result.getString("descrizione"));
					prodotto.setPrezzoMin(result.getDouble("p_min"));
					prodotto.setPrezzoMax(result.getDouble("p_max"));
				}
			}
		}
		return prodotto;
	}
	
	
	// fatto per inserire un prodotto composto e i suoi componenti
	public void inserisciProdottoComposto(int codice, String nome, String descrizione, double pMin, double pMax, String[] codiciFigli) throws SQLException {
		String queryProdotto = "INSERT INTO Prodotto (codice, nome) VALUES (?, ?)";
		String queryComposto = "INSERT INTO ProdottoComposto (codice, descrizione, p_min, p_max) VALUES (?, ?, ?, ?)";
		String queryComposizione = "INSERT INTO Composizione (codice_padre, codice_figlio) VALUES (?, ?)";

		try {
			// transazione 
			con.setAutoCommit(false);

			// inseriamo il prodotto generico
			try (PreparedStatement psProdotto = con.prepareStatement(queryProdotto)) {
				psProdotto.setInt(1, codice);
				psProdotto.setString(2, nome);
				psProdotto.executeUpdate();
			}

			// inseriamo i dati specifici del prodotto composto
			try (PreparedStatement psComposto = con.prepareStatement(queryComposto)) {
				psComposto.setInt(1, codice);
				psComposto.setString(2, descrizione);
				psComposto.setDouble(3, pMin);
				psComposto.setDouble(4, pMax);
				psComposto.executeUpdate();
			}

			// inseriamo i legami con i sottoprodotti
			if (codiciFigli != null && codiciFigli.length > 0) {
				try (PreparedStatement psComposizione = con.prepareStatement(queryComposizione)) {
					for (String idFiglioStr : codiciFigli) {
						psComposizione.setInt(1, codice);
						psComposizione.setInt(2, Integer.parseInt(idFiglioStr));
						psComposizione.addBatch();
					}
					psComposizione.executeBatch();
				}
			}

			// se tutto ok mandiamo il commit
			con.commit();

		} catch (SQLException e) {
			con.rollback();
			throw e;
		} finally {
			con.setAutoCommit(true);
		}
	}
	
	
	public int calcolaProfondita(int codiceProdotto) throws SQLException {
		String query = "SELECT codice_figlio FROM Composizione WHERE codice_padre = ?";
		int maxProfonditaFigli = 0;

		try (PreparedStatement pstatement = con.prepareStatement(query)) {
			pstatement.setInt(1, codiceProdotto);
			
			try (ResultSet result = pstatement.executeQuery()) {
				while (result.next()) {
					int codiceFiglio = result.getInt("codice_figlio");
					
					// in modo ricorsivo prendiamo la profondità del figlio
					int profonditaFiglio = calcolaProfondita(codiceFiglio);
					
					// teniamo quello con profondità maggiore
					if (profonditaFiglio > maxProfonditaFigli) {
						maxProfonditaFigli = profonditaFiglio;
					}
				}
			}
		}
		
		// 1 è il minimo, al massivo 4
		return 1 + maxProfonditaFigli;
	}
	
	
	// in modo ricorsivo carichiamo l'albero di sottoprodotti e SKU
	public List<Prodotto> trovaSottoprodottiRicorsivi(int codicePadre, SkuDAO skuDao) throws SQLException {
		List<Prodotto> sottoprodotti = new ArrayList<>();
		
		// prendiamo i figli ordinati per nome decrescente
		String query = "SELECT p.codice, p.nome, pc.descrizione, pc.p_min, pc.p_max, ps.codice AS is_semplice " +
		               "FROM Composizione c " +
		               "JOIN Prodotto p ON c.codice_figlio = p.codice " +
		               "LEFT JOIN ProdottoComposto pc ON p.codice = pc.codice " +
		               "LEFT JOIN ProdottoSemplice ps ON p.codice = ps.codice " +
		               "WHERE c.codice_padre = ? " +
		               "ORDER BY p.nome DESC"; 
		
		try (PreparedStatement pstatement = con.prepareStatement(query)) {
			pstatement.setInt(1, codicePadre);
			
			try (ResultSet result = pstatement.executeQuery()) {
				while (result.next()) {
					int codice = result.getInt("codice");
					String nome = result.getString("nome");
					
					// vediamo se il sottoprodotto è semplice o composto
					if (result.getObject("is_semplice") != null) {
						
						// semplice
						ProdottoSemplice semplice = new ProdottoSemplice();
						semplice.setCodice(codice);
						semplice.setNome(nome);
						
						// carichiamo le sku associate
						semplice.setSkus(skuDao.findSkuByProdotto(codice)); 
						
						sottoprodotti.add(semplice);
						
					} else {
						
						// composto
						ProdottoComposto composto = new ProdottoComposto();
						composto.setCodice(codice);
						composto.setNome(nome);
						composto.setDescrizione(result.getString("descrizione"));
						composto.setPrezzoMin(result.getDouble("p_min"));
						composto.setPrezzoMax(result.getDouble("p_max"));
						
						// facciamo in modo ricorsivo il caricamento dei componenti
						composto.setComponenti(trovaSottoprodottiRicorsivi(codice, skuDao));
						
						sottoprodotti.add(composto);
					}
				}
			}
		}
		return sottoprodotti;
	}
	
	
	public List<Prodotto> cercaProdotti(String keyword) throws SQLException {
		
		List<Prodotto> risultati = new ArrayList<>();
		// cerca nel nome del prodotto o nella descrizione (se è composto)
		String query = "SELECT p.codice, p.nome, ps.codice AS is_semplice " +
		               "FROM Prodotto p " +
		               "LEFT JOIN ProdottoComposto pc ON p.codice = pc.codice " +
		               "LEFT JOIN ProdottoSemplice ps ON p.codice = ps.codice " +
		               "WHERE LOWER(p.nome) LIKE LOWER(?) OR LOWER(pc.descrizione) LIKE LOWER(?) " +
		               "ORDER BY p.nome DESC";
		
		try (PreparedStatement pstatement = con.prepareStatement(query)) {
			String searchPattern = "%" + keyword + "%";
			pstatement.setString(1, searchPattern);
			pstatement.setString(2, searchPattern);
			
			try (ResultSet result = pstatement.executeQuery()) {
				while (result.next()) {
					Prodotto p;
					if (result.getObject("is_semplice") != null) {
						p = new ProdottoSemplice();
					} else {
						p = new ProdottoComposto();
					}
					p.setCodice(result.getInt("codice"));
					p.setNome(result.getString("nome"));
					risultati.add(p);
				}
			}
		}
		return risultati;
	}
	
	
	// tasto rimuovi tra prodotto composto e figlio
	public void rimuoviComposizione(int idPadre, int idFiglio) throws SQLException {
		// non possiamo rimuovere tutti i figli da un prodotto composto
		String countQuery = "SELECT COUNT(*) AS totale FROM Composizione WHERE codice_padre = ?";
		try (PreparedStatement ps = con.prepareStatement(countQuery)) {
			ps.setInt(1, idPadre);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next() && rs.getInt("totale") <= 1) {
					// impedisce se ne ha solo 1
					throw new SQLException("Errore: Un prodotto composto deve avere almeno un sottoprodotto.");
				}
			}
		}
		
		// se ha più di un figlio procede con la rimozione
		String query = "DELETE FROM Composizione WHERE codice_padre = ? AND codice_figlio = ?";
		try (PreparedStatement ps = con.prepareStatement(query)) {
			ps.setInt(1, idPadre);
			ps.setInt(2, idFiglio);
			ps.executeUpdate();
		}
	}

	// tasto rimuovi tra prodotto semplice e SKU
	public void rimuoviRealizzazione(int idSemplice, int idSku) throws SQLException {
		// controlla quante SKU ha il prodotto semplice
		String countQuery = "SELECT COUNT(*) AS totale FROM Realizzazione WHERE cod_prod_s = ?";
		try (PreparedStatement ps = con.prepareStatement(countQuery)) {
			ps.setInt(1, idSemplice);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next() && rs.getInt("totale") <= 1) {
					throw new SQLException("Errore: Un prodotto semplice deve avere almeno una SKU associata.");
				}
			}
		}
		
		// se ha più di una SKU procede
		String query = "DELETE FROM Realizzazione WHERE cod_prod_s = ? AND cod_sku = ?";
		try (PreparedStatement ps = con.prepareStatement(query)) {
			ps.setInt(1, idSemplice);
			ps.setInt(2, idSku);
			ps.executeUpdate();
		}
	}

	
	// elimina un prodotto e i figli in modo ricorsivo
	public void eliminaProdotto(int codice) throws SQLException {
		// transazione
		con.setAutoCommit(false); 
		try {
			eliminaRicorsivoInternal(codice);
			con.commit();
		} catch (SQLException e) {
			con.rollback();
			throw e;
		} finally {
			con.setAutoCommit(true);
		}
	}

	private void eliminaRicorsivoInternal(int codice) throws SQLException {
		// va a prendere i figli del prodotto da eliminare e chiama per ognuno la funzione ricorsiva per eliminare i figli
		String queryFigli = "SELECT codice_figlio FROM Composizione WHERE codice_padre = ?";
		try (PreparedStatement ps = con.prepareStatement(queryFigli)) {
			ps.setInt(1, codice);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					eliminaRicorsivoInternal(rs.getInt("codice_figlio")); 
				}
			}
		}
		
		// se è un prodotto semplice elimina le config in cui è presente
		String deleteConfig = "DELETE FROM Configurazione WHERE id IN " +
							 "(SELECT id_config FROM Dettaglio WHERE cod_prod_s = ?)";
		try (PreparedStatement ps = con.prepareStatement(deleteConfig)) {
		ps.setInt(1, codice);
		ps.executeUpdate();
		}
		
		// elimina l'oggetto singolo e poi il cascade in db fa il resto
		String delete = "DELETE FROM Prodotto WHERE codice = ?";
		try (PreparedStatement ps = con.prepareStatement(delete)) { 
			ps.setInt(1, codice); 
			ps.executeUpdate(); 
		}
	}
	
	
	//zona cliente
	//dobbiamo limitare i prodotti a 10 per pagina, quindi query che ci trova tot prodotti tramite offset e limit
	public List<ProdottoComposto> getProdottiPrimoLivello(int limit, int offset) throws SQLException {
		List<ProdottoComposto> prodotti = new ArrayList<>();
		
		// ordine alfabetico decrescente
		// estraggo limitando a 10 per pag e con offset che dipende dalla pagina in cui ci troviamo, quindi viene fatto poi in modo dinamico
		String query = "SELECT p.codice, p.nome, pc.descrizione, pc.p_min, pc.p_max " +
		               "FROM Prodotto p " +
		               "JOIN ProdottoComposto pc ON p.codice = pc.codice " +
		               "WHERE p.codice NOT IN (SELECT codice_figlio FROM Composizione) " +
		               "ORDER BY p.nome DESC " +
		               "LIMIT ? OFFSET ?";
		
		try (PreparedStatement ps = con.prepareStatement(query)) {
			ps.setInt(1, limit);
			ps.setInt(2, offset);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					ProdottoComposto pc = new ProdottoComposto();
					pc.setCodice(rs.getInt("codice"));
					pc.setNome(rs.getString("nome"));
					pc.setDescrizione(rs.getString("descrizione"));
					pc.setPrezzoMin(rs.getDouble("p_min"));
					pc.setPrezzoMax(rs.getDouble("p_max"));
					prodotti.add(pc);
				}
			}
		}
		return prodotti;
	}
	
	// ci serve per capire se serve mettere il bottone successivo o no (in thymeleaf non si può fare direttamente perché usiamo il limit)
	public int contaProdottiPrimoLivello() throws SQLException {
		String query = "SELECT COUNT(*) AS totale FROM ProdottoComposto " +
		               "WHERE codice NOT IN (SELECT codice_figlio FROM Composizione)";
		try (PreparedStatement ps = con.prepareStatement(query);
			 ResultSet rs = ps.executeQuery()) {
			if (rs.next()) {
				return rs.getInt("totale");
			}
		}
		return 0;
	}

	
}