import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
const emptyState = () => ({ sequence: 0, changes: [], sessions: [], acknowledgedThrough: 0 });
export class RelayStore {
    file;
    operation = Promise.resolve();
    constructor(dataDir) {
        this.file = path.join(dataDir, "relay-state.json");
    }
    async read() {
        try {
            return JSON.parse(await readFile(this.file, "utf8"));
        }
        catch (error) {
            if (error.code === "ENOENT")
                return emptyState();
            throw error;
        }
    }
    async write(state) {
        await mkdir(path.dirname(this.file), { recursive: true });
        const temp = `${this.file}.tmp`;
        await writeFile(temp, JSON.stringify(state, null, 2), { encoding: "utf8", mode: 0o600 });
        await rename(temp, this.file);
    }
    mutate(action) {
        const result = this.operation.then(async () => {
            const state = await this.read();
            const value = await action(state);
            await this.write(state);
            return value;
        });
        this.operation = result.catch(() => undefined);
        return result;
    }
    async snapshot() {
        await this.operation;
        return (await this.read()).snapshot;
    }
    async replaceSnapshot(snapshot) {
        await this.mutate((state) => { state.snapshot = snapshot; });
    }
    async enqueue(type, payload) {
        return this.mutate((state) => {
            const change = {
                sequence: ++state.sequence,
                id: crypto.randomUUID(),
                type,
                createdAt: new Date().toISOString(),
                payload,
            };
            state.changes.push(change);
            return change;
        });
    }
    async pending(after = 0) {
        await this.operation;
        return (await this.read()).changes.filter((change) => change.sequence > after);
    }
    async acknowledge(through) {
        await this.mutate((state) => {
            state.acknowledgedThrough = Math.max(state.acknowledgedThrough, through);
            state.changes = state.changes.filter((change) => change.sequence > state.acknowledgedThrough);
        });
    }
    async createSession(input) {
        return this.mutate((state) => {
            const session = { ...input, id: crypto.randomUUID(), startedAt: new Date().toISOString() };
            state.sessions.push(session);
            return session;
        });
    }
    async finishSession(id, transcriptComplete) {
        return this.mutate((state) => {
            const session = state.sessions.find((candidate) => candidate.id === id);
            if (!session)
                throw new Error("Unknown session");
            session.endedAt = new Date().toISOString();
            session.transcriptComplete = transcriptComplete;
            return session;
        });
    }
    async session(id) {
        await this.operation;
        return (await this.read()).sessions.find((candidate) => candidate.id === id);
    }
    async status() {
        await this.operation;
        const state = await this.read();
        return {
            pairedDevice: state.snapshot?.deviceId ?? null,
            snapshotUpdatedAt: state.snapshot?.updatedAt ?? null,
            pendingChanges: state.changes.length,
            latestSequence: state.sequence,
        };
    }
}
