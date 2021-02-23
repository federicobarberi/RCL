import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class ClientChatListener implements Runnable{

	private final String multicastAddress;
	private final String projectName;
	private ConcurrentHashMap<String, String> chatList;	//Key : projectName, value : chatMsg
	private final int MULTICAST_PORT = 30000;
	private final int BUFFER_SIZE = 1024 * 1024;
	
	public ClientChatListener(String projectName, String multicastAddress, ConcurrentHashMap<String, String> chatList) {
		
		this.projectName = projectName;
		this.multicastAddress = multicastAddress;
		this.chatList = chatList;
		
	}
	
	/*
	 * 	@Override
	 */
	public void run() {
		
		try( MulticastSocket multicastGroup = new MulticastSocket(this.MULTICAST_PORT) ){
			
			/*	Ci uniamo al gruppo multicast	*/
			multicastGroup.joinGroup(InetAddress.getByName(multicastAddress));
			
			/*	Creazione buffer ricezione	*/
			byte[] rcvBuffer = new byte[BUFFER_SIZE];
			DatagramPacket rcvPacket = new DatagramPacket(rcvBuffer, rcvBuffer.length);
			
			/*	Leggiamo il messaggio ricevuto sulla chat e lo salviamo in chatList	*/
			while(true) {
				
				multicastGroup.receive(rcvPacket);
				String nuovoMessaggio = new String(rcvPacket.getData(), rcvPacket.getOffset(), rcvPacket.getLength());
				String messaggiPrecedenti = this.chatList.get(projectName);
				this.chatList.put(projectName, messaggiPrecedenti + "\n" + nuovoMessaggio); 
				
			}			
			
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		
	}
	
}
