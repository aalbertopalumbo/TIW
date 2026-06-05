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
import beans.Utente;
import dao.ConfigurazioneDAO;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import utils.ConnectionHandler;

@WebServlet("/GoToDettaglioConfigurazione")
public class GoToDettaglioConfigurazione extends HttpServlet {
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

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		// controllo cliente
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("utente") == null || ((Utente) session.getAttribute("utente")).getRuolo() != Utente.RuoloUtente.CLIENTE) {
			response.sendRedirect(getServletContext().getContextPath() + "/HomePage.html");
			return;
		}

		String idParam = request.getParameter("idConfig");
		if (idParam == null || idParam.isEmpty()) {
			response.sendRedirect(getServletContext().getContextPath() + "/GoToHomeCliente");
			return;
		}

		try {
			int idConfig = Integer.parseInt(idParam);
			ConfigurazioneDAO dao = new ConfigurazioneDAO(connection);
			
			// Andiamo a prendere la configurazione intera
			Configurazione config = dao.getConfigurazioneCompleta(idConfig);
			
			if (config == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND, "Configurazione non trovata");
				return;
			}
			
			Utente utenteConnesso = (Utente) session.getAttribute("utente");
			
			// impediamo di cambiare schermata
			if (!config.getCliente().getUsername().equals(utenteConnesso.getUsername())) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Non sei autorizzato a visualizzare questa configurazione.");
				return;
			}

			final IWebExchange webExchange = JakartaServletWebApplication.buildApplication(getServletContext()).buildExchange(request, response);
			final WebContext ctx = new WebContext(webExchange, request.getLocale());
			
			// passiamo l'oggetto configurazione
			ctx.setVariable("configurazione", config);
			templateEngine.process("/DettaglioConfigurazione.html", ctx, response.getWriter());

		} catch (NumberFormatException | SQLException e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel caricamento del dettaglio");
		}
	}
	
	public void destroy() { try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {} }
}