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

@WebServlet("/CreateProdottoSemplice")
public class CreateProdottoSemplice extends HttpServlet {
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
		
		// solito controllo autenticazione
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("utente") == null) {
			request.setAttribute("errore", "Accesso negato o sessione scaduta.");
            request.getRequestDispatcher("/GoToHomePage").forward(request, response);
		}

		Utente utente = (Utente) session.getAttribute("utente");
		if (utente.getRuolo() != Utente.RuoloUtente.FORNITORE) {
			request.setAttribute("errore", "Accesso negato o sessione scaduta.");
            request.getRequestDispatcher("/GoToHomePage").forward(request, response);
		}

		// prendiamo i valori dal form
		String codiceStr = request.getParameter("codice");
		String nome = request.getParameter("nome");
		
		// raccogliamo tutte le sku selezionate
		String[] skuScelti = request.getParameterValues("skuScelti");

		if (codiceStr == null || codiceStr.isEmpty() || nome == null || nome.isEmpty()) {
			request.setAttribute("errore", "Errore: Compila tutti i campi del prodotto");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}
		
		// almeno una sku per prodotto
		if (skuScelti == null || skuScelti.length == 0) {
			request.setAttribute("errore", "Errore: Devi selezionare almeno una SKU per il prodotto semplice");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}

		int codice = 0;
		try {
			codice = Integer.parseInt(codiceStr);
		} catch (NumberFormatException e) {
			request.setAttribute("errore", "Errore: Il codice deve essere un numero valido");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}

		// 3. INSERIMENTO TRANSAZIONALE NEL DATABASE
		ProdottoDAO prodottoDao = new ProdottoDAO(connection);
		try {
			prodottoDao.inserisciProdottoSemplice(codice, nome, skuScelti);
		} catch (SQLException e) {
			e.printStackTrace();
			request.setAttribute("errore", "Errore Database: Esiste già un prodotto con questo codice.");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}
		
		// anche qua salviamo in sessione per mostrare al redirect
		request.getSession().setAttribute("ultimoCreatoTipo", "PRODOTTO_SEMPLICE");
		request.getSession().setAttribute("ultimoCreatoCodice", codice);

		// se funziona redirect
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