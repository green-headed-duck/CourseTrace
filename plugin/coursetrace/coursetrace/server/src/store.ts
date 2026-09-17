import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";

export type OwnerType = "course" | "project";
export type EventKind = "TRANSCRIPT" | "QUESTION" | "PAIN_POINT" | "WRONG_ANSWER" | "PROGRESS" | "DECISION" | "NOTE";

export interface UpcomingClass {
  ownerId: string;
  ownerType: OwnerType;
  title: string;
  start: string;
  end: string;
  room?: string;
  teacher?: string;
}

export interface DeviceSnapshot {
  updatedAt: string;
  deviceId: string;
  upcoming: UpcomingClass[];
  materials: Array<{ id: string; ownerId: string; title: string; kind: string; week?: number; chapter?: string }>;
  records: Array<Record<string, unknown>>;
}

export interface RelayChange {
  sequence: number;
  id: string;
  type: string;
  createdAt: string;
  payload: Record<string, unknown>;
}

export interface RelaySession {
  id: string;
  ownerId: string;
  ownerType: OwnerType;
  title: string;
  startedAt: string;
  endedAt?: string;
  transcriptComplete?: boolean;
}

interface PersistedState {
  sequence: number;
  snapshot?: DeviceSnapshot;
  changes: RelayChange[];
  sessions: RelaySession[];
  acknowledgedThrough: number;
}

const emptyState = (): PersistedState => ({ sequence: 0, changes: [], sessions: [], acknowledgedThrough: 0 });

export class RelayStore {
  private readonly file: string;
  private operation: Promise<unknown> = Promise.resolve();

  constructor(dataDir: string) {
    this.file = path.join(dataDir, "relay-state.json");
  }

  private async read(): Promise<PersistedState> {
    try {
      return JSON.parse(await readFile(this.file, "utf8")) as PersistedState;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "ENOENT") return emptyState();
      throw error;
    }
  }

  private async write(state: PersistedState): Promise<void> {
    await mkdir(path.dirname(this.file), { recursive: true });
    const temp = `${this.file}.tmp`;
    await writeFile(temp, JSON.stringify(state, null, 2), { encoding: "utf8", mode: 0o600 });
    await rename(temp, this.file);
  }

  private mutate<T>(action: (state: PersistedState) => T | Promise<T>): Promise<T> {
    const result = this.operation.then(async () => {
      const state = await this.read();
      const value = await action(state);
      await this.write(state);
      return value;
    });
    this.operation = result.catch(() => undefined);
    return result;
  }

  async snapshot(): Promise<DeviceSnapshot | undefined> {
    await this.operation;
    return (await this.read()).snapshot;
  }

  async replaceSnapshot(snapshot: DeviceSnapshot): Promise<void> {
    await this.mutate((state) => { state.snapshot = snapshot; });
  }

  async enqueue(type: string, payload: Record<string, unknown>): Promise<RelayChange> {
    return this.mutate((state) => {
      const change: RelayChange = {
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

  async pending(after = 0): Promise<RelayChange[]> {
    await this.operation;
    return (await this.read()).changes.filter((change) => change.sequence > after);
  }

  async acknowledge(through: number): Promise<void> {
    await this.mutate((state) => {
      state.acknowledgedThrough = Math.max(state.acknowledgedThrough, through);
      state.changes = state.changes.filter((change) => change.sequence > state.acknowledgedThrough);
    });
  }

  async createSession(input: Omit<RelaySession, "id" | "startedAt">): Promise<RelaySession> {
    return this.mutate((state) => {
      const session: RelaySession = { ...input, id: crypto.randomUUID(), startedAt: new Date().toISOString() };
      state.sessions.push(session);
      return session;
    });
  }

  async finishSession(id: string, transcriptComplete: boolean): Promise<RelaySession> {
    return this.mutate((state) => {
      const session = state.sessions.find((candidate) => candidate.id === id);
      if (!session) throw new Error("Unknown session");
      session.endedAt = new Date().toISOString();
      session.transcriptComplete = transcriptComplete;
      return session;
    });
  }

  async session(id: string): Promise<RelaySession | undefined> {
    await this.operation;
    return (await this.read()).sessions.find((candidate) => candidate.id === id);
  }

  async status(): Promise<Record<string, unknown>> {
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
