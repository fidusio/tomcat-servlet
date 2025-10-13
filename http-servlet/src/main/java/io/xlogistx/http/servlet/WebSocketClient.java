package io.xlogistx.http.servlet;


import org.zoxweb.server.io.IOUtil;
import org.zoxweb.shared.http.HTTPMessageConfig;
import org.zoxweb.shared.util.SharedStringUtil;

import javax.websocket.*;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;


@ClientEndpoint
public class WebSocketClient
        implements Closeable {

    private Session userSession = null;
    private BiConsumer<WebSocketClient, String> dataHandler = null;
    private final AutoCloseable closeHandler;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicLong responseCounter = new AtomicLong(0);
    private final boolean statEnabled;
    private boolean closed = false;


    public WebSocketClient(HTTPMessageConfig hmc, BiConsumer<WebSocketClient, String> dataHandler, AutoCloseable closeHandler, boolean statEnabled)
            throws DeploymentException, IOException, URISyntaxException {

        this(new URI(SharedStringUtil.concat(hmc.getURL(), hmc.getURI(), "/")), dataHandler, closeHandler, statEnabled);
    }


    public WebSocketClient(URI endpointURI, BiConsumer<WebSocketClient, String> dataHandler, AutoCloseable closeHandler, boolean statEnabled)
            throws DeploymentException, IOException {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(this, endpointURI);
        this.closeHandler = closeHandler;
        setDataHandler(dataHandler);
        this.statEnabled = statEnabled;

    }

    /**
     * Callback hook for Connection open events.
     *
     * @param userSession
     *            the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
        this.userSession = userSession;
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession
     *            the userSession which is getting closed.
     * @param reason
     *            the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        IOUtil.close(this);
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a
     * client send a message.
     *
     * @param message
     *            The text message
     */
    @OnMessage
    public void onMessage(String message) {
        //respCounter.incrementAndGet();
        //respCounter++;
        if (statEnabled) {
            responseCounter.incrementAndGet();
        }
        if (dataHandler != null)
            dataHandler.accept(this, message);
    }

    /**
     * Return the request count if stat is enabled
     * @return request counts
     */
    public long requestCount() {
        return requestCounter.get();
    }


    public boolean isStartEnabled() {
        return statEnabled;
    }

    /**
     * Return the response count if stat is enabled
     * @return
     */
    public long responseCount() {
        return responseCounter.get();
    }

    /**
     * set the data handler
     * @param dh data handler
     */
    public void setDataHandler(BiConsumer<WebSocketClient, String> dh) {
        this.dataHandler = dh;
    }


    /**
     * Send a message
     * @param message
     */
    public void sendMessage(String message) throws IOException {
        sendMessage(message, false);
    }

    public void sendMessage(String message, boolean async) throws IOException {
        if (userSession.isOpen()) {
            if (async)
                userSession.getAsyncRemote().sendText(message);
            else
                userSession.getBasicRemote().sendText(message);
            if (statEnabled) {
                requestCounter.incrementAndGet();
            }
        } else {
            throw new IOException("WebSocket closed");
        }
    }

    public void close() throws IOException {
        boolean test = false;
        synchronized (this) {
            if (!closed) {
                closed = true;
                test = true;
            }
        }

        if (test) {
            IOUtil.close(userSession);
            IOUtil.close(closeHandler);
        }
    }

    public boolean isClosed() {
        return userSession.isOpen();
    }

}