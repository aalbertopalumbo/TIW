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
import jakarta.servlet.annotation.MultipartConfig;

import beans.Utente;
import dao.UtenteDAO;
import utils.ConnectionHandler;

@WebServlet("/CheckRegistrazione")
@MultipartConfig
public class CheckRegistrazione extends HttpServlet {
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
			throw new UnavailableException("Couldn't get db connection");
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		
		String user = request.getParameter("username");
		String pass = request.getParameter("password");
		String nome = request.getParameter("nome");
		String cognome = request.getParameter("cognome");
		String ruolo = request.getParameter("ruolo");
		
		if (user == null || user.isEmpty() || pass == null || pass.isEmpty() || nome == null || nome.isEmpty() 
				|| cognome == null || cognome.isEmpty() || (!"CLIENTE".equals(ruolo) && !"FORNITORE".equals(ruolo))) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Campi mancanti o ruolo non valido");
			return;
		}
		
		Utente nuovoUtente = new Utente();
		nuovoUtente.setUsername(user);
		nuovoUtente.setPassword(pass);
		nuovoUtente.setNome(nome);
		nuovoUtente.setCognome(cognome);
		nuovoUtente.setRuolo(ruolo.equals("CLIENTE") ? Utente.RuoloUtente.CLIENTE : Utente.RuoloUtente.FORNITORE);

		UtenteDAO utenteDao = new UtenteDAO(connection);
		boolean registrazione = false;
		
		try {
			registrazione = utenteDao.registraUtente(nuovoUtente);
		} catch (SQLException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.getWriter().println("Errore di connessione al database");
			return;
		}

		if (registrazione == false) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Username già esistente");
		} else {
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().println("Registrazione avvenuta con successo! Ora puoi accedere.");
		}
	}

	public void destroy() {
		try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
	}
}