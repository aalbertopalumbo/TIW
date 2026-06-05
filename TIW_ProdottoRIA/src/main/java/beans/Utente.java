package beans;

public class Utente {

	public enum RuoloUtente {CLIENTE, FORNITORE};
	
	private String username;
	private String nome;
	private String cognome;
	private String password;
	private RuoloUtente ruolo;
	
	
	public String getUsername() {
		return username;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getNome() {
		return nome;
	}
	
	public void setNome(String nome) {
		this.nome = nome;
	}
	
	public String getCognome() {
		return cognome;
	}
	
	public void setCognome(String cognome) {
		this.cognome = cognome;
	}
	
	public String getPassword() {
		return password;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}

	public RuoloUtente getRuolo() {
		return ruolo;
	}

	public void setRuolo(RuoloUtente ruolo) {
		this.ruolo = ruolo;
	}
}
