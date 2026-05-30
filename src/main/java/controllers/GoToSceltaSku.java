package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import beans.ProdottoComposto;
import beans.Utente;
import dao.ProdottoDAO;
import dao.SkuDAO;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import utils.ConnectionHandler;

@WebServlet("/GoToSceltaSku")
public class GoToSceltaSku extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;
	private TemplateEngine templateEngine;

	public void init() throws ServletException {
		try {
			ServletContext context = getServletContext();
			connection = ConnectionHandler.getConnection(context.getInitParameter("dbAddress"), context.getInitParameter("dbPort"), context.getInitParameter("dbName"), context.getInitParameter("dbUser"), context.getInitParameter("dbPassword"));
		} catch (SQLException e) {
			throw new UnavailableException("Couldn't get db connection");
		}
		
		JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(getServletContext());
		WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);
		templateResolver.setTemplateMode(TemplateMode.HTML);
		templateResolver.setPrefix("/");
		templateResolver.setSuffix(".html");
		this.templateEngine = new TemplateEngine();
		this.templateEngine.setTemplateResolver(templateResolver);
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// controllo accessi per il cliente
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

		String codiceParam = request.getParameter("codiceProdotto");
		if (codiceParam == null || codiceParam.isEmpty()) {
			response.sendRedirect(getServletContext().getContextPath() + "/GoToHomeCliente");
			return;
		}

		try {
			// prendiamo il prodotto passato da input (quello cliccato)
			int codiceRadice = Integer.parseInt(codiceParam);
			ProdottoDAO prodottoDao = new ProdottoDAO(connection);
			SkuDAO skuDao = new SkuDAO(connection);

			// ne troviamo i dati
			ProdottoComposto radice = prodottoDao.findProdottoCompostoById(codiceRadice);
			
			if (radice != null) {
				// carichiamo ricorsivamente tutto l'albero di sottoprodotti e sku
				radice.setComponenti(prodottoDao.trovaSottoprodottiRicorsivi(codiceRadice, skuDao));
			}

			final IWebExchange webExchange = JakartaServletWebApplication.buildApplication(getServletContext()).buildExchange(request, response);
			final WebContext ctx = new WebContext(webExchange, request.getLocale());
			
			// passiamo il prodotto alla pagina per visualizzare tutti i vari sottocosi
			ctx.setVariable("prodottoRadice", radice);
			
			String errore = (String) request.getAttribute("errore");
			if (errore != null) ctx.setVariable("errore", errore);

			templateEngine.process("/SceltaSku.html", ctx, response.getWriter());

		} catch (Exception e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel caricamento della configurazione");
		}
	}
	
	public void destroy() {
		try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
	}
}