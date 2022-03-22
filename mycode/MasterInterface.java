import java.rmi.*;

public interface MasterInterface extends Remote {
    public int get_role(int vmid) throws RemoteException;




}