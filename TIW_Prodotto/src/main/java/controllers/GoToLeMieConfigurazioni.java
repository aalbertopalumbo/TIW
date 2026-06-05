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

@WebServlet("/GoToLeMieConfigurazioni")
public class GoToLeMieConfigurazioni extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;
    private TemplateEngine templateEngine;

    public void init() throws ServletException {
        try {
            ServletContext context = getServletContext();
            connection = ConnectionHandler.getConnection(
                context.getInitParameter("dbAddress"),
                context.getInitParameter("dbPort"),
                context.getInitParameter("dbName"),
                context.getInitParameter("dbUser"),
                context.getInitParameter("dbPassword")
            );
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
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("utente") == null ||
            ((Utente) session.getAttribute("utente")).getRuolo() != Utente.RuoloUtente.CLIENTE) {
        		request.setAttribute("errore", "Accesso negato o sessione scaduta.");
            request.getRequestDispatcher("/GoToHomePage").forward(request, response);
        }

        Utente utente = (Utente) session.getAttribute("utente");
        ConfigurazioneDAO dao = new ConfigurazioneDAO(connection);

        List<Configurazione> configurazioni = null;
        try {
            configurazioni = dao.getConfigurazioniByUtente(utente.getUsername());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore caricamento configurazioni");
            return;
        }

        final IWebExchange webExchange = JakartaServletWebApplication.buildApplication(getServletContext()).buildExchange(request, response);
        final WebContext ctx = new WebContext(webExchange, request.getLocale());

        ctx.setVariable("configurazioni", configurazioni);

        String errore = (String) request.getAttribute("errore");
        if (errore != null) ctx.setVariable("errore", errore);

        templateEngine.process("/LeMieConfigurazioni.html", ctx, response.getWriter());
    }

    public void destroy() {
        try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
    }
}