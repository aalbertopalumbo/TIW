package beans;

public class DettaglioConfigurazione {
	
	private ProdottoSemplice componente;
	private Sku skuSelezionata;

	public ProdottoSemplice getComponente() {
		return componente;
	}

	public void setComponente(ProdottoSemplice componente) {
		this.componente = componente;
	}

	public Sku getSkuSelezionata() {
		return skuSelezionata;
	}

	public void setSkuSelezionata(Sku skuSelezionata) {
		this.skuSelezionata = skuSelezionata;
	}
}