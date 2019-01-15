package co.gongzh.procbridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import static co.gongzh.procbridge.Protocol.REQ_ID;
import static co.gongzh.procbridge.Protocol.RESP_TO;

/**
 * @author Gong Zhang
 */
public final class ProcBridge {

	private static final JsonParser parser = new JsonParser();
	private final String host;
	private final int port;
	private int timeout;

	private Socket socket;
	
	private InputStream is;
	private Object isLock = new Object();
	
	private OutputStream os;
	private Object osLock = new Object();

	private MessageHandler messageHandler;
	private boolean stopRequested = false;

	private Iterator<Integer> intSpender;
	private Map<Integer, CompletableFuture<JsonObject>> futures = new HashMap<Integer, CompletableFuture<JsonObject>>() ;

	public ProcBridge(String host, int port, int timeout, MessageHandler messageHandler) {
		this.host = host;
		this.port = port;
		this.timeout = timeout;
		this.intSpender = IntStream.iterate(1, i -> i + 1).iterator();
		this.messageHandler = messageHandler;

		this.socket = new Socket();
		try {
			socket.connect(new InetSocketAddress(host, port), timeout);

			// socket.setSoTimeout(timeout);

			is = socket.getInputStream();
			os = socket.getOutputStream();

		} catch (IOException e) {
			throw new RuntimeException("Socket can not be connected.", e);
		}

		startHandlingMessages();

	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public long getTimeout() {
		return timeout;
	}
	
	public int getClientID() throws ProcBridgeException {
		//TODO : implement it correctly !
		sendMessage(Protocol.GET_CLIENT_ID_API);
		return 0;
	}

	public CompletableFuture<JsonObject> sendMessage(@NotNull String api) throws ProcBridgeException {
		return sendMessage(api, (String) null);
	}

	public CompletableFuture<JsonObject> sendMessage(@NotNull String api, @Nullable String jsonText) throws ProcBridgeException {
		try {
			if (jsonText == null) {
				jsonText = "{}";
			}
			JsonObject obj = parser.parse(jsonText).getAsJsonObject();
			return sendMessage(api, obj);
		} catch (JsonParseException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	public CompletableFuture<JsonObject> sendMessage(@NotNull String api, @Nullable JsonObject body) throws ProcBridgeException {
		
		if (socket.isOutputShutdown()) {
			this.stopRequested = true;
			throw new ProcBridgeException("OutputStream closed from the server. No more messages can be handled. Please reconnect.");
		}
		
		synchronized (osLock) {
            int reqid = this.intSpender.next();
            if (body != null) body.addProperty(REQ_ID, reqid);
            final RequestEncoder request = new RequestEncoder(api, body);
            //just send the message here.
            CompletableFuture<JsonObject> res = createFuture(reqid);
            Protocol.write(os, request);
            return res;
        }
	}

    /**
     * creates a future waiting for the given request
     * @param reqid
     * @return the future
     */
	CompletableFuture<JsonObject> createFuture(int reqid){
        CompletableFuture<JsonObject> res = new CompletableFuture<JsonObject>();
        futures.put(reqid, res);
        return res;
    }

    /**
     * completes a future waiting for a request
     * @param reqid
     * @param value will be passed to the
     */
    void completeFuture(int reqid, JsonObject value) throws ProcBridgeException {
        if(!futures.containsKey(reqid)){
            throw new ProcBridgeException("request id not exists:"+reqid);
        }
        CompletableFuture<JsonObject> fut = futures.get(reqid);
        fut.complete(value);
        futures.remove(reqid);
    }

    void cancelFuture(int reqid, Throwable e){
        CompletableFuture<JsonObject> fut = futures.get(reqid);
        fut.completeExceptionally(e);
        fut.cancel(true);
    }

	private void startHandlingMessages() {
		Thread threadMessageHandler = new Thread(() -> {
			while (!stopRequested) {
				try {

                    if (socket.isInputShutdown()) {
                        this.stopRequested = true;
                        throw new ProcBridgeException("InputStream closed from the server. No more messages can be handled. Please reconnect.");
                    }

                    JsonObject out_json = null;
                    String out_err_msg = null;

                    Decoder decoder;

                    synchronized (isLock) {
                        decoder = Protocol.read(is);
                    }

                    if (decoder instanceof GoodResponseDecoder) {
                        out_json = decoder.getResponseBody();

                        int reqid = -1;
                        messageHandler.onMessage(out_json);
                        reqid = decoder.getRespTo();
						if (reqid != -1) completeFuture(reqid, out_json);
                    } else if (decoder instanceof BadResponseDecoder) {
                        out_err_msg = decoder.getErrorMessage();
                        if (out_err_msg == null) {
                            out_err_msg = "server error";
                        }
                        throw new ProcBridgeException("msg from server:" + out_err_msg);
                    } else if (decoder instanceof ErrorResponseDecoder) {
                        out_err_msg = decoder.getErrorMessage();
                        if (out_err_msg == null) {
                            out_err_msg = "server error";
                        }
                        cancelFuture(decoder.getRespTo(),new ProcBridgeException (out_err_msg));
                    }

				} catch (SocketException e) {
					messageHandler.onError(new ProcBridgeException(e));
					return;
				} catch (EmptyStreamException e) {
					return;
				} catch(ProcBridgeException e) {
					if (stopRequested && socket.isClosed()) {
						//the current read operation generates a socket closed exception during a normal stop.
						//it is detected here not to send an error to the caller.
						return; //stop handler
					}
					messageHandler.onError(e);
				} catch (Throwable t) {
					//Prevent thread to die in case of any error.
					messageHandler.onError(new ProcBridgeException(t));
				}
			}
		});
		threadMessageHandler.setDaemon(true);
		threadMessageHandler.setName("pb_clientMessageReceiver");
		threadMessageHandler.start();
	}

	/**
	 * Stop must be called at the end of the ProcBridge usage.
	 */
	public void stop() {
		//sends a close message to server in order to close the socket for the current client.
		try {
			sendMessage(Protocol.CLOSE_MESSAGE_API);
		} catch (ProcBridgeException e1) {
			e1.printStackTrace();
		}
		stopRequested = true;
		if (!this.socket.isClosed()) {
			try {
				this.socket.close();
			} catch (IOException e) {
				throw new RuntimeException("Error closing socket : ", e);
			}
		}
		//Let the connection close normally before exiting.
		//This method is generally called just before exit of the client program and without this small time, 
		//The connection is closed by the end of the JVM and there is an error on the server side.
        try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
