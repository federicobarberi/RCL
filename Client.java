/*	RMI Import	*/
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;

/*	Java IO/NIO Import	*/
import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/*	Java util Import	*/
import java.util.*;

public class Client extends RemoteObject implements NotifyEventInterface{

	private final static long serialVersionUID = 1L;

	/*	Utility per la comunicazione	*/
	private final int TCP_PORT;
	private final int RMI_PORT;
	private final int MULTICAST_PORT;
	private final int BUFFER_SIZE = 1024 * 1024;

	/*	Liste private Client	*/
	private HashMap<String, String> localUserList;		//Key : nickUtente - value : stato
	private HashMap<String, String> progettiIndirizzi;	//key : projectName - value : multicastAdd. Contiene le associazioni progetti - indirizzi multicast di cui l' utente è membro

	/*	Utility per uniformità	*/
	private final String SUCCESS = "< ok,";		//Format : "< ok," 		+ descr
	private final String ERROR = "< errore,";	//Format : "< errore, " + descr

	/*	Buffers per il dialogo tra client e server	*/
	private ByteBuffer lengthBuffer;
	private ByteBuffer clientRequestBuffer;
	private ByteBuffer serverResponseBuffer;

	/*	Utility per i comandi	*/
	private boolean exit;
	private boolean logged;
	private boolean poolStart;
	private String userName;	//Per tenere traccia di chi è che sta eseguendo le operazioni

	/*	Thread Handler per la chat	*/
	private ClientChatHandler chatHandler = null;

	public Client(int tcpPort, int rmiPort, int multicastPort) {

		super();	//Per NotifyeventInterface
		this.TCP_PORT = tcpPort;
		this.RMI_PORT = rmiPort;
		this.MULTICAST_PORT = multicastPort;

		this.localUserList = new HashMap<String, String>();

		this.exit = false;
		this.logged = false;
		this.poolStart = false;
		this.userName = null;

	}

	/*
	 * 	@RMI method callback
	 *
	 * 	@param nickUtente di cui è arrivato l' aggiornamento
	 * 		   stato in cui è aggiornato l' utente
	 * 		   newRegister per capire se è una nuova registrazione o un semplice cambio di stato
	 *
	 * 	@effects a seguito di un evento che puo essere la registrazione al servizio di un nuovo utente
	 * 			 oppure il cambiamento di stato di un utente, aggiorniamo i valori ricevuti nella nostra lista utenti e stampiamo una notifica
	 */
	public void notifyUsers(String nickUtente, String stato, boolean newRegister) {

		/*	Per evitare di ricevere notifiche appartenenti a noi stessi	*/
		if(!this.userName.equals(nickUtente)) {

			if(!newRegister) {

				String returnMessage = "< messaggio da Worth: " + nickUtente + " è " + stato + "\n";
				System.out.println(returnMessage);

			}
			else {

				String returnMessage = "< messaggio da Worth: " + nickUtente + " si è appena registrato\n";
				System.out.println(returnMessage);

			}

		}

		/*	Aggiungiamo un nuovo utente, o semplicemente cambiamo il suo stato	*/
		this.localUserList.put(nickUtente, stato);

	}

