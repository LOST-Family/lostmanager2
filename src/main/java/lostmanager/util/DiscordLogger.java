package lostmanager.util;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class DiscordLogger {

    // HARDCODED CHANNEL ID - User requested this be hardcoded.
    private static final String CHANNEL_ID = "1462095320317165859";

    private static JDA jda;
    private static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;
    private static final String THREAD_NAME = "DiscordLogThread";

    public static void setup() {
        // Replace System.out
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                originalOut.write(b);
                if (shouldLog()) {
                    logQueue.offer(String.valueOf((char) b));
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                originalOut.write(b, off, len);
                if (shouldLog()) {
                    logQueue.offer(new String(b, off, len));
                }
            }
        }));

        // Replace System.err
        System.setErr(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                originalErr.write(b);
                if (shouldLog()) {
                    logQueue.offer(String.valueOf((char) b));
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                originalErr.write(b, off, len);
                if (shouldLog()) {
                    logQueue.offer(new String(b, off, len));
                }
            }
        }));

        startLogThread();
    }

    // Prevent infinite loops if JDA logs to System.out from our logging thread
    private static boolean shouldLog() {
        return !Thread.currentThread().getName().equals(THREAD_NAME);
    }

    public static void setJda(JDA jdaInstance) {
        jda = jdaInstance;
    }

    private static void startLogThread() {
        Thread t = new Thread(() -> {
            StringBuilder buffer = new StringBuilder();
            long lastSend = System.currentTimeMillis();

            while (true) {
                try {
                    // Poll with timeout to allow periodic flushing even if no new logs
                    String s = logQueue.poll(1000, TimeUnit.MILLISECONDS);
                    if (s != null) {
                        buffer.append(s);
                    }

                    long now = System.currentTimeMillis();
                    // Flush if:
                    // 1. Buffer is getting full (> 1800 chars, limit is 2000)
                    // 2. Buffer is not empty AND (we got no new log OR it's been a while)
                    boolean bufferFull = buffer.length() >= 1800;
                    boolean timeToFlush = buffer.length() > 0 && (s == null || now - lastSend > 2000);

                    if (bufferFull || timeToFlush) {
                        sendToDiscord(buffer.toString());
                        buffer.setLength(0);
                        lastSend = now;
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    originalErr.println("Error in DiscordLogger thread: " + e.getMessage());
                }
            }
        }, THREAD_NAME);
        t.setDaemon(true);
        t.start();
    }

    private static void sendToDiscord(String msg) {
        if (jda == null)
            return;

        try {
            TextChannel channel = jda.getTextChannelById(CHANNEL_ID);
            if (channel != null) {
                if (msg.length() > 1990) {
                    // Split if larger than 1990 to account for code block wrapper
                    for (int i = 0; i < msg.length(); i += 1990) {
                        String chunk = msg.substring(i, Math.min(msg.length(), i + 1990));
                        // Avoid empty chunks
                        if (!chunk.trim().isEmpty()) {
                            channel.sendMessage("```" + chunk + "```").queue();
                        }
                    }
                } else {
                    if (!msg.trim().isEmpty()) {
                        channel.sendMessage("```" + msg + "```").queue();
                    }
                }
            }
        } catch (Exception e) {
            // Write to original err so we see it but don't loop
            originalErr.println("Failed to send log to Discord: " + e.getMessage());
        }
    }
}
