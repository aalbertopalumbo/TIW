package beans;

import java.util.List;

public class ProdottoComposto extends Prodotto {
	
	private List<Prodotto> componenti;
	private String descrizione;
	private double prezzoMin;
	private double prezzoMax;
	
	public List<Prodotto> getComponenti() {
		return componenti;
	}
	
	public void setComponenti(List<Prodotto> componenti) {
		this.componenti = componenti;
	}

	public String getDescrizione() {
		return descrizione;
	}

	public void setDescrizione(String descrizione) {
		this.descrizione = descrizione;
	}

	public double getPrezzoMin() {
		return prezzoMin;
	}

	public void setPrezzoMin(double prezzoMin) {
		this.prezzoMin = prezzoMin;
	}

	public double getPrezzoMax() {
		return prezzoMax;
	}

	public void setPrezzoMax(double prezzoMax) {
		this.prezzoMax = prezzoMax;
	}
	
	public List<Sku> getSkus() { return null; }
	
	public String getTipo() { 
	    return "PRODOTTO_COMPOSTO"; 
	}

}
