/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.ExtensionRequest;
import net.kazyx.wirespider.extension.compression.CompressionStrategy;
import net.kazyx.wirespider.extension.compression.DeflateRequest;
import net.kazyx.wirespider.extension.compression.PerMessageDeflate;
import org.junit.AfterClass;
import org.junit.Before;
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
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class ExtensionDeflateTest {
    public static class CompressorTest {
        private PerMessageDeflate mCompression;

        @Before
        public void setup() {
            mCompression = new PerMessageDeflate(null);
        }

        @Test
        public void compressDecompress() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                sb.append("TestMessage");
            }
            byte[] source = sb.toString().getBytes("UTF-8");
            byte[] compressed = mCompression.compress(source);
            System.out.println("Compressed: " + source.length + " to " + compressed.length);

            byte[] decompressed = mCompression.decompress(compressed);

            System.out.println("Decompressed: " + decompressed.length);
            assertThat(Arrays.equals(source, decompressed), is(true));
        }

        @Test
        public void compressDecompress2() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                sb.append("TestMessage");
            }
            byte[] source = sb.toString().getBytes("UTF-8");
            byte[] compressed = mCompression.compress(source);
            System.out.println("Compressed: " + source.length + " to " + compressed.length);

            byte[] decompressed = mCompression.decompress(compressed);

            System.out.println("Decompressed: " + decompressed.length);
            assertThat(Arrays.equals(source, decompressed), is(true));

            compressed = mCompression.compress(source);
            System.out.println("Compressed: " + source.length + " to " + compressed.length);

            decompressed = mCompression.decompress(compressed);

            System.out.println("Decompressed: " + decompressed.length);
            assertThat(Arrays.equals(source, decompressed), is(true));
        }
    }

    public static class BuilderTest {

        @Test(expected = IllegalArgumentException.class)
        public void maxClientWindowBitsLow() {
            new DeflateRequest.Builder().setMaxClientWindowBits(7);
        }

        @Test(expected = IllegalArgumentException.class)
        public void maxClientWindowBitsHigh() {
            new DeflateRequest.Builder().setMaxClientWindowBits(16);
        }

        @Test(expected = IllegalArgumentException.class)
        public void maxServerWindowBitsLow() {
            new DeflateRequest.Builder().setMaxServerWindowBits(7);
        }

        @Test(expected = IllegalArgumentException.class)
        public void maxServerWindowBitsHigh() {
            new DeflateRequest.Builder().setMaxServerWindowBits(16);
        }

        @Test
        public void maxClientWindowBitsInRange() {
            new DeflateRequest.Builder()
                    .setMaxClientWindowBits(8)
                    .setMaxClientWindowBits(9)
                    .setMaxClientWindowBits(10)
                    .setMaxClientWindowBits(11)
                    .setMaxClientWindowBits(12)
                    .setMaxClientWindowBits(13)
                    .setMaxClientWindowBits(14)
                    .setMaxClientWindowBits(15);
        }

        @Test
        public void maxServerWindowBitsInRange() {
            new DeflateRequest.Builder()
                    .setMaxServerWindowBits(8)
                    .setMaxServerWindowBits(9)
                    .setMaxServerWindowBits(10)
                    .setMaxServerWindowBits(11)
                    .setMaxServerWindowBits(12)
                    .setMaxServerWindowBits(13)
                    .setMaxServerWindowBits(14)
                    .setMaxServerWindowBits(15);
        }
    }

    public static class IntegrationDeflateTest {
        private static TestWebSocketServer server = new TestWebSocketServer(10000);

        @BeforeClass
        public static void setupClass() throws Exception {
            RandomSource.setSeed(0x12345678);
            Base64.setEncoder(new Base64Encoder());
            server.registerExtension(TestWebSocketServer.Extension.DEFLATE);
            server.boot();
        }

        @AfterClass
        public static void teardownClass() throws Exception {
            server.shutdown();
        }

        @Test
        public void fixedTextCompressionWindow15() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            fixedTextCompressionByWindowSize(15);
        }

        @Test
        public void fixedTextCompressionWindow8() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            fixedTextCompressionByWindowSize(8);
        }

        private void fixedTextCompressionByWindowSize(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
            final int MESSAGE_SIZE = 4096;
            final CustomLatch latch = new CustomLatch(1);
            final String data = TestUtil.fixedLengthFixedString(MESSAGE_SIZE);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .setMaxClientWindowBits(size)
                    .setMaxServerWindowBits(size)
                    .build();
            SessionRequest seed = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
                @Override
                public void onClosed(int code, String reason) {
                    latch.unlockByFailure();
                }

                @Override
                public void onTextMessage(String message) {
                    if (data.equals(message)) {
                        latch.countDown();
                    } else {
                        System.out.println("Text message not matched");
                        latch.unlockByFailure();
                    }
                }
            }).setExtensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .setMaxResponsePayloadSizeInBytes(MESSAGE_SIZE - 1)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();
            WebSocket ws = null;
            try {
                Future<WebSocket> future = factory.openAsync(seed);
                ws = future.get(1000, TimeUnit.MILLISECONDS);
                assertThat(ws.extensions().size(), is(1));
                assertThat(ws.extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendTextMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                if (ws != null) {
                    ws.closeNow();
                }
                factory.destroy();
            }
        }

        @Test
        public void randomTextCompressionWindow15() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            randomTextCompressionByWindowSize(15);
        }

        @Test
        public void randomTextCompressionWindow8() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            randomTextCompressionByWindowSize(8);
        }

        private void randomTextCompressionByWindowSize(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
            final int MESSAGE_SIZE = 4096;
            final CustomLatch latch = new CustomLatch(1);
            final String data = TestUtil.fixedLengthRandomString(MESSAGE_SIZE);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .setMaxClientWindowBits(size)
                    .setMaxServerWindowBits(size)
                    .build();
            SessionRequest seed = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
                @Override
                public void onClosed(int code, String reason) {
                    latch.unlockByFailure();
                }

                @Override
                public void onTextMessage(String message) {
                    if (data.equals(message)) {
                        latch.countDown();
                    } else {
                        System.out.println("Text message not matched");
                        latch.unlockByFailure();
                    }
                }
            }).setExtensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .setMaxResponsePayloadSizeInBytes(MESSAGE_SIZE * 5)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();
            WebSocket ws = null;
            try {
                Future<WebSocket> future = factory.openAsync(seed);
                ws = future.get(1000, TimeUnit.MILLISECONDS);
                assertThat(ws.extensions().size(), is(1));
                assertThat(ws.extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendTextMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                if (ws != null) {
                    ws.closeNow();
                }
                factory.destroy();
            }
        }

        @Test
        public void fixedBinaryCompressionWindow8() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            fixedBinaryCompressionByWindowSize(8);
        }

        @Test
        public void fixedBinaryCompressionWindow15() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            fixedBinaryCompressionByWindowSize(15);
        }

        private void fixedBinaryCompressionByWindowSize(int size) throws ExecutionException, InterruptedException, TimeoutException, IOException {
            final int MESSAGE_SIZE = 4096;
            final CustomLatch latch = new CustomLatch(1);
            final byte[] data = TestUtil.fixedLengthFixedByteArray(MESSAGE_SIZE);
            final byte[] copy = Arrays.copyOf(data, data.length);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .setMaxClientWindowBits(size)
                    .setMaxServerWindowBits(size)
                    .build();
            SessionRequest seed = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
                @Override
                public void onClosed(int code, String reason) {
                    latch.unlockByFailure();
                }

                @Override
                public void onBinaryMessage(byte[] message) {
                    if (Arrays.equals(copy, message)) {
                        latch.countDown();
                    } else {
                        System.out.println("Text message not matched");
                        latch.unlockByFailure();
                    }
                }
            }).setExtensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .setMaxResponsePayloadSizeInBytes(MESSAGE_SIZE - 1)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();
            WebSocket ws = null;
            try {
                Future<WebSocket> future = factory.openAsync(seed);
                ws = future.get(1000, TimeUnit.MILLISECONDS);
                assertThat(ws.handshake().extensions().size(), is(1));
                assertThat(ws.handshake().extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendBinaryMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                if (ws != null) {
                    ws.closeNow();
                }
                factory.destroy();
            }
        }

        @Test
        public void randomBinaryCompressionWindow8() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            randomBinaryCompressionByWindowSize(8);
        }

        @Test
        public void randomBinaryCompressionWindow15() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            randomBinaryCompressionByWindowSize(15);
        }

        private void randomBinaryCompressionByWindowSize(int size) throws ExecutionException, InterruptedException, TimeoutException, IOException {
            final int MESSAGE_SIZE = 4096;
            final CustomLatch latch = new CustomLatch(1);
            final byte[] data = TestUtil.fixedLengthRandomByteArray(MESSAGE_SIZE);
            final byte[] copy = Arrays.copyOf(data, data.length);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .setMaxClientWindowBits(size)
                    .setMaxServerWindowBits(size)
                    .build();
            SessionRequest seed = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
                @Override
                public void onClosed(int code, String reason) {
                    latch.unlockByFailure();
                }

                @Override
                public void onBinaryMessage(byte[] message) {
                    if (Arrays.equals(copy, message)) {
                        latch.countDown();
                    } else {
                        System.out.println("Text message not matched");
                        latch.unlockByFailure();
                    }
                }
            }).setExtensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .setMaxResponsePayloadSizeInBytes(MESSAGE_SIZE * 5)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();
            WebSocket ws = null;
            try {
                Future<WebSocket> future = factory.openAsync(seed);
                ws = future.get(1000, TimeUnit.MILLISECONDS);
                assertThat(ws.handshake().extensions().size(), is(1));
                assertThat(ws.handshake().extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendBinaryMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                if (ws != null) {
                    ws.closeNow();
                }
                factory.destroy();
            }
        }
    }

    public static class CompressionStrategyTest {
        private static final int SIZE_BASE = 200;

        private PerMessageDeflate mCompression;

        @Before
        public void setup() {
            mCompression = new PerMessageDeflate(new CompressionStrategy() {
                @Override
                public int minSizeInBytes() {
                    return SIZE_BASE;
                }
            });
        }

        @Test
        public void dataSizeSmallerThanCompressionMinRange() throws IOException {
            final byte[] original = TestUtil.fixedLengthFixedByteArray(SIZE_BASE - 1);
            final byte[] copy = Arrays.copyOf(original, original.length);
            byte[] compressed = mCompression.compress(original);
            assertThat(Arrays.equals(copy, compressed), is(true)); // If data size is smaller than min, it should not be compressed.
        }

        @Test
        public void dataSizeEqualsCompressionMinRange() throws IOException {
            final byte[] original = TestUtil.fixedLengthFixedByteArray(SIZE_BASE);
            final byte[] copy = Arrays.copyOf(original, original.length);
            byte[] compressed = mCompression.compress(original);
            assertThat(Arrays.equals(copy, compressed), is(false));
        }

        @Test
        public void dataSizeLargerThanCompressionMinRange() throws IOException {
            final byte[] original = TestUtil.fixedLengthFixedByteArray(SIZE_BASE + 1);
            final byte[] copy = Arrays.copyOf(original, original.length);
            byte[] compressed = mCompression.compress(original);
            assertThat(Arrays.equals(copy, compressed), is(false));
        }
    }
}
