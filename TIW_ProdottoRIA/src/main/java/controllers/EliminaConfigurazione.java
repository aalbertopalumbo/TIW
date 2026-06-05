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
import dao.ConfigurazioneDAO;
import utils.ConnectionHandler;

@WebServlet("/EliminaConfigurazione")
public class EliminaConfigurazione extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;

    public void init() throws ServletException {
        try {
            ServletContext context = getServletContext();
            connection = ConnectionHandler.getConnection(
                context.getInitParameter("dbAddress"), 
                context.getInitParameter("dbPort"),
                context.getInitParameter("dbName"), 
                context.getInitParameter("dbUser"),
                context.getInitParameter("dbPassword"));
        } catch (SQLException e) {
            throw new UnavailableException("Couldn't get db connection");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("utente") == null ||
            ((Utente) session.getAttribute("utente")).getRuolo() != Utente.RuoloUtente.CLIENTE) {
            response.sendRedirect(getServletContext().getContextPath() + "/HomePage.html");
            return;
        }

        String idStr = request.getParameter("idConfig");
        if (idStr == null) {
            response.sendRedirect(getServletContext().getContextPath() + "/GoToLeMieConfigurazioni");
            return;
        }

        try {
            new ConfigurazioneDAO(connection).eliminaConfigurazione(Integer.parseInt(idStr));
        } catch (SQLException e) {
            e.printStackTrace();
            request.setAttribute("errore", "Errore durante l'eliminazione.");
            request.getRequestDispatcher("/GoToLeMieConfigurazioni").forward(request, response);
            return;
        }

        response.sendRedirect(getServletContext().getContextPath() + "/GoToLeMieConfigurazioni");
    }

    public void destroy() {
        try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {}
    }
}