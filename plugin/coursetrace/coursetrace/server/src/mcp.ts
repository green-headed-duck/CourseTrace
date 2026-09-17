import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { RelayStore, type EventKind, type OwnerType } from "./store.js";

const textResult = (value: unknown) => ({
  content: [{ type: "text" as const, text: JSON.stringify(value) }],
});

const ownerTypeSchema = z.enum(["course", "project"]);
const eventKindSchema = z.enum(["TRANSCRIPT", "QUESTION", "PAIN_POINT", "WRONG_ANSWER", "PROGRESS", "DECISION", "NOTE"]);

export function createMcpServer(store: RelayStore): McpServer {
  const server = new McpServer({ name: "coursetrace", version: "0.1.0" });

  server.registerTool(
    "get_current_class",
    {
      description: "Return the current or next imminent class from the Android timetable snapshot.",
      inputSchema: { at: z.string().datetime().optional() },
    },
    async ({ at }) => {
      const snapshot = await store.snapshot();
      if (!snapshot) return textResult({ match: null, reason: "Android device has not uploaded a schedule snapshot" });
      const now = new Date(at ?? Date.now());
      const candidates = snapshot.upcoming
        .map((item) => ({ item, start: new Date(item.start), end: new Date(item.end) }))
        .filter(({ end }) => end.getTime() >= now.getTime() - 30 * 60_000)
        .sort((a, b) => a.start.getTime() - b.start.getTime());
      const current = candidates.find(({ start, end }) => start.getTime() - 30 * 60_000 <= now.getTime() && end >= now);
      return textResult({ match: current?.item ?? candidates[0]?.item ?? null, confidence: current ? 0.98 : candidates[0] ? 0.82 : 0 });
    },
  );

  server.registerTool(
    "start_class_session",
    {
      description: "Start a course or self-study learning session and queue it for the paired phone.",
      inputSchema: {
        owner_id: z.string().optional(),
        owner_type: ownerTypeSchema.optional(),
        title: z.string().min(1).optional(),
      },
    },
    async ({ owner_id, owner_type, title }) => {
      let ownerId = owner_id;
      let ownerType = owner_type;
      let resolvedTitle = title;
      if (!ownerId || !ownerType || !resolvedTitle) {
        const snapshot = await store.snapshot();
        const now = Date.now();
        const match = snapshot?.upcoming.find((item) => new Date(item.start).getTime() - 30 * 60_000 <= now && new Date(item.end).getTime() >= now);
        ownerId ??= match?.ownerId;
        ownerType ??= match?.ownerType;
        resolvedTitle ??= match?.title;
      }
      if (!ownerId || !ownerType || !resolvedTitle) throw new Error("Course is ambiguous; ask the user to identify it");
      const session = await store.createSession({ ownerId, ownerType: ownerType as OwnerType, title: resolvedTitle });
      await store.enqueue("SESSION_STARTED", session as unknown as Record<string, unknown>);
      return textResult(session);
    },
  );

  server.registerTool(
    "append_learning_event",
    {
      description: "Append a chronological question, pain point, wrong answer, progress marker, decision, or note.",
      inputSchema: {
        session_id: z.string().uuid(),
        kind: eventKindSchema,
        content: z.string().min(1).max(20_000),
        occurred_at: z.string().datetime().optional(),
      },
    },
    async ({ session_id, kind, content, occurred_at }) => {
      if (!await store.session(session_id)) throw new Error("Unknown session");
      const change = await store.enqueue("LEARNING_EVENT", {
        sessionId: session_id,
        kind: kind as EventKind,
        content,
        occurredAt: occurred_at ?? new Date().toISOString(),
        source: "chatgpt",
      });
      return textResult({ saved: true, sequence: change.sequence });
    },
  );

  server.registerTool(
    "append_transcript_chunk",
    {
      description: "Append actual conversation text. The text must not be presented as verbatim if it was reconstructed.",
      inputSchema: {
        session_id: z.string().uuid(),
        content: z.string().min(1).max(100_000),
        occurred_at: z.string().datetime().optional(),
      },
    },
    async ({ session_id, content, occurred_at }) => {
      if (!await store.session(session_id)) throw new Error("Unknown session");
      const change = await store.enqueue("TRANSCRIPT_CHUNK", {
        sessionId: session_id,
        content,
        occurredAt: occurred_at ?? new Date().toISOString(),
        source: "chatgpt",
      });
      return textResult({ saved: true, sequence: change.sequence });
    },
  );

  server.registerTool(
    "finish_class_session",
    {
      description: "Finish a learning session, preserving transcript completeness and unresolved issues.",
      inputSchema: {
        session_id: z.string().uuid(),
        summary: z.string().min(1).max(30_000),
        transcript_complete: z.boolean(),
        unresolved: z.array(z.string()).optional(),
        next_actions: z.array(z.string()).optional(),
      },
    },
    async ({ session_id, summary, transcript_complete, unresolved, next_actions }) => {
      const session = await store.finishSession(session_id, transcript_complete);
      const change = await store.enqueue("SESSION_FINISHED", {
        sessionId: session_id,
        endedAt: session.endedAt,
        summary,
        transcriptComplete: transcript_complete,
        unresolved: unresolved ?? [],
        nextActions: next_actions ?? [],
      });
      return textResult({ saved: true, sequence: change.sequence, transcriptComplete: transcript_complete });
    },
  );

  server.registerTool(
    "query_learning_records",
    {
      description: "Query the limited learning-record snapshot explicitly shared by the paired Android device.",
      inputSchema: {
        owner_id: z.string().optional(),
        from: z.string().datetime().optional(),
        to: z.string().datetime().optional(),
        kinds: z.array(eventKindSchema).optional(),
      },
    },
    async ({ owner_id, from, to, kinds }) => {
      const records = (await store.snapshot())?.records ?? [];
      const filtered = records.filter((record) => {
        if (owner_id && record.ownerId !== owner_id) return false;
        if (from && typeof record.timestamp === "string" && record.timestamp < from) return false;
        if (to && typeof record.timestamp === "string" && record.timestamp > to) return false;
        if (kinds?.length && typeof record.kind === "string" && !kinds.includes(record.kind as EventKind)) return false;
        return true;
      });
      return textResult({ records: filtered.slice(-500), snapshotUpdatedAt: (await store.snapshot())?.updatedAt ?? null });
    },
  );

  server.registerTool(
    "create_timetable_import_draft",
    {
      description: "Queue a preview-only timetable import draft for user confirmation in the Android app.",
      inputSchema: {
        slots: z.array(z.object({
          courseName: z.string().min(1),
          teacher: z.string().default(""),
          dayOfWeek: z.number().int().min(1).max(7),
          startTime: z.string().regex(/^\d{2}:\d{2}$/),
          endTime: z.string().regex(/^\d{2}:\d{2}$/),
          room: z.string().default(""),
          startWeek: z.number().int().min(1),
          endWeek: z.number().int().min(1),
          weekPattern: z.enum(["EVERY", "ODD", "EVEN"]),
          confidence: z.number().min(0).max(1),
          warnings: z.array(z.string()).default([]),
        })).min(1).max(500),
        warnings: z.array(z.string()).optional(),
      },
    },
    async ({ slots, warnings }) => {
      const change = await store.enqueue("TIMETABLE_IMPORT_DRAFT", { slots, warnings: warnings ?? [], source: "CHATGPT_MOBILE" });
      return textResult({ queued: true, sequence: change.sequence, slotCount: slots.length, requiresAndroidConfirmation: true });
    },
  );

  server.registerTool(
    "save_next_material_prediction",
    {
      description: "Save a cautious next-material prediction with evidence and confidence for the Android detail page.",
      inputSchema: {
        owner_id: z.string(),
        title: z.string().min(1),
        reason: z.string().min(1),
        confidence: z.number().min(0).max(1),
        material_ids: z.array(z.string()).default([]),
        source_session_id: z.string().uuid().optional(),
      },
    },
    async (input) => {
      const snapshot = await store.snapshot();
      const known = new Set(snapshot?.materials.map((item) => item.id) ?? []);
      if (input.material_ids.some((id) => !known.has(id))) throw new Error("Prediction references an unknown material ID");
      const change = await store.enqueue("NEXT_MATERIAL_PREDICTION", input as unknown as Record<string, unknown>);
      return textResult({ saved: true, sequence: change.sequence });
    },
  );

  server.registerTool(
    "sync_status",
    { description: "Return paired-device snapshot age and queued-change count.", inputSchema: {} },
    async () => textResult(await store.status()),
  );

  return server;
}