	/*
	 * 	@RMI method callback
	 *
	 * 	@param utenteAggiunto nickUtente dell' utente aggiunto al progetto
	 * 		   nomeProgetto a cui abbiamo aggiunto l' utente
	 *         multicastAddress del progetto
	 *         isCanceled per capire se il progetto è stato cancellato o siamo stati aggiunti a un progetto
	 *         allMembers lista di tutti i membri del progetto, viene passata solo se isCanceled = true, altrimenti è null
	 *
	 *  @effects a seguito di un evento che puo essere la cancellazione o l' aggiunta come membri a un progetto, aggiorniamo i valori della nostra lista
	 *  		 locale progetti indirizzi, avviamo il pool o gli sottomettiamo un task, e stampiamo una notifica
	 */
	public void notifyProject(String utenteAggiunto, String nomeProgetto, String multicastAddress, boolean isCanceled, ArrayList<String> allMembers) {

		/*	Se il progetto non è stato cancellato	*/
		if(!isCanceled) {

			/*	Se siamo stati aggiunti noi lo comunichiamo e salviamo l' associazione progetto indirizzo, altrimenti ignoriamo la callback	*/
			if(this.userName.equals(utenteAggiunto)) {

				System.out.println("< messaggio da Worth: sei stato aggiunto al progetto " + nomeProgetto + "\n");
				this.progettiIndirizzi.put(nomeProgetto, multicastAddress);

				/*	Avvisiamo il pool che c'è un nuovo progetto per cui tenere d' occhio la chat	*/
				if(!this.poolStart) this.chatPoolStart();
				else this.chatHandler.nuovaChatProgetto(nomeProgetto, multicastAddress);

			}

		}

		/*	Se il progetto è stato cancellato	*/
		if(isCanceled) {

			for(String membro : allMembers) {

				/*	Se siamo membri del progetto cancellato riceviamo la notifica e cancelliamo l' associazione progetto - indirizzo dalla lista	*/
				if(this.userName.equals(membro)) {

					System.out.println("< messaggio da Worth: progetto " + nomeProgetto + " chiuso\n");
					this.progettiIndirizzi.remove(nomeProgetto);

				}

			}

		}

	}

	/*
	 * 	@effects avvia il client che instaura una connessione TCP con il server, prepara gli strumenti per le chiamate remote RMI e
	 * 			 successivamente entriamo in un ciclo infinto in cui vengono lette le richieste, tokenizzate e gestite in base al comando,
	 * 			 se viene digitato il comando exit dopo che l'utente ha eseguito il logout viene terminata l' esecuzione
	 */
	public void start() {

		/*	Set Up connessione TCP con il server	*/
		SocketAddress address = new InetSocketAddress(this.TCP_PORT);
		try ( SocketChannel client = SocketChannel.open(address); ){

			/*	Set Up RMI	*/
			Registry r = LocateRegistry.getRegistry(this.RMI_PORT);
			RMIInterface remoteDB = (RMIInterface) r.lookup("USERDATABASE");

			/*	Utility per la callback	*/
			NotifyEventInterface callbackObj = this;
			NotifyEventInterface stub = (NotifyEventInterface) UnicastRemoteObject.exportObject(callbackObj, 0);

			/*	Input lettura comandi	*/
			BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("---------- WELCOME TO WORTH! ----------");

			/*	Ciclo lettura comando finche non digita il comando 'exit'	*/
			while(!exit) {

				System.out.println();
				/*	Lettura comando	e tokenizzazione, mi serve solo la prima parte per capire che comando è */
				String requestLine = input.readLine().trim();
				String sendToServer = requestLine + " " + this.userName;	//Aggiugniamo ad ogni richiesta anche lo username, in alcuni casi verrà ignorato
				String[] cmdTkz = requestLine.split(" ");	//Format : cmd [args]

				/*	Allocazione buffers per comunicazione con il server	*/
				this.lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
                this.lengthBuffer.putInt(sendToServer.length());
                this.lengthBuffer.flip();
                this.clientRequestBuffer = ByteBuffer.wrap(sendToServer.getBytes());

                /*	Allocazione buffer per lettura risposta dal server	*/
                this.serverResponseBuffer = ByteBuffer.allocate(this.BUFFER_SIZE);

				/*	Gestione comandi	*/
				switch(cmdTkz[0]) {

					case "register"  		: this.register(cmdTkz, remoteDB);					break;

					case "login" 	 		: this.login(cmdTkz, client, remoteDB, stub);		break;

					case "logout"	 		: this.logout(cmdTkz, client, remoteDB, stub);		break;

					case "listusers" 		: this.listUsers(cmdTkz);							break;

					case "listonlineusers"	: this.listOnlineUsers(cmdTkz);						break;

					case "listprojects"		: this.listProjects(cmdTkz, client); 				break;

					case "createproject" 	: this.createProject(cmdTkz, client);				break;

					case "addmember"		: this.addMember(cmdTkz, client);					break;

					case "showmembers"		: this.showMembers(cmdTkz, client); 				break;

					case "showcards"		: this.showCards(cmdTkz, client);					break;

					case "showcard"			: this.showCard(cmdTkz, client); 					break;

					case "addcard" 			: this.addCard(cmdTkz, client);						break;

					case "movecard" 		: this.moveCard(cmdTkz, client);					break;

					case "getcardhistory"	: this.getCardHistory(cmdTkz, client); 				break;

					case "readchat"			: this.readChat(cmdTkz); 							break;

					case "send"				: this.sendChatMsg(cmdTkz); 						break;

					case "cancelproject"	: this.cancelProject(cmdTkz, client); 				break;

					case "help" 	 		: this.help(cmdTkz, client);						break;

					case "exit" 	 		: this.exit(cmdTkz, client);						break;

					default					: System.out.println(ERROR + " comando sconosciuto! Se hai bisogno di aiuto digita help"); 	break;

				}

				/*	Pulizia buffer	*/
				this.lengthBuffer.clear();
				this.clientRequestBuffer.clear();
				this.serverResponseBuffer.clear();

			}

			System.out.println(SUCCESS + " Bye Bye!");

		}
		catch(Exception e) {
			e.printStackTrace();
		}

	}

