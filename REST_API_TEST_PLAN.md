# Manual Test Plan for REST API

## Prerequisites
- Start the bot application
- Ensure REST_API_PORT is set to 8070 (default)
- Have a tool like `curl` or Postman ready
- Know at least one clan tag from your database
- Know at least one player tag from your database
- Know at least one Discord user ID with linked accounts

## Test Cases

### Test 1: Get All Clans
```bash
curl -X GET "http://localhost:8070/api/clans"
```

**Expected Result:**
- HTTP 200 OK
- JSON array of clan objects
- Each clan should have: `tag`, `nameDB`, `nameAPI`

**Example:**
```json
[
  {
    "tag": "#2P0LRJP9V",
    "nameDB": "Lost Aethon",
    "nameAPI": "Lost Aethon"
  }
]
```

---

### Test 2: Get Clan Members
Replace `{CLAN_TAG}` with an actual clan tag (URL encoded, e.g., `%232P0LRJP9V` for `#2P0LRJP9V`):

```bash
curl -X GET "http://localhost:8070/api/clans/%232P0LRJP9V/members"
```

**Expected Result:**
- HTTP 200 OK
- JSON array of player objects
- Each player should have:
  - `tag`: Player tag
  - `userId`: Discord user ID or null
  - `roleInClan`: LEADER, COLEADER, ELDER, MEMBER, or NOTINCLAN
  - `nameAPI`: Player name from API
  - `clanDB`: Clan tag from DB
  - `clanAPI`: Clan tag from API

**Example:**
```json
[
  {
    "tag": "#ABC123DEF",
    "userId": "123456789012345678",
    "roleInClan": "LEADER",
    "nameAPI": "PlayerName",
    "clanDB": "#2P0LRJP9V",
    "clanAPI": "#2P0LRJP9V"
  }
]
```

---

### Test 3: Get Clan Members - Invalid Clan
```bash
curl -X GET "http://localhost:8070/api/clans/%23INVALID/members"
```

**Expected Result:**
- HTTP 404 Not Found
- JSON error message:
```json
{
  "error": "Clan not found"
}
```

---

### Test 4: Get Clan Members - Invalid Path
```bash
curl -X GET "http://localhost:8070/api/clans/%232P0LRJP9V/invalid"
```

**Expected Result:**
- HTTP 400 Bad Request
- JSON error message

---

### Test 5: Get Player
Replace `{PLAYER_TAG}` with an actual player tag (URL encoded):

```bash
curl -X GET "http://localhost:8070/api/players/%23ABC123DEF"
```

**Expected Result:**
- HTTP 200 OK
- JSON player object with all fields

**Example:**
```json
{
  "tag": "#ABC123DEF",
  "userId": "123456789012345678",
  "roleInClan": "LEADER",
  "nameAPI": "PlayerName",
  "clanDB": "#2P0LRJP9V",
  "clanAPI": "#2P0LRJP9V"
}
```

---

### Test 6: Get Player - Not Found
```bash
curl -X GET "http://localhost:8070/api/players/%23INVALID"
```

**Expected Result:**
- HTTP 404 Not Found
- JSON error message:
```json
{
  "error": "Player not found"
}
```

---

### Test 7: Get User
Replace `{USER_ID}` with an actual Discord user ID:

```bash
curl -X GET "http://localhost:8070/api/users/123456789012345678"
```

**Expected Result:**
- HTTP 200 OK
- JSON user object

**Example:**
```json
{
  "userId": "123456789012345678",
  "isAdmin": true,
  "linkedPlayers": [
    "#ABC123DEF",
    "#XYZ789GHI"
  ]
}
```

---

### Test 8: Get User - Not Found
```bash
curl -X GET "http://localhost:8070/api/users/999999999999999999"
```

**Expected Result:**
- HTTP 404 Not Found
- JSON error message:
```json
{
  "error": "User not found or has no linked accounts"
}
```

---

### Test 9: Method Not Allowed
Test with POST on a GET-only endpoint:

```bash
curl -X POST "http://localhost:8070/api/clans"
```

**Expected Result:**
- HTTP 405 Method Not Allowed
- JSON error message:
```json
{
  "error": "Method Not Allowed"
}
```

---

## Additional Validation

### Check Server Startup
When starting the bot, look for this message in logs:
```
REST API Server started on port 8070
```

### Check Server Shutdown
When stopping the bot, look for this message in logs:
```
REST API Server stopped
```

### Verify Content-Type
All responses should have `Content-Type: application/json; charset=UTF-8` header.

### Verify CORS (if needed)
If you need to access the API from a browser application, you may need to add CORS headers.

---

## Test Results Template

| Test Case | Status | Notes |
|-----------|--------|-------|
| Test 1: Get All Clans | ☐ Pass ☐ Fail | |
| Test 2: Get Clan Members | ☐ Pass ☐ Fail | |
| Test 3: Invalid Clan | ☐ Pass ☐ Fail | |
| Test 4: Invalid Path | ☐ Pass ☐ Fail | |
| Test 5: Get Player | ☐ Pass ☐ Fail | |
| Test 6: Invalid Player | ☐ Pass ☐ Fail | |
| Test 7: Get User | ☐ Pass ☐ Fail | |
| Test 8: Invalid User | ☐ Pass ☐ Fail | |
| Test 9: Method Not Allowed | ☐ Pass ☐ Fail | |
| Server Startup Message | ☐ Pass ☐ Fail | |
| Server Shutdown Message | ☐ Pass ☐ Fail | |
