import java.rmi.*;
import java.util.ArrayList;

public interface NotifyEventInterface extends Remote{

	/*
	 * 	@param nickUtente per ricevere aggiornamenti sul nickname
	 * 		   stato per ricevere aggiornamenti sullo stato(online/offline)
	 * 		   newRegister per capire se si tratta di una nuova registrazione o di un cambio di stato
	 * 
	 * 	@throws RemoteException se si verificano errrori nella chiamata remota
	 */
	public void notifyUsers(String nickUtente, String stato, boolean newRegister) throws RemoteException;
	
	/*
	 * 	@param utenteAggiunto, nickUtente dell' utente aggiunto al progetto (Caso isCanceled = false)
	 * 		   nomeProgetto del progetto in questione
	 * 		   multicastAddress associato al progetto
	 * 		   isCanceled, variabile, booleana per capire se si tratta di un aggiunta di un membro al progetto oppure della cancellazione di un progetto
	 * 		   allMembers, ArrayList contente i nickUtente di tutti i membri del progetto (Caso isCanceled = true)
	 * 
	 *  @throws RemoteException se si verificano errori nella chiamata remota
	 */
	public void notifyProject(String utenteAggiunto, String nomeProgetto, String multicastAddress, boolean isCanceled, ArrayList<String> allMembers) throws RemoteException;
	
}
