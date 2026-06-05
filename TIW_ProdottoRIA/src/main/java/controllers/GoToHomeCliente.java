package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import beans.ProdottoComposto;
import beans.Utente;
import dao.ProdottoDAO;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import utils.ConnectionHandler;

@WebServlet("/GoToHomeCliente")
public class GoToHomeCliente extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;
	private TemplateEngine templateEngine;
	
	// messa come costante che tanto non cambia e in caso si può modificare
	private static final int PRODOTTI_PER_PAGINA = 10; 

	public void init() throws ServletException {
		//ci colleghiamo al database leggendo dal web.xml
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
		
		JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(getServletContext());
		WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);
		templateResolver.setTemplateMode(TemplateMode.HTML);
		templateResolver.setPrefix("/"); // Cerca i file partendo dalla cartella webapp
		templateResolver.setSuffix(".html");
		
		this.templateEngine = new TemplateEngine();
		this.templateEngine.setTemplateResolver(templateResolver);
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// controllo accessi
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("utente") == null) {
			response.sendRedirect(getServletContext().getContextPath() + "/HomePage.html");
			return;
		}

		Utente utente = (Utente) session.getAttribute("utente");
		if (utente.getRuolo() != Utente.RuoloUtente.CLIENTE) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Accesso negato");
			return;
		}

		// capiamo in che pagina siamo
		int pagina = 1;
		String pageParam = request.getParameter("pagina");
		if (pageParam != null) {
			try {
				pagina = Integer.parseInt(pageParam);
				if (pagina < 1) pagina = 1;
			} catch (NumberFormatException e) {
				pagina = 1;
			}
		}

		// calcoliamo l'offset in base a che pagina siamo
		int offset = (pagina - 1) * PRODOTTI_PER_PAGINA;

		ProdottoDAO prodottoDao = new ProdottoDAO(connection);
		List<ProdottoComposto> prodottiPrincipali = null;
		int totaleProdotti = 0;

		try {
			prodottiPrincipali = prodottoDao.getProdottiPrimoLivello(PRODOTTI_PER_PAGINA, offset);
			totaleProdotti = prodottoDao.contaProdottiPrimoLivello();
		} catch (SQLException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore caricamento prodotti");
			return;
		}

		// vediamo se mettere i bottoni prec e succ
		boolean hasPrev = pagina > 1;
		boolean hasNext = (offset + PRODOTTI_PER_PAGINA) < totaleProdotti;

		// passiamo a thymeleaf i dati
		final IWebExchange webExchange = JakartaServletWebApplication.buildApplication(getServletContext()).buildExchange(request, response);
		final WebContext ctx = new WebContext(webExchange, request.getLocale());
		
		String errore = (String) request.getAttribute("errore");
		if (errore != null) ctx.setVariable("errore", errore);

		ctx.setVariable("prodottiPrincipali", prodottiPrincipali);
		ctx.setVariable("paginaCorrente", pagina);
		ctx.setVariable("hasPrev", hasPrev);
		ctx.setVariable("hasNext", hasNext);

		templateEngine.process("/HomeCliente.html", ctx, response.getWriter());
	}
	
	public void destroy() {
		try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
	}
}