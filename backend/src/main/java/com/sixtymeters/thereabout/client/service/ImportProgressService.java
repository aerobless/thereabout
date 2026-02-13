package com.sixtymeters.thereabout.client.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared service for tracking the progress of file imports across all import types.
 * Progress is represented as a percentage (0 = idle, 1-99 = in progress).
 * Resetting to 0 signals that the import has completed.
 */
@Service
public class ImportProgressService {

    private final AtomicInteger progress = new AtomicInteger(0);

    public int getProgress() {
        return progress.get();
    }

    public void setProgress(int value) {
        progress.set(value);
    }

    public void reset() {
        progress.set(0);
    }
}
