import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;


public class Server extends UnicastRemoteObject implements MasterInterface {

	// Request queue, maintained by master server
	public static LinkedBlockingQueue<Cloud.FrontEndOps.Request> request_queue;

	// A list of frontend servers, containing VMIDs
	public static Map<Integer, Boolean> frontend_servers;

	// A list of app servers, containing VMIDs
	public static Map<Integer, Boolean> app_servers;
	
	public static String master_name = "/MAIN_SERVER";

	public static int INIT_FRONTEND = 0;

	public static final int INIT_APPTIER = 1;

	public static ServerLib SL;

	public static final int FRONT_THROUGHPUT = 6;

	public static final double APP_THROUGHPUT = 1.5;

	public static final int APP_SCALEOUT_THRESHOLD = 3;

	public static final int APP_SCALEIN_THREASHOLD = 5;


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


	// Drop some requests at the begining
	// Should depend mainly on client arrival rate to decide scaling
	// At the beginning, it may be a good idea to have a server act as both frontend and app tier servers
	// Test some static configuration first and test with changed loads
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
            Naming.bind(master_name, srv);


        } catch(Exception e) {
            // e.printStackTrace();
			return -1;
        }
		
		// INIT 2 maps and 1 queue for master server
		frontend_servers = new ConcurrentHashMap<Integer, Boolean>();
		app_servers = new ConcurrentHashMap<Integer, Boolean>();
		request_queue = new LinkedBlockingQueue<Cloud.FrontEndOps.Request>();
		return 0;

	}

	public static int boost_servers() {
		app_servers.put(SL.startVM(), true);
		int num_app = get_init_app();
		for (int i = 0; i < num_app; i++) {
			app_servers.put(SL.startVM(), true);
		}

		for (int i = 0; i < INIT_FRONTEND; i++) {
			frontend_servers.put(SL.startVM(), true);
		}
		return 0;
	}


	public static void run_frontend(MasterInterface master, int VMID) {
		System.out.println("FRONTEND WORKING PROPERLY");
		while (true) {
			long t1 = System.currentTimeMillis();
			Cloud.FrontEndOps.Request req = SL.getNextRequest();
			try {
				master.add_request(req);
				// 60~150 ms
				System.out.println("FRONTEND Process time: " + (System.currentTimeMillis() - t1));
			} catch (RemoteException e) {
				System.err.println("run_frontend(): add_request() exeception");
				e.printStackTrace();
			}
			
		}
	}

	public static void run_apptier(MasterInterface master, int VMID) {
		int drop_count = 0;
		int miss_count = 0;
		while (true) {
			Cloud.FrontEndOps.Request req = null;
			try {
				long t1 = System.currentTimeMillis();
				req = master.get_request();
				if (req == null) {
					System.out.println("NULL REQ");
					miss_count += 1;
					if (miss_count == APP_SCALEIN_THREASHOLD) {
						// remove_server(master, VMID, 1);
						miss_count = 0;
					}
				} else {
					miss_count = 0;
				}
				if (master.check_app_status() == 1) {
					System.out.println("INSIDE, DROP_COUNT: " + drop_count);

					if (req != null) {
						System.out.println("HIT AND DROP");
						SL.drop(req);
						drop_count += 1;
					}
					if (drop_count > APP_SCALEOUT_THRESHOLD) {
						System.out.println("HIT ONCE!!!");
						master.scale_out(1);
						drop_count = 0;
					}
				} else {
					if (req != null) {
						SL.processRequest(req);
						long t2 = System.currentTimeMillis();
						long t = t2 - t1;
						// Average process time: 250~300
						System.out.println("APP TIER Process time: " + t);
						drop_count = 0;
					}
				}



			} catch (RemoteException e) {
				// System.err.println("run_apptier(): get_request() exeception");
				// e.printStackTrace();
			}
		}
	}

	public static void run_master() {
		System.out.println("MASTER WORKING PROPERLY");
		int count = 0;
		long previous_time = 0;
		long test_mark1 = System.currentTimeMillis();
		int new_servers = 0;

		// This booting takes ~5 second, 500 ms: 3 app servers, 300 ms 4 app servers
		while (SL.getStatusVM​(2) == Cloud.CloudOps.VMStatus.Booting) {
			SL.dropHead();
			// count += 1;
			// System.out.println("COUNTIS: " + count);

			// if (count > 4) {

			// 	long current_time = System.currentTimeMillis();
			// 	// COOLDOWN MECHANISM
			// 	// if (current_time - previous_time > 4000) {
			// 		System.out.println("BOOTING: " + (APP_THROUGHPUT * app_servers.size()));
			// 		add_apptier();
			// 		new_servers += 1;
			// 		previous_time = current_time;
			// 	// }
			// 	count = 0;
			// }
		}
		System.out.println("BOOT TIME: " + (System.currentTimeMillis() - test_mark1));
		
		while (true) {

			Cloud.FrontEndOps.Request req = SL.getNextRequest();
			request_queue.offer(req);
			System.out.println("QUEUE SIZE: " + request_queue.size());

			// if (request_queue.size() > APP_THROUGHPUT * app_servers.size()) {
			// 	long current_time = System.currentTimeMillis();
			// 	// COOLDOWN MECHANISM
			// 	if (current_time - previous_time > 4000) {
			// 		int num = request_queue.size() / 3;
			// 		System.out.println("potential adding num: " + num);
			// 		// add_apptier();
			// 		previous_time = current_time;
			// 	}
			// }
		}
	}

	public static int get_init_app() {

		int count = 0;
		long time = 0;
		// Have the master server act as both frontend and app tier at the beginning to reduce timeout requests

		// while (SL.getStatusVM​(2) != Cloud.CloudOps.VMStatus.Running) {
		if (SL.getStatusVM​(2) != Cloud.CloudOps.VMStatus.Running) {
			Cloud.FrontEndOps.Request req1 = SL.getNextRequest();
			long t1 = System.currentTimeMillis();
			Cloud.FrontEndOps.Request req2 = SL.getNextRequest();
			long t2 = System.currentTimeMillis();
			count += 1;
			time += (t2 - t1);
		}


		// time = 1820, count = 7, time = 1828, count = 7, time = 1838, count = 7
		int inter_arrival_time = (int)time / count;
		System.out.println(count + ", " + time + ", inter_arrival_time is: " + inter_arrival_time);

		int num = 0;
		if (inter_arrival_time >= 400) {
			num = (1000 / inter_arrival_time);
		} else if (inter_arrival_time >= 200) {
			num = (int)Math.ceil((1000.0 / inter_arrival_time) * 2.0 / 3.0);
		} else {
			num = (int)Math.ceil((1000.0 / inter_arrival_time) / 2);
			INIT_FRONTEND = 1;
		}
		System.out.println("boost extra servers: " + num);
		return num;
	}

	public static int scale_out() {
		return 0;
	}

	public static int scale_back() {
		return 0;
	}

	public static boolean add_frontend() {
		int new_vmid = SL.startVM();
		frontend_servers.put(new_vmid, true);
		return true;
	}

	public static boolean add_apptier() {
		int new_vmid = SL.startVM();
		System.out.println("ADD A NEW APP TIER SERVER WHEN BOOTING" + new_vmid);
		app_servers.put(new_vmid, true);
		return true;
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

	/**
     * @brief Get request from request queue, executed by master server
     * @return request or null
     */
	public Cloud.FrontEndOps.Request get_request() throws RemoteException {
		if (request_queue.size() > 0) {
			return request_queue.poll();
		}
		return null;
	}

	/**
     * @brief Add request to request queue, executed by master server
     * @param req request to be added
     */
    public void add_request(Cloud.FrontEndOps.Request req) throws RemoteException {
		request_queue.offer(req);
	}

	// /**
    //  * @brief Add server to master's state map, executed by master server. NOTE: not responsible for SL.startVM()
    //  * @param vmid server vm id
	//  * @param server_type 0 for frontend, 1 for app tier
    //  */
	// public void add_server(int vmid, int server_type) throws RemoteException {
	// 	if (server_type == 0) {
	// 		if (!frontend_servers.containsKey(vmid)) {
	// 			frontend_servers.put(vmid, true);
	// 		}
	// 	} else if (server_type == 1) {
	// 		if (!app_servers.containsKey(vmid)) {
	// 			app_servers.put(vmid, true);
	// 		}
	// 	}
	// }

	/**
     * @brief Add server to master's state map, executed by master server. NOTE: not responsible for SL.endVM()
     * @param vmid server vm id
	 * @param server_type 0 for frontend, 1 for app tier
     */
	public static int remove_server(MasterInterface master, int vmid, int server_type) {
		if (server_type == 0) {
			int frontend_num = 0;
			try {
 				frontend_num = master.get_frontend_num();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			if (frontend_num <= 1) {
				return -1;
			}
			SL.interruptGetNext();
			SL.unregister_frontend();

		} else if (server_type == 1) {
			int app_num = 0;
			try {
				app_num = master.get_app_num();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			if (app_num <= 1) {
				return -1;
			}
		}
		try {
			master.scale_in(server_type, vmid);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		System.out.println("SHUTDOWN: " + vmid);
		SL.endVM(vmid);
		return 0;
	}



	public int scale_in(int server_type, int vmid) throws RemoteException {
		if (server_type == 0) {
			if (frontend_servers.containsKey(vmid)) {
				frontend_servers.remove(vmid);
				return 0;
			}

		} else if (server_type == 1) {
			if (app_servers.containsKey(vmid)) {
				app_servers.remove(vmid);
				return 0;
			}
		}
		return -1;
	}

	public int get_app_num() throws RemoteException {
		return app_servers.size();
	}

	public int get_frontend_num() throws RemoteException {
		return frontend_servers.size();
	}

	/**
     * @brief scale out by calling startVM()
	 * @param server_type 0 for frontend, 1 for app tier
     */
	public void scale_out(int server_type) throws RemoteException {

		int vmid = SL.startVM();
		if (server_type == 0) {
			frontend_servers.put(vmid, true);
		} else {
			System.out.println("MASTER SCALING OUT APP TIER");
			app_servers.put(vmid, true);
		}
	}

	public int check_app_status() throws RemoteException {
		if (request_queue.size() > APP_THROUGHPUT * app_servers.size()) {
			System.out.println("HIT THOUGHPUT THRESHOLD");
			// SL.dropHead();
			return 1;
		} else {
			return 0;
		}
	}



	public static void main ( String args[] ) throws Exception {
		// if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");

		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		SL = new ServerLib( args[0],  port );
		int VMID = Integer.parseInt(args[2]);
		MasterInterface master = null;
		
		// Bind master server
		int init_master_result = init_master(ip, port);
		if (init_master_result == -1) {

			// Get master server RMI
			master = (MasterInterface) Naming.lookup(master_name);
			String server_name = "/SERVER_" + VMID;
			server_name = "//" + ip + ":" + port + server_name;
			Server srv = null;

			// Bind non-master servers
			try {
				srv = new Server();
				Naming.bind(server_name, srv);
			} catch (RemoteException e) {
				System.err.println("EXCEPTION in binding non-master servers");
				e.printStackTrace();
			}
			System.out.print("Non Master Server: ");
		} else {
			// register master server as frontend
			SL.register_frontend();
			frontend_servers.put(VMID, true);
			System.out.println("MASTER BOOST SERVERS");
			boost_servers();
		}

		// master != null indicates non-master servers
		if (master != null) {
			int role_flag = master.get_role(VMID);
			while (role_flag == -1) {
				role_flag = master.get_role(VMID);
			}
			System.out.println(role_flag);
			if (role_flag == 0) {
				SL.register_frontend();
				run_frontend(master, VMID);
			} else if (role_flag == 1) {
				run_apptier(master, VMID);
			}
		} else {
			run_master();
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

