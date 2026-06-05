package beans;

import java.util.List;

public class ProdottoSemplice extends Prodotto {

	private List<Sku> skus;

	public List<Sku> getSkus() {
		return skus;
	}

	public void setSkus(List<Sku> skus) {
		this.skus = skus;
	}
	
	public List<Prodotto> getComponenti() { return null; }
	
	public String getTipo() { 
	    return "PRODOTTO_SEMPLICE"; 
	}
}
