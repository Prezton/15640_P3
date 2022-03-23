import java.rmi.*;

public interface MasterInterface extends Remote {
    public int get_role(int vmid) throws RemoteException;

    public Cloud.FrontEndOps.Request get_request() throws RemoteException;

    public void add_request(Cloud.FrontEndOps.Request req) throws RemoteException;




}