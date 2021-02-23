import java.io.Serializable;

public class Utente implements Serializable{
	
	private static final long serialVersionUID = 1L;

	private String nickUtente;
	private String password;
	private String stato;
	
	public Utente(String nickUtente, String password, String stato) {
		
		this.nickUtente = nickUtente;
		this.password = password;
		this.stato = stato;
		
	}
	
	/*	
	 * 	Costruttore di default per json
	 */
	public Utente() { }
	
	/*	
	 * 	Setters method
	 */	
	public void setNickUtente(String nickUtente) { 
		
		this.nickUtente = nickUtente; 
		
	}
	
	public void setPassword(String password) { 
		
		this.password = password;	
		
	}

	public void setStato(String stato) { 
		
		this.stato = stato;	
		
	}
	
	/*
	 * 	Getters method
	 */
	public String getNickUtente() { 
		
		return this.nickUtente; 
		
	}
	
	public String getPassword() { 
		
		return this.password;	
		
	}
	
	public String getStato() {	
		
		return this.stato;	
		
	}
	
}
