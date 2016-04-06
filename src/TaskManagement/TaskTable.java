/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TaskManagement;

import Core.FlareClient;
import WebSocket.WebSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author mac
 */
public class TaskTable {
        
    
    public static final Map<Byte, Class> taskTable = initializeTable();

    private static Map<Byte, Class> initializeTable() {

        Map<Byte, Class> table = new HashMap<Byte, Class>();

        try {


            table.put(FlareOpCode.OPEN_VIDEO, OpenVideoTaskHandler.class);


        } catch (Exception e) {

            System.out.println("couldnt add " + e.getMessage());
        }

        return Collections.unmodifiableMap(table);

    }
    
    
}
