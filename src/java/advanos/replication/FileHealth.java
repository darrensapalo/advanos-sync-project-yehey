/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package advanos.replication;

import java.util.AbstractMap;

/**
 *
 * @author Darren
 */
class FileHealth {

    private final AbstractMap.SimpleEntry<String, Integer> entry;
    private final Integer aliveServers;

    public FileHealth(AbstractMap.SimpleEntry<String, Integer> entry, Integer aliveServers) {
        this.entry = entry;
        this.aliveServers = aliveServers;
    }

    public AbstractMap.SimpleEntry<String, Integer> getEntry() {
        return entry;
    }

    public Integer getAliveServers() {
        return aliveServers;
    }
    
    
}
