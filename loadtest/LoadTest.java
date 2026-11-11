// java loadtest/LoadTest.java http://localhost:8080 <seconds> <concurrency> <num_users> [hot_fraction]
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class LoadTest {
    public static void main(String[] a) throws Exception {
        String base = a[0]; int secs = Integer.parseInt(a[1]), conc = Integer.parseInt(a[2]), users = Integer.parseInt(a[3]);
        double hot = a.length > 4 ? Double.parseDouble(a[4]) : 0.0;     // share of traffic to 100 "celebrity" users
        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(2)).build();
        String[] types = {"reply", "retweet", "both"};
        long end = System.nanoTime() + secs * 1_000_000_000L;
        AtomicLong ok = new AtomicLong(), err = new AtomicLong();
        ConcurrentLinkedQueue<Long> lat = new ConcurrentLinkedQueue<>();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int w = 0; w < conc; w++) ex.submit(() -> {
                var rnd = ThreadLocalRandom.current();
                while (System.nanoTime() < end) {
                    int uid = rnd.nextDouble() < hot ? 1 + rnd.nextInt(100) : 1 + rnd.nextInt(users);
                    var req = HttpRequest.newBuilder(URI.create(base + "/recommend?user_id=" + uid + "&type=" + types[rnd.nextInt(3)])).build();
                    long t0 = System.nanoTime();
                    try {
                        var r = http.send(req, HttpResponse.BodyHandlers.discarding());
                        if (r.statusCode() == 200) ok.incrementAndGet(); else err.incrementAndGet();
                        lat.add(System.nanoTime() - t0);
                    } catch (Exception e) { err.incrementAndGet(); }
                }
                return null;
            });
        }
        long[] l = lat.stream().mapToLong(Long::longValue).sorted().toArray();
        double p = 1e6;
        System.out.printf("requests=%d errors=%d throughput=%.0f rps p50=%.2fms p95=%.2fms p99=%.2fms%n",
                ok.get(), err.get(), ok.get() / (double) secs, l[l.length / 2] / p, l[(int) (l.length * .95)] / p, l[(int) (l.length * .99)] / p);
    }
}
