# Data Model

## Table: event_logs

### Fields

id
uuid

event_timestamp
datetime

raw_text
text

event_type
varchar

structured_json
json

source
varchar
(default: web)

parser_version
varchar

created_at
datetime

updated_at
datetime

---

## Supported Event Types

SMOKING

FOOD

WEIGHT

EXERCISE

ALCOHOL

MOOD

SLEEP

NOTE

UNKNOWN

---

## Example Record

raw_text

"1 cigarette pee li"

event_type

"SMOKING"

structured_json

{
  "quantity": 1,
  "unit": "cigarette"
}