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
