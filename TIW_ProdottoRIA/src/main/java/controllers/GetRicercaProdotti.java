package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import beans.Prodotto;
import beans.Sku;
import dao.ProdottoDAO;
import dao.SkuDAO;
import utils.ConnectionHandler;

@WebServlet("/GetRicercaProdotti")
public class GetRicercaProdotti extends HttpServlet {
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

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (request.getSession(false) == null || request.getSession().getAttribute("utente") == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return;
		}

		String keyword = request.getParameter("keyword");
		if (keyword == null || keyword.trim().isEmpty()) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST); return;
		}

		try {
			ProdottoDAO pDao = new ProdottoDAO(connection);
			SkuDAO sDao = new SkuDAO(connection);
			
			// chiama entrambi i dao
			List<Prodotto> prodotti = pDao.cercaProdotti(keyword); 
			List<Sku> skus = sDao.cercaSku(keyword);
			
			JsonArray jsonArray = new JsonArray();
			
			// aggiunge i prodotti
			for (Prodotto p : prodotti) {
				JsonObject obj = new JsonObject();
				obj.addProperty("codice", p.getCodice());
				obj.addProperty("nome", p.getNome());
				obj.addProperty("tipo", p.getTipo().replace("PRODOTTO_", "")); 
				jsonArray.add(obj);
			}
			// aggiunge le sku
			for (Sku s : skus) {
				JsonObject obj = new JsonObject();
				obj.addProperty("codice", s.getCodice());
				obj.addProperty("nome", s.getNome());
				obj.addProperty("tipo", "SKU"); 
				jsonArray.add(obj);
			}
			
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(new Gson().toJson(jsonArray));
			
		} catch (SQLException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
	public void destroy() { try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {} }
}