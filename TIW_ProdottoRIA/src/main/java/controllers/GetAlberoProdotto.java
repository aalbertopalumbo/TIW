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
import beans.ProdottoComposto;
import beans.ProdottoSemplice;
import beans.Sku;
import dao.ProdottoDAO;
import dao.SkuDAO;
import utils.ConnectionHandler;

@WebServlet("/GetAlberoProdotto")
public class GetAlberoProdotto extends HttpServlet {
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

		String codiceStr = request.getParameter("codice");
		String tipo = request.getParameter("tipo"); // prendiamo il tipo di prodotto
		if (codiceStr == null || tipo == null) { response.setStatus(HttpServletResponse.SC_BAD_REQUEST); return; }

		try {
			
			ProdottoDAO pDao = new ProdottoDAO(connection);
			SkuDAO sDao = new SkuDAO(connection);
			int codice = Integer.parseInt(codiceStr);
			JsonObject rootJson = new JsonObject();
			
			if ("SKU".equalsIgnoreCase(tipo)) {
				// sku
				Sku s = sDao.findSkuById(codice); 
				if (s != null) {
					rootJson.addProperty("tipo", "SKU"); // Nome speciale per il JS
					rootJson.addProperty("codice", s.getCodice());
					rootJson.addProperty("nome", s.getNome());
					rootJson.addProperty("prezzo", s.getPrezzo());
					rootJson.addProperty("descrizione", s.getDescrizione());
					rootJson.addProperty("foto", s.getFoto());
					rootJson.addProperty("isNuovo", false);
				}
			} else {
		
				// se è composto
				if ("COMPOSTO".equalsIgnoreCase(tipo)) {
					ProdottoComposto pc = pDao.findProdottoCompostoById(codice);
					if (pc != null) {
						// prendiamo tutti i sottoprodotti
						pc.setComponenti(pDao.trovaSottoprodottiRicorsivi(codice, sDao));
						rootJson = convertiCompostoInJson(pc);
					}
				} else {
					Prodotto p = pDao.findProdottoSempliceById(codice);
					if (p != null) {
						List<Sku> skus = sDao.findSkuByProdotto(codice);
						rootJson = convertiSempliceInJson(p, skus);
					}
				}
			}
			
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(new Gson().toJson(rootJson));
				
		} catch (SQLException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	// traduce da prodotto a json
	private JsonObject convertiCompostoInJson(ProdottoComposto pc) {
		JsonObject obj = new JsonObject();
		obj.addProperty("tipo", "COMPOSTO");
		obj.addProperty("codice", pc.getCodice());
		obj.addProperty("nome", pc.getNome());
		obj.addProperty("descrizione", pc.getDescrizione());
		obj.addProperty("prezzoMin", pc.getPrezzoMin());
		obj.addProperty("prezzoMax", pc.getPrezzoMax());
		obj.addProperty("isNuovo", false);

		JsonArray figli = new JsonArray();
		if (pc.getComponenti() != null) {
			for (Prodotto figlio : pc.getComponenti()) {
				// trovaSottoprodottiRicorsivi mette tutto in una List<Prodotto>, facciamo i cast
				if (figlio instanceof ProdottoComposto) {
					figli.add(convertiCompostoInJson((ProdottoComposto) figlio));
				} else if (figlio instanceof ProdottoSemplice) {
					ProdottoSemplice ps = (ProdottoSemplice) figlio;
					figli.add(convertiSempliceInJson(ps, ps.getSkus()));
				}
			}
		}
		obj.add("figli", figli);
		return obj;
	}

	private JsonObject convertiSempliceInJson(Prodotto ps, List<Sku> skus) {
		JsonObject obj = new JsonObject();
		obj.addProperty("tipo", "SEMPLICE");
		obj.addProperty("codice", ps.getCodice());
		obj.addProperty("nome", ps.getNome());
		obj.addProperty("isNuovo", false);

		JsonArray skusArray = new JsonArray();
		if (skus != null) {
			for (Sku s : skus) {
				JsonObject skuObj = new JsonObject();
				skuObj.addProperty("codice", s.getCodice());
				skuObj.addProperty("nome", s.getNome());
				skuObj.addProperty("prezzo", s.getPrezzo());
				skuObj.addProperty("descrizione", s.getDescrizione());
				skuObj.addProperty("foto", s.getFoto());
				skusArray.add(skuObj);
			}
		}
		obj.add("skus", skusArray);
		obj.add("nuoveSkus", new JsonArray()); // vuoto di default
		return obj;
	}

	public void destroy() { try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {} }
}