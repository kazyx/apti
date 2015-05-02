package net.kazyx.apti;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

class AsyncSource {
    private static final String TAG = AsyncSource.class.getSimpleName();

    /**
     * Release all of thread resources.
     */
    synchronized void destroy() {
        Logger.d(TAG, "destroy");
        mIsAlive = false;
        mConnectionThreadPool.shutdown();
        mTimer.purge();
        mSelectorThread.interrupt();
    }

    private boolean mIsAlive = true;

    synchronized boolean isAlive() {
        return mIsAlive;
    }

    AsyncSource(SelectorProvider provider) throws IOException {
        mSelectorThread = new SelectorThread(provider.openSelector());
        mSelectorThread.start();
    }

    final ExecutorService mConnectionThreadPool = Executors.newCachedThreadPool();

    void safeAsync(Runnable task) {
        try {
            mConnectionThreadPool.submit(task);
        } catch (RejectedExecutionException e) {
            Logger.d(TAG, "RejectedExecution");
        }
    }

    final Timer mTimer = new Timer("apti-timer");

    static class SelectorThread extends Thread {
        private final Selector mSelector;

        SelectorThread(Selector selector) {
            super("apti-selector-thread");
            mSelector = selector;
        }

        @Override
        public void run() {
            // Logger.d(TAG, "SelectorThread started");
            try {
                while (true) {
                    if (!select()) {
                        break;
                    }
                    Iterator<SelectionKey> itr = mSelector.selectedKeys().iterator();
                    while (itr.hasNext()) {
                        SelectionKey key = itr.next();
                        itr.remove();
                        if (key.isValid()) {
                            SelectionHandler handler = (SelectionHandler) key.attachment();
                            handler.onSelected(key);
                        } else {
                            key.cancel();
                        }
                    }
                }
                Logger.d(TAG, "Select Loop finished");
            } finally {
                for (SelectionKey key : mSelector.keys()) {
                    key.cancel();
                    IOUtil.close(key.channel());
                    SelectionHandler handler = (SelectionHandler) key.attachment();
                    handler.onCancelled();
                }
                mQueue.clear();
                IOUtil.close(mSelector);
            }
        }

        private final List<Runnable> mQueue = new ArrayList<>();

        private boolean select() {
            try {
                mSelector.select();
                //Logger.d(TAG, "selected: " + selected);
                if (this.isInterrupted()) {
                    return false;
                }
                synchronized (mQueue) {
                    Iterator<Runnable> itr = mQueue.iterator();
                    while (itr.hasNext()) {
                        itr.next().run();
                        itr.remove();
                    }
                }
                return true;
            } catch (IOException e) {
                Logger.stacktrace(TAG, e);
                return false;
            }
        }

        void registerNewChannel(final SocketChannel channel, final int ops, final SelectionHandler handler) throws IOException {
            synchronized (mQueue) {
                mQueue.add(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            channel.register(mSelector, ops, handler);
                        } catch (ClosedChannelException e) {
                            Logger.stacktrace(TAG, e);
                        }
                    }
                });
            }
            mSelector.wakeup();
        }
    }

    private final SelectorThread mSelectorThread;

    void registerNewChannel(SocketChannel ch, int ops, SelectionHandler handler) throws IOException {
        mSelectorThread.registerNewChannel(ch, ops, handler);
    }
}
