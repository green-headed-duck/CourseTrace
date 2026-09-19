# 节假日数据源

课迹默认每 12 小时读取一次 [`china-holidays.json`](china-holidays.json)。应用内同时包含当年规则，因此首次启动或断网时也能应用。

设置页可以改成任意可信的 HTTPS JSON 地址。自定义源会被自动应用，不再逐条确认，因此客户端只接受最多 1 MB、500 条规则的固定结构：

```json
{
  "schemaVersion": 1,
  "sourceName": "数据源名称",
  "sourcePage": "https://example.com/original-notice",
  "updatedAt": "2026-09-19",
  "rules": [
    {
      "id": "holiday-2026-10-01",
      "date": "2026-10-01",
      "type": "NO_CLASS",
      "title": "国庆节"
    },
    {
      "id": "makeup-2026-10-10",
      "date": "2026-10-10",
      "type": "FOLLOW_DATE",
      "title": "按 10 月 5 日课表补课",
      "sourceDate": "2026-10-05"
    }
  ]
}
```

规则类型：

- `NO_CLASS`：当天停课。
- `WORKDAY`：只标记为调休工作日，不猜测学校补星期几的课程。
- `FOLLOW_DATE`：当天直接使用 `sourceDate` 的课程安排，并按当天时间发送提醒。

同一日期只能有一条规则。只有 `FOLLOW_DATE` 可以带 `sourceDate`。
