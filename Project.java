import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;

public class Project implements Serializable{

	private static final long serialVersionUID = 1L;
	
	private String nome;
	private String multicastAddress;
	private ArrayList<String> membri;
	private ArrayList<Card> TODO;
	private ArrayList<Card> INPROGRESS;
	private	ArrayList<Card> TOBEREVISED;
	private ArrayList<Card> DONE;
	
	public Project() { }
	
	public Project(String nome, String multicastAddress) {
		
		this.nome = nome;
		this.membri = new ArrayList<>();
		this.multicastAddress = multicastAddress;
		this.TODO = new ArrayList<>();
		this.INPROGRESS = new ArrayList<>();
		this.TOBEREVISED = new ArrayList<>();
		this.DONE = new ArrayList<>();
		
	}	
	
	/*
	 * 	Setters method
	 */
	public void addMembro(String membro) {
		
		this.membri.add(membro);
		
	}
	
	public void addCard(Card card) {
		
		this.TODO.add(card);
		
	}
	
	/*
	 * 	Getters method
	 */
	public String getNome() {
		
		return this.nome;
		
	}
	
	public String getMembro(int index) {
		
		return this.membri.get(index);
		
	}
	
	
	public ArrayList<String> getMembri() {
		
		return this.membri;
		
	}
	
	
	public String getMulticastAddress(){
	   
	   return this.multicastAddress;
	   
	}
	 
	
	/*
	 * 	Utility method
	 */
	
	/*
	 * 	@param cardName della card da eliminare
	 * 		   lista nome della lista dove cercare la card
	 * 
	 * 	@return Card se riesce ad eliminare la card dalla lista di partenza
	 * 			null se la card non è presente in quella lista
	 */
	@JsonIgnore
	public Card deleteCard(String cardName, String lista) {
		
		Card returnCard = null;
		switch(lista) {
		
			case "todo" : 										
				 
				for(int i = 0; i < this.TODO.size(); i++) {
					
					if(this.TODO.get(i).getNome().equals(cardName)) {
						
						returnCard = this.TODO.get(i);
						this.TODO.remove(i);
						
					}
					
				}
					
			break; 
		
			case "inprogress" 	:  
				
				for(int i = 0; i < this.INPROGRESS.size(); i++) {
					
					if(this.INPROGRESS.get(i).getNome().equals(cardName)) {
						
						returnCard = this.INPROGRESS.get(i);
						this.INPROGRESS.remove(i);
						
					}
					
				}
				
				
			break;
		
			case "toberevised"	:   
				
				for(int i = 0; i < this.TOBEREVISED.size(); i++) {
					
					if(this.TOBEREVISED.get(i).getNome().equals(cardName)) {
						
						returnCard = this.TOBEREVISED.get(i);
						this.TOBEREVISED.remove(i);
						
					}
					
				}
				
			break;
		
			case "done"			: 	
				
				for(int i = 0; i < this.DONE.size(); i++) {
					
					if(this.DONE.get(i).getNome().equals(cardName)) {
						
						returnCard = this.DONE.get(i);
						this.DONE.remove(i);
						
					}
					
				}
				
			break;
		
		}
		
		return returnCard;
		
	}
	
	/*
	 * 	@param cardName della card da spostare
	 * 		   listaPartenza nome della lista dove cercare all' inzio la card
	 * 		   listaDestinazione nome della lista dove spostare la card
	 * 
	 * 	@return true se riesce a spostare la card dalla listaPartenza alla listaDestinazione
	 * 			fase altrimenti
	 */
	@JsonIgnore
	public boolean moveCard(String cardName, String listaPartenza, String listaDestinazione) {
						
		/*	Verifichiamo i vincoli di precedenza delle liste	*/
		if(this.vincoliDiPrecedenza(listaPartenza, listaDestinazione)) {
			
			/*	Cerchiamo la card e la eliminiamo dalla lista partenza	*/
			Card c = this.deleteCard(cardName, listaPartenza);
			if(c != null) {
				
				switch(listaDestinazione) {
				
					case "todo" 		: c.setStato(listaDestinazione); c.setHistory("todo"); 			this.TODO.add(c);			break; 
					
					case "inprogress" 	: c.setStato(listaDestinazione); c.setHistory("inprogress"); 	this.INPROGRESS.add(c);		break;
					
					case "toberevised"	: c.setStato(listaDestinazione); c.setHistory("toberevised");	this.TOBEREVISED.add(c);	break;
					
					case "done"			: c.setStato(listaDestinazione); c.setHistory("done"); 			this.DONE.add(c);			break;	
			
				}
				return true;
				
			}
			else return false;	//Siamo nella situazione in cui la card non è presente nella lista indicata come listaPartenza
			
		}
		else return false;	
		
	}
	
