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
import beans.Utente;
import dao.UtenteDAO;
import utils.ConnectionHandler;

@WebServlet("/CheckRegistrazione")
public class CheckRegistrazione extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

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

	// in reg abbiamo post
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		// prendiamo dati dal form
		String user = request.getParameter("username");
		String pass = request.getParameter("password");
		String nome = request.getParameter("nome");
		String cognome = request.getParameter("cognome");
		String ruolo = request.getParameter("ruolo");
		
		
		// controlli lato server
		if (user == null || user.isEmpty() || pass == null || pass.isEmpty() || nome == null || nome.isEmpty() 
				|| cognome == null || cognome.isEmpty() || (!"CLIENTE".equals(ruolo) && !"FORNITORE".equals(ruolo))) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Credenziali mancanti");
			return;
		}
		
		Utente nuovoUtente = new Utente();
		nuovoUtente.setUsername(user);
		nuovoUtente.setPassword(pass);
		nuovoUtente.setNome(nome);
		nuovoUtente.setCognome(cognome);
		nuovoUtente.setRuolo(ruolo.equals("CLIENTE") ? Utente.RuoloUtente.CLIENTE : Utente.RuoloUtente.FORNITORE);

		// chiamiamo il dao
		UtenteDAO utenteDao = new UtenteDAO(connection);
		
		boolean registrazione = false;
		
		try {
			registrazione = utenteDao.registraUtente(nuovoUtente);
		} catch (SQLException e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore di connessione al database");
			return;
		}

		if (registrazione == false) {
			// l'utente era già nel database
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username già esistente");
		} else {
			// registrazione riuscita, rimandiamo alla homepage
			String contextPath = getServletContext().getContextPath();
			response.sendRedirect(contextPath + "/HomePage.html");
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