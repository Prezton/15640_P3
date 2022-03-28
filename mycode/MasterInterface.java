import java.rmi.*;

public interface MasterInterface extends Remote {
    public int get_role(int vmid) throws RemoteException;

    public Cloud.FrontEndOps.Request get_request() throws RemoteException;

    public void add_request(Cloud.FrontEndOps.Request req) throws RemoteException;

	public int check_app_status() throws RemoteException;

    public void scale_out(int server_type) throws RemoteException;

    public int scale_in(int server_type, int vmid) throws RemoteException;

	public int get_app_num() throws RemoteException;

	public int get_frontend_num() throws RemoteException;
}