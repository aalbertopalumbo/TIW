package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.google.gson.Gson;

import beans.Prodotto;
import dao.ProdottoDAO;
import utils.ConnectionHandler;

@WebServlet("/GetProdottiDisponibili")
public class GetProdottiDisponibili extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public void init() throws ServletException {
		try {
			ServletContext context = getServletContext();
			String address = context.getInitParameter("dbAddress");
			String port = context.getInitParameter("dbPort");
			String dbName = context.getInitParameter("dbName");
			String user = context.getInitParameter("dbUser");
			String password = context.getInitParameter("dbPassword");
			
			connection = ConnectionHandler.getConnection(address, port, dbName, user, password);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new UnavailableException("Couldn't get db connection");
		}
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (request.getSession(false) == null || request.getSession().getAttribute("utente") == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		ProdottoDAO prodottoDao = new ProdottoDAO(connection);
		try {
			List<Prodotto> prodotti = prodottoDao.trovaProdottiDisponibili();
			
			// creiamo il JSON per forzare l'inclusione del getTipo()
			com.google.gson.JsonArray prodottiJSON = new com.google.gson.JsonArray();
			for (Prodotto p : prodotti) {
				com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
				obj.addProperty("codice", p.getCodice());
				obj.addProperty("nome", p.getNome());
				
				// getTipo() restituisce PRODOTTO_SEMPLICE o PRODOTTO_COMPOSTO.
				// rimuoviamo il prefisso "PRODOTTO_" per farlo combaciare con la logica js
				String tipoJs = p.getTipo().replace("PRODOTTO_", "");
				obj.addProperty("tipo", tipoJs); 
				
				prodottiJSON.add(obj);
			}
			
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().write(new com.google.gson.Gson().toJson(prodottiJSON));
			
		} catch (SQLException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.getWriter().println("Errore nel recupero dei prodotti disponibili");
		}
	}

	public void destroy() { try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {} }
}