package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import beans.Utente;
import dao.ProdottoDAO;
import dao.SkuDAO;
import utils.ConnectionHandler;

@WebServlet("/EliminaOggetto")
public class EliminaOggetto extends HttpServlet {
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

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// soliti controlli
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("utente") == null) {
			response.sendRedirect(getServletContext().getContextPath() + "/HomePage.html");
			return;
		}
		Utente utente = (Utente) session.getAttribute("utente");
		if (utente.getRuolo() != Utente.RuoloUtente.FORNITORE) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Utente non autorizzato");
			return;
		}

		String codice = request.getParameter("codice");
		String tipo = request.getParameter("tipo");

		if (codice != null && tipo != null) {
			try {
				int id = Integer.parseInt(codice);
				if (tipo.equals("SKU")) {
					new SkuDAO(connection).eliminaSku(id);
				} else {
					new ProdottoDAO(connection).eliminaProdotto(id); 
				}
				
				// Se tutto va bene, ricarica la pagina normalmente
				response.sendRedirect(getServletContext().getContextPath() + "/GoToHomeFornitore");
				
			} catch (SQLException e) {
				// se la sku non può essere eliminata
				request.setAttribute("errore", e.getMessage());
				request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
				return;
			}
		} else {
			response.sendRedirect(getServletContext().getContextPath() + "/GoToHomeFornitore");
		}
	}
	
	public void destroy() {
		try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
	}
}