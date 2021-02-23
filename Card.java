import java.io.Serializable;

public class Card implements Serializable{
	
	private static final long serialVersionUID = 1L;

	private String nome;
	private String descrizione;
	private String stato;
	private String history;	
	
	public Card(String nome, String descrizione) {
		
		this.nome = nome;
		this.descrizione = descrizione;
		this.stato = "todo";		//Alla creazione di una card viene messa nello stato "todo"
		this.history = "todo -> ";
		
	}
	
	public Card() { }
	
	/*
	 * 	Setters method
	 */
	public void setNome(String nome) {
		
		this.nome = nome;
		
	}
	
	public void setDescrizione(String descrizione) {
		
		this.descrizione = descrizione;
		
	}
	
	public void setStato(String stato) {
		
		this.stato = stato;
		
	}
	
	public void setHistory(String stato) {
		
		if(stato != null) {
			
			if(!this.stato.equals("done")) this.history = this.history + stato + " -> ";
			else this.history = this.history + stato;
			
		}
		
	}
	
	/*
	 * 	Getters method
	 */	
	public String getNome() {
		
		return this.nome;
		
	}
	
	public String getDescrizione() {
		
		return this.descrizione;
		
	}
	
	public String getStato() {
		
		return this.stato;
		
	}
	
	public String getHistory() {
		
		return this.history;
		
	}
	
}