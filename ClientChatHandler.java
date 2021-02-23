import java.util.concurrent.*;
import java.util.*;

public class ClientChatHandler extends Thread{

	private HashMap<String, String> progettiIndirizzi;	//Key : projectName, value : multicastAddress
	private ConcurrentHashMap<String, String> chatList;	//Key : projectName, value : chatMsg
	private ExecutorService chatPool;
 	
	public ClientChatHandler(HashMap<String, String> progettiIndirizzi){
			
		this.progettiIndirizzi = progettiIndirizzi;
		this.chatList = new ConcurrentHashMap<String, String>();
		this.chatPool = Executors.newCachedThreadPool();
		
	}
	
	/*
	 * 	@Override
	 */
	public void run() {
		
		/*	Intanto attiviamo i task dei progetti che abbiamo	*/
		Iterator<Map.Entry<String, String>> it = this.progettiIndirizzi.entrySet().iterator();	//Costruiamo l' iteratore per iterare su tutta la hashmap
		while(it.hasNext()) {
			
			Map.Entry<String, String> entry = (Map.Entry<String, String>)it.next();
			String progetto = entry.getKey();
			String multicastAddress = entry.getValue();
			this.chatList.put(progetto, "");	//Inizializzo la chatList
			chatPool.execute(new ClientChatListener(progetto, multicastAddress, this.chatList));
			
		}
		
	}
	
	/*
	 * 	@effects shutdown del pool
	 */
	public void closePool() {
		
		this.chatPool.shutdown();
		
	}
	
	/*
	 * 	@param projectName del progetto nuovo
	 * 		   multicastaddress del progetto
	 * 
	 * 	@effects submit di un nuovo task al pool, abbiamo creato/siamo stati aggiunti a un nuovo progetto e attiviamo il thread della chat per questo progetto
	 */
	public void nuovaChatProgetto(String projectName, String multicastAddress) {
		
		this.chatList.put(projectName, "");
		this.chatPool.execute(new ClientChatListener(projectName, multicastAddress, this.chatList));
		
	}
	
	/*
	 * 	@param projectName del progetto di cui vogliamo recuperare la chat
	 * 
	 * 	@return String contente la chat di quel progetto
	 */
	public String getChatMessage(String projectName) {
		
		String chatMsg = this.chatList.get(projectName);
		if(chatMsg != null && !chatMsg.equals("")) {
			
			chatMsg = chatMsg + "\n< non ci sono altri messaggi";
			this.chatList.put(projectName, "");
			return chatMsg;
			
		}
		else return "< non ci sono nuovi messaggi";
		
	}	

}
