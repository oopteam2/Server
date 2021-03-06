package WebSocket;
import WebSocket.Message.WebSocketBinaryMessage;
import WebSocket.Message.WebSocketMessage;
import WebSocket.Message.WebSocketTextMessage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * This class handles the core websocket protocol. Most of the specifications have been implemented. Only supports data of lengths 
 * up to 2^32 for now since underlying data structure is byte array. Can be extended to 2^64 though
 * @author Brian Parra
 */
public class WebSocket extends Socket {

    private final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final Map<Byte, Class> webSocketTable = initializeTable();

    private String socketResponseKey;

    private String webSocketKey;

    private byte currentByte;

    private byte opCode;

    private InputStream inputStream;

    private OutputStream outputStream;

    private DataInputStream dataInputStream;

    /**
     * Static Constructor
     * @return new WebSocket
     * @throws IOException if cannot establish connection
     */
    public static WebSocket create() throws IOException {

        WebSocket newSocket = new WebSocket();


 
        return newSocket;
        

    }

    /**
     * Default Constructor
     * @throws IOException if cannot establish connection
     */
    protected WebSocket() throws IOException {

        super();

    }

    /**
     * Sets up the pointers for the input streams for reading
     * @throws IOException if cannot io streams
     */
    public void initialize() throws IOException {

        inputStream = this.getInputStream();
        outputStream = this.getOutputStream();
        dataInputStream = new DataInputStream(inputStream);
        

    }
    
    /**
     * Sends text data
     * @param text data to send
     * @throws IOException if cant write data
     */
    public void sendTextData(String text) throws IOException {
        
        byte[] binaryText = text.getBytes(StandardCharsets.US_ASCII);
        int messageLength = binaryText.length;
        
        
        byte isFinal = (byte) (1 << 7);
        byte firstHeader = (byte) (WebSocket.OP_CODE.TEXT | (byte)isFinal);
        outputStream.write(firstHeader);
        
        //Don't need to worry about mask bit, will allways be 0.
        
        if(messageLength < 126){
            
            outputStream.write((byte) messageLength);
            
        }else if (messageLength < 65536 ){
            
            outputStream.write((byte)  0x7e); //Set to 256 to use next 2 bytes
            //Write bottom 16 bits as message length use 2 bytes as length 
            ByteBuffer payloadLengthbuffer = ByteBuffer.allocate(Short.BYTES);
            payloadLengthbuffer.putShort((short) (messageLength & 0xffff));            
            outputStream.write(payloadLengthbuffer.array());
            
        }else {
            
            outputStream.write((byte) 0x7f); //set to 8 for 64 bit unsigned number    
            //Only need 32 bits as length, but still has to be 64 bits long
            ByteBuffer payloadLengthbuffer = ByteBuffer.allocate(Long.BYTES);
            payloadLengthbuffer.putLong(messageLength);
            outputStream.write(payloadLengthbuffer.array());
            
        }
        
        //Finally Write the data
        outputStream.write(binaryText);
    
    }
    

    /**
     * Sends binary data
     * @param data to send
     * @throws IOException if can't write data
     */
    public void sendBinaryData(byte[] data) throws IOException {
       
        int messageLength = data.length;
 
        
        
        byte isFinal = (byte) (1 << 7);
        byte firstHeader = (byte) (WebSocket.OP_CODE.BINARY | (byte)isFinal);
        outputStream.write(firstHeader);
        
        //Don't need to worry about mask bit, will allways be 0.
        
        if(messageLength < 126){
           
            outputStream.write((byte) messageLength);
            
        }else if (messageLength < 65536 ){
            
            outputStream.write((byte) 0x7e); //Set to 126 to use next 2 bytes
            //Write bottom 16 bits as message length use 2 bytes as length 
            ByteBuffer payloadLengthbuffer = ByteBuffer.allocate(Short.BYTES);
            payloadLengthbuffer.putShort((short) (messageLength & 0xffff));            
            outputStream.write(payloadLengthbuffer.array());

            
        }else {
            
            outputStream.write((byte) 0x7f); //set to 8 for 64 bit unsigned number    
            //Only need 32 bits as length, but still has to be 64 bits long
            ByteBuffer payloadLengthbuffer = ByteBuffer.allocate(Long.BYTES);
            payloadLengthbuffer.putLong(messageLength);
            outputStream.write(payloadLengthbuffer.array());

        }
        
        //Finally Write the data
        outputStream.write(data);
   
    }
    

