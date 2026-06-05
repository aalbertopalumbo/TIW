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
import dao.SkuDAO;
import utils.ConnectionHandler;

@WebServlet("/CreateSku")
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
			e.printStackTrace();
			throw new UnavailableException("Couldn't get db connection");
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		// controllo che utente sia autorizzato
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

		// controllo parametri
		String codiceStr = request.getParameter("codice");
		String nome = request.getParameter("nome");
		String foto = request.getParameter("foto");
		String descrizione = request.getParameter("descrizione");
		String prezzoStr = request.getParameter("prezzo");

		if (codiceStr == null || codiceStr.isEmpty() || nome == null || nome.isEmpty() || prezzoStr == null || prezzoStr.isEmpty()
				|| foto == null || foto.isEmpty() || descrizione == null || descrizione.isEmpty()) {
			// errore
			request.setAttribute("errore", "Errore: Compila tutti i campi obbligatori della Sku");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}

		int codice = 0;
		int prezzo = 0;

		try {
			codice = Integer.parseInt(codiceStr);
			prezzo = Integer.parseInt(prezzoStr);
			
			if (prezzo < 0) {
				request.setAttribute("errore", "Errore: Il prezzo non può essere negativo");
				request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
				return;
			}
		} catch (NumberFormatException e) {
			request.setAttribute("errore", "Errore: Codice e prezzo devono essere numeri validi");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}

		// carico sul db
		SkuDAO skuDao = new SkuDAO(connection);
		try {
			skuDao.inserisciSku(codice, nome, foto, descrizione, prezzo);
		} catch (SQLException e) {
			// evitiamo duplicazioni di codiceSku
			e.printStackTrace();
			request.setAttribute("errore", "Errore Database: Esiste già una SKU con questo codice.");
			request.getRequestDispatcher("/GoToHomeFornitore").forward(request, response);
			return;
		}
		
		// ci servono i dati per mostrare l'ultimo elemento creato
		request.getSession().setAttribute("ultimoCreatoTipo", "SKU");
		request.getSession().setAttribute("ultimoCreatoCodice", codice);

		// se funziona facciamo redirect alla home del fornitore
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