package com.watchtower.backend.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * AJT — RMI: Remote interface.
 * Must extend java.rmi.Remote — every method must declare RemoteException.
 * DiagnosisServer implements this and exports it over RMI registry port 1099.
 * DiagnosisClient looks it up via Naming.lookup() from a separate JVM.
 */
public interface DiagnosisRemote extends Remote {

    /** Returns current total network load percentage. */
    double getTotalLoad() throws RemoteException;

    /** Returns count of currently unresolved alerts. */
    long getUnresolvedAlertCount() throws RemoteException;

    /** Returns last diagnosis result as key→value pairs. */
    List<Map<String, String>> getLastDiagnosisReport() throws RemoteException;
}
