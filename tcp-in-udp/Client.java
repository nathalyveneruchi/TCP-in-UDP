import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.ArrayList;

public class Client {

	private static final int MAXIMUM_BUFFER_SIZE = 512;
	private static DatagramSocket clientSocket;
	private static State state = State.NONE;
	private static int ACK_NUM = 0;
	private static int SYNC_NUM = 0;
	private static int WINDOW_SIZE = 0;
	private static String DATA = "";
	private static int INITIAL_SEGMENT = 0;
	
	
	static String fn = ""; //	fn: nome do arquivo a ser enviado
	static InetAddress sip; //	sip: endereçoo IP do servidor
	static int sport = 0; //	sport: porta UDP do servidor
	static int wnd = 0; //	wnd: tamanho da janela do transmissor e receptor em bytes
	static int rto = 0; //	rto: valor inicial de timeout para retransmissão de um segmento em milisegundos
	static int mss = 0; //	mss: TCP Maximum Segment Size
	static int dupack = 0; //	dupack: deve ser um para usar retransmissão via ACKs duplicados e zero caso contrário
	static double lp = 0; 	//	lp: probabilidade de um datagrama UDP ser descartado
	
	public static void main(String[] args) {
		if(args.length < 2){
			System.out.println("java Server <own port> <server address> <server port>");
			return;
		}
		try{
			fn = (args[0]);
			wnd = Integer.parseInt(args[3]);
			rto = Integer.parseInt(args[4]);
			mss = Integer.parseInt(args[5]);
			dupack = Integer.parseInt(args[6]);
			lp = Integer.parseInt(args[7]);
			
		}catch(NumberFormatException nfe){
		
		}
		
		try{
			sip = InetAddress.getByName(args[1]);
			sport = Integer.parseInt(args[2]);
		}catch(NumberFormatException nfe){
			sport = 8080;
		}catch(UnknownHostException uhe){
			try {
				sip = InetAddress.getByName("localhost");
			} catch (UnknownHostException e) {
				System.out.println("Endereço do servidor desconhecido!");
				return;
			}
		}
		
		try {
			System.out.println("Conectando servidor UDP na porta "+80+"...");
			clientSocket = new DatagramSocket(80);
			System.out.println("Conexão realizada com sucesso!");
		} catch (SocketException e) {
			System.out.println("Não foi possivel conextar na porta: "+80);
			return;
		}
		
		//INICIA LISTENER
		listener.start();
		System.out.println("Listener UDP para esse cliente inciado!");
		
		//INICIA MAIN PROCESS
		//INICIALIZA THREE WAY HANDSHAKE AO ENVIAR PACOTE SYN
		Packet syncPacket = new Packet();
		syncPacket.setSyncFlag(true);
		SYNC_NUM = ThreadLocalRandom.current().nextInt(1, 5000);
		syncPacket.setSyncNum(SYNC_NUM);
		send(syncPacket.toString());
		state = State.SYN_SEND;
		System.out.println("Threeway handshake 1/3.");
	}
	

	private static List<Packet> BUFFER = new ArrayList<Packet>();

	private static Thread listener = new Thread(new Runnable(){
		@Override
		public void run() {
			while(true){
				byte[] buff = new byte[MAXIMUM_BUFFER_SIZE];
				DatagramPacket packet = new DatagramPacket(buff,buff.length);
				
				try {
					clientSocket.receive(packet);
					Packet p = Packet.valueOf(new String(buff));
					//espera 2 segundos, depois imprime o pacote e processa
					Thread t = timerThread(2);
					t.start();
					try{t.join();}catch(InterruptedException ie){}
					System.out.println("RCVD: "+p.toString());


					if(state == State.SYN_SEND){ //primeiro pacote deve ser ACK+SYN 
						if(p.getAckNum() == SYNC_NUM +1){ //ack deve ser valido
							System.out.println("Threeway handshake 2/3.");
							//envia pacote ACK para servidor
							Packet ackPacket = new Packet();
							SYNC_NUM = p.getAckNum();
							ackPacket.setSyncFlag(true);
							ackPacket.setSyncNum(SYNC_NUM);

							ACK_NUM = p.getSyncNum() + 1;
							ackPacket.setAckFlag(true);
							ackPacket.setAckNum(ACK_NUM);

							WINDOW_SIZE = 16;
							ackPacket.setWindowSize(WINDOW_SIZE);

							send(ackPacket.toString());
							state = State.ESTABLISHED;
							System.out.println("Threeway handshake 3/3.");
							SYNC_NUM =  INITIAL_SEGMENT = ACK_NUM;
							SYNC_NUM--;
						}
					}else if(state == State.ESTABLISHED){
						//recebe o dado
						
						
						Packet ack = new Packet();
						if(p.getSyncNum() > SYNC_NUM){
							BUFFER.add(p);
							ack.setAckFlag(true);
							ack.setAckNum(p.getSyncNum()+1);
							ack.setWindowSize(WINDOW_SIZE - BUFFER.size());
							send(ack.toString());
						}

						if(BUFFER.size() == WINDOW_SIZE || p.isFinFlag()){
							//processa dado coletado
							char[] contents = new char[BUFFER.size()];
							for(Packet pckt : BUFFER){
								if(pckt.getSyncNum() <= SYNC_NUM){
									continue; //packet duplicate
								}
								contents[pckt.getSyncNum()-SYNC_NUM-1] = pckt.getData().charAt(0);
							}

							for(int i=0; i<BUFFER.size(); i++){
								if(contents[i]=='\0'){
									break;
								}else{
									DATA += ""+contents[i];
								}
							}
							System.out.println("Dado atual: "+DATA);
							//limpa buffer
							BUFFER.clear();
							if(p.isFinFlag()){
								System.out.println("Fourway handshake 1/4");
								
								//envia fin+ack
								
								state = State.FIN_RECV;
								ack = new Packet();
								ack.setFinFlag(true);
								ack.setAckFlag(true);
								send(ack.toString());
								System.out.println("Fourway handshake 2/4");
								System.out.println("Fourway handshake 3/4");
							}else{
								//envia novo ack
								ack.setAckNum(INITIAL_SEGMENT+DATA.length());
								ack.setWindowSize(WINDOW_SIZE-BUFFER.size());
								send(ack.toString());
							}
						}
						SYNC_NUM = INITIAL_SEGMENT + DATA.length()-1;
					}else if(state == State.FIN_RECV){
						if(p.isAckFlag() && p.getAckNum()==0){
							System.out.println("Fourway handshake 4/4");
							//10 segundos timeout depois termina
							t = timerThread(10);
							t.start();
							try{t.join();}catch(InterruptedException ie){}
							clientSocket.close();
							break;
						}
					}



				} catch (IOException e) {
					System.out.println("Falha ao receber pacote... "+e.getMessage());
				}
			}
		}
	});
	
	//envia para servidor por padrão
	private static void send(String message){
		send(sip, sport, message);
	}

	private static void send(InetAddress address, int port, String message){
		byte[] buff = message.getBytes();
		DatagramPacket packet = new DatagramPacket(buff, buff.length,address,port);
		try {
			clientSocket.send(packet);
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


