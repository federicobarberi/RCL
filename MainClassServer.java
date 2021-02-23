import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class MainClassServer {

	final static int TCP_PORT = 5678;
	final static int RMI_PORT = 9000;
	final static int MULTICAST_PORT = 30000;
	
	public static void main(String[] args){
		
		Server server = new Server(TCP_PORT, RMI_PORT, MULTICAST_PORT);
		
		/*	Set up RMI	*/
		try {
			
			/*	Esportazione dell' oggetto	*/
			RMIInterface stub = (RMIInterface) UnicastRemoteObject.exportObject(server, RMI_PORT);
			
			/*	Creazione di un registry sulla porta 'RMI_PORT'	*/
			LocateRegistry.createRegistry(RMI_PORT);
			Registry r = LocateRegistry.getRegistry(RMI_PORT);
			
			/*	Pubblicazione dello stub nel registry	*/
			r.rebind("USERDATABASE", stub);
			
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		server.start();
		
		System.exit(0);
		
	}
	
}
