package com.sixtymeters.thereabout.client.service;

import org.springframework.stereotype.Service;

/** One application-wide import. Terminal results survive polling until the next import or restart. */
@Service
public class ImportProgressService {
    public enum State { IDLE, IN_PROGRESS, SUCCEEDED, FAILED }
    public record Snapshot(State status, int progress, String error) {}
    private Snapshot snapshot = new Snapshot(State.IDLE, 0, null);

    public synchronized Snapshot snapshot() { return snapshot; }
    public synchronized int getProgress() { return snapshot.progress(); }
    public synchronized boolean begin() {
        if (snapshot.status() == State.IN_PROGRESS) return false;
        snapshot = new Snapshot(State.IN_PROGRESS, 0, null);
        return true;
    }
    public synchronized void setProgress(int value) {
        if (snapshot.status() == State.IN_PROGRESS) {
            snapshot = new Snapshot(State.IN_PROGRESS, Math.max(0, Math.min(value, 100)), null);
        }
    }
    public synchronized void succeed() { snapshot = new Snapshot(State.SUCCEEDED, 100, null); }
    public synchronized void fail() {
        snapshot = new Snapshot(State.FAILED, snapshot.progress(),
                "Import failed. Some records may already have been saved. Check the server log before retrying.");
    }
}
