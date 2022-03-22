import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;

public class Server extends UnicastRemoteObject {

	// Request queue, maintained by master server
	public static Queue request_queue;

	// A list of frontend servers, containing VMIDs
	public static Map<Integer, boolean> frontend_servers;

	// A list of app servers, containing VMIDs
	public static Map<Integer, boolean> app_servers;
	


	// Server load: request per server
	// Request queue length
	// Above may be good hints to decide scaling params
	// Do not register the App tiers to the cloud
	// Hyteresis: put some lag when changing the # of servers
	// Time sample: do not observe too long or too short time for good # of new servers
	// Be very careful with scaling-backs: actually add on double time
	// CKPT2 arrival rates are also fixed
	// Pay some attention to heavily tailed distribution
	// Unhappy clients are more important?
    public Server() {
		super();
    }

	/**
     * @brief Initialize master server, RMI registration, etc
     * @return 1 on success, else errno
     */
	public static int init_master() {

	}




	public static void main ( String args[] ) throws Exception {
		// if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");
		ServerLib SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
		int VMID = Integer.parseInt(args[2]);

		if (VMID == 1) {
			float current_time = SL.getTime();

			if (current_time == 0) {
				for (int i = 0; i < 8; i ++) {
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

