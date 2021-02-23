/*	RMI import	*/
import java.rmi.*;
import java.rmi.server.*;

/*	Java IO/NIO import	*/
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.io.File;
import java.io.IOException;

/*	Utility import	*/
import java.net.*;
import java.util.*;

/*	JSON import	*/
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonAutoDetect;	
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;

public class Server extends RemoteServer implements RMIInterface{

	private final static long serialVersionUID = 1L;
	
	/*	Utility per la comunicazione	*/
	private final int TCP_PORT;
	private final int RMI_PORT;			
	private final int MULTICAST_PORT;
	private final int BUFFER_SIZE = 1024 * 1024;
	
	/*	Liste private Server	*/
	private ArrayList<Utente> listaUtenti;
	private ArrayList<Project> listaProgetti;
	private HashMap<String, String> progettiIndirizzi; 	//key : projectName, value : multicastAdd. HashMap che mette in relazione progetti e indirizzi
	
	/*	Utility per uniformità	*/
	private final String SUCCESS = "< ok,";		//Format : "< ok," 		+ descr 
	private final String ERROR = "< errore,";	//Format : "< errore, " + descr
	private final String ONLINE = "online";
	private final String OFFLINE = "offline";
	private final String TODO = "todo";
	private final String INPROGRESS = "inprogress";
	private final String TOBEREVISED = "toberevised";
	private final String DONE = "done";
	
	/*	Utility per Json	*/
	ObjectMapper mapper;
	private final String WORTH_DIR = "./WORTH";
	private final String BACKUP_DIR = "/Backup";
	private final String USERS_FILE = "/BackupUsers.json";
	private final String PROJECT_FILE = "/BackupProject.json";	
	
	/*	Lista dei client registrati alla callback	*/
	private List <NotifyEventInterface> clients;
	
	public Server(int tcpPort, int rmiPort, int multicastPort) {
		
		super();	//Per la callback
		
		this.TCP_PORT = tcpPort;
		this.RMI_PORT = rmiPort;
		this.MULTICAST_PORT = multicastPort;
		
		this.clients = new ArrayList<NotifyEventInterface>();
		this.progettiIndirizzi = new HashMap<String, String>();
		
		this.mapper = new ObjectMapper();
		this.mapper.enable(SerializationFeature.INDENT_OUTPUT);	//Formattazione più leggibile
		this.mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
		
		System.out.println("> avvio ricostruzione stato del sistema...");
		this.backupDir();
		System.out.println("> avvio backup utenti...");
		this.backupUtenti();
		System.out.println("> avvio backup progetti...");
		this.backupProgetti();
		System.out.println("> backup completati, sistema ripristinato");
		
	}
	
	/*
	 * 	@Utility method
	 * 	
	 * 	@effects controlla se esistono o no le cartelle WORTH e Backup e nel caso le crea
	 */
	private void backupDir() {
		
		/*	Controlliamo se esiste o no la directory WORTH e nel caso la creiamo	*/
		File worthDir = new File(WORTH_DIR);
		if(!worthDir.exists()) {
			
			if(!worthDir.mkdir())	System.err.println(ERROR + " something goes wrong with mkdir...");
			
		}
		
		/*	Controlliamo se esiste o no la directory di Backup e la creiamo	*/
		File backupDir = new File(WORTH_DIR + BACKUP_DIR);
		if(!backupDir.exists()) {
			
			if(!backupDir.mkdir())	System.err.println(ERROR + " something goes wrong with mkdir...");
			
		}
		
	}
	
