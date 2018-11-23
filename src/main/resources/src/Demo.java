package com.xy.pmd.processor;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.processor.AbstractPMDProcessor;
import net.sourceforge.pmd.processor.PmdRunnable;
import net.sourceforge.pmd.processor.PmdThreadFactory;
import net.sourceforge.pmd.renderers.Renderer;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

public class Demo extends AbstractPMDProcessor {
    private final ExecutorService executor;
    private final CompletionService<Report> completionService;
    private List<Report> reports = new LinkedList<>();

    private long submittedTasks = 0L;

    public ResultProcessor(final PMDConfiguration configuration) {
        super(configuration);

        executor = Executors.newFixedThreadPool(configuration.getThreads(), new PmdThreadFactory());
        completionService = new ExecutorCompletionService<>(executor);
    }

    @Override
    protected void runAnalysis(PmdRunnable runnable) {
        completionService.submit(runnable);
        submittedTasks++;
    }

    @Override
    protected void collectReports(List<Renderer> renderers) {
        try {
            for (int i = 0; i < submittedTasks; i++) {
                final Report report = completionService.take().get();
                reports.add(report);
            }
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException ee) {
            final Throwable t = ee.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new IllegalStateException("PmdRunnable exception", t);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    public List<Report> getReports() {
        return reports;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
    }
}