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
import dao.UtenteDAO;
import utils.ConnectionHandler; 

@WebServlet("/CheckLogin")
public class CheckLogin extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	//init() viene chiamato da Tomcat la prima volta che la Servlet si accende
	public void init() throws ServletException {
		//apriamo la connessione con il database 
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

	// in login abbiamo post
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		// prendiamo dati dal form
		String user = request.getParameter("username");
		String pass = request.getParameter("password");
		
		// controlli lato server
		if (user == null || user.isEmpty() || pass == null || pass.isEmpty()) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Credenziali mancanti");
			return;
		}

		// chiamiamo il dao
		UtenteDAO utenteDao = new UtenteDAO(connection);
		Utente utente = null;
		
		try {
			utente = utenteDao.checkLogin(user, pass);
		} catch (SQLException e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore di connessione al database");
			return;
		}

		if (utente == null) {
			// credenziali sbagliate, l'utente non c'è in MySQL
			// response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Username o password errati");
			request.setAttribute("errore", "Errore: Username o password errati");
			request.getRequestDispatcher("/GoToHomePage").forward(request, response);
			return;
		} else {
			// login riuscito
			// creiamo la sessione
			HttpSession session = request.getSession();
			// mettiamo l'utente in sessione per recuperarlo in altre pagine
			session.setAttribute("utente", utente);
			
			// A seconda del ruolo scegliamo pagina
			String contextPath = getServletContext().getContextPath();
				
			if (utente.getRuolo() == Utente.RuoloUtente.FORNITORE) {
				// chiamo la servlet, non l'html
				response.sendRedirect(contextPath + "/GoToHomeFornitore");
			} else {
				response.sendRedirect(contextPath + "/GoToHomeCliente");
			}
		}
		
		
	}

	public void destroy() {
		try {
			ConnectionHandler.closeConnection(connection);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}