    /**
     * Returns the WebSocket message
     * @return the appropriate processed message
     * @throws IOException if cant read data
     */
    public WebSocketMessage getMessage() throws IOException{
        
        currentByte = dataInputStream.readByte();
        
        opCode = (byte) (currentByte & 0x0f);
        
    
        WebSocketFrameHandler frameHandler = null;
        WebSocketMessage message = null;
        
        
        Class frameClass = WebSocket.webSocketTable.get(opCode);
        
      
            
        try {
            
            frameHandler = (WebSocketFrameHandler) frameClass.getConstructor(WebSocket.class).newInstance(this);
            frameHandler.initialize(this, currentByte);
            message = frameHandler.process();
            
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            Logger.getLogger(WebSocket.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return message;

    }

    /**
     * Performs the WebSocket authentication handshake
     * @throws IOException if cant read/write data
     */
    public void handshake() throws IOException {

        InputStream stream = getInputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(stream));

        try {
            // Redo this, save all header data for later use
            String line = in.readLine();

            while (line != null && line.trim().length() > 0) {
                StringTokenizer st = new StringTokenizer(line);
                String args = st.nextToken();

                if (args.equals("Sec-WebSocket-Key:")) {
                    webSocketKey = st.nextToken().trim();
                }

                line = in.readLine();
            }

            socketResponseKey = Base64.encodeBase64String(DigestUtils.sha1(webSocketKey + GUID));

        } catch (IOException e) {
            System.out.println("Error reading body");

        }

        //Now We Respond!
        BufferedOutputStream out = new BufferedOutputStream(getOutputStream());
        PrintWriter writer = new PrintWriter(out, true);  // char output to the client

        writer.println("HTTP/1.1 101 Switching Protocols");
        writer.println("Upgrade: websocket");
        writer.println("Connection: Upgrade");
        writer.println("Sec-WebSocket-Accept: " + socketResponseKey);

        writer.println("");

    }

    /**
     * WebSocket op code constants
     */
    public static class OP_CODE {

        public static final byte CONTINUATION = 0;
        public static final byte TEXT = 1;
        public static final byte BINARY = 2;
        public static final byte CLOSE = 8;
        public static final byte PING = 9;
        public static final byte PONG = 10;

    }

    /**
     * Initialized hash map of the op codes and corresponding processor classes
     * @return constant hash map
     */
    private static Map<Byte, Class> initializeTable() {

        Map<Byte, Class> table = new HashMap<Byte, Class>();

        try {

            //table.put(OP_CODE.CONTINUATION, Class.forName("ProcessContinuationFrame"));
            table.put(OP_CODE.TEXT, ProcessTextFrame.class);
            table.put(OP_CODE.BINARY, ProcessBinaryFrame.class );
            table.put(OP_CODE.CLOSE, ProcessCloseFrame.class );
            table.put(OP_CODE.PING, ProcessPingFrame.class );
            table.put(OP_CODE.PONG, ProcessPongFrame.class );

        } catch (Exception e) {
            
            System.out.println("couldnt add " + e.getMessage());
        }

        return Collections.unmodifiableMap(table);

    }

    /**
     * Abstract class for handling WebSocket frames, these are the actual chunks of data sent
     * @author brian parra
     */
    public abstract class WebSocketFrameHandler {

        protected byte opCode;

        protected byte currentByte;

        protected byte initialByte;

        protected WebSocket webSocket;
        protected InputStream inputStream;

        protected OutputStream outputStream;

        protected DataInputStream dataInputStream;

        byte mask[] = new byte[4];

        byte data[];

        int dataLength;

        int maskBit;
        
        /**
         * Constructor
         */
        public WebSocketFrameHandler(){
            
        }

        /**
         * Initialization for the frame handler. Sets up i/o streams
         * @param _webSocket reference to current websocket
         * @param _initializerByte byte with the op code
         * @throws IOException if cannot get io streams
         */
        public void initialize(WebSocket _webSocket, byte _initializerByte) throws IOException {
            
            initialByte = _initializerByte;
            opCode = (byte) (_initializerByte & 0x0f);
            webSocket = _webSocket;
            inputStream = webSocket.getInputStream();
            outputStream = webSocket.getOutputStream();
            dataInputStream = new DataInputStream(inputStream);
            
            

        }
        
        /**
         * Abstract method. Needed to call data processing
         * @return WebSocketMessage that contians processed data
         * @throws IOException if cannot read
         */
        public abstract WebSocketMessage process() throws IOException;

    }

    /**
     * class that processes a continuation type frame
     */
    public class ProcessContinuationFrame extends WebSocketFrameHandler {
        
        /**
         * Parse the frame
         * @return processed data
         */
        public WebSocketMessage process() {

            return new WebSocketTextMessage();
        }

    }

    /**
     * Class for processing text frame
     * 
     */
    public class ProcessTextFrame extends WebSocketFrameHandler {
        
        /**
         * Constructor
         */
        public ProcessTextFrame(){
            super();
        }

        /**
         * Processes the data
         * @return processed websocket message
         * @throws IOException if cannot read
         */
        public WebSocketMessage process() throws IOException {
            
            
            //System.out.println("is Final :" + WebSocket.getBit(initialByte, 7));
            //System.out.println("Op Code: " + opCode);

            //Dont forget to handle isFinal bit
            currentByte = dataInputStream.readByte();
            maskBit = (currentByte >> 7) & 0x1;

            dataLength = currentByte & 0x7f;
            //System.out.println("Mask Bit is :" + maskBit);
            //System.out.println("Data Length is :" + dataLength);

            mask[0] = dataInputStream.readByte();
            mask[1] = dataInputStream.readByte();
            mask[2] = dataInputStream.readByte();
            mask[3] = dataInputStream.readByte();

            data = new byte[dataLength];
            int maskIndex;
            for (int n = 0; n < dataLength; n++) {
                maskIndex = n % 4;
                data[n] = (byte) (dataInputStream.readByte() ^ mask[maskIndex]);
            }
            String textMessage = new String(data);

            
            WebSocketTextMessage message = new WebSocketTextMessage();
            message.setOpcode(opCode);
            message.setText(textMessage);

            return message;
        }
    }

    /**
     * Class for processing binary frames
     */
    public class ProcessBinaryFrame extends WebSocketFrameHandler {

        /**
         * processes the binary frame
         * @return websocket message
         * @throws IOException if cannot read
         */
        public WebSocketMessage process() throws IOException {
            
            
            //System.out.println("is Final :" + WebSocket.getBit(initialByte, 7));
            //System.out.println("Op Code: " + opCode);

            //Dont forget to handle isFinal bit
            currentByte = dataInputStream.readByte();
            maskBit = (currentByte >> 7) & 0x1;

            dataLength = currentByte & 0x7f;
            //System.out.println("Mask Bit is :" + maskBit);
            //System.out.println("Data Length is :" + dataLength);

            mask[0] = dataInputStream.readByte();
            mask[1] = dataInputStream.readByte();
            mask[2] = dataInputStream.readByte();
            mask[3] = dataInputStream.readByte();

            data = new byte[dataLength];
            int maskIndex;
            for (int n = 0; n < dataLength; n++) {
                maskIndex = n % 4;
                data[n] = (byte) (dataInputStream.readByte() ^ mask[maskIndex]);
            }
            //String textMessage = new String(data);

            
            WebSocketBinaryMessage message = new WebSocketBinaryMessage();
            message.setOpcode(opCode);
            message.setData(data);

            return message;
        }
        
    }

    /**
     * Class for handling close requests
     */
    public class ProcessCloseFrame extends WebSocketFrameHandler {

        /**
         * Processes the close request
         * @return new websocket text message with stats
         */
        public WebSocketMessage process() {

            return new WebSocketTextMessage();
        }
    }

    /**
     * Class for handling ping
     */
    public class ProcessPingFrame extends WebSocketFrameHandler {

        /**
         * Processes ping. Should respond with a pong
         * @return websocket message
         */
        public WebSocketMessage process() {

            return new WebSocketTextMessage();
        }
    }

    /**
     * Handles a pong request
     */
    public class ProcessPongFrame extends WebSocketFrameHandler {

        /**
         * Process pong
         * @return websocket message with details
         */
        public WebSocketMessage process() {

            return new WebSocketTextMessage();
        }
    }
    


}
