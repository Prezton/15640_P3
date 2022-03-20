import java.io.*;

public class Server {

    public Server() {

    }

	public static void main ( String args[] ) throws Exception {
		// if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");
		ServerLib SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
		int VMID = Integer.parseInt(args[2]);

		if (VMID == 1) {
			float current_time = SL.getTime();

			if (current_time == 0) {
				for (int i = 0; i < 7; i ++) {
					SL.startVM();
				}
			}

			else if (current_time == 6) {
				for (int i = 0; i < 1; i ++) {
					SL.startVM();
				}
			}

			else if (current_time == 8) {
				for (int i = 0; i < 3; i ++) {
					SL.startVM();
				}
			}
			
			else if (current_time == 19) {
				for (int i = 0; i < 4; i ++) {
					SL.startVM();
				}
			}

		}
		
		// register with load balancer so requests are sent to this server
		// Frontend operation to register this Server with the load balancer and start receiving client requests.
		SL.register_frontend();
		
		// main loop
		while (true) {
			Cloud.FrontEndOps.Request r = SL.getNextRequest();
			SL.processRequest( r );
		}

	}
}

