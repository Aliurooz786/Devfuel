# API Contract

## Create Log

POST

/api/logs

### Request

{
  "message": "1 cigarette pee li"
}

### Response

{
  "success": true,
  "eventType": "SMOKING",
  "timestamp": "2026-08-16T20:00:00"
}

---

## Get Timeline

GET

/api/logs

### Response

[
  {
    "timestamp": "...",
    "eventType": "SMOKING",
    "rawText": "1 cigarette pee li"
  }
]

---

## Search Logs

GET

/api/logs/search?q=cigarette

### Response

List of matching events.