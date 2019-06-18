import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Scanner;
import java.io.FileNotFoundException;
import java.io.File;

public class Server {
	private static final int MAXIMUM_BUFFER_SIZE = 512;
	private static final String INPUT_DATA_FILE_NAME = "data.in";
	private static DatagramSocket serverSocket;
	private static State state = State.NONE;
	private static int ACK_NUM = 0;
	private static int SYNC_NUM = 0;
	private static int WINDOW_SIZE = 0;
	private static String INPUT_DATA = "";
	private static int INITIAL_SEGMENT = 0;
	
	
	static String fn = ""; //	fn: nome do arquivo a ser recebido e gravado em disco
	static int sport = 0; //	sport: porta UDP que o servidor deve escutar
	static int wnd = 0; //	wnd: tamanho da janela do transmissor e receptor em bytes
	static int lp = 0; 	//	lp: probabilidade de um datagrama UDP ser descartado
	
	public static void main(String[] args) {
		if(args.length < 1){
			System.out.println("java Server <port>");
			return;
		}
		
		try{
			fn = (args[0]);
			sport = Integer.parseInt(args[1]);
			wnd = Integer.parseInt(args[2]);
			lp = Integer.parseInt(args[3]);
		}catch(NumberFormatException nfe){
			sport = 8080;
		}
		try {
			System.out.println("Conectando servidor UDP na porta:: "+sport+"...");
			serverSocket = new DatagramSocket(sport);
			System.out.println("Conexão realizada com sucesso!");
		} catch (SocketException e) {
			System.out.println("Não foi possível conecatr o socket na porta:: "+sport);
			return;
		}
		
		System.out.println("Listener UDP para esse servidor iniciado!");
		listener.start();
	}
	
	private static Thread listener = new Thread(new Runnable(){
		@Override
		public void run() {
			while(true){
				byte[] buff = new byte[MAXIMUM_BUFFER_SIZE];
				DatagramPacket packet = new DatagramPacket(buff,buff.length);
				
				
				try {
					serverSocket.receive(packet);
					Packet p = Packet.valueOf(new String(buff));
					InetAddress clientAddress = packet.getAddress();
					int clientPort = packet.getPort();

					//espera por 2 segundos e então imprime e processa
					Thread t = timerThread(2);
					t.start();
					
					try{
						t.join();
						}
					catch(InterruptedException ie){}
					
					System.out.println("RCVD: "+p.toString());

					if(state == State.NONE){
						
						if(p.isSyncFlag()){ //primeiro pacote deve ser um pacote SYN
							System.out.println("Threeway handshake 1/3.");
							

							//envia pacote ACK+SYN
							Packet ackSyncPacket = new Packet();
							ackSyncPacket.setAckFlag(true);
							ACK_NUM = p.getSyncNum()+1;
							ackSyncPacket.setAckNum(ACK_NUM);

							SYNC_NUM = ThreadLocalRandom.current().nextInt(1, 5000);
							ackSyncPacket.setSyncFlag(true);
							ackSyncPacket.setSyncNum(SYNC_NUM);

							send(clientAddress, clientPort, ackSyncPacket.toString());
							state = State.SYN_RECV;
							System.out.println("Threeway handshake 2/3.");
						}
						
					}else if(state == State.SYN_RECV){
						if(p.isAckFlag() && p.getAckNum() == (++SYNC_NUM)){ //deve ser um pacote ACK e um ack valido
							
							state = State.ESTABLISHED;
							System.out.println("Threeway handshake 3/3.");

							//Obtem os dados
							Scanner in = new Scanner(new File(INPUT_DATA_FILE_NAME));
							while(in.hasNextLine()){
								INPUT_DATA += in.nextLine();
							}
							in.close();

							INITIAL_SEGMENT = SYNC_NUM;
							//envia primeiro dado
							Packet datum = new Packet();
							datum.setSyncNum(SYNC_NUM);
							int index = datum.getSyncNum()-INITIAL_SEGMENT;
							datum.setData(""+INPUT_DATA.charAt(index));
							send(clientAddress,clientPort, datum.toString());


						}
					}else if(state == State.ESTABLISHED){
						//envia WINDOW_SIZE em bytes de dados
						WINDOW_SIZE = p.getWindowSize();
						if(p.isAckFlag()){
							
							if(WINDOW_SIZE == 0){
								//não envia
								continue;
							}

							SYNC_NUM = p.getAckNum();
							//envia unACKd de dados depois de 4s segundos
							t = timerThread(4);
							t.start();
							try{t.join();}catch(InterruptedException ie){}
							for(int i=0; i<WINDOW_SIZE; i++){
								Packet datum = new Packet();
								datum.setSyncNum(SYNC_NUM+i);
								int index = datum.getSyncNum()-INITIAL_SEGMENT;
								if(index == INPUT_DATA.length()){
									//envia disconnect;
									System.out.println("Dado enviado!");
									datum = new Packet();
									datum.setFinFlag(true);
									state = State.FIN_SEND;
									System.out.println("Fourway handshake 1/4");
									//break;
								}else{
									try{datum.setData(""+INPUT_DATA.charAt(index));}catch(StringIndexOutOfBoundsException sioobe){}
								}
								send(clientAddress,clientPort, datum.toString());

							}
						}
						System.out.println("RCVD: "+p.toString());
					}else if(state == State.FIN_SEND){
						
						//deve ser um FIN acknowledgement
						if(p.isFinFlag() && p.getAckNum() == 0){ 
							System.out.println("Fourway handshake 2/4");
							System.out.println("Fourway handshake 3/4");
							state = State.FIN_ACKD;

							//envia ultimo ACK para FIN
							Packet finAck = new Packet();
							finAck.setAckFlag(true);
							send(clientAddress,clientPort,finAck.toString());
							System.out.println("Fourway handshake 4/4");
							break;
						}
					}

					

				} catch(FileNotFoundException fnfe){
					System.out.println("Arquivo "+INPUT_DATA_FILE_NAME+" não encontrado!");
				} catch (IOException e) {
					System.out.println("Falha ao receber pacote... "+e.getMessage());
				}

				if(state == State.FIN_ACKD){
					Thread t = timerThread(10);
					t.start();
					try{t.join();}catch(InterruptedException ie){}
					//serverSocket.close();
				}
			}
		}
	});
	
	public static void send(InetAddress address, int port, String message){
		byte[] buff = message.getBytes();
		DatagramPacket packet = new DatagramPacket(buff, buff.length,address,port);
		try {
			serverSocket.send(packet);
		} catch (IOException e) {
			System.out.println("Incapaz de enviar pacote: "+message);
		}
	}

	private static Thread timerThread(final int seconds){
		return new Thread(new Runnable(){
			@Override
			public void run(){
				try{

					Thread.sleep(seconds*1000);
				}catch(InterruptedException ie){

				}
			}
		});
	}
}

