# REST API Documentation

## Overview

The LostManager REST API provides programmatic access to Clans, Players, and Users data. The API runs on port **8070** by default (configurable via `REST_API_PORT` environment variable).

**Base URL:** `http://localhost:8070`

All endpoints return JSON responses with proper HTTP status codes.

## Authentication

The API requires authentication via an API token set through the `REST_API_TOKEN` environment variable.

### How to Authenticate

Include the API token in your requests using one of these methods:

**Method 1: Authorization Header (Bearer Token)**
```bash
curl -H "Authorization: Bearer YOUR_API_TOKEN" http://localhost:8070/api/clans
```

**Method 2: Authorization Header (Direct Token)**
```bash
curl -H "Authorization: YOUR_API_TOKEN" http://localhost:8070/api/clans
```

**Method 3: X-API-Token Header**
```bash
curl -H "X-API-Token: YOUR_API_TOKEN" http://localhost:8070/api/clans
```

### Configuration

Set the API token as an environment variable:

```bash
export REST_API_TOKEN=your_secure_token_here
```

**Important:** If `REST_API_TOKEN` is not set, the API will be accessible without authentication (not recommended for production).

---

## Endpoints

### 1. Get All Clans

**Endpoint:** `GET /api/clans`

**Description:** Returns a list of all available clans with detailed information including settings and event times.

**Authentication:** Required

**Response:**
```json
[
  {
    "tag": "#2P0LRJP9V",
    "index": 1,
    "nameDB": "Lost Aethon",
    "badgeUrl": "https://example.com/badge.png",
    "description": "Clan description",
    "maxKickpoints": 10,
    "minSeasonWins": 4,
    "kickpointsExpireAfterDays": 30,
    "kickpointReasons": [
      {
        "name": "Missed War Attack",
        "clanTag": "#2P0LRJP9V",
        "amount": 2
      }
    ],
    "cwEndTimeMillis": 1704996000000,
    "raidEndTimeMillis": 1704996000000,
    "cwlDayEndTimeMillis": 1704996000000,
    "cgEndTimeMillis": 1704996000000
  }
]
```

**Response Fields:**
- `tag` (string): The unique clan tag
- `index` (number): Clan index in database
- `nameDB` (string): Clan name from database
- `badgeUrl` (string): URL to clan badge image
- `description` (string): Clan description
- `maxKickpoints` (number): Maximum allowed kickpoints before action
- `minSeasonWins` (number): Minimum season wins required
- `kickpointsExpireAfterDays` (number): Days after which kickpoints expire
- `kickpointReasons` (array): List of kickpoint reasons configured for this clan
- `cwEndTimeMillis` (number): Clan War end time in milliseconds (epoch)
- `raidEndTimeMillis` (number): Raid end time in milliseconds (epoch)
- `cwlDayEndTimeMillis` (number): CWL day end time in milliseconds (epoch)
- `cgEndTimeMillis` (number): Clan Games end time in milliseconds (epoch)

**Status Codes:**
- `200 OK`: Success
- `401 Unauthorized`: Invalid or missing API token
- `500 Internal Server Error`: Server error

---

### 2. Get Clan Info

**Endpoint:** `GET /api/clans/{tag}`

**Description:** Returns detailed information about a specific clan.

**Authentication:** Required

**Parameters:**
- `tag` (path parameter): The clan tag (e.g., `#2P0LRJP9V`, URL encoded as `%232P0LRJP9V`)

**Example Request:**
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8070/api/clans/%232P0LRJP9V
```

**Response:** Same as individual clan object in "Get All Clans" endpoint.

**Status Codes:**
- `200 OK`: Success
- `400 Bad Request`: Invalid path format
- `401 Unauthorized`: Invalid or missing API token
- `404 Not Found`: Clan not found
- `500 Internal Server Error`: Server error

---

### 3. Get Clan Members

**Endpoint:** `GET /api/clans/{tag}/members`

**Description:** Returns the member list of a specific clan. Each player object includes complete player data including kickpoints, raid stats, and war data.

**Authentication:** Required

**Parameters:**
- `tag` (path parameter): The clan tag (e.g., `#2P0LRJP9V`, URL encoded as `%232P0LRJP9V`)

