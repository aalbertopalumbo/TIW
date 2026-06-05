package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import utils.ConnectionHandler;

@WebServlet("/SaveAlberoComposto")
@MultipartConfig
public class SaveAlberoComposto extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection con = null;

	public void init() throws ServletException {
		try {
			ServletContext context = getServletContext();
			String address = context.getInitParameter("dbAddress");
			String port = context.getInitParameter("dbPort");
			String dbName = context.getInitParameter("dbName");
			String user = context.getInitParameter("dbUser");
			String password = context.getInitParameter("dbPassword");
			
			con = ConnectionHandler.getConnection(address, port, dbName, user, password);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new UnavailableException("Couldn't get db connection");
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setCharacterEncoding("UTF-8");
		
		String jsonAlbero = request.getParameter("alberoJSON");
		String jsonDaEliminare = request.getParameter("daEliminareJSON");
		if (jsonAlbero == null || jsonAlbero.isEmpty()) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Nessun dato inviato.");
			return;
		}

		try {
			// transazione unica
			con.setAutoCommit(false);
			
			if (jsonDaEliminare != null && !jsonDaEliminare.isEmpty()) {
				JsonArray daEliminare = JsonParser.parseString(jsonDaEliminare).getAsJsonArray();
				dao.ProdottoDAO prodDao = new dao.ProdottoDAO(con);
				dao.SkuDAO skuDao = new dao.SkuDAO(con);
				
				for (JsonElement el : daEliminare) {
					JsonObject obj = el.getAsJsonObject();
					String tipo = obj.get("tipo").getAsString();
					int codice = obj.get("codice").getAsInt();
					
					// elimina relazioni e realizzazioni prima
					if (tipo.equals("SKU")) {
						skuDao.eliminaSku(codice);
					} else {
						prodDao.eliminaProdotto(codice);
					}
				}
			}
			
			String jsonRelazioni = request.getParameter("relazioniDaEliminareJSON");
			if (jsonRelazioni != null && !jsonRelazioni.isEmpty()) {
				JsonArray relDaEliminare = JsonParser.parseString(jsonRelazioni).getAsJsonArray();
				dao.ProdottoDAO prodDao = new dao.ProdottoDAO(con);
				
				for (JsonElement el : relDaEliminare) {
					JsonObject obj = el.getAsJsonObject();
					String tipoRel = obj.get("tipoRel").getAsString();
					int padre = obj.get("padre").getAsInt();
					int figlio = obj.get("figlio").getAsInt();
					
					// Chiamate pulite al DAO!
					if (tipoRel.equals("COMPOSIZIONE")) {
						prodDao.rimuoviComposizione(padre, figlio);
					} else if (tipoRel.equals("REALIZZAZIONE")) {
						prodDao.rimuoviRealizzazione(padre, figlio);
					}
				}
			}
			
			// parsing del JSON
			JsonObject root = JsonParser.parseString(jsonAlbero).getAsJsonObject();
			// faccio in modo ricorsivo
			salvaNodoRicorsivo(root, null);

			// salva tutto alla fine
			con.commit();
			
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().println("Salvataggio completato con successo!");

		} catch (Exception e) {
			try { con.rollback(); } catch (SQLException ex) {} // revert
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			String msg = e.getMessage();
			response.getWriter().println(msg != null ? msg : "Errore interno del DB.");
		} finally {
			try { con.setAutoCommit(true); } catch (SQLException ex) {}
		}
	}

	
	private void salvaNodoRicorsivo(JsonObject nodo, Integer codicePadre) throws SQLException {
		String tipo = nodo.get("tipo").getAsString();
		int codice = nodo.get("codice").getAsInt();
		String nome = nodo.get("nome").getAsString();
		
		boolean isNuovo = true;
		if (nodo.has("isNuovo")) {
			isNuovo = nodo.get("isNuovo").getAsBoolean();
		}
		
		if (isNuovo) {
			// INSERIMENTO PRODOTTO NUOVO
			String sqlProd = "INSERT INTO Prodotto (codice, nome) VALUES (?, ?)";
			try (PreparedStatement ps = con.prepareStatement(sqlProd)) {
				ps.setInt(1, codice);
				ps.setString(2, nome);
				ps.executeUpdate();
			}
			
			if (tipo.equals("COMPOSTO")) {
				double pMin = nodo.has("prezzoMin") ? nodo.get("prezzoMin").getAsDouble() : 0;
				double pMax = nodo.has("prezzoMax") ? nodo.get("prezzoMax").getAsDouble() : 0;
				String desc = nodo.has("descrizione") ? nodo.get("descrizione").getAsString() : "";

				String sqlComposto = "INSERT INTO ProdottoComposto (codice, descrizione, p_min, p_max) VALUES (?, ?, ?, ?)";
				try (PreparedStatement ps = con.prepareStatement(sqlComposto)) {
					ps.setInt(1, codice);
					ps.setString(2, desc);
					ps.setDouble(3, pMin);
					ps.setDouble(4, pMax);
					ps.executeUpdate();
				}
			} else if (tipo.equals("SEMPLICE")) {
				String sqlSemplice = "INSERT INTO ProdottoSemplice (codice) VALUES (?)";
				try (PreparedStatement ps = con.prepareStatement(sqlSemplice)) {
					ps.setInt(1, codice);
					ps.executeUpdate();
				}
			}
		} else {
			// AGGIORNAMENTO PRODOTTO ESISTENTE (Per salvare l'Inline Editing locale!)
			String updProd = "UPDATE Prodotto SET nome = ? WHERE codice = ?";
			try (PreparedStatement ps = con.prepareStatement(updProd)) {
				ps.setString(1, nome);
				ps.setInt(2, codice);
				ps.executeUpdate();
			}
		}

		// CREAZIONE LEGAMI: Evitiamo i duplicati controllando prima se esistono!
		if (codicePadre != null) {
			String checkRel = "SELECT 1 FROM Composizione WHERE codice_padre = ? AND codice_figlio = ?";
			boolean exists = false;
			try (PreparedStatement ps = con.prepareStatement(checkRel)) {
				ps.setInt(1, codicePadre);
				ps.setInt(2, codice);
				try (ResultSet rs = ps.executeQuery()) { if (rs.next()) exists = true; }
			}
			if (!exists) {
				String sqlComp = "INSERT INTO Composizione (codice_padre, codice_figlio) VALUES (?, ?)";
				try (PreparedStatement ps = con.prepareStatement(sqlComp)) {
					ps.setInt(1, codicePadre);
					ps.setInt(2, codice);
					ps.executeUpdate();
				}
			}
		}

		if (nodo.has("figli")) {
			JsonArray figli = nodo.getAsJsonArray("figli");
			for (JsonElement figlio : figli) {
				salvaNodoRicorsivo(figlio.getAsJsonObject(), codice);
			}
		}
		
		if (nodo.has("skus")) {
			JsonArray skus = nodo.getAsJsonArray("skus");
			String checkSku = "SELECT 1 FROM Realizzazione WHERE cod_prod_s = ? AND cod_sku = ?";
			String sqlRel = "INSERT INTO Realizzazione (cod_prod_s, cod_sku) VALUES (?, ?)";
			try (PreparedStatement psCheck = con.prepareStatement(checkSku);
			     PreparedStatement psInsert = con.prepareStatement(sqlRel)) {
				for (JsonElement skuElem : skus) {
					int codSku = skuElem.getAsInt();
					psCheck.setInt(1, codice);
					psCheck.setInt(2, codSku);
					try (ResultSet rs = psCheck.executeQuery()) {
						if (!rs.next()) { // Se l'associazione non esiste, la inseriamo!
							psInsert.setInt(1, codice);
							psInsert.setInt(2, codSku);
							psInsert.executeUpdate();
						}
					}
				}
			}
		}

		if (nodo.has("nuoveSkus")) {
			// ... (Il blocco delle nuoveSkus rimane IDENTICO a quello che hai già) ...
			JsonArray nuoveSkus = nodo.getAsJsonArray("nuoveSkus");
			String sqlNuovaSku = "INSERT INTO Sku (codice, nome, foto, descrizione_tecnica, prezzo) VALUES (?, ?, ?, ?, ?)";
			String sqlRel = "INSERT INTO Realizzazione (cod_prod_s, cod_sku) VALUES (?, ?)";
			
			for (JsonElement nSkuElem : nuoveSkus) {
				JsonObject nSku = nSkuElem.getAsJsonObject();
				int skuCod = nSku.get("codice").getAsInt();

				try (PreparedStatement ps = con.prepareStatement(sqlNuovaSku)) {
					ps.setInt(1, skuCod);
					ps.setString(2, nSku.get("nome").getAsString());
					ps.setString(3, nSku.get("foto").getAsString());
					ps.setString(4, nSku.get("descrizione").getAsString());
					ps.setInt(5, nSku.get("prezzo").getAsInt());
					ps.executeUpdate();
				}
				try (PreparedStatement ps = con.prepareStatement(sqlRel)) {
					ps.setInt(1, codice);
					ps.setInt(2, skuCod);
					ps.executeUpdate();
				}
			}
		}
	}

	public void destroy() { try { ConnectionHandler.closeConnection(con); } catch (Exception e) {} }
}