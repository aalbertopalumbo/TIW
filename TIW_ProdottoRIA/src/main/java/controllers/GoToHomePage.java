package controllers;

import java.io.IOException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/GoToHomePage")
public class GoToHomePage extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private TemplateEngine templateEngine;

	public void init() throws ServletException {
		
		JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(getServletContext());
		WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);
		templateResolver.setTemplateMode(TemplateMode.HTML);
		templateResolver.setPrefix("/"); // Cerca i file partendo dalla cartella webapp
		templateResolver.setSuffix(".html");
		
		this.templateEngine = new TemplateEngine();
		this.templateEngine.setTemplateResolver(templateResolver);
	}

	// per logout e inizializzazione
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		// prendiamo la sessione e la distruggiamo se esiste già, così togliamo i dati di log
		HttpSession session = request.getSession(false);
		
		if (session != null) {
			session.invalidate();
		}
		
		final IWebExchange webExchange = JakartaServletWebApplication.buildApplication(getServletContext()).buildExchange(request, response);
		final WebContext ctx = new WebContext(webExchange, request.getLocale());

		// stampiamo l'errore
		String errore = (String) request.getAttribute("errore");
		if (errore != null) {
		    // Lo metti a disposizione di Thymeleaf
		    ctx.setVariable("errore", errore);
		}
		// -----------------------------

		templateEngine.process("/HomePage.html", ctx, response.getWriter());
	}

	// non usato ma vabbe
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
}