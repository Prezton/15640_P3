import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;

public class Server extends UnicastRemoteObject {

	// Request queue, maintained by master server
	public static ArrayBlockingQueue<Cloud.FrontEndOps.Request> request_queue;

	// A list of frontend servers, containing VMIDs
	public static Map<Integer, Boolean> frontend_servers;

	// A list of app servers, containing VMIDs
	public static Map<Integer, Boolean> app_servers;
	


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
    public Server() throws RemoteException {
		super();
    }

	/**
     * @brief Initialize master server, RMI registration, etc
     * @return 0 on success, else errno
     */
	public static int init_master(int port) {
		frontend_servers = new ConcurrentHashMap<Integer, Boolean>();
		app_servers = new ConcurrentHashMap<Integer, Boolean>();
		request_queue = new ArrayBlockingQueue<Cloud.FrontEndOps.Request>(Integer.MAX_VALUE);
		String master_name = "MAIN_SERVER_9";

		try {
			Server srv = new Server();
            LocateRegistry.createRegistry(port);
            Naming.rebind(master_name, srv);

        } catch(Exception e) {
            System.err.println("An exception in Master INIT!");
            e.printStackTrace();
			return -1;
        }
		return 0;

	}




	public static void main ( String args[] ) throws Exception {
		// if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");

		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		ServerLib SL = new ServerLib( args[0],  port );
		int VMID = Integer.parseInt(args[2]);

		if (VMID == 1) {
			int init_master_result = init_master(port);
			if (init_master_result == -1) {
				System.err.println("ERROR WHEN INITIALIZING MASTER SERVER, VMID: " + VMID);
			}
		} else {
			Server srv = new Server();
			
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

