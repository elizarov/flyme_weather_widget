package me.elizarov.weather;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.location.Location;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class WeatherUpdateService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final class RunningJob {
        volatile boolean cancelled;
        Future<?> future;

        void cancel() {
            cancelled = true;
            if (future != null) future.cancel(true);
        }
    }

    private final java.util.concurrent.ConcurrentMap<Long, RunningJob> running = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean onStartJob(JobParameters params) {
        long request = params.getExtras().getLong("request_id");
        RunningJob run = new RunningJob();
        running.put(request, run);
        run.future = executor.submit(() -> {
            long now = System.currentTimeMillis();
            WeatherConfig config = WeatherConfig.read(this);
            Location location = config.automatic ? LocationPolicy.recentLocation(this) : null;
            try {
                config.source.provider.update(this, config, location, now,
                        () -> !run.cancelled && !Thread.currentThread().isInterrupted() && config.isCurrent(this));
            } finally {
                if (!run.cancelled && config.isCurrent(this))
                    WeatherRepository.checked(config.source.provider.cache(this), config, now, location);
                running.remove(request, run);
                jobFinished(params, false);
                // Only an enabled AppWidget keeps periodic weather work scheduled.
                WeatherWidgetProvider.refresh(this);
                WeatherRepository.notifyChanged(this);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        RunningJob work = running.remove(params.getExtras().getLong("request_id"));
        if (work != null) work.cancel();
        return true;
    }

    @Override
    public void onDestroy() {
        for (RunningJob work : running.values()) work.cancel();
        running.clear();
        executor.shutdownNow();
        super.onDestroy();
    }
}