	/*
	 * 	@Utility method
	 * 
	 * 	@effects funzione di utilità per ripristinare la lista degli utenti
	 */
	private void backupUtenti(){
		
		/*	Recupero informazione sugli utenti	*/
		File usersFile = new File(WORTH_DIR + BACKUP_DIR + USERS_FILE);
		try {
			
			/*	Se il file di backup utenti non esiste lo creiamo e inizializziamo la lista utenti	*/
			if(!usersFile.exists()) {	
				usersFile.createNewFile();
				this.listaUtenti = new ArrayList<>();
			}
			
			/*	Altrimenti riempiamo la lista con le informazioni contenute nel file	*/
			else {	
				
				ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
				try ( FileChannel inChannel= FileChannel.open(Paths.get(usersFile.getPath()), StandardOpenOption.READ) ) {
					boolean stop = false;
					while (!stop) {
						if (inChannel.read(buffer) == -1){
							stop = true;
						}
					}
				}
				catch (IOException e) {
					e.printStackTrace();
					return;
				}
				try {
					
					if(buffer.position() == 0) {	//Caso in cui creo solo file vuoto, altrimenti lancerebbe un eccezione fare backup da un file vuoto
						
						this.listaUtenti = new ArrayList<>();
						
					}
					else {
						
						this.listaUtenti = mapper.reader()
								.forType(new TypeReference<ArrayList<Utente>>() {})
								.readValue(buffer.array());	
						
					}

				}
				catch (IOException e){
					e.printStackTrace();
					return;
				}
				
				/*	Per evitare situazioni di inconsistenza al avvio/riavvio settiamo offline tutti gli utenti	*/
				this.setAllOffline();
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}		
		
	}
	
	/*
	 * 	@Utility method
	 * 	
	 * 	@effects funzione di utilità per ripristinare la lista dei progetti
	 */
	private void backupProgetti() {
		
		File projectFile = new File(WORTH_DIR + BACKUP_DIR + PROJECT_FILE);
		
		try {
			
			/*	Se il file dei progetti non esiste lo creiamo	*/
			if(!projectFile.exists()) {
				
				projectFile.createNewFile();
				this.listaProgetti = new ArrayList<>();
				
			}
			
			/*	Altrimenti facciamo il backup dal file	*/
			else {
				
				ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
				buffer.clear();
				try ( FileChannel inChannel= FileChannel.open(Paths.get(projectFile.getPath()), StandardOpenOption.READ) ) {
					boolean stop = false;
					while (!stop) {						
						if (inChannel.read(buffer) == -1){
							stop = true;
						}
					}
				}
				catch (IOException e) {
					e.printStackTrace();
					return;
				}
				try {
					
					if(buffer.position() == 0) {
						
						this.listaProgetti = new ArrayList<>();
						
					}
					else {
						
						 this.listaProgetti = mapper.reader()
									.forType(new TypeReference<ArrayList<Project>>() {})
									.readValue(buffer.array());
						 
						/*	Ripristino anche le associazioni progetti indirizzi multicast	*/
						for(int i = 0; i < this.listaProgetti.size(); i++) {
								
							this.progettiIndirizzi.put(this.listaProgetti.get(i).getNome(), this.listaProgetti.get(i).getMulticastAddress());
								
						}
						
					}

				}
				catch (IOException e){
					e.printStackTrace();
					return;
				}
				
			}
					
		}
		catch(IOException e) {
			
			e.printStackTrace();
			
		}
		
	}
	
	/*
	 * 	@Utilitiy method
	 * 
	 * 	@effects funzione di supporto alla backupUtenti che setta offline tutti gli utenti 
	 */
	private void setAllOffline() {
		
		for(int i = 0; i < listaUtenti.size(); i++) {
			
			listaUtenti.get(i).setStato(OFFLINE);
			
		}
		
	}
	
	/*
	 * 	@effects Avvia il server relativo al servizio WORTH, il server utilizza il multiplexing dei canali per accettare richieste TCP da parte dei client
	 * 			 Ogni volta che viene stabilita una connessione, si registra un interesse di lettura poichè il client invierà un comando
	 * 			 A seguito di ogni comando il server invierà un messaggio di esito del comando e si rimetterà in attesa di nuovi comandi.	
	 */
	public void start() {	
		
		/*	Set up server TCP tramite i Selector	*/
		try(
			ServerSocketChannel serverChannel = ServerSocketChannel.open();
		){
			
			/*	Configurazione server in modalità non bloccante	*/
			ServerSocket ss = serverChannel.socket();
			InetSocketAddress address = new InetSocketAddress(this.TCP_PORT);
			ss.bind(address);
			serverChannel.configureBlocking(false);
			Selector selector = Selector.open();
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
			
			System.out.println("> set up completed, WORTH online! #TCP : " + this.TCP_PORT + " #RMI : " + this.RMI_PORT + " #MULTICAST : " + this.MULTICAST_PORT);
			
			/*	Attesa richieste	*/
			while(true) {
				
				if(selector.select() == 0)
					continue;
				
				/*	Set delle chiavi e iteratore	*/
				Set<SelectionKey> readyKeys = selector.selectedKeys();
				Iterator<SelectionKey> iterator = readyKeys.iterator();
				
				while(iterator.hasNext()) {
					
					SelectionKey key = iterator.next();
					iterator.remove();
					
					try {
						
						/*	Accettare connessione	*/
						if(key.isAcceptable()) {
							
							ServerSocketChannel server = (ServerSocketChannel) key.channel();
							SocketChannel client = server.accept();
							client.configureBlocking(false);
							registerRead(selector, client);
							
						}
						
						/*	Lettura comando del client	*/
						if(key.isReadable()) {
							
							readCMD(selector, key);
							
						}
						
						/*	Risposta al comando del client	*/
						else if(key.isWritable()) {
							
							writeResponse(selector, key);
							
						}
						
					}
					catch(IOException e) {		//Terminazione improvvisa Client
						
						e.printStackTrace();
						key.channel().close();
                        key.cancel();
						
					}
					
				}
				
			}		
			
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		
	}
	
	/*
	 * 	@Utility method
	 * 
	 *  @param sel per la selezione dei canali
	 *  	   client SocketChannel del client
	 *  
	 *  @effects registra l'interesse in lettura sul canale del client, instanziando anche i buffer per la comunicazione
	 *  
	 *  @thros IOException se si verificano errori di I/O
	 */
	private void registerRead(Selector sel, SocketChannel client) throws IOException {
		
		ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer message = ByteBuffer.allocate(this.BUFFER_SIZE);
        ByteBuffer[] bfs = {length, message};
		client.register(sel, SelectionKey.OP_READ, bfs);
		
	}
	
	/*	
	 * 	@Utility method
	 * 
	 * 	@param 	sel per la selezione dei canali
	 * 			key relativa al canale del client
	 * 	
	 * 	@effects legge dal canale del client il comando inviato e registra l'interesse in scrittura con l'esito dell operazione richiesta
	 * 
	 * 	@throws IOException se si verificano errori di I/O
	 */
	private void readCMD(Selector sel, SelectionKey key) throws IOException{
		
		SocketChannel client = (SocketChannel)key.channel();
		ByteBuffer[] buffers = (ByteBuffer[]) key.attachment(); //buffer 0 per la dimensione, buffer 1 per il comando
		client.read(buffers);
		if(!buffers[0].hasRemaining()) {
			
			/*	Recuperiamo lunghezza comando dal buffer 0	*/
			buffers[0].flip();
			int cmdLength = buffers[0].getInt();
			
			/*	Recuperiamo il comando	*/
			if(buffers[1].position() == cmdLength) {
				
				buffers[1].flip();
				String cmd = new String(buffers[1].array()).trim();	//requestLine ricevuta dal client
				String[] cmdTkz = cmd.split(" ");	//Tokenizziamo la requestLine, esattamente come lato Client
				
				switch(cmdTkz[0]) {	//Format : cmd [args]
				
					case "login"  			: client.register(sel, SelectionKey.OP_WRITE, this.login(cmdTkz[1], cmdTkz[2]));										break;
					
					case "logout" 			: client.register(sel, SelectionKey.OP_WRITE, this.logout(cmdTkz[1]));													break;
				
					case "listprojects" 	: client.register(sel, SelectionKey.OP_WRITE, this.listProjects(cmdTkz[1]));											break;
					
					case "createproject"	: client.register(sel, SelectionKey.OP_WRITE, this.createProject(cmdTkz[1], cmdTkz[2]));								break;
					
					case "addmember" 		: client.register(sel, SelectionKey.OP_WRITE, this.addMember(cmdTkz[1], cmdTkz[2], cmdTkz[3]));							break;
						
					case "showmembers"		: client.register(sel, SelectionKey.OP_WRITE, this.showMembers(cmdTkz[1], cmdTkz[2]));									break;
					
					case "showcards"		: client.register(sel, SelectionKey.OP_WRITE, this.showCards(cmdTkz[1], cmdTkz[2]));									break;
					
					case "showcard"			: client.register(sel, SelectionKey.OP_WRITE, this.showCard(cmdTkz[1], cmdTkz[2], cmdTkz[3]));							break;
					
					case "addcard"			: client.register(sel, SelectionKey.OP_WRITE, this.addCard(cmdTkz[1], cmdTkz[2], cmdTkz[3], cmdTkz[4]));				break;	
					
					case "movecard"			: client.register(sel, SelectionKey.OP_WRITE, this.moveCard(cmdTkz[1], cmdTkz[2], cmdTkz[3], cmdTkz[4], cmdTkz[5]));	break;
					
					case "getcardhistory"	: client.register(sel, SelectionKey.OP_WRITE, this.getCardHistory(cmdTkz[1], cmdTkz[2], cmdTkz[3]));     				break;
					
					case "cancelproject"	: client.register(sel, SelectionKey.OP_WRITE, this.cancelProject(cmdTkz[1], cmdTkz[2])); 								break;
					
					case "help"   			: client.register(sel, SelectionKey.OP_WRITE, this.helpMenu());															break; 
					
					case "exit"   			: client.close(); key.cancel(); 																						break;
					
				}
				
			}			
			
		}
		
	}
	
	/*	
	 * 	@Utility method
	 * 
	 * 	@param 	sel per la selezione dei canali
	 * 			key relativa al canale del client
	 * 	
	 * 	@effects scrive una risposta relativa all' esito dell' operazione richiesta dal Client
	 * 
	 * 	@throws IOException se si verificano errori di I/O
	 */
	private void writeResponse(Selector sel, SelectionKey key) throws IOException{
		
		SocketChannel client = (SocketChannel)key.channel();
		String response = (String)key.attachment();
		ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes());
		client.write(responseBuffer);
		
		/*	Registriamo una read per leggere il prossimo comando del client	*/
		if(!responseBuffer.hasRemaining()) {
			
			responseBuffer.clear();
			registerRead(sel, client);
			
		}
		
	}
	
	/*
	 * 	@Utility method
	 * 
	 * 	@param element da cercare nella lista
	 * 		   wichList in quale lista cercare (0 -> listaUtenti, 1 -> projectList)
	 * 
	 * 	@return index di element nella lista se presente
	 * 			-1 altrimenti
	 */
	private int contains(String element, int wichList) {
		
		if(wichList == 0) {
			
			for(int i = 0; i < listaUtenti.size(); i++) {
				
				if(listaUtenti.get(i).getNickUtente().equals(element))
					return i;
			}
			return -1;
			
		}
		
		if(wichList == 1) {
			
			for(int i = 0; i < this.listaProgetti.size(); i++) {
				
				if(this.listaProgetti.get(i).getNome().equals(element))
					return i;
				
			}
			return -1;
			
		}
		
		return -1;	
		
	}
	
	/*
	 * 	@RMI method
	 * 
	 * 	@param nickUtente dell'utente da registrare
	 * 		   password dell' utente da registrare
	 * 
	 * 	@return "< ok," se l' utente viene registrato correttamente
	 * 			"< errore," utente già presente altrimenti
	 */
	public synchronized String register(String nickUtente, String password) throws RemoteException, IllegalArgumentException{
		
		/*	Controllo sui parametri e se esiste gia un utente registrato con quel nickUtente	*/
		if(nickUtente == null) throw new IllegalArgumentException();
		if(password == null) throw new IllegalArgumentException();
		if(this.contains(nickUtente, 0) != -1 || nickUtente.equals("****")) return ERROR + " utente " + nickUtente + " gia registrato";
		
		/*	Creiamo l' utente e lo aggiungiamo alla lista	*/
		Utente ut = new Utente(nickUtente, password, OFFLINE);
		listaUtenti.add(ut);
		
		/*	Aggiorniamo il file JSON contenente gli utenti	e mandiamo messaggio di successo	*/
		this.jsonUpdate(this.listaUtenti, WORTH_DIR + BACKUP_DIR + USERS_FILE);
		
		/*	Effettuiamo la callback perchè c' è un nuovo utente	*/
		try {
			updateUsers(nickUtente, OFFLINE, true);
		}
		catch(RemoteException e) {
			e.printStackTrace();
		}
		
		return SUCCESS;
		
	}
	
	/*
	 * 	@RMI method
	 */
	public synchronized void registerForCallback(NotifyEventInterface ClientInterface) throws RemoteException{
		
		if(!clients.contains(ClientInterface)) {
			
			clients.add(ClientInterface);
			
		}
		
	}
	
	/*
	 * 	@RMI method
	 */
	public synchronized void unregisterForCallback(NotifyEventInterface ClientInterface) throws RemoteException{
		
		if(!clients.remove(ClientInterface))
			System.out.println("> unable to unregister client");
		
	}
	
	/*
	 * 	@RMI method
	 * 
	 * 	@param nickUtente dell' utente che si è registrato o ha cambiato stato
	 * 		   stato nuovo stato dell' utente
	 * 		   newRegister per capire se si tratta di una nuova registrazione o di un cambio di stato
	 * 
	 * 	@effects esegue la callback avvisando tutti i client registrati di un nuovo cambio di stato oppure di una nuova registrazione
	 */
	private synchronized void doCallbackUsers(String nickUtente, String stato, boolean newRegister) throws RemoteException{
		
		/*	Informiamo tutti i client tramite callback dell' evento	*/
		Iterator<NotifyEventInterface> i = clients.iterator();
		while(i.hasNext()) {
			
			NotifyEventInterface client = (NotifyEventInterface) i.next();
			client.notifyUsers(nickUtente, stato, newRegister);		
		
		}
		
	}
	
	/*
	 * 	@RMI method
	 * 
	 * 	@param nickUtente dell' utente che si è registrato o ha cambiato stato
	 * 		   stato nuovo stato dell' utente
	 * 		   newRegister per capire se si tratta di una nuova registrazione o di un cambio di stato
	 * 
	 * 	@effects richiama la funzione per eseguire la callback
	 */
	public void updateUsers(String nickUtente, String stato, boolean newRegister) throws RemoteException{
		
		doCallbackUsers(nickUtente, stato, newRegister);
		
	}
	
	/*
	 * 	@RMI method
	 * 
	 * 	@param utenteAggiunto nickUtente dell' utente aggiunto al progetto, null se si tratta di una cancellazione
	 * 		   nomeProgetto del progetto in questione
	 * 		   multicastAddress del progetto in questione
	 * 		   isCanceled per capire se si tratta di una cancellazione di progetto oppure di un aggiunta membro
	 * 		   allMembers lista di tutti i membri del progetto, null se si tratta di un aggiunta membro
	 * 
	 * 	@effects esegue la callback avvisando tutti i client di una nuova aggiunta membro oppure di una cancellazione progetto
	 */
	private synchronized void doCallbackProject(String utenteAggiunto, String nomeProgetto, String multicastAddress, boolean isCanceled, ArrayList<String> allMembers) throws RemoteException{
		
		Iterator<NotifyEventInterface> i = clients.iterator();
		while(i.hasNext()) {
			
			NotifyEventInterface client = (NotifyEventInterface) i.next();
			client.notifyProject(utenteAggiunto, nomeProgetto, multicastAddress, isCanceled, allMembers);
			
		}
		
	}
	
	/*
	 * 	@RMI method
	 * 
	 * 	@param utenteAggiunto nickUtente dell' utente aggiunto al progetto, null se si tratta di una cancellazione
	 * 		   nomeProgetto del progetto in questione
	 * 		   multicastAddress del progetto in questione
	 * 		   isCanceled per capire se si tratta di una cancellazione di progetto oppure di un aggiunta membro
	 * 		   allMembers lista di tutti i membri del progetto, null se si tratta di un aggiunta membro
	 * 
	 * 	@effects richiama la funzione per eseguire la callback
	 */
	public void updateProject(String utenteAggiunto, String nomeProgetto, String multicastAddress, boolean isCanceled, ArrayList<String> allMembers) throws RemoteException{
		
		doCallbackProject(utenteAggiunto, nomeProgetto, multicastAddress, isCanceled, allMembers);
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param nickUtente dell' utente di cui vogliamo effettuare la login
	 * 		   password dell' utente di cui vogliamo effettuare la login
	 * 
	 * 	@return "< ok, +descr" se l' operazione di login è andata a buon fine, invia la lista utenti registrati e anche i progetti con indirizzi multicast di cui è membro l'utente, aggiorna anche il file json degli utenti per il backup
	 * 			"< errore, +descr" altrimenti
	 */
	private String login(String nickUtente, String password){
		
		/*	Controllo se l' utente esiste e se non fosse gia loggato	*/
		int index = this.contains(nickUtente, 0);
		if(index != -1){
			
			if(listaUtenti.get(index).getStato().equals(ONLINE))
				return ERROR + " utente già online impossibile loggare";
			
			if(!listaUtenti.get(index).getPassword().equals(password))
				return ERROR + " password non corretta";
			
			listaUtenti.get(index).setStato(ONLINE);
			
			/*	Eseguiamo la chiamata di callback	*/
			try {
				updateUsers(nickUtente, ONLINE, false);	
			}
			catch(RemoteException e) {
				e.printStackTrace();
			}
			return SUCCESS + this.sendUserList() + " ****" + this.sendAddressList(nickUtente);	//Qui insieme al codice di successo mandiamo la lista utenti e la lista indirizzi multicast dei progetti di cui è membro 
		}
		return ERROR + " utente " + nickUtente + " non esistente";
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param nickUtente di cui vogliamo effettuare la logout
	 * 
	 * 	@return "< ok, +descr" se l' operazione di logout è andata a buon fine
	 * 			"< errore, +descr" altrimenti
	 */
	private String logout(String nickUtente) {
		
		int index = this.contains(nickUtente, 0);
		if(index != -1) {
			
			if(listaUtenti.get(index).getStato().equals(ONLINE)) {
				
				listaUtenti.get(index).setStato(OFFLINE);
				
				/*	Eseguiamo la chiamata di callback	*/
				try {
					updateUsers(nickUtente, OFFLINE, false);
				}
				catch(RemoteException e) {
					e.printStackTrace();
				}
				return SUCCESS + " " + nickUtente + " scollegato";
				
			}
			return ERROR + " utente già offline";
			
		}
		return ERROR + " utente " + nickUtente + " non esistente";
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param nickUtente di chi ha effettuato la richiesta
	 * 
	 * 	@return "< ok, +descr" se l' utente è membro di almeno un progetto
	 * 			"< errore, +descr" altrimenti
	 */
	private String listProjects(String nickUtente) {
		
		String ret = SUCCESS + " lista progetti di cui è membro " + nickUtente + "\n";
		boolean found = false; 	//Se l' utente è membro di almeno un progetto viene settata a true
		for(int i = 0; i < this.listaProgetti.size(); i++) {
			
			if(this.listaProgetti.get(i).isProjectMember(nickUtente)) {
				if(!found) found = true;
				ret = ret + "< project: " + this.listaProgetti.get(i).getNome() + "\n";
			}
					
		}
		if(!found)
			ret = ERROR + nickUtente + " non è membro di nessun progetto";
		return ret;
		
	}
	
	/*
	 * 	@Utility method
	 * 
	 * 	@return String contente un indirizzo multicast per un progetto generato Random ma controllando che sia unico e che sia valido
	 */
	private String multicastGenerator() {
		
		Random rnd = new Random();
		String multicastAddress = "";
		boolean approved= false;
		while(!approved) {
			
			int byte1 = rnd.nextInt(16) + 224;
			int byte2 = rnd.nextInt(256);
			int byte3 = rnd.nextInt(256);
			int byte4 = rnd.nextInt(256);
			multicastAddress = byte1 + "." + byte2 + "." + byte3 + "." + byte4;
			
			try {
				
				/*	Controlliamo che sia effettivamente un indirizzo valido	*/
				if(InetAddress.getByName(multicastAddress).isMulticastAddress()) {
					
					/*	Controlliamo che non sia ancora stato assegnato	*/
					if(!this.progettiIndirizzi.containsValue(multicastAddress))	approved = true;
					
				}
				
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			
		}
		return multicastAddress;
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param projectName da creare
	 * 		   nickUtente che ha creato il progetto da aggiungere come membro
	 * 
	 * 	@return "< ok, +descr" se l' operazione di createProject è andata a buon fine, viene anche aggiornato il file json per il backup dei progetti
	 * 			"< errore, +descr" altrimenti
	 */
	private String createProject(String projectName, String nickUtente) {
		
		int index = this.contains(projectName, 1);
		if(index == -1) {
			
			/*	Creiamo il nuovo progetto e la propria cartella, se tutto va a buon fine aggiungiamo nickUtente tra i membri	*/
			String multicastAddress = this.multicastGenerator();
			Project newProg = new Project(projectName, multicastAddress);
			File progDir = new File(WORTH_DIR + "/" + projectName);
			if(!progDir.mkdir())
				return ERROR + " something goes wrong with mkdir...";
			
			else {
				
				newProg.addMembro(nickUtente);
				this.listaProgetti.add(newProg);
				this.progettiIndirizzi.put(projectName, multicastAddress);
				
				/*	Aggiorniamo il file JSON relativo al progetto	*/
				this.jsonUpdate(this.listaProgetti, WORTH_DIR + BACKUP_DIR + PROJECT_FILE);
				
				return SUCCESS + " " + projectName + " creato " + multicastAddress; //Comunichiamo anche a chi crea il progetto l' indirizzo multicast scelto
				
			}
			
		}
		else return ERROR + " progetto già esistente";
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param projectName del progetto in cui vogliamo aggiungere l 'utente
	 * 		   nickUtente dell' utente da aggiungere al progetto
	 *		   whoRequest contente il nickUtente di chi ha effettuato la richiesta 
	 *
	 * 	@return "< ok, +descr" se l' operazione di addMember è andata a buon fine, viene anche aggiornato il file json per il backup dei progetti
	 * 			"< errore, +descr" altrimenti
	 */
	private String addMember(String projectName, String nickUtente, String whoRequest) {
		
		int indexUt = this.contains(nickUtente, 0);
		int indexProg = this.contains(projectName, 1);
		
		/*	Controlliamo se esiste il progetto	*/
		if(indexProg != -1) {
			
			/*	Controlliamo se chi ha effettuato la richiesta è membro	*/
			if(this.listaProgetti.get(indexProg).isProjectMember(whoRequest)) {
				
				/*	Controlliamo se esiste l' utente	*/
				if(indexUt != -1) {
					
					/*	Controlliamo se non fosse gia membro	*/
					if(!this.listaProgetti.get(indexProg).isProjectMember(nickUtente)) {
						
						/*	Aggiungiamo l' utente alla lista dei membri, aggiorniamo il file json e poi ritorniamo successo	*/
						this.listaProgetti.get(indexProg).addMembro(nickUtente);
						this.jsonUpdate(this.listaProgetti, WORTH_DIR + BACKUP_DIR + PROJECT_FILE);
						
						/*	Mandiamo anche la callback per fare sapere all' utente che è stato aggiunto e dargli l'indirizzo multicast	*/
						try {
							this.updateProject(nickUtente, projectName, this.listaProgetti.get(indexProg).getMulticastAddress(), false, null);
						}
						catch(RemoteException e) {
							e.printStackTrace();
						}
						return SUCCESS + " " + nickUtente + " aggiunto a " + projectName;
						
					}
					else return ERROR + " utente " + nickUtente + " già membro";
					
				}
				else return ERROR + " utente " + nickUtente + " non esistente";
				
			}
			else return ERROR + " non hai i permessi per effettuare questa operazione";
			
		}
		else return ERROR + " progetto non esistente";
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param projectName del progetto di cui vogliamo vedere i membri
	 * 		   nickUtente di chi fa la richiesta
	 * 
	 * 	@return "< ok, +descr" se l' operazione di showMembers è andata a buon fine
	 * 			"< errore, +descr" altrimenti
	 */
	private String showMembers(String projectName, String nickUtente) {
		
		int indexProg = this.contains(projectName, 1);
		
		/*	Controlliamo se esiste il progetto	*/
		if(indexProg != -1) {
			
			/*	Controlliamo se chi ha effettuato la richiesta è membro	*/
			if(this.listaProgetti.get(indexProg).isProjectMember(nickUtente)) {
				
				ArrayList<String> tmp = this.listaProgetti.get(indexProg).getMembri();
				String ret = "";
				for(int i = 0; i < tmp.size(); i++) {
					
					ret = ret + "< member: " + tmp.get(i) + "\n";
					
				}
				return SUCCESS + " progetto " + projectName + "\n" + ret; 
				
			}
			else return ERROR + " non hai i permessi per effettuare questa operazione";
			
		}
		else return ERROR + " progetto non esistente";
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param projectName di cui vogliamo visualizzare le cards
	 * 		   nickUtente che richiede l'operazione
	 * 
	 * 	@return "< ok, +descr" se l' operazione di showCards è andata a buon fine
	 * 			"< errore, +descr" altrimenti
	 */
	private String showCards(String projectName, String nickUtente) {
		
		int indexProg = this.contains(projectName, 1);
		
		/*	Controlliamo se il progetto esiste	*/
		if(indexProg != -1) {
			
			/*	Controlliamo se l'utente è membro	*/
			if(this.listaProgetti.get(indexProg).isProjectMember(nickUtente)) {
				
				return SUCCESS + " progetto " + projectName + "\n" + this.listaProgetti.get(indexProg).getCardsInfo();
				
			}
			else return ERROR + " non hai i permessi per effettuare questa operazione";
			
		}
		else return ERROR + " progetto non esistente";
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param projectName del progetto di cui vogliamo recuperare la card
	 * 		   cardName della card di cui vogliamo vedere le informazioni
	 * 		   nickUtente di chi fa la richiesta
	 * 
	 * 	@return "< ok, +descr" se l' operazione di showCard è andata a buon fine
	 * 			"< errore, +descr" altrimenti
	 */
	private String showCard(String projectName, String cardName, String nickUtente) {
		
		int indexProg = this.contains(projectName, 1);
		
		/*	Controlliamo se il progetto esiste	*/
		if(indexProg != -1) {
			
			/*	Controlliamo se l'utente è membro	*/
			if(this.listaProgetti.get(indexProg).isProjectMember(nickUtente)) {
				
				/*	Controlliamo se la card esiste	*/
				Card card = this.listaProgetti.get(indexProg).isProjectCard(cardName);
				if(card != null) {
					
					return SUCCESS + " informazioni sulla card richiesta\n" +
								   "< progetto: " + projectName + "\n" +
								   "< nome: " + card.getNome() + "\n" +
								   "< descrizione: " + card.getDescrizione() + "\n" +
								   "< stato: " + card.getStato();
					
				}
				else return ERROR + " card non esistente";
				
			}
			else return ERROR + " non hai i permessi per effettuare questa operazione";
			
		}
		else return ERROR + " progetto non esistente";
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param projectName del progetto a cui vogliamo aggiungere la nuova card
	 * 		   cardName della card che vogliamo creare
	 * 		   descrizione della card che vogliamo creare
	 * 		   nickUtente di chi fa la richiesta
	 * 
	 * 	@return "< ok, +descr" se l' operazione di addCard è andata a buon fine, viene anche aggiornato il file json per il backup dei progetti e mandato un messaggio sulla chat
	 * 			"< errore, +descr" altrimenti
	 */
	private String addCard(String projectName, String cardName, String descrizione, String nickUtente) {
		
		int indexProg = this.contains(projectName, 1);
		
		/*	Controlliamo se il progetto esiste	*/
		if(indexProg != -1) {
			
			/*	Controlliamo se l' utente è membro	*/
			if(this.listaProgetti.get(indexProg).isProjectMember(nickUtente)) {
				
				/*	Controlliamo che non esista gia una card dello stesso nome	*/
				if(this.listaProgetti.get(indexProg).isProjectCard(cardName) == null) {
					
					Card newCard = new Card(cardName, descrizione);
					
					/*	Aggiorniamo il file json per i backup dei progetti	*/
					this.listaProgetti.get(indexProg).addCard(newCard);
					this.jsonUpdate(this.listaProgetti, WORTH_DIR + BACKUP_DIR + PROJECT_FILE);
					
					/*	Creiamo il file della card e popoliamo il file json	*/
					String path = WORTH_DIR + "/" + projectName + "/" + cardName + ".json";
					File cardFile = new File(path);
					try {
						cardFile.createNewFile();
					}
					catch(IOException e) {
						e.printStackTrace();
					}
					this.jsonUpdate(newCard, path);
					
					/*	Comunichiamo sulla chat che è stata aggiunta una nuova card	*/
					String msg = "< messaggio da Worth: '" + nickUtente + " ha aggiunto la card " + cardName + " a " + projectName + "'";
					try( DatagramSocket serverSock = new DatagramSocket() ){
						
						DatagramPacket sendPacket = new DatagramPacket(msg.getBytes(), msg.length(), InetAddress.getByName(this.progettiIndirizzi.get(projectName)), MULTICAST_PORT);
						serverSock.send(sendPacket);
						
					}
					catch(IOException e) {
						e.printStackTrace();
					}
					
					return SUCCESS + " card " + cardName + " aggiunta al progetto " + this.listaProgetti.get(indexProg).getNome();
					
				}
				else return ERROR + " carta già presente nel progetto";
				
			}
			else return ERROR + " non hai i permessi per effettuare questa operazione";
			
		}
		else return ERROR + " progetto non esistente";
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param projectName del progetto di cui vogliamo spostare la card
	 * 		   cardName della card che vogliamo spostare
	 * 		   listaPartenza da cui vogliamo togliere la card
	 * 		   listaDestinazione in cui vogliamo spostare la card
	 *	
	 * 	@return "< ok, +descr" se l' operazione di moveCard è andata a buon fine, viene anche mandato un messaggio sulla chat del progetto e aggiornato il file json relativo al backup dei progetti e quello della card
	 * 			"< errore, +descr" altrimenti
	 */
	private String moveCard(String projectName, String cardName, String listaPartenza, String listaDestinazione, String nickUtente) {
		
		int indexProg = this.contains(projectName, 1);
		
		/*	Controlliamo se il progetto esiste	*/
		if(indexProg != -1) {
			
			/*	Controlliamo se l'utente è membro	*/
			if(this.listaProgetti.get(indexProg).isProjectMember(nickUtente)) {
				
				/*	Controlliamo se la card esiste	*/
				if(this.listaProgetti.get(indexProg).isProjectCard(cardName) != null) {
					
					/* Controlliamo se le liste passate sono valide	*/
					if(listaPartenza.equals(TODO) || listaPartenza.equals(INPROGRESS) || listaPartenza.equals(TOBEREVISED) || listaPartenza.equals(DONE)) {
						
						if(listaDestinazione.equals(TODO) || listaDestinazione.equals(INPROGRESS) || listaDestinazione.equals(TOBEREVISED) || listaDestinazione.equals(DONE)) {
							
							boolean result = this.listaProgetti.get(indexProg).moveCard(cardName, listaPartenza, listaDestinazione);
							if(result) {	//Aggiorniamo i file e ritorniamo successo
								
								Card card = this.listaProgetti.get(indexProg).isProjectCard(cardName);
								this.jsonUpdate(this.listaProgetti, WORTH_DIR + BACKUP_DIR + PROJECT_FILE);
								String path = WORTH_DIR + "/" + projectName + "/" + cardName + ".json";
								this.jsonUpdate(card, path);
								
								/*	Mandiamo anche messaggio multicast per far sapere che una card è stata spostata	*/
								String msg = "< messaggio da Worth: '" + nickUtente + " ha spostato " + cardName + " da " + listaPartenza + " a " + listaDestinazione + "'";
								try( DatagramSocket serverSock = new DatagramSocket() ){
									
									DatagramPacket sendPacket = new DatagramPacket(msg.getBytes(), msg.length(), InetAddress.getByName(this.progettiIndirizzi.get(projectName)), MULTICAST_PORT);
									serverSock.send(sendPacket);
									
								}
								catch(IOException e) {
									e.printStackTrace();
								}
								
								return SUCCESS + " card spostata con successo";
								
							}
							else return ERROR + " card non presente in lista di partenza o vincoli di precedenza non rispettati";
							
						}
						else return ERROR + " la lista di destinazione non è valida";
						
					}
					else return ERROR + " la lista di partenza non è valida";
					
				}
				else return ERROR + " card non esistente";
				
			}	
			else return ERROR + " non hai i permessi per effettuare questa operazione";
			
		}
		else return ERROR + " progetto non esistente";
		
	}
	
	/*
	 * 	@TCP method
	 * 	
	 * 	@param projectName del progetto di cui vogliamo recuperare la card
	 * 		   cardName della card di cui vogliamo vedere la cronologia
	 *         nickUtente di chi fa la richiesta
	 *         
	 * 	@return "< ok, +descr" se l' operazione di getCardHistory è andata a buon fine
	 * 			"< errore, +descr" altrimenti      
	 */
	private String getCardHistory(String projectName, String cardName, String nickUtente) {
		
		int indexProg = this.contains(projectName, 1);
		
		/*	Controlliamo se il progetto esiste	*/
		if(indexProg != -1) {
			
			/*	Controlliamo se l'utente è membro	*/
			if(this.listaProgetti.get(indexProg).isProjectMember(nickUtente)) {
				
				Card card = this.listaProgetti.get(indexProg).isProjectCard(cardName);
				
				/*	Controlliamo se la card esiste	*/
				if(card != null) {
					
					return SUCCESS + " " + card.getHistory();
					
				}
				else return ERROR + " card non esistente";
				
			}
			else return ERROR + " non hai i permessi per effettuare questa operazione";
			
		}
		else return ERROR + " progetto non esistente";
		
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@param projectName del progetto che vogliamo eliminare
	 * 		   nickUtente di chi fa la richiesta
	 * 
	 * 	@return "< ok, +descr" se l' operazione di cancelProject è andata a buon fine, viene anche effettuata la callback, e aggiornato il file json relativo al backup dei progetti
	 * 			"< errore, +descr" altrimenti
	 */
	private String cancelProject(String projectName, String nickUtente) {
		
		int indexProg = this.contains(projectName, 1);
		
		/*	Controlliamo se il progetto esiste	*/
		if(indexProg != -1) {
			
			/*	Controlliamo se l' utente è membro	*/
			if(this.listaProgetti.get(indexProg).isProjectMember(nickUtente)) {
				
				/*	Controlliamo se tutte le card sono in stato di done, oppure non ci sono card	*/
				if(this.listaProgetti.get(indexProg).isAllDone()) {
					
					/*	Cancelliamo tutti i file e la directory	*/
					File path = new File(WORTH_DIR + "/" + projectName);
					this.deleteDirectory(path);
					
					/*	Aggiorniamo tutti i membri del progetto che è stato chiuso	*/
					ArrayList<String> allMembers = this.listaProgetti.get(indexProg).getMembri();
					int index = allMembers.indexOf(nickUtente); allMembers.remove(index);	//Da questa lista togliamo chi ha fatto la richiesta
					try {
						this.updateProject(null, projectName, this.listaProgetti.get(indexProg).getMulticastAddress(), true, allMembers);
					}
					catch(RemoteException e) {
						e.printStackTrace();
					}
					this.progettiIndirizzi.remove(projectName);	//Andiamo anche a eliminare l' indirizzo multicast associato a quel progetto nella lista
					
					/*	Eliminiamo il progetto dalla lista e aggiorniamo file json di backup	*/
					this.listaProgetti.remove(indexProg);
					
					this.jsonUpdate(this.listaProgetti, WORTH_DIR + BACKUP_DIR + PROJECT_FILE);

					return SUCCESS + " progetto " + projectName + " chiuso"; 
					
				}
				else return ERROR + " per eliminare il progetto tutte le card devono essere in stato done";
				
			}
			else return ERROR + " non hai i permessi per effettuare questa operazione";
			
		}
		else return ERROR + " progetto non esistente";
		
	}
	
	/*
	 * 	@Utility method
	 * 
	 * 	@overview funzione di supporto alla cancelProject per eliminare tutti i file all' interno della directory e poi la directory stessa
	 * 
	 * 	@return true se riesce a cancellare tutti i file all' interno della directory identificata da 'path' e poi anche la direcotry stessa
	 * 			false altrimenti
	 */
	private boolean deleteDirectory(File path) {
		
        if(path.exists()) {
        	
        	File[] files = path.listFiles();
        	for(int i = 0; i < files.length; i++) {
        		
        		if(files[i].isDirectory()) {
        			deleteDirectory(files[i]);
        		}
        		else {
        			files[i].delete();	
        		}	
        		
        	}
        	
		 }
        
		 return(path.delete());
		 
	}
	
	/*
	 * 	@TCP method
	 * 
	 * 	@return String contenente un menu di aiuto che verrà inviato al client e visualizzato poi dall' utente
	 */
	private String helpMenu() {
		
		String help = "\n---------- WORTH MENU ----------" + "\n" +
					  "Sintassi comandi(in minuscolo) e parametri da passare : " + "\n" +
				      "1) register nickUtente password" + "\n" +
					  "2) login nickUtente password" + "\n" +
					  "3) logout nickUtente" + "\n" +
				  	  "4) listusers" + "\n" +
				  	  "5) listonlineusers" + "\n" +
				  	  "6) listprojects" + "\n" +
				  	  "7) createproject projectName" + "\n" +
				  	  "8) addmember projectName nickUtente (Project Member only)" + "\n" +
				  	  "9) showmembers projectName (Project Member only)" + "\n" +
				  	  "10) showcards projectName (Project Member only)" + "\n" +
				  	  "11) showcard projectName cardName (Project Member only)" + "\n" +
				  	  "12) addcard projectName cardName descrizione (Project Member only)" + "\n" +
				  	  "13) movecard projectName cardName listaPartenza listaDestinazione (Project Member only)" + "\n" +
				  	  "14) getcardhistory projectName cardName (Project Member only)" + "\n" +
				  	  "15) readchat projectName (Project Member only)" + "\n" +
				  	  "16) send projectName messaggio (Project Member only)" + "\n" +
				  	  "17) cancelproject projectName (Project Member only)" + "\n" +
				  	  "18) exit" + "\n";
		return help;
		
	}
	
	/*	
	 * 	@Utility method
	 * 
	 * 	@return String contente la lista di progetti con il loro indirizzo multicast cosi da poterla mandare al client dopo aver eseguito la login cosi può crearsela localmente
	 */
	private String sendAddressList(String nickUtente) {
		
		String tmp = "";
		for(int i = 0; i < listaProgetti.size(); i++) {
			
			if(this.listaProgetti.get(i).isProjectMember(nickUtente)) {
				
				tmp = tmp + " " + this.listaProgetti.get(i).getNome() + " " + this.listaProgetti.get(i).getMulticastAddress();
				
			}
			
		}
		return tmp;
		
	}
	
	/*	
	 * 	@Utility method
	 * 
	 * 	@return String contente la lista utenti cosi da poterla mandare al client dopo aver eseguito la login cosi può crearsi una sua lista locale di utenti	
	 */
	private String sendUserList() {
		
		String tmp = "";
		for(int i = 0; i < listaUtenti.size(); i++) {
			
			String tmpUser = listaUtenti.get(i).getNickUtente();
			String tmpStato = listaUtenti.get(i).getStato();	
			
			tmp = tmp + " " + tmpUser + " " + tmpStato;
			
		}
		return tmp;
		
	}
	
	/*
	 * 	@Utility method
	 * 
	 * 	@param obj che vogliamo scrivere sul file json
	 * 		   path del file json
	 * 
	 *  @effects scrive l'oggetto obj sul file json identificato da path
	 */
	private void jsonUpdate(Object obj, String filePath) {
		
		File file = new File(filePath);
		
		if(file.exists()) {		//Ulteriore controllo
			
			try {
				this.mapper.writeValue(file, obj);
			}
	        catch (JsonGenerationException e) {
	            e.printStackTrace();
	        }
	        catch (JsonMappingException e) {
	            e.printStackTrace();
	        }
	        catch (IOException e) {
	            e.printStackTrace();
	        }
			
		}	

	}
	
}
