package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.google.gson.Gson;

import beans.Utente;
import beans.ProdottoSemplice;
import dao.ProdottoDAO;
import dao.SkuDAO;
import utils.ConnectionHandler;

@WebServlet("/CreateProdottoSemplice")
@MultipartConfig // per leggere la tendina multipla
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
		response.setCharacterEncoding("UTF-8");

		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("utente") == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		Utente utente = (Utente) session.getAttribute("utente");
		if (utente.getRuolo() != Utente.RuoloUtente.FORNITORE) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.getWriter().println("Non hai i permessi");
			return;
		}

		String codiceStr = request.getParameter("codice");
		String nome = request.getParameter("nome");
		// legge i valori della tendina
		String[] skuScelti = request.getParameterValues("skuScelti"); 

		if (codiceStr == null || codiceStr.isEmpty() || nome == null || nome.isEmpty()) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Errore: Compila tutti i campi del prodotto");
			return;
		}
		if (skuScelti == null || skuScelti.length == 0) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Errore: Devi selezionare almeno una SKU");
			return;
		}

		int codice = 0;
		try {
			codice = Integer.parseInt(codiceStr);
		} catch (NumberFormatException e) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().println("Errore: Il codice deve essere un numero");
			return;
		}

		ProdottoDAO prodottoDao = new ProdottoDAO(connection);
		SkuDAO skuDao = new SkuDAO(connection);
		
		try {
			// mettiamo in db
			prodottoDao.inserisciProdottoSemplice(codice, nome, skuScelti);
			
			// inviamo al frontend il prodotto
			ProdottoSemplice prodottoCreato = new ProdottoSemplice();
			prodottoCreato.setCodice(codice);
			prodottoCreato.setNome(nome);
			prodottoCreato.setSkus(skuDao.findSkuByProdotto(codice));
			
			response.setContentType("application/json");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().write(new Gson().toJson(prodottoCreato));
			
		} catch (SQLException e) {
			response.setContentType("text/plain");
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.getWriter().println("Errore Database: Esiste già un prodotto con questo codice.");
		}
	}

	public void destroy() { 
		try { ConnectionHandler.closeConnection(connection); 
		} catch (Exception e) {} }
}