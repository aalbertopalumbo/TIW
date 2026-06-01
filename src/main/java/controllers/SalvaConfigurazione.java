package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import beans.Prodotto;
import beans.ProdottoComposto;
import beans.ProdottoSemplice;
import beans.Sku;
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

@WebServlet("/SalvaConfigurazione")
public class SalvaConfigurazione extends HttpServlet {
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

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		HttpSession session = request.getSession(false);
		
		// controllo accessi
		if (session == null || session.getAttribute("utente") == null || ((Utente) session.getAttribute("utente")).getRuolo() != Utente.RuoloUtente.CLIENTE) {
			response.sendRedirect(getServletContext().getContextPath() + "/HomePage.html");
			return;
		}

		String nomeConfig = request.getParameter("nomeConfigurazione");
		String codiceRadiceStr = request.getParameter("codiceRadice");

		if (nomeConfig == null || nomeConfig.trim().isEmpty() || codiceRadiceStr == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Dati form mancanti");
			return;
		}

		try {
			int codiceRadice = Integer.parseInt(codiceRadiceStr);
			ProdottoDAO prodottoDao = new ProdottoDAO(connection);
			
			// controlliamo che l'id dell'oggetto sia effettivamente in db
			ProdottoComposto radice = prodottoDao.findProdottoCompostoById(codiceRadice);
			if (radice == null) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Prodotto non valido");
				return;
			}
			radice.setComponenti(prodottoDao.trovaSottoprodottiRicorsivi(codiceRadice, new SkuDAO(connection)));

			Map<Integer, Integer> scelteEffettuate = new HashMap<>();
			double[] prezzoTotale = {0.0}; // array per calcolare il prezzo totale
			
			// controlliamo che il client abbia inserito tutto
			boolean valido = verificaComponenti(radice.getComponenti(), request, scelteEffettuate, prezzoTotale);

			if (!valido) {
				// se il client non ha messo tutto errore
				request.setAttribute("errore", "Attenzione: Devi selezionare una SKU per ogni componente!");
				request.getRequestDispatcher("/GoToSceltaSku?codiceProdotto=" + codiceRadice).forward(request, response);
				return;
			}

			// se tutto ok salviamo
			ConfigurazioneDAO configDao = new ConfigurazioneDAO(connection);
			Utente utente = (Utente) session.getAttribute("utente");

			String idConfigModificaStr = request.getParameter("idConfigInModifica");

			if (idConfigModificaStr != null && !idConfigModificaStr.isEmpty()) {
			    // modifica configurazione esistente
			    int idConfigModifica = Integer.parseInt(idConfigModificaStr);
			    configDao.aggiornaConfigurazione(idConfigModifica, nomeConfig, prezzoTotale[0], scelteEffettuate);
			    response.sendRedirect(getServletContext().getContextPath() + "/GoToDettaglioConfigurazione?idConfig=" + idConfigModifica);
			} else {
			    // nuova configurazione
			    int newConfigId = configDao.salvaConfigurazione(nomeConfig, utente.getUsername(), codiceRadice, prezzoTotale[0], scelteEffettuate);
			    response.sendRedirect(getServletContext().getContextPath() + "/GoToDettaglioConfigurazione?idConfig=" + newConfigId);
			}

		} catch (NumberFormatException | SQLException e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel salvataggio");
		}
	}

	// Metodo ricorsivo che naviga l'albero ufficiale e controlla cosa ha inviato il client
	private boolean verificaComponenti(List<Prodotto> componenti, HttpServletRequest request, Map<Integer, Integer> scelte, double[] prezzoTotale) {
		if (componenti == null) return true;

		for (Prodotto p : componenti) {
			if (p instanceof ProdottoSemplice) {
				ProdottoSemplice ps = (ProdottoSemplice) p;
				// cerchiamo lo sku id
				String skuSceltaStr = request.getParameter("sku_" + ps.getCodice());
				
				if (skuSceltaStr == null || skuSceltaStr.trim().isEmpty()) {
					return false; // se utente non seleziona sku
				}
				
				try {
					int idSkuScelta = Integer.parseInt(skuSceltaStr);
					// controllo che la sku esista davvero
					boolean skuValida = false;
					for (Sku skuPossibile : ps.getSkus()) {
						if (skuPossibile.getCodice() == idSkuScelta) {
							skuValida = true;
							prezzoTotale[0] += skuPossibile.getPrezzo(); // aggiungiamo il prezzo
							break;
						}
					}
					if (!skuValida) return false; // se la sku non è valida false
					
					scelte.put(ps.getCodice(), idSkuScelta);
					
				} catch (NumberFormatException e) {
					return false; // non fa inserire testo (vuole id)
				}
			} 
			else if (p instanceof ProdottoComposto) {
				// Se è composto, scendiamo nei suoi figli ricorsivamente
				if (!verificaComponenti(((ProdottoComposto) p).getComponenti(), request, scelte, prezzoTotale)) {
					return false;
				}
			}
		}
		return true;
	}
	
	public void destroy() { try { ConnectionHandler.closeConnection(connection); } catch (Exception e) {} }
}