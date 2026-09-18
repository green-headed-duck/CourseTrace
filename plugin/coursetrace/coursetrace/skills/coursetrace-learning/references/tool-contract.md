# CourseTrace Tool Contract

The Android app accepts a shared timetable draft as one JSON object:

```json
{
  "slots": [
    {
      "courseName": "数字电路",
      "teacher": "",
      "dayOfWeek": 2,
      "startTime": "08:50",
      "endTime": "10:25",
      "room": "A301",
      "startWeek": 1,
      "endWeek": 16,
      "weekPattern": "EVERY",
      "confidence": 0.95,
      "warnings": []
    }
  ],
  "warnings": []
}
```

`weekPattern` is one of `EVERY`, `ODD`, or `EVEN`. Times always include two-digit hours and minutes. Unknown text is an empty string, never a guessed value.

Learning events use the kinds `TRANSCRIPT`, `QUESTION`, `PAIN_POINT`, `WRONG_ANSWER`, `PROGRESS`, `DECISION`, and `NOTE`. Every event has a server sequence number so the phone can merge it idempotently.

When CourseTrace tools are unavailable, return one JSON object that can be pasted into the Android app's dedicated **导入 ChatGPT 课堂 JSON** dialog. Do not wrap it in explanatory prose:

```json
{
  "summary": "本节课的人类可读摘要",
  "events": [
    {
      "kind": "QUESTION",
      "content": "为什么异或不能直接当作普通或？",
      "occurredAt": "2026-09-18T10:12:00+08:00"
    },
    {
      "kind": "PROGRESS",
      "content": "完成半加器到全加器的推导"
    }
  ],
  "rawTranscript": "用户与 ChatGPT 的课堂聊天原文",
  "transcriptComplete": false
}
```

`summary` is for later reading, `events` becomes the chronological timeline, and `rawTranscript` contains the ordinary chat transcript. The user should paste this whole object into the dedicated JSON importer—not into the manual event, summary, or raw-transcript fields.

The expected tools are:

- `get_current_class(at?)`
- `start_class_session(owner_id?, owner_type?, title?)`
- `append_learning_event(session_id, kind, content, occurred_at?)`
- `append_transcript_chunk(session_id, content, occurred_at?)`
- `finish_class_session(session_id, summary, transcript_complete, unresolved?, next_actions?)`
- `query_learning_records(owner_id?, from?, to?, kinds?)`
- `create_timetable_import_draft(slots, warnings?)`
- `sync_status()`

Writes must be queued idempotently for the paired Android device. A timetable draft is always preview-only until the Android user confirms it.
