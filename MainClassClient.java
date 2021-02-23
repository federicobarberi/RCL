
public class MainClassClient {

	final static int TCP_PORT = 5678;
	final static int RMI_PORT = 9000;
	final static int MULTICAST_PORT = 30000;
	
	public static void main(String[] args) {
		
		Client client = new Client(TCP_PORT, RMI_PORT, MULTICAST_PORT);
		client.start();
		System.exit(0);
		
	}
	
}