	/*
	 * 	@Utility method
	 *
	 * 	@effects funzione di utilità che permette di avviare il threadpool delle chat, viene invocata quando abbiamo almeno un progetto di cui seguire la chat
	 */
	private void chatPoolStart() {

		this.chatHandler = new ClientChatHandler(this.progettiIndirizzi);
		this.chatHandler.start();
		this.poolStart = true;

	}

	/*
	 * 	@Utility method
	 *
	 * 	@param client per la comunicazione con il Server
	 *
	 * 	@return String contente l' esito della richiesta fatta al Server
	 *
	 * 	@throws IOException se si verificano problemi di I/O
	 */
	private String serverComunication(SocketChannel client) throws IOException{

			/*	Invio richiesta		*/
			client.write(this.lengthBuffer);
	        client.write(this.clientRequestBuffer);

	        /*	Lettura risposta	*/
	        client.read(this.serverResponseBuffer);
	        this.serverResponseBuffer.flip();
	        String esito = new String(this.serverResponseBuffer.array()).trim();

	        return esito;

	}

	/*
	 * 	@RMI method
	 *
	 * 	@param cmdTkz contenente la requestLine tokenizzata
	 * 		   remoteDB per la chiamata remota RMI
	 *
	 * 	@effects se i parametri sono corretti, e se l' utente risulta non ancora registrato, lo registra al servizio WORTH e stampa messaggio di successo
	 * 			 altrimenti stampa messaggio di errore
	 *
	 * 	@throws RemoteException se si verificano errori durante la chiamata remota
	 */
	private void register(String[] cmdTkz, RMIInterface remoteDB) throws RemoteException{

		/*	Controllo se non fossimo gia loggati	*/
		if(!this.logged) {

			/*	Controllo su parametri	*/
			if(cmdTkz.length == 3) {	//Format: register nickUtente password

				String nickUtente = cmdTkz[1];
				String password = cmdTkz[2];
				String esitoRegister = remoteDB.register(nickUtente, password);
				if(esitoRegister.equals(SUCCESS)) {
					System.out.println(esitoRegister + " utente " + nickUtente + " registrato correttamente");
					this.userName = nickUtente;
				}
				else System.out.println(esitoRegister);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " per effettuare una registrazione devi prima fare il logout");
	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contenente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 * 		   remoteDB & stub per la registrazione alla callback dopo il login
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di login, inoltre, se andata a buon fine, viene costruita la lista locale degli utenti e dei progetti e nel caso
	 * 			 viene avviato anche il threadpool per le chat e registrati per la callback
	 *
	 * 	@throws IOException se si verificano problemi di I/O
	 */
	private void login(String[] cmdTkz, SocketChannel client, RMIInterface remoteDB, NotifyEventInterface stub) throws IOException{

		/*	Controllo se siamo gia loggati	*/
		if(!this.logged) {

			/*	Controllo sui parametri	*/
			if(cmdTkz.length == 3) {	//Format : login nickUtente password

				/*	Invio richiesta di login	*/
				String esitoLogin = this.serverComunication(client);
				String[] esitoTkz = esitoLogin.split(" ");

				/*	Se riceviamo un messaggio di errore lo stampiamo	*/
				if(!(esitoTkz[0] + " " + esitoTkz[1]).equals(SUCCESS)) {
					System.out.println(esitoLogin);
					return;
				}

				/*	Se andato tutto a buon fine creiamo la nostra lista locale utenti e ci registriamo per la callback RMI	*/
				int i = 2;
				while(!esitoTkz[i].equals("****")) {	//Marca di separazione tra liste

					String tmpUt = esitoTkz[i];
					i++;
					String tmpStato = esitoTkz[i];
					i++;
					this.localUserList.put(tmpUt, tmpStato);

				}

				/*	Ricostruiamo anche le associazioni progetti indirizzi alla login	*/
				i = i+1;
				while(i < esitoTkz.length) {

					String tmpProg = esitoTkz[i];
					i++;
					String tmpAdd = esitoTkz[i];
					i++;
					this.progettiIndirizzi.put(tmpProg, tmpAdd);

				}

				this.logged = true;
				this.userName = cmdTkz[1];
				System.out.println(SUCCESS + " utente " + this.userName + " logged in");
				remoteDB.registerForCallback(stub);
				this.progettiIndirizzi = new HashMap<String, String>();

				/*	Se abbiamo ricevuto degli indirizzi di progetti e non lo abbiamo ancora avviato, attiviamo il pool delle chat	*/
				if(!this.progettiIndirizzi.isEmpty() && !this.poolStart)	this.chatPoolStart();

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " c'è un utente già loggato, deve essere prima scollegato");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contenente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 *  	   remoteDB & stub per la de registrazione alla callback dopo il logout
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di logout, inoltre, in caso di esito positivo, ci deregistriamo alla callback e chiudiamo il pool e facciamo il join del thread
	 *
	 * 	@throws IOException se si verificano problemi di I/O
	 */
	private void logout(String[] cmdTkz, SocketChannel client, RMIInterface remoteDB, NotifyEventInterface stub) throws IOException {

		/*	Prima controlliamo che l'utente sia loggato	*/
		if(this.logged) {

			/*	Controllo su parametri passati	*/
			if(cmdTkz.length == 2) {	//Format : logout nickUtente

				/*	Controllo su chi fa la logout	*/
				if(this.userName.equals(cmdTkz[1])) {

					/*	Invio richiesta di logout, ricezione esito e stampa */
					String esitoLogout = this.serverComunication(client);
					System.out.println(esitoLogout);
					String[] esitoTkz = esitoLogout.split(" ");

					/*	In caso di esito positivo	*/
					if((esitoTkz[0] + " " + esitoTkz[1]).equals(SUCCESS)) {

						/*	Cancelliamo la registrazione alla callback	*/
						remoteDB.unregisterForCallback(stub);

						/*	Chiudiamo il threadpool per la gestione delle chat	*/
						if(this.chatHandler != null) {
							this.chatHandler.closePool();
							try {
								this.chatHandler.join();
							}
							catch(InterruptedException e) {
								e.printStackTrace();
							}
						}

						this.logged = false;

					}

				}
				else System.out.println(ERROR + " non puoi fare la logout di un account che non sia il tuo");

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@Local method
	 *
	 * 	@param cmdTkz contenente la requestLine tokenizzata
	 *
	 * 	@effects effettuati i vari controlli, viene stampata la lista degli utenti registrati al servizio Worth
	 */
	private void listUsers(String[] cmdTkz) {

		/*	Prima controlliamo che l' utente sia loggato	*/
		if(this.logged) {

			/*	Controllo su parametri passati	*/
			if(cmdTkz.length == 1) {	//Format : listusers

				System.out.println(SUCCESS + " lista utenti registrati a WORTH : ");
				Iterator<Map.Entry<String, String>> userIterator = this.localUserList.entrySet().iterator();
				while(userIterator.hasNext()) {

					Map.Entry<String, String> entry = (Map.Entry<String, String>)userIterator.next();
					System.out.println("< user: nome=" + entry.getKey() + ", stato=" + entry.getValue());

				}

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@Local method
	 *
	 * 	@param cmdTkz contenente la requestLine tokenizzata
	 *
	 * 	@effects effettuati i vari controlli, viene stampata la lista degli utenti online in quel momento
	 */
	private void listOnlineUsers(String[] cmdTkz) {

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo su parametri	*/
			if(cmdTkz.length == 1) {	//Format : listonlineusers

				System.out.println(SUCCESS + " lista utenti online : ");
				Iterator<Map.Entry<String, String>> userIterator = this.localUserList.entrySet().iterator();
				while(userIterator.hasNext()) {

					Map.Entry<String, String> entry = (Map.Entry<String, String>)userIterator.next();
					if(entry.getValue().equals("online"))
						System.out.println("< user: nome=" + entry.getKey());

				}

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");
	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per comunicare con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di listProjects, se va tutto a buon fine vedremo a schermo al lista dei progetti di cui l' utente è membro
	 *
	 * 	@throws IOException se si verificano errori di I/O
	 */
	public void listProjects(String[] cmdTkz, SocketChannel client) throws IOException{

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo su parametri	*/
			if(cmdTkz.length == 1) {	//Format : listprojects

				/*	Invio richiesta di listprojects al server	*/
				String esitoListProject = this.serverComunication(client);
				System.out.println(esitoListProject);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di createProject, se va tutto a buon fine riceveremo anche l'indirizzo multicast del progetto che abbiamo creato cosi possiamo
	 * 			 aggiornare la lista locale progettiIndirizzi e, se non è stato ancora avviato il pool delle chat viene avviato, altrimenti viene semplicemente
	 * 			 sottomesso un nuono task
	 *
	 * 	@throws IOException se si verificano errori di I/O
	 */
	public void createProject(String[] cmdTkz, SocketChannel client) throws IOException{

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo su parametri passati	*/
			if(cmdTkz.length == 2) {	//Format : createproject projectName

				/*	Invio richiesta di creare il progetto	*/
				String esitoCreateProject = this.serverComunication(client);
				String[] esitoTkz = esitoCreateProject.split(" ");	//Ho bisogno di recuperare l' indirizzo multicast del progetto creato
				if((esitoTkz[0] + " " + esitoTkz[1]).equals(SUCCESS)) {
					System.out.println(esitoTkz[0] + " " + esitoTkz[1] + " " + esitoTkz[2] + " " + esitoTkz[3]); //Stampo tutto l' esito tranne l' indirizzo multicast
					this.progettiIndirizzi.put(esitoTkz[2], esitoTkz[4]);	//Format risposta : < ok, projectName creato multicastAddress
					if(!this.poolStart)	this.chatPoolStart(); //Se non era ancora stato avviato il pool lo avviamo
					else this.chatHandler.nuovaChatProgetto(esitoTkz[2], esitoTkz[4]);	//Altrimenti aggiungiamo semplicemente un task al pool
				}
				else {
					System.out.println(esitoCreateProject);
				}

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 *
	 *	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di addMember
	 *
	 * 	@throws IOException se si verificano errori di I/O
	 */
	private void addMember(String[] cmdTkz, SocketChannel client) throws IOException{

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo sui parametri	*/
			if(cmdTkz.length == 3) {	//Format : addmember projectName nickUtente

				/*	Invio richiesta di aggiungere un membro al progetto	*/
				String esitoAddMember = this.serverComunication(client);
				System.out.println(esitoAddMember);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di showMembers, in caso di successo verrà stampata la lista dei membri del progetto specificato
	 *
	 * 	@throws IOException se si verificano errori di I/O
	 */
	private void showMembers(String[] cmdTkz, SocketChannel client) throws IOException{

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo sui parametri	*/
			if(cmdTkz.length == 2) {	//Format : showmembers projectName

				/*	Invio richiesta di showMembers al progetto	*/
				String esitoShowMembers = this.serverComunication(client);
				System.out.println(esitoShowMembers);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di showCards, in caso di esito positivo viene stampata la lista delle card, con il loro stato, del progetto indicato
	 *
	 * 	@throws IOException se si verificano errori di I/O
	 */
	private void showCards(String[] cmdTkz, SocketChannel client) throws IOException{

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo sui parametri	*/
			if(cmdTkz.length == 2) {	//Format : showcards projectName

				/*	Invio richiesta di showcards al server	*/
				String esitoShowCards = this.serverComunication(client);
				System.out.println(esitoShowCards);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di showCard, in caso di esito positivo verranno mostrate tutte le informazioni relative alla card indicata
	 *
	 * 	@throws IOException se si verificano errori di I/O
	 */
	private void showCard(String[] cmdTkz, SocketChannel client) throws IOException{

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo sui parametri	*/
			if(cmdTkz.length == 3) { 	//Format : showcard projectName cardName

				/*	Invio richiesta di showcard al server	*/
				String esitoShowCard = this.serverComunication(client);
				System.out.println(esitoShowCard);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di addCard
	 *
	 * 	@throws IOException se si verificano errori di I/O
	 */
	private void addCard(String[] cmdTkz, SocketChannel client) throws IOException{

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo sui parametri	*/
			if(cmdTkz.length == 4) {	//Format : addcard projectName cardName descrizione

				/*	Invio richiesta di creare una card al server	*/
				String esitoAddCard = this.serverComunication(client);
				System.out.println(esitoAddCard);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di moveCard
	 *
	 * 	@throws IOException se si verificano errori di I/O
	 */
	private void moveCard(String[] cmdTkz, SocketChannel client) throws IOException{

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo sui parametri	*/
			if(cmdTkz.length == 5) {	//Format : movecard projectName cardName listaPartenza listaDestinazione

				/*	Invio richiesta di movecard al server	*/
				String esitoMoveCard = this.serverComunication(client);
				System.out.println(esitoMoveCard);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per la comunicazione con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di getCardHistory
	 *
	 * 	@throws IOException se si verificano errori di I/O
	 */
	private void getCardHistory(String[] cmdTkz, SocketChannel client) throws IOException{

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo sui parametri	*/
			if(cmdTkz.length == 3) {	//Format : getcardhistory projectName cardName

				/*	Invio richiesta di recuperare la cronologia della card al server	*/
				String esitoGetCardHistory = this.serverComunication(client);
				System.out.println(esitoGetCardHistory);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@UDP Multicast method
	 *
	 * 	@param cmdTkz contenente la requestLine tokenizzata
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione del pool per recuperare i messaggi della chat relativa al progetto e stampati
	 */
	private void readChat(String[] cmdTkz){

		/*	Controlliamo se siamo loggati	*/
		if(this.logged) {

			/*	Controlliamo i parametri	*/
			if(cmdTkz.length == 2) { //Format : readchat projectName

				/*	Controlliamo se abbiamo l' indirizzo multicast di quel progetto, ovvero ne siamo membri	*/
				if(this.progettiIndirizzi.containsKey(cmdTkz[1])) {

					String chatMessage = this.chatHandler.getChatMessage(cmdTkz[1]);
					System.out.println(chatMessage);

				}
				else System.out.println(ERROR + " non puoi leggere chat di progetti di cui non sei membro");

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@UDP Multicast method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 *
	 * 	@effects effettuati i vari controlli, viene recuperato l' indirizzo multicast del progetto, utilizzando queste informazioni ci uniamo al gruppo multicast
	 * 			 e mandiamo il messaggio sulla chat
	 */
	private void sendChatMsg(String[] cmdTkz) {

		/*	Controlliamo se siamo loggati	*/
		if(this.logged) {

			/*	Controlliamo i parametri, in questo caso il messaggio può contentere spazi	*/
			if(cmdTkz.length >= 3) { //Format : send projectName messaggio

				/*	Controlliamo se abbiamo l' indirizzo multicast di quel progetto, ovvero ne siamo membri	*/
				if(this.progettiIndirizzi.containsKey(cmdTkz[1])) {

					/*	Ricostruiamo il messaggio, formattandolo correttamente	*/
					String messaggio = "< " + this.userName + " ha detto: '";
					for(int i = 2; i < cmdTkz.length; i++)
						messaggio = messaggio + cmdTkz[i] + " ";
					messaggio = messaggio + "'";

					/*	Inviamo il messaggio sul gruppo multicast del progetto	*/
					try( DatagramSocket clientSock = new DatagramSocket() ){

						/*	Creazione e invio data e ora	*/
						DatagramPacket sendPacket = new DatagramPacket(messaggio.getBytes(), messaggio.length(), InetAddress.getByName(this.progettiIndirizzi.get(cmdTkz[1])), MULTICAST_PORT);
						clientSock.send(sendPacket);
						System.out.println("< message sent");

					}
					catch(IOException e) {
						e.printStackTrace();
					}

				}
				else System.out.println(ERROR + " non puoi scrivere sulla chat di progetti di cui non sei membro");

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}


	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contente la requestLine tokenizzata
	 * 		   client per comunicare con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di cancelProject
	 *
	 * 	@throws IOException se si verificano problemi di I/O
	 */
	private void cancelProject(String[] cmdTkz, SocketChannel client) throws IOException {

		/*	Controllo se siamo loggati	*/
		if(this.logged) {

			/*	Controllo sui parametri	*/
			if(cmdTkz.length == 2) {	//Format : cancelproject projectName

				/*	Comunicazione comando al server e stampo esito	*/
				String esitoCancelProject = this.serverComunication(client);
				System.out.println(esitoCancelProject);

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " prima devi effettuare la login");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param cmdTkz contenente la requestLine tokenizzata
	 * 		   client per comunicare con il server
	 *
	 * 	@effects effettuati i vari controlli, viene richiamata la funzione per comunicare il comando al server, e viene stampato l' esito
	 * 			 dell' operazione di help, viene stampato un menu di aiuto per il client
	 *
	 * 	@throws IOException se si verificano problemi di I/O
	 */
	private void help(String[] cmdTkz, SocketChannel client) throws IOException {

		/*	Controllo sui parametri	*/
		if(cmdTkz.length == 1) {	//Format : help

			/*	Invio richiesta di help	*/
			String esitoHelp = this.serverComunication(client);
			System.out.println(esitoHelp);

		}
		else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

	}

	/*
	 * 	@TCP method
	 *
	 * 	@param 	cmdTkz, contenente la request line tokenizzata
	 * 			client, per comunicare con il server
	 *
	 * 	@effects effettuati i vari controlli, viene comunicato il comando al server, e viene settata la variabile exit a true
	 *
	 * 	@throws IOException se si verificano problemi di I/O
	 */
	private void exit(String[] cmdTkz, SocketChannel client) throws IOException{

		if(!this.logged) {

			if(cmdTkz.length == 1) { //Format : exit

				/*	Comunichiamo al server il comando exit	*/
		        client.write(this.lengthBuffer);
		        client.write(this.clientRequestBuffer);
				this.exit = true;

			}
			else System.out.println(ERROR + " parametri errati! Se hai bisogno di aiuto digita help");

		}
		else System.out.println(ERROR + " sei loggato! prima di uscire ricordati di effettuare il logout");

	}

}
