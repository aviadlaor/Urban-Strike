package ai.model;

import javax.sound.sampled.*;

public class SoundEngine {

    private static final int SAMPLE_RATE = 44100;

    public static void playShoot()      { playAsync(SoundEngine::generateShoot);      }
    public static void playHit()        { playAsync(SoundEngine::generateHit);        }
    public static void playPlayerHit()  { playAsync(SoundEngine::generatePlayerHit);  }
    public static void playPickup()     { playAsync(SoundEngine::generatePickup);     }
    public static void playReload()     { playAsync(SoundEngine::generateReload);     }
    public static void playEnemyDeath() { playAsync(SoundEngine::generateEnemyDeath); }

    private static void playAsync(java.util.concurrent.Callable<byte[]> generator) {
        new Thread(() -> {
            try {
                byte[] data = generator.call();
                AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(fmt);
                line.start();
                line.write(data, 0, data.length);
                line.drain();
                line.close();
            } catch (Exception ignored) {}
        }).start();
    }

    // ירי — white noise קצר עם decay
    private static byte[] generateShoot() {
        int len = SAMPLE_RATE / 8;
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            double decay = 1.0 - (double) i / len;
            buf[i] = (byte) ((Math.random() * 2 - 1) * 127 * decay * decay);
        }
        return buf;
    }

    // פגיעה באויב — click חד עם pitch יורד
    private static byte[] generateHit() {
        int len = SAMPLE_RATE / 20;
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            double decay = 1.0 - (double) i / len;
            double freq  = 800 - 600 * ((double) i / len);
            buf[i] = (byte) (Math.sin(i * freq * 2 * Math.PI / SAMPLE_RATE) * 90 * decay);
        }
        return buf;
    }

    // פגיעה בשחקן — טון נמוך מזעזע עם רעש
    private static byte[] generatePlayerHit() {
        int len = SAMPLE_RATE / 6;
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            double decay = 1.0 - (double) i / len;
            double noise = (Math.random() * 2 - 1) * 0.4;
            double tone  = Math.sin(i * 120 * 2 * Math.PI / SAMPLE_RATE) * 0.6;
            buf[i] = (byte) ((noise + tone) * 100 * decay);
        }
        return buf;
    }

    // pickup — טון עולה נעים
    private static byte[] generatePickup() {
        int len = SAMPLE_RATE / 5;
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            double decay = 1.0 - (double) i / len;
            double freq  = 400 + 800 * ((double) i / len);
            buf[i] = (byte) (Math.sin(i * freq * 2 * Math.PI / SAMPLE_RATE) * 80 * decay);
        }
        return buf;
    }

    // reload — שני קליקים
    private static byte[] generateReload() {
        int len = SAMPLE_RATE / 3;
        byte[] buf = new byte[len];
        int click1 = len / 4;
        int click2 = len * 3 / 4;
        for (int i = 0; i < len; i++) {
            int nearest = Math.abs(i - click1) < Math.abs(i - click2) ? click1 : click2;
            if (Math.abs(i - click1) < 800 || Math.abs(i - click2) < 800) {
                double decay = 1.0 - (double) Math.abs(i - nearest) / 800.0;
                buf[i] = (byte) ((Math.random() * 2 - 1) * 110 * decay);
            }
        }
        return buf;
    }

    // מוות אויב — גניחה יורדת עם רעש
    private static byte[] generateEnemyDeath() {
        int len = SAMPLE_RATE / 4;
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            double decay = 1.0 - (double) i / len;
            double freq  = 300 - 200 * ((double) i / len);
            double noise = (Math.random() * 2 - 1) * 0.3;
            buf[i] = (byte) ((Math.sin(i * freq * 2 * Math.PI / SAMPLE_RATE) * 0.7 + noise) * 90 * decay);
        }
        return buf;
    }
}
