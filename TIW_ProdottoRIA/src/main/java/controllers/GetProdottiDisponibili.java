package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import utils.ConnectionHandler;

@WebServlet("/GetProdottiDisponibili")
public class GetProdottiDisponibili extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public void init() throws ServletException {
		try {
			ServletContext context = getServletContext();
			connection = ConnectionHandler.getConnection(
				context.getInitParameter("dbAddress"), context.getInitParameter("dbPort"), 
				context.getInitParameter("dbName"), context.getInitParameter("dbUser"), context.getInitParameter("dbPassword")
			);
		} catch (SQLException e) { throw new ServletException(e); }
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("utente") == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		try {
			JsonArray prodottiJSON = new JsonArray();
			
			// Prendiamo tutti i prodotti "radice" disponibili (non figli di nessuno)
			String query = "SELECT codice FROM Prodotto WHERE codice NOT IN (SELECT codice_figlio FROM Composizione) ORDER BY nome DESC";
			try (PreparedStatement ps = connection.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					// Per ogni prodotto, scateniamo la ricorsione che costruisce l'albero intero!
					prodottiJSON.add(costruisciAlbero(rs.getInt("codice"), connection));
				}
			}
			
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().write(new Gson().toJson(prodottiJSON));
			
		} catch (SQLException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.getWriter().println("Errore nel recupero dei prodotti disponibili");
		}
	}

	// MOTORE RICORSIVO DI LETTURA
	private JsonObject costruisciAlbero(int codice, Connection con) throws SQLException {
		JsonObject nodo = new JsonObject();
		nodo.addProperty("codice", codice);
		nodo.addProperty("isNuovo", false); // FONDAMENTALE: Informa JS che esiste già nel DB

		String query = "SELECT p.nome, ps.codice AS is_semplice, pc.p_min, pc.p_max, pc.descrizione " +
		               "FROM Prodotto p " +
		               "LEFT JOIN ProdottoSemplice ps ON p.codice = ps.codice " +
		               "LEFT JOIN ProdottoComposto pc ON p.codice = pc.codice " +
		               "WHERE p.codice = ?";
		
		try (PreparedStatement ps = con.prepareStatement(query)) {
			ps.setInt(1, codice);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					nodo.addProperty("nome", rs.getString("nome"));
					
					if (rs.getObject("is_semplice") != null) {
						nodo.addProperty("tipo", "SEMPLICE");
						nodo.add("nuoveSkus", new JsonArray()); // Campo vuoto pronto per JS
						
						// Recupera le SKU
						JsonArray skus = new JsonArray();
						String qSkus = "SELECT cod_sku FROM Realizzazione WHERE cod_prod_s = ?";
						try (PreparedStatement psSku = con.prepareStatement(qSkus)) {
							psSku.setInt(1, codice);
							try (ResultSet rsSku = psSku.executeQuery()) {
								while (rsSku.next()) skus.add(rsSku.getInt("cod_sku"));
							}
						}
						nodo.add("skus", skus);
					} else {
						nodo.addProperty("tipo", "COMPOSTO");
						nodo.addProperty("prezzoMin", rs.getDouble("p_min"));
						nodo.addProperty("prezzoMax", rs.getDouble("p_max"));
						nodo.addProperty("descrizione", rs.getString("descrizione"));
						
						// Recupera i Figli (RICORSIONE!)
						JsonArray figli = new JsonArray();
						String qFigli = "SELECT codice_figlio FROM Composizione WHERE codice_padre = ?";
						try (PreparedStatement psFigli = con.prepareStatement(qFigli)) {
							psFigli.setInt(1, codice);
							try (ResultSet rsFigli = psFigli.executeQuery()) {
								while (rsFigli.next()) {
									figli.add(costruisciAlbero(rsFigli.getInt("codice_figlio"), con));
								}
							}
						}
						nodo.add("figli", figli);
					}
				}
			}
		}
		return nodo;
	}

	public void destroy() { try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {} }
}