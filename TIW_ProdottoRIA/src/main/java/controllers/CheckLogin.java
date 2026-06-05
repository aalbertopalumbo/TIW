package controllers;

import java.io.IOException;

import java.sql.Connection;
import java.sql.SQLException;

import com.google.gson.Gson;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.annotation.MultipartConfig;

import beans.Utente;
import dao.UtenteDAO;
import utils.ConnectionHandler; 

@WebServlet("/CheckLogin")
@MultipartConfig
public class CheckLogin extends HttpServlet {
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
		// impostiamo il tipo di risposta come JSON
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		
		String user = request.getParameter("username");
		String pass = request.getParameter("password");
		
		if (user == null || user.isEmpty() || pass == null || pass.isEmpty()) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Credenziali mancanti");
			return;
		}

		UtenteDAO utenteDao = new UtenteDAO(connection);
		Utente utente = null;
		
		try {
			utente = utenteDao.checkLogin(user, pass);
		} catch (SQLException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.getWriter().println("Errore di connessione al database");
			return;
		}

		if (utente == null) {
			// no forward o redirect, ma solo messaggio di errore
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.getWriter().println("Username o password errati");
		} else {
			// se ok facciamo sessione
			HttpSession session = request.getSession();
			session.setAttribute("utente", utente);
			
			// qui idem mandiamo con json
			Gson gson = new Gson();
			String json = gson.toJson(utente);
			
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().write(json);
		}
	}

	public void destroy() {
		try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
	}
}