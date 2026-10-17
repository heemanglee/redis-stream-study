# Redis Stream Study

Spring Boot backend only example for learning a Redis Stream based LLM chat flow.

## Prerequisites

- Java 21
- Redis at `localhost:6379`
- `OPENAI_API_KEY` when running the real worker

Start the application with:

```bash
export OPENAI_API_KEY="your-api-key"
./gradlew bootRun
```

The application does not use an RDB and does not set TTLs on Redis keys.

## API flow

### 1. Create a chat room

```bash
curl -X POST http://localhost:8080/api/v1/chat-rooms
```

Response contains only the UUID:

```json
{"id":"450e8400-e29b-41d4-a716-446655440000"}
```

Redis data:

```bash
HGETALL chat_rooms:450e8400-e29b-41d4-a716-446655440000:metadata
SMEMBERS chat_rooms:index
```

### 2. Send a question

```bash
ROOM_ID="450e8400-e29b-41d4-a716-446655440000"
curl -X POST "http://localhost:8080/api/v1/chat-rooms/${ROOM_ID}/messages" \
  -H 'Content-Type: application/json' \
  -d '{"content":"Redis Stream이 뭐야?"}'
```

The backend executes these Redis operations in order:

```text
RPUSH chat_rooms:{room_id}:messages <user-message-json>
XADD chat_rooms:{room_id}:question_events * room_id ... message_id ... content ... role user
```

The response contains the application `message_id` and the Redis-generated `stream_message_id`.

### 3. Inspect the question event

```bash
redis-cli XRANGE "chat_rooms:${ROOM_ID}:question_events" - +
redis-cli XINFO STREAM "chat_rooms:${ROOM_ID}:question_events"
redis-cli XINFO GROUPS "chat_rooms:${ROOM_ID}:question_events"
```

The worker discovers room IDs through `chat_rooms:index`, creates the `llm-workers` Consumer Group from `0-0`, and consumes with:

```text
XGROUP CREATE chat_rooms:{room_id}:question_events llm-workers 0-0
XREADGROUP GROUP llm-workers worker-local COUNT 5 BLOCK 1s STREAMS chat_rooms:{room_id}:question_events >
```

The worker reads up to five events per poll and submits them to a fixed
five-thread executor. Redis adds the delivered entries to the consumer
group's PEL immediately; each processing task stores its assistant message
and then acknowledges its own event with `XACK`. Processing order is not
guaranteed in this learning project.

After the OpenAI response, it executes:

```text
RPUSH chat_rooms:{room_id}:messages <assistant-message-json>
XACK chat_rooms:{room_id}:question_events llm-workers <stream_message_id>
```

`XACK` is executed only after the assistant message is successfully stored.

### 4. Read the conversation

```bash
curl "http://localhost:8080/api/v1/chat-rooms/${ROOM_ID}/messages"
redis-cli --raw LRANGE "chat_rooms:${ROOM_ID}:messages" 0 -1
redis-cli XPENDING "chat_rooms:${ROOM_ID}:question_events" llm-workers
```

### 5. Use the concurrent message web UI

Run the application and open [http://localhost:8080/](http://localhost:8080/).
The page lets you choose one to five messages, enter each message content, and
send them concurrently. It displays the HTTP status, elapsed time,
the question, `message_id`, and Redis `stream_message_id` for each request.
Click a successful result to poll
`GET /api/v1/chat-rooms/{roomId}/question-events/{streamMessageId}/status` and
watch `PUBLISHED → PENDING → ASSISTANT_SAVED → ACKED` until the worker finishes.


## Important log events

The worker and API use SLF4J INFO logs. Search for these event names:

```text
CHAT_ROOM_CREATED
REDIS_MESSAGE_SAVED
REDIS_EVENT_PUBLISHED
REDIS_EVENT_CONSUMED
LLM_REQUEST_STARTED
LLM_RESPONSE_RECEIVED
REDIS_EVENT_ACKNOWLEDGED
MESSAGE_PROCESSING_COMPLETED
MESSAGE_PROCESSING_FAILED
```

Each Redis log includes the command name, key, `room_id`, `message_id`, and when applicable `stream_message_id`, consumer group, and consumer name. Full question and answer content is not logged.

## Java formatting

Java sources use the NAVER Java formatter (tabs displayed at width 4 and a
120-character line width), managed by Spotless 8.10.4 and Eclipse JDT 4.41.
The official formatter profile is stored in
`config/formatter/naver-eclipse-formatter.xml` from
[naver/hackday-conventions-java](https://github.com/naver/hackday-conventions-java/blob/e12658edad4017e2c00ef560704129901770cc2b/rule-config/naver-eclipse-formatter.xml).

Apply formatting:

```bash
./gradlew spotlessApply
```

Check formatting:

```bash
./gradlew spotlessCheck
```

`./gradlew check` also checks formatting alongside the tests.

## Tests

Run unit and integration tests:

```bash
./gradlew test
./gradlew clean test
```

`RedisStreamFlowIntegrationTest` uses the local Redis instance and removes only its UUID-scoped test keys during cleanup.
