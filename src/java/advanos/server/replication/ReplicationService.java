/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package advanos.server.replication;

/**
 *
 * @author Darren
 */
public class ReplicationService extends Thread{
    
    private static ReplicationService instance;
    
    public static ReplicationService instance(){
        if (instance == null)
            instance = new ReplicationService();
        return instance;
    }
    
    public static void main(String[] args) {
        ReplicationService.instance().start();
    }

    @Override
    public void run() {
        
    }
}
