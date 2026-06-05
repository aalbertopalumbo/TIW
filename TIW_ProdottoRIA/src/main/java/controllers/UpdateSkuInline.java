package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import dao.SkuDAO;
import utils.ConnectionHandler;

@WebServlet("/UpdateSkuInline")
@MultipartConfig
public class UpdateSkuInline extends HttpServlet {
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
			throw new ServletException("Impossibile connettersi al database", e);
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/plain");
		response.setCharacterEncoding("UTF-8");

		String codiceStr = request.getParameter("codice");
		String campo = request.getParameter("campo"); // nome, prezzo, foto, descrizione
		String valore = request.getParameter("valore");

		if (codiceStr == null || campo == null || valore == null || valore.trim().isEmpty()) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Dati mancanti o non validi");
			return;
		}

		// Validazione di sicurezza per evitare SQL Injection sui nomi delle colonne
		if (!campo.equals("nome") && !campo.equals("prezzo") && !campo.equals("descrizione") && !campo.equals("foto")) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Campo non modificabile");
			return;
		}

		int codice = Integer.parseInt(codiceStr);
		
		SkuDAO skuDao = new SkuDAO(connection);
		try {
			skuDao.updateValoreSku(codice, campo, valore); 
			
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().println("Aggiornamento riuscito");
		} catch (SQLException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.getWriter().println("Errore di salvataggio nel database");
		}
	}

	public void destroy() { try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {} }
}