package beans;

import java.util.Date;
import java.util.List;

public class Configurazione {
	
	private int id;
	private String nome;
	private ProdottoComposto prodotto;
	private List<DettaglioConfigurazione> dettagli;
	private Date dataCreazione;
	private Date dataUltimaModifica;
	private double prezzoTotale;
	private Utente cliente;
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public ProdottoComposto getProdotto() {
		return prodotto;
	}

	public void setProdotto(ProdottoComposto prodotto) {
		this.prodotto = prodotto;
	}
	
	public List<DettaglioConfigurazione> getDettagli() {
		return dettagli;
	}
	
	public void setDettagli(List<DettaglioConfigurazione> dettagli) {
		this.dettagli = dettagli;
	}	
	
	public Date getDataCreazione() {
		return dataCreazione;
	}

	public void setDataCreazione(Date dataCreazione) {
		this.dataCreazione = dataCreazione;
	}

	public Date getDataUltimaModifica() {
		return dataUltimaModifica;
	}

	public void setDataUltimaModifica(Date dataUltimaModifica) {
		this.dataUltimaModifica = dataUltimaModifica;
	}

	public double getPrezzoTotale() {
		return prezzoTotale;
	}

	public void setPrezzoTotale(double prezzoTotale) {
		this.prezzoTotale = prezzoTotale;
	}

	public Utente getCliente() {
		return cliente;
	}

	public void setCliente(Utente cliente) {
		this.cliente = cliente;
	}

	
	

}
