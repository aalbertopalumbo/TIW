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

@WebServlet("/CreateProdottoComposto")
public class CreateProdottoComposto extends HttpServlet {
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
		
		//controlli
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("utente") == null) {
			response.sendRedirect(getServletContext().getContextPath() + "/HomePage.html");
			return;
		}

		Utente utente = (Utente) session.getAttribute("utente");
		if (utente.getRuolo() != Utente.RuoloUtente.FORNITORE) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Non hai i permessi");
			return;
		}

		// lettura parametri
		String codiceStr = request.getParameter("codice");
		String nome = request.getParameter("nome");
		String descrizione = request.getParameter("descrizione");
		String pMinStr = request.getParameter("p_min");
		String pMaxStr = request.getParameter("p_max");
		
		// sottoprodotti scelti
		String[] sottoprodottiScelti = request.getParameterValues("prodottiScelti");

		// controlli su input
		if (codiceStr == null || codiceStr.isEmpty() || nome == null || nome.isEmpty() || pMinStr == null || pMinStr.isEmpty() 
				|| pMaxStr == null || pMaxStr.isEmpty() || descrizione == null || descrizione.isEmpty() || sottoprodottiScelti == null || sottoprodottiScelti.length == 0) {
			request.setAttribute("errore", "Errore: Compila tutti i campi obbligatori del prodotto composto");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}

		int codice = 0;
		double pMin = 0;
		double pMax = 0;

		try {
			codice = Integer.parseInt(codiceStr);
			pMin = Double.parseDouble(pMinStr);
			pMax = Double.parseDouble(pMaxStr);
			
			if (pMin < 0 || pMax < pMin) {
				request.setAttribute("errore", "Errore: I prezzi non sono validi.");
				request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
				return;
			}
		} catch (NumberFormatException e) {
			request.setAttribute("errore", "Errore: Codice e prezzi devono essere numeri validi");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}

		// inserimento in db
		ProdottoDAO prodottoDao = new ProdottoDAO(connection);
		
		// mettiamo il controllo di profondità anche lato server per evitare injections
		if (sottoprodottiScelti != null && sottoprodottiScelti.length > 0) {
			for (String idFiglioStr : sottoprodottiScelti) {
				try {
					int idFiglio = Integer.parseInt(idFiglioStr);
					int profonditaFiglio = prodottoDao.calcolaProfondita(idFiglio);
					
					// se è 4 vuol dire che è già troppo
					if (profonditaFiglio >= 4) {
						request.setAttribute("errore", "Errore: Questo sottoprodotto supera il limite gerarchia consentito");
						request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
						return;
					}
				} catch (SQLException e) {
					e.printStackTrace();
					request.setAttribute("errore", "Errore Database durante il calcolo della gerarchia");
					request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
					return;
				}
			}
		}
		
		
		try {
			prodottoDao.inserisciProdottoComposto(codice, nome, descrizione, pMin, pMax, sottoprodottiScelti);
		} catch (SQLException e) {
			e.printStackTrace();
			request.setAttribute("errore", "Errore Database: Impossibile creare il prodotto. Codice già esistente");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}

		// inserimento dei dati in sessione per Thymeleaf
		request.getSession().setAttribute("ultimoCreatoTipo", "PRODOTTO_COMPOSTO");
		request.getSession().setAttribute("ultimoCreatoCodice", codice);

		// redirect
		response.sendRedirect(getServletContext().getContextPath() + "/GoToHomeFornitore");
	}

	public void destroy() {
		try {
			ConnectionHandler.closeConnection(connection);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}