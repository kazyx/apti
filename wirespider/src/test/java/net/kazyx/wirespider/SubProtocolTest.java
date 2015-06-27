package net.kazyx.wirespider;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class SubProtocolTest {
    private static TestWebSocketServer server = new TestWebSocketServer(10000);

    private static final String SUBPROTOCOL = "v1.test.protocol";
    private static final String INVALID_SUBPROTOCOL = "dummy.protocol";

    @BeforeClass
    public static void setupClass() throws Exception {
        RandomSource.seed(0x12345678);
        Log.logLevel(Log.Level.VERBOSE);
        server.registerSubProtocol(SUBPROTOCOL);
        server.boot();
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        server.shutdown();
    }

    @Test
    public void accepted() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .protocols(Collections.singletonList(SUBPROTOCOL))
                .build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            assertThat(ws.isConnected(), is(true));
            assertThat(ws.protocol(), is(SUBPROTOCOL));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test(expected = IOException.class)
    public void rejected() throws IOException, InterruptedException, ExecutionException, TimeoutException, HandshakeFailureException {
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .protocols(Collections.singletonList(INVALID_SUBPROTOCOL))
                .build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) (e.getCause());
            }
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void multiple() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .protocols(Arrays.asList(SUBPROTOCOL, INVALID_SUBPROTOCOL))
                .build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            assertThat(ws.isConnected(), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void customHandlerAccept() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .protocols(Collections.singletonList(INVALID_SUBPROTOCOL))
                .handshakeHandler(new HandshakeResponseHandler() {
                    @Override
                    public boolean onReceived(HandshakeResponse response) {
                        if (!INVALID_SUBPROTOCOL.equals(response.protocol())) {
                            System.out.println("Response does not contain " + INVALID_SUBPROTOCOL);
                        }
                        return true;
                    }
                })
                .build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            assertThat(ws.isConnected(), is(true));
            assertThat(ws.protocol(), is(nullValue()));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test(expected = IOException.class)
    public void customHandlerReject() throws IOException, InterruptedException, ExecutionException, TimeoutException, HandshakeFailureException {
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .protocols(Collections.singletonList(SUBPROTOCOL))
                .handshakeHandler(new HandshakeResponseHandler() {
                    @Override
                    public boolean onReceived(HandshakeResponse response) {
                        return false;
                    }
                })
                .build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) (e.getCause());
            }
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }
}
