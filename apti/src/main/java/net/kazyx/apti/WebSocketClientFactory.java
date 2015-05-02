package net.kazyx.apti;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Factory to create WebSocket client connection.
 */
public class WebSocketClientFactory {
    private static final String TAG = WebSocketClientFactory.class.getSimpleName();

    private final AsyncSource mAsync;
    private final SelectorProvider mProvider;

    public WebSocketClientFactory() throws IOException {
        mProvider = SelectorProvider.provider();
        mAsync = new AsyncSource(mProvider);
    }

    /**
     * Destroy this {@link WebSocketClientFactory}.<br>
     * Note that any connections created by this instance will be released.
     */
    public synchronized void destroy() {
        mAsync.destroy();
    }

    /**
     * Open WebSocket connection to the specified remote server.<br>
     * Equivalent to {@code open(uri, handler, null);}.
     *
     * @param uri     URI of the remote server.
     * @param handler WebSocket connection event handler.
     * @return Future of WebSocket instance.
     */
    public synchronized Future<WebSocket> openAsync(URI uri, WebSocketConnection handler) {
        return openAsync(uri, handler, null);
    }

    /**
     * Open WebSocket connection to the specified remote server.
     *
     * @param uri     URI of the remote server.
     * @param handler WebSocket connection event handler.
     * @param headers Additional HTTP header to be inserted to opening request.
     * @return Future of WebSocket instance.
     * @throws IllegalStateException if this instance is already destroyed.
     */
    public synchronized Future<WebSocket> openAsync(final URI uri, final WebSocketConnection handler, final List<HttpHeader> headers) {
        if (!mAsync.isAlive()) {
            throw new IllegalStateException("This WebSocketClientFactory is already destroyed.");
        }
        return mAsync.mConnectionThreadPool.submit(new Callable<WebSocket>() {
            @Override
            public WebSocket call() throws Exception {
                WebSocket ws = null;
                SocketChannel ch = null;
                try {
                    ws = new WebSocket(mAsync, uri, handler, headers);
                    ch = mProvider.openSocketChannel();
                    ch.configureBlocking(false);
                    ws.connect(ch);
                    return ws;
                } catch (IOException e) {
                    ws.closeNow();
                    IOUtil.close(ch);
                    throw e;
                }
            }
        });
    }
}