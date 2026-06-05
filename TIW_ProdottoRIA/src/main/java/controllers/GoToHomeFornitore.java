package controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
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

@WebServlet("/GoToHomeFornitore")
public class GoToHomeFornitore extends HttpServlet {
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
		
		// facciamo controllo doppio per evitare che qualcuno possa entrare mettendo l'url a mano
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("utente") == null) {
			// se uno non è né utente né fornitore
			response.sendRedirect(getServletContext().getContextPath() + "/HomePage.html");
			return;
		}

		Utente utente = (Utente) session.getAttribute("utente");
		
		// check anche su fornitore
		if (utente.getRuolo() != Utente.RuoloUtente.FORNITORE) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Non hai i permessi per vedere questa pagina");
			return;
		}

		// attiviamo la roba di thymeleaf
		SkuDAO skuDao = new SkuDAO(connection);
		ProdottoDAO prodottoDao = new ProdottoDAO(connection);
		
		List<Sku> listaSkus = new ArrayList<>();
		List<Prodotto> listaProdotti = new ArrayList<>();
		
		// fa le query coi DAO
		try {
			listaSkus = skuDao.trovaTutteLeSku(); 
			listaProdotti = prodottoDao.trovaProdottiDisponibili();
		} catch (SQLException e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore di connessione al database");
			return;
		}
		
		// creiamo il context di thymeleaf
		final IWebExchange webExchange = JakartaServletWebApplication.buildApplication(getServletContext()).buildExchange(request, response);
		final WebContext ctx = new WebContext(webExchange, request.getLocale());
		
		ctx.setVariable("listaSkus", listaSkus);
		ctx.setVariable("listaProdotti", listaProdotti);		
		
		String ultimoTipo = (String) session.getAttribute("ultimoCreatoTipo");
		Integer ultimoCodice = (Integer) session.getAttribute("ultimoCreatoCodice");

		if (ultimoTipo != null && ultimoCodice != null) {
			if (ultimoTipo.equals("SKU")) {
				// prendiamo la Sku appena creata
				Sku ultimoSku = null;
				try {
					ultimoSku = skuDao.findSkuById(ultimoCodice);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ctx.setVariable("dettaglioSku", ultimoSku);
				
			} else if (ultimoTipo.equals("PRODOTTO_SEMPLICE")) {
				// prendiamo il prodotto e la sua lista di SKU
				Prodotto ultimoProdSemplice = null;
				try {
					ultimoProdSemplice = prodottoDao.findProdottoSempliceById(ultimoCodice);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				List<Sku> skuAssociate = null;
				try {
					skuAssociate = skuDao.findSkuByProdotto(ultimoCodice);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				ctx.setVariable("dettaglioProdSemplice", ultimoProdSemplice);
				ctx.setVariable("skuAssociate", skuAssociate);
			} else if (ultimoTipo.equals("PRODOTTO_COMPOSTO")) {
				
				// prendiamo il prodotto dal db
				ProdottoComposto ultimoProdComposto = null;
				try {
					// prendiamo dati
					ultimoProdComposto = prodottoDao.findProdottoCompostoById(ultimoCodice);
					
					if (ultimoProdComposto != null) {
						// costruiamo l'albero in modo ricorsivo
						List<Prodotto> alberoFigli = prodottoDao.trovaSottoprodottiRicorsivi(ultimoCodice, skuDao);
						ultimoProdComposto.setComponenti(alberoFigli);
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
				
				ctx.setVariable("dettaglioProdComposto", ultimoProdComposto);
			}
			
			// rimuoviamo le info dalla sessione per evitare problemi
			session.removeAttribute("ultimoCreatoTipo");
			session.removeAttribute("ultimoCreatoCodice");
		}
		
		String path = "/HomeFornitore.html";
		// stampa il context nel path
		templateEngine.process(path, ctx, response.getWriter());
	}
		

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	public void destroy() {
		try {
			ConnectionHandler.closeConnection(connection);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}