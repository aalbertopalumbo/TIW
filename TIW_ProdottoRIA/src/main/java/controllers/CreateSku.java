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

@WebServlet("/CreateSku")
@MultipartConfig
public class CreateSku extends HttpServlet {
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
		response.setCharacterEncoding("UTF-8");

		// leggiamo i parametri
		String codiceStr = request.getParameter("codice");
		String nome = request.getParameter("nome");
		String foto = request.getParameter("foto"); 
		String prezzoStr = request.getParameter("prezzo");
		String descrizione = request.getParameter("descrizione");

		// controllo valori
		if (codiceStr == null || codiceStr.isEmpty() || nome == null || nome.isEmpty() || 
			prezzoStr == null || prezzoStr.isEmpty() || descrizione == null || descrizione.isEmpty() || foto == null || foto.isEmpty()) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Errore: Compila tutti i campi obbligatori.");
			return;
		}

		int prezzo;
		try {
			prezzo = Integer.parseInt(prezzoStr);
			if (prezzo <= 0) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getWriter().println("Errore: Il prezzo deve essere maggiore di zero.");
				return;
			}
		} catch (NumberFormatException e) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Errore: Formato prezzo non valido.");
			return;
		}
		
		int codice;
		try {
			codice = Integer.parseInt(codiceStr);
			if (codice <= 0) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getWriter().println("Errore: Il codice deve essere maggiore di zero.");
				return;
			}
		} catch (NumberFormatException e) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Errore: Formato codice non valido.");
			return;
		}

		// inseriamo in db
		SkuDAO skuDao = new SkuDAO(connection);
		try {
			skuDao.inserisciSku(codice, nome, foto, descrizione, prezzo);
			
			// prendiami la SKU creata dal db
			beans.Sku nuovaSku = skuDao.findSkuById(codice); 
			
			// la trasformiamo in json
			com.google.gson.Gson gson = new com.google.gson.Gson();
			String json = gson.toJson(nuovaSku);
			
			response.setContentType("application/json"); // restituiamo json
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().write(json);
			
		} catch (SQLException e) {
			response.setContentType("text/plain");
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.getWriter().println("Errore interno del database. Il codice SKU potrebbe già esistere.");
		}
	}

	public void destroy() {
		try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
	}
}