import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;

public class Server extends UnicastRemoteObject implements MasterInterface {

	// Request queue, maintained by master server
	public static ArrayBlockingQueue<Cloud.FrontEndOps.Request> request_queue;

	// A list of frontend servers, containing VMIDs
	public static Map<Integer, Boolean> frontend_servers;

	// A list of app servers, containing VMIDs
	public static Map<Integer, Boolean> app_servers;
	
	public static String master_name = "/MAIN_SERVER9";


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
     * @return 0 on master registered, -1 on other roles or exceptions
     */
	public static int init_master(String ip, int port) {

		try {
			master_name = "//" + ip + ":" + port + master_name;
			Server srv = new Server();
            LocateRegistry.createRegistry(port);
            Naming.rebind(master_name, srv);
			frontend_servers = new ConcurrentHashMap<Integer, Boolean>();
			app_servers = new ConcurrentHashMap<Integer, Boolean>();
			request_queue = new ArrayBlockingQueue<Cloud.FrontEndOps.Request>(Integer.MAX_VALUE);

        } catch(Exception e) {
            System.err.println("NOT MASTER OR MASTER EXCEPTION");
            // e.printStackTrace();
			return -1;
        }
		return 0;

	}


	/**
     * @brief Get role of a specific VM
     * @return 0 for frontend, 1 for app-tier, -1 for non-existent server
     */
	public int get_role(int vmid) throws RemoteException {
		if (frontend_servers.containsKey(vmid)) {
			return 0;
		} else if (app_servers.containsKey(vmid)) {
			return 1;
		} else {
			return -1;
		}
	}

	public static void run_frontend() {

	}

	public static void run_apptier() {

	}




	public static void main ( String args[] ) throws Exception {
		// if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");

		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		ServerLib SL = new ServerLib( args[0],  port );
		int VMID = Integer.parseInt(args[2]);
		MasterInterface master = null;
		int init_master_result = init_master(ip, port);
		// Bind master servers
		if (init_master_result == -1) {
			// Get master server RMI
			master = (MasterInterface) Naming.lookup(master_name);
			String server_name = "/SERVER9_" + VMID;
			server_name = "//" + ip + ":" + port + server_name;
			Server srv = null;
			try {
				srv = new Server();
				// Bind non-master servers
				Naming.rebind(server_name, srv);
			} catch (RemoteException e) {
				System.err.println("EXCEPTION in binding non-master servers");
				e.printStackTrace();
			}
		}

		// master != null indicates non-master servers
		if (master != null) {
			int role_flag = master.get_role(VMID);
			if (role_flag == 0) {
				run_frontend();
			} else if (role_flag == 1) {
				run_apptier();
			}
		}


		
		// register with load balancer so requests are sent to this server
		// Frontend operation to register this Server with the load balancer and start receiving client requests.
		// SL.register_frontend();
		
		// // main loop
		// while (true) {
		// 	Cloud.FrontEndOps.Request r = SL.getNextRequest();
		// 	SL.processRequest( r );
		// }

	}
}

