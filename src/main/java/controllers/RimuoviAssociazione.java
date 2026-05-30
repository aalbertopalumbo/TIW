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
import utils.ConnectionHandler;

@WebServlet("/RimuoviAssociazione")
public class RimuoviAssociazione extends HttpServlet {
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

		// prende le info per capire se stiamo rimuovendo un oggeto composto o semplice
		String idPadre = request.getParameter("idPadre");
		String idFiglio = request.getParameter("idFiglio");
		String idSemplice = request.getParameter("idSemplice");
		String idSku = request.getParameter("idSku");
		
		ProdottoDAO dao = new ProdottoDAO(connection);
		try {
			if (idPadre != null && idFiglio != null) {
				dao.rimuoviComposizione(Integer.parseInt(idPadre), Integer.parseInt(idFiglio));
			} else if (idSemplice != null && idSku != null) {
				dao.rimuoviRealizzazione(Integer.parseInt(idSemplice), Integer.parseInt(idSku));
			}
			
			// se la rimozione va a buon fine fa redirect
			response.sendRedirect(getServletContext().getContextPath() + "/GoToHomeFornitore");
			
		} catch (SQLException e) {
			request.setAttribute("errore", e.getMessage());
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
		}
	}
	
	public void destroy() {
		try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
	}
}