**Example Request:**
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8070/api/clans/%232P0LRJP9V/members
```

**Response:**
```json
[
  {
    "tag": "#ABC123DEF",
    "nameDB": "PlayerName",
    "userId": "123456789012345678",
    "roleInClan": "LEADER",
    "clanDB": {
      "tag": "#2P0LRJP9V",
      "nameDB": "Lost Aethon",
      "index": 1
    },
    "totalKickpoints": 5,
    "activeKickpoints": [
      {
        "id": 123,
        "description": "Missed war attack",
        "amount": 2,
        "date": "2024-01-01T00:00:00Z",
        "givenDate": "2024-01-01T00:00:00Z",
        "expirationDate": "2024-02-01T00:00:00Z",
        "givenByUserId": "987654321098765432"
      }
    ],
    "currentRaidAttacks": 5,
    "currentRaidGoldLooted": 50000,
    "currentRaidAttackLimit": 6,
    "currentRaidBonusAttackLimit": 0,
    "warPreference": true,
    "warMapPosition": 1
  }
]
```

**Response Fields (Player Object):**
- `tag` (string): Player's unique tag
- `nameDB` (string): Player name from database
- `userId` (string|null): Discord user ID if linked, null otherwise
- `roleInClan` (string): Role in database clan - one of: `LEADER`, `COLEADER`, `ELDER`, `MEMBER`, `NOTINCLAN`
- `clanDB` (object): Clan information from database
- `totalKickpoints` (number): Total active kickpoints for player
- `activeKickpoints` (array): List of active kickpoint records
  - `id` (number): Kickpoint record ID
  - `description` (string): Reason for kickpoint
  - `amount` (number): Kickpoint value
  - `date` (string): Date the kickpoint was for (ISO 8601)
  - `givenDate` (string): Date the kickpoint was given (ISO 8601)
  - `expirationDate` (string): Date the kickpoint expires (ISO 8601)
  - `givenByUserId` (string): Discord ID of user who gave the kickpoint
- `currentRaidAttacks` (number): Number of raid attacks done
- `currentRaidGoldLooted` (number): Gold looted in current raid
- `currentRaidAttackLimit` (number): Raid attack limit
- `currentRaidBonusAttackLimit` (number): Bonus raid attack limit
- `warPreference` (boolean): War preference setting
- `warMapPosition` (number): Position on war map

**Status Codes:**
- `200 OK`: Success
- `400 Bad Request`: Invalid path format
- `401 Unauthorized`: Invalid or missing API token
- `404 Not Found`: Clan not found
- `500 Internal Server Error`: Server error

---

### 4. Get Clan Kickpoint Reasons

**Endpoint:** `GET /api/clans/{tag}/kickpoint-reasons`

**Description:** Returns the list of kickpoint reasons configured for a specific clan.

**Authentication:** Required

**Parameters:**
- `tag` (path parameter): The clan tag

**Example Request:**
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8070/api/clans/%232P0LRJP9V/kickpoint-reasons
```

**Response:**
```json
[
  {
    "name": "Missed War Attack",
    "clanTag": "#2P0LRJP9V",
    "amount": 2
  },
  {
    "name": "Missed Raid Attack",
    "clanTag": "#2P0LRJP9V",
    "amount": 1
  }
]
```

**Status Codes:**
- `200 OK`: Success
- `401 Unauthorized`: Invalid or missing API token
- `404 Not Found`: Clan not found
- `500 Internal Server Error`: Server error

---

### 5. Get Clan War Members

**Endpoint:** `GET /api/clans/{tag}/war-members`

**Description:** Returns the list of players participating in the current clan war.

**Authentication:** Required

**Parameters:**
- `tag` (path parameter): The clan tag

**Example Request:**
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8070/api/clans/%232P0LRJP9V/war-members
```

**Response:**
```json
[
  "#ABC123DEF",
  "#XYZ789GHI"
]
```

**Status Codes:**
- `200 OK`: Success
- `401 Unauthorized`: Invalid or missing API token
- `404 Not Found`: Clan not found
- `500 Internal Server Error`: Server error

---

### 6. Get Raid Members

**Endpoint:** `GET /api/clans/{tag}/raid-members`

**Description:** Returns the list of players participating in the current raid weekend.

**Authentication:** Required

**Parameters:**
- `tag` (path parameter): The clan tag

**Example Request:**
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8070/api/clans/%232P0LRJP9V/raid-members
```

**Response:**
```json
[
  "#ABC123DEF",
  "#XYZ789GHI"
]
```

**Status Codes:**
- `200 OK`: Success
- `401 Unauthorized`: Invalid or missing API token
- `404 Not Found`: Clan not found
- `500 Internal Server Error`: Server error

---

### 7. Get CWL Members

**Endpoint:** `GET /api/clans/{tag}/cwl-members`

**Description:** Returns the list of players participating in Clan War League.

**Authentication:** Required

**Parameters:**
- `tag` (path parameter): The clan tag

**Example Request:**
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8070/api/clans/%232P0LRJP9V/cwl-members
```

**Response:**
```json
[
  "#ABC123DEF",
  "#XYZ789GHI"
]
```

**Status Codes:**
- `200 OK`: Success
- `401 Unauthorized`: Invalid or missing API token
- `404 Not Found`: Clan not found
- `500 Internal Server Error`: Server error

---

### 8. Get Player

**Endpoint:** `GET /api/players/{tag}`

**Description:** Returns detailed information about a specific player including kickpoints, raid stats, and war data.

**Authentication:** Required

**Parameters:**
- `tag` (path parameter): The player tag (e.g., `#ABC123DEF`, URL encoded as `%23ABC123DEF`)

**Example Request:**
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8070/api/players/%23ABC123DEF
```

**Response:**
```json
{
  "tag": "#ABC123DEF",
  "nameDB": "PlayerName",
  "userId": "123456789012345678",
  "roleInClan": "LEADER",
  "clanDB": {
    "tag": "#2P0LRJP9V",
    "nameDB": "Lost Aethon",
    "index": 1
  },
  "totalKickpoints": 5,
  "activeKickpoints": [...],
  "currentRaidAttacks": 5,
  "currentRaidGoldLooted": 50000,
  "currentRaidAttackLimit": 6,
  "currentRaidBonusAttackLimit": 0,
  "warPreference": true,
  "warMapPosition": 1
}
```

**Response Fields:** Same as player object in "Get Clan Members" endpoint.

**Status Codes:**
- `200 OK`: Success
- `400 Bad Request`: Invalid path format
- `401 Unauthorized`: Invalid or missing API token
- `404 Not Found`: Player not found or not linked
- `500 Internal Server Error`: Server error

---

### 9. Get User

**Endpoint:** `GET /api/users/{userId}`

**Description:** Returns information about a Discord user, including admin status and linked player accounts.

**Authentication:** Required

**Parameters:**
- `userId` (path parameter): The Discord user ID (e.g., `123456789012345678`)

**Example Request:**
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8070/api/users/123456789012345678
```

**Response:**
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

**Response Fields:**
- `userId` (string): Discord user ID
- `isAdmin` (boolean): Whether the user has admin privileges
- `linkedPlayers` (array of strings): List of player tags linked to this user

**Status Codes:**
- `200 OK`: Success
- `400 Bad Request`: Invalid path format
- `401 Unauthorized`: Invalid or missing API token
- `404 Not Found`: User not found or has no linked accounts
- `500 Internal Server Error`: Server error

---

## Error Responses

All error responses follow this format:

```json
{
  "error": "Error message describing what went wrong"
}
```

**Common Error Responses:**

**401 Unauthorized:**
```json
{
  "error": "Unauthorized - Invalid or missing API token"
}
```

**404 Not Found:**
```json
{
  "error": "Clan not found"
}
```

**405 Method Not Allowed:**
```json
{
  "error": "Method Not Allowed"
}
```

---

## Configuration

### Environment Variables

**REST_API_PORT** - Port for REST API (default: 8070)
```bash
export REST_API_PORT=8070
```

**REST_API_TOKEN** - API token for authentication (required for production)
```bash
export REST_API_TOKEN=your_secure_token_here
```

Default port is **8070**.

---

## Notes

1. **Authentication:** All endpoints require authentication via API token. Include the token in the `Authorization` or `X-API-Token` header.

2. **URL Encoding:** Clan and player tags containing `#` must be URL encoded (`#` becomes `%23`).

3. **Performance:** 
   - All endpoints now use **database calls only** (no external API calls), improving response times significantly.
   - The `/api/clans/{tag}/members` endpoint includes full player data including kickpoints, raid stats, and war data to minimize subsequent API calls.

4. **Admin vs Role:** The `roleInClan` field in Player objects reflects the clan role (Leader, CoLeader, Elder, Member), not admin privileges. To check if a user is an admin, use the User endpoint.

5. **Linked Players:** Only players that have been linked in the database will be returned by the `/api/players/{tag}` endpoint. Unlinked players will return a 404 error.

6. **Data Freshness:** All data is retrieved from the database. For real-time data from Clash of Clans API, the database should be updated separately using the bot's normal data refresh mechanisms.

---

## Examples

### Example 1: Get all clans with authentication

```bash
# Using Bearer token
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8070/api/clans

# Using X-API-Token header
curl -H "X-API-Token: YOUR_TOKEN" http://localhost:8070/api/clans
```

### Example 2: Get clan members and kickpoint reasons

```bash
# Get members of a specific clan (tag #2P0LRJP9V)
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8070/api/clans/%232P0LRJP9V/members

# Get kickpoint reasons for a clan
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8070/api/clans/%232P0LRJP9V/kickpoint-reasons
```

### Example 3: Get player and user information

```bash
# Get player details
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8070/api/players/%23ABC123DEF

# Get user details
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8070/api/users/123456789012345678
```

### Example 4: Get event participants

```bash
# Get clan war participants
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8070/api/clans/%232P0LRJP9V/war-members

# Get raid participants
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8070/api/clans/%232P0LRJP9V/raid-members

# Get CWL participants
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8070/api/clans/%232P0LRJP9V/cwl-members
```

---

## CORS Support

The API includes CORS (Cross-Origin Resource Sharing) headers to allow access from web applications:
- `Access-Control-Allow-Origin: *`
- `Access-Control-Allow-Methods: GET, OPTIONS`
- `Access-Control-Allow-Headers: Content-Type, Authorization, X-API-Token`

This allows the API to be accessed from browser-based applications on different domains. The API supports OPTIONS preflight requests for CORS.

---

## Support

For issues or questions about the API, please contact the development team.
