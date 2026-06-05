package beans;

import java.util.List;

public class Prodotto {
	
	private int codice;
	private String nome;
	
	public int getCodice() {
		return codice;
	}
	
	public void setCodice(int codice) {
		this.codice = codice;
	}
	
	public String getNome() {
		return nome;
	}
	
	public void setNome(String nome) {
		this.nome = nome;
	}
	
	public List<Sku> getSkus() { return null; }
	
	public List<Prodotto> getComponenti() { return null; }
	
	public String getTipo() { 
	    return "PRODOTTO"; 
	}

}
