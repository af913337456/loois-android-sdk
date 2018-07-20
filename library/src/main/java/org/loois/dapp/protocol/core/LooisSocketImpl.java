package org.loois.dapp.protocol.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.loois.dapp.protocol.Config;
import org.loois.dapp.protocol.LooisSocketApi;
import org.loois.dapp.protocol.core.socket.OwnerBody;
import org.loois.dapp.protocol.core.socket.SocketBalance;
import org.loois.dapp.protocol.core.socket.SocketBalanceBody;
import org.loois.dapp.protocol.core.socket.SocketTransaction;
import org.loois.dapp.protocol.secure.SSLSocketClient;
import org.loois.dapp.protocol.secure.TrustAllManager;
import org.web3j.protocol.ObjectMapperFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.engineio.client.transports.WebSocket;
import okhttp3.OkHttpClient;

public class LooisSocketImpl implements LooisSocketApi {

    private static final String TAG = "LooisSocketImpl";


    private Socket socket;

    private ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();

    private Map<String, ArrayList<SocketListener>> eventListeners = new HashMap<>();



    public LooisSocketImpl() {
        initDefaultSocket();
    }

    public LooisSocketImpl(Socket socket) {
        this.socket = socket;
    }

    private void initDefaultSocket() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .sslSocketFactory(SSLSocketClient.getSSLSocketFactory(), new TrustAllManager())
                .hostnameVerifier(SSLSocketClient.getHostnameVerifier())
                .build();
        IO.Options opts = new IO.Options();
        opts.transports = new String[]{WebSocket.NAME};
        opts.forceNew = true;
        opts.upgrade = false;
        opts.reconnection = false;
        opts.callFactory = okHttpClient;
        opts.webSocketFactory = okHttpClient;
        try {
            socket = IO.socket(Config.BASE_URL, opts);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onBalance(String owner) {
        SocketBalanceBody body = new SocketBalanceBody(owner);
        try {
            String json = objectMapper.writeValueAsString(body);
            if (!socket.connected()) {
                socket.connect();
                socket.on(Socket.EVENT_CONNECT, args -> {
                    socket.emit(SocketMethod.balance_req, json);
                });
            } else {
                socket.emit(SocketMethod.balance_req, json);
            }

            socket.on(SocketMethod.balance_res, args -> {
                try {
                    String jsonString = (String) args[0];
                    SocketBalance socketBalance = objectMapper.readValue(jsonString, SocketBalance.class);
                    ArrayList<SocketListener> socketListeners = eventListeners.get(SocketMethod.balance_res);
                    if (socketListeners != null) {
                        for (SocketListener listener: socketListeners) {
                            listener.onBalance(socketBalance);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void offBalance() {
        socket.off(SocketMethod.balance_res);
        socket.emit(SocketMethod.balance_end, "", (Ack) args -> {
        });
    }

    public void registerBalanceListener(SocketListener socketListener) {
        addListener(SocketMethod.balance_res, socketListener);
    }

    public void removeBalanceListener(SocketListener socketListener) {
        removeListener(SocketMethod.balance_res, socketListener);
    }

    @Override
    public void onTransaction(String owner) {
        OwnerBody body = new OwnerBody(owner);
        try {
            String json = objectMapper.writeValueAsString(body);
            if (!socket.connected()) {
                socket.connect();
                socket.on(Socket.EVENT_CONNECT, args -> {
                    socket.emit(SocketMethod.pendingTx_req, json);
                });
            } else {
                socket.emit(SocketMethod.pendingTx_req, json);
            }
            socket.on(SocketMethod.pendingTx_res, args -> {
                String jsonString = (String) args[0];
                try {
                    SocketTransaction socketTransaction = objectMapper.readValue(jsonString, SocketTransaction.class);
                    ArrayList<SocketListener> listeners = eventListeners.get(SocketMethod.pendingTx_res);
                    if (listeners != null) {
                        for (SocketListener listener: listeners) {
                            listener.onTransactions(socketTransaction);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            });
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void offTransaction() {
        socket.off(SocketMethod.pendingTx_res);
        socket.emit(SocketMethod.pendingTx_end);
    }

    public void registerTransactionListener(SocketListener listener) {
        addListener(SocketMethod.pendingTx_res, listener);
    }

    public void removeTransactionListener(SocketListener listener) {
        removeListener(SocketMethod.pendingTx_res, listener);
    }




    private void addListener(String key, SocketListener listener) {
        ArrayList<SocketListener> listeners = eventListeners.get(key);
        if (listeners == null) {
            eventListeners.put(key, new ArrayList<>());
        }
        eventListeners.get(key).add(listener);
    }

    private void removeListener(String key, SocketListener listener) {
        ArrayList<SocketListener> listeners = eventListeners.get(key);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }
}
