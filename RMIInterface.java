import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RMIInterface extends Remote{

	/*	
	 * 	@param	nickUtente, password
	 * 
	 * 	@return < ok, se l' utente viene registrato correttamente
	 * 			< errore, + descr,  se l' utente è gia registrato
	 * 
	 * 	@throws RemoteException	se si verificano errori durante la chiamata remota
	 *	@throws IllegalArgumentException se i parametri passati sono uguali a null 
	 */
	String register(String nickUtente, String password) throws RemoteException, IllegalArgumentException;
	
	/*
	 * 	@param ClientInterface 
	 * 
	 * 	@effects registra ClientInterface alla callback per ricevere update
	 * 
	 * 	@throws RemoteException se si verificano errori durante la chiamata remota
	 */
	public void registerForCallback(NotifyEventInterface ClientInterface) throws RemoteException;
	
	/*
	 * 	@param ClientInterface
	 * 
	 * 	@effects toglie ClientInterface dalla lista dei client interessati a update tramite callback
	 * 
	 * 	@throws RemoteException se si verificano errori durante la chiamata remota
	 */
	public void unregisterForCallback(NotifyEventInterface ClientInterface) throws RemoteException;
	
}