	/*
	 * 	@param listaPartenza nome della lista di partenza
	 * 		   listaDestinazione nome della lista di destinazione
	 * 
	 * 	@return true se lo spostamento da listaPartenza a listaDestinazione rispetta i vincoli di precedenza richiesti dalle specifiche
	 * 			false altrimenti
	 */
	@JsonIgnore
	public boolean vincoliDiPrecedenza(String listaPartenza, String listaDestinazione) {
			
		boolean result = false;
		switch(listaPartenza) {
		
			case "todo" 		: if(listaDestinazione.equals("inprogress")) result = true; else result = false;										break; 
			
			case "inprogress" 	: if(listaDestinazione.equals("toberevised") || listaDestinazione.equals("done")) result = true; else result = false; 	break;
			
			case "toberevised"	: if(listaDestinazione.equals("inprogress") || listaDestinazione.equals("done")) result = true; else result = false;   	break;
			
			case "done"			: result = false;																										break;	
		
		}
		return result;
		
	}
	
	/*
	 * 	@return String contente le informazioni su tutte le card presenti nel progetto
	 */
	@JsonIgnore 
	public String getCardsInfo() {
		
		if(this.TODO.isEmpty() && this.INPROGRESS.isEmpty() && this.TOBEREVISED.isEmpty() && this.DONE.isEmpty()) {
			return "< nessuna card associata a " + this.nome;
		}
		else {	//C'è almeno una card
		
			String info = "";
			for(int i = 0; i < this.TODO.size(); i++) {
				
				info = info + "< card: nome=" + this.TODO.get(i).getNome() + ", stato=" + this.TODO.get(i).getStato() + "\n";
				
			}
			
			for(int i = 0; i < this.INPROGRESS.size(); i++) {
				
				info = info + "< card: nome=" + this.INPROGRESS.get(i).getNome() + ", stato=" + this.INPROGRESS.get(i).getStato() + "\n";
				
			}
			
			for(int i = 0; i < this.TOBEREVISED.size(); i++) {
				
				info = info + "< card: nome=" + this.TOBEREVISED.get(i).getNome() + ", stato=" + this.TOBEREVISED.get(i).getStato() + "\n";
				
			}
			
			for(int i = 0; i < this.DONE.size(); i++) {

				info = info + "< card: nome=" + this.DONE.get(i).getNome() + ", stato=" + this.DONE.get(i).getStato() + "\n";
				
			}
			
			return info;
			
		}
		
	}
	
	/*
	 * 	@param nickUtente da cercare tra i membri del progetto
	 * 
	 * 	@return true se nickUtente è membro del progetto
	 * 			false altrimenti
	 */
	@JsonIgnore
	public boolean isProjectMember(String nickUtente) {
		
		for(int i = 0; i < this.membri.size(); i++) {
			
			if(this.membri.get(i).equals(nickUtente))
				return true;
			
		}
		return false;
	}
	
	/*
	 * 	@param cardName della card da cercare nel progetto
	 * 
	 * 	@return Card se è presente nel progetto
	 * 		    null altrimenti
	 */
	@JsonIgnore
	public Card isProjectCard(String cardName) {
		
		for(int i = 0; i < this.TODO.size(); i++) {
			
			if(this.TODO.get(i).getNome().equals(cardName))
				return this.TODO.get(i);
			
		}
		
		for(int i = 0; i < this.INPROGRESS.size(); i++) {
			
			if(this.INPROGRESS.get(i).getNome().equals(cardName))
				return this.INPROGRESS.get(i);
			
		}
		
		for(int i = 0; i < this.TOBEREVISED.size(); i++) {
			
			if(this.TOBEREVISED.get(i).getNome().equals(cardName))
				return this.TOBEREVISED.get(i);
			
		}
		
		for(int i = 0; i < this.DONE.size(); i++) {
			
			if(this.DONE.get(i).getNome().equals(cardName))
				return this.DONE.get(i);
			
		}
		return null;
		
	}
	
	/*
	 * 	@return true se tutte le card sono in stato di DONE oppure non ci sono card
	 * 			false altrimenti
	 */
	@JsonIgnore
	public boolean isAllDone() {
	
		if(this.TODO.isEmpty() && this.INPROGRESS.isEmpty() && this.TOBEREVISED.isEmpty())	//In questo caso le liste sono o tutte vuote perche tutte le card sono in stato di done oppure perche non ci sono Card
			return true;
		else return false;
		
	}
	
}
