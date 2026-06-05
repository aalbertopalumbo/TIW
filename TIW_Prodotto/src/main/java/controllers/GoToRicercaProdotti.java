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

import beans.Prodotto;
import beans.ProdottoComposto;
import beans.Sku;
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

@WebServlet("/GoToRicercaProdotti")
public class GoToRicercaProdotti extends HttpServlet {
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

		// webexchange serve a Thymeleaf per accedere a request, response, session, ecc. in modo più facile
		final IWebExchange webExchange = JakartaServletWebApplication.buildApplication(getServletContext()).buildExchange(request, response);
		final WebContext ctx = new WebContext(webExchange, request.getLocale());

		ProdottoDAO prodottoDao = new ProdottoDAO(connection);
		SkuDAO skuDao = new SkuDAO(connection);

		// gestione barra di ricerca
		String keyword = request.getParameter("keyword");
		if (keyword != null && !keyword.trim().isEmpty()) {
			try {
				List<Prodotto> risultatiProdotti = prodottoDao.cercaProdotti(keyword);
				List<Sku> risultatiSku = skuDao.cercaSku(keyword);
				
				ctx.setVariable("keyword", keyword);
				ctx.setVariable("risultatiProdotti", risultatiProdotti);
				ctx.setVariable("risultatiSku", risultatiSku);
			} catch (SQLException e) {
				ctx.setVariable("errore", "Errore nel database durante la ricerca.");
			}
		}

		// quando utente clicca su un elemento
		String dettaglioCodiceStr = request.getParameter("dettaglioCodice");
		String dettaglioTipo = request.getParameter("dettaglioTipo");

		if (dettaglioCodiceStr != null && dettaglioTipo != null) {
			try {
				int codice = Integer.parseInt(dettaglioCodiceStr);
				
				if (dettaglioTipo.equals("SKU")) {
					ctx.setVariable("dettaglioSku", skuDao.findSkuById(codice));
					
				} else if (dettaglioTipo.equals("PRODOTTO_SEMPLICE")) {
					Prodotto pSemplice = prodottoDao.findProdottoSempliceById(codice);
					ctx.setVariable("dettaglioProdSemplice", pSemplice);
					ctx.setVariable("skuAssociate", skuDao.findSkuByProdotto(codice));
					
				} else if (dettaglioTipo.equals("PRODOTTO_COMPOSTO")) {
					ProdottoComposto pComposto = prodottoDao.findProdottoCompostoById(codice);
					if (pComposto != null) {
						pComposto.setComponenti(prodottoDao.trovaSottoprodottiRicorsivi(codice, skuDao));
						ctx.setVariable("dettaglioProdComposto", pComposto);
					}
				}
			} catch (Exception e) {
				ctx.setVariable("errore", "Errore nel caricamento del dettaglio.");
			}
		}

		templateEngine.process("/RicercaProdotti.html", ctx, response.getWriter());
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	public void destroy() {
		try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
	}
}