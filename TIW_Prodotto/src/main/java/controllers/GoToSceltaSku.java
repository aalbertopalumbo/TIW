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

import beans.Configurazione;
import beans.ProdottoComposto;
import beans.Utente;
import dao.ConfigurazioneDAO;
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
		templateResolver.setPrefix("/");
		templateResolver.setSuffix(".html");
		
		this.templateEngine = new TemplateEngine();
		this.templateEngine.setTemplateResolver(templateResolver);
	}
	
	@Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // se riceve un POST (es. tramite forward da SalvaConfigurazione per un errore)
        doGet(request, response);
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    HttpSession session = request.getSession(false);
	    if (session == null || session.getAttribute("utente") == null) {
	    		request.setAttribute("errore", "Accesso negato o sessione scaduta.");
            request.getRequestDispatcher("/GoToHomePage").forward(request, response);
	    }

	    Utente utente = (Utente) session.getAttribute("utente");
	    if (utente.getRuolo() != Utente.RuoloUtente.CLIENTE) {
	    		request.setAttribute("errore", "Accesso negato o sessione scaduta.");
            request.getRequestDispatcher("/GoToHomePage").forward(request, response);
	    }

	    String codiceParam = request.getParameter("codiceProdotto");
	    if (codiceParam == null || codiceParam.isEmpty()) {
	        response.sendRedirect(getServletContext().getContextPath() + "/GoToHomeCliente");
	        return;
	    }

	    try {
	        int codiceRadice = Integer.parseInt(codiceParam);
	        ProdottoDAO prodottoDao = new ProdottoDAO(connection);
	        SkuDAO skuDao = new SkuDAO(connection);

	        ProdottoComposto radice = prodottoDao.findProdottoCompostoById(codiceRadice);
	        if (radice != null) {
	        		// andiamo a prendere ricorsivamente tutti i sottoprodotti e sku
	            radice.setComponenti(prodottoDao.trovaSottoprodottiRicorsivi(codiceRadice, skuDao));
	        }

	        final IWebExchange webExchange = JakartaServletWebApplication.buildApplication(getServletContext()).buildExchange(request, response);
	        final WebContext ctx = new WebContext(webExchange, request.getLocale());
	        ctx.setVariable("prodottoRadice", radice);

	        // se stiamo modificando una config esistente, carichiamo le scelte precedenti
	        String idConfigParam = request.getParameter("idConfig");
	        if (idConfigParam != null && !idConfigParam.isEmpty()) {
	            int idConfig = Integer.parseInt(idConfigParam);
	            ConfigurazioneDAO configDao = new ConfigurazioneDAO(connection);
	            
	            // verifica che la configurazione appartenga all'utente connesso
	            Configurazione config = configDao.getConfigurazioneCompleta(idConfig);
	            if (config == null || !config.getCliente().getUsername().equals(utente.getUsername())) {
	                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Non autorizzato");
	                return;
	            }
	            
	            ctx.setVariable("idConfigInModifica", idConfig);
	            ctx.setVariable("sceltePrec", configDao.getScelteByConfigurazione(idConfig));
	            ctx.setVariable("nomeConfigEsistente", config.getNome());
	        }
	        
	        // andiamo a prendere eventuali scelte precedenti da un errore di validazione in SalvaConfigurazione 
	        // se uno non mette tutte le variabili e preme salva
	        @SuppressWarnings("unchecked")
	        java.util.Map<Integer, Integer> sceltePrecDaErrore = (java.util.Map<Integer, Integer>) request.getAttribute("sceltePrec");
	        if (sceltePrecDaErrore != null) {
	            ctx.setVariable("sceltePrec", sceltePrecDaErrore);
	        }
	        
	        String nomeConfigDaErrore = (String) request.getAttribute("nomeConfigSalvato");
	        if (nomeConfigDaErrore != null) {
	            ctx.setVariable("nomeConfigEsistente", nomeConfigDaErrore);
	        }

	        String errore = (String) request.getAttribute("errore");
	        if (errore != null) ctx.setVariable("errore", errore);

	        templateEngine.process("/SceltaSku.html", ctx, response.getWriter());

	    } catch (Exception e) {
	        e.printStackTrace();
	        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel caricamento");
	    }
	}
	
	public void destroy() {
		try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
	}
}