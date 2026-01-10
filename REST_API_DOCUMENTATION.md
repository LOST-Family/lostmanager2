# REST API Documentation

## Overview

The LostManager REST API provides programmatic access to Clans, Players, and Users data. The API runs on port **8070** by default (configurable via `REST_API_PORT` environment variable).

**Base URL:** `http://localhost:8070`

All endpoints return JSON responses with proper HTTP status codes.

---

## Endpoints

### 1. Get All Clans

**Endpoint:** `GET /api/clans`

**Description:** Returns a list of all available clans.

**Response:**
```json
[
  {
    "tag": "#2P0LRJP9V",
    "nameDB": "Lost Aethon",
    "nameAPI": "Lost Aethon"
  },
  {
    "tag": "#2YRJP0LV9",
    "nameDB": "Lost Phoenix",
    "nameAPI": "Lost Phoenix"
  }
]
```

**Response Fields:**
- `tag` (string): The unique clan tag
- `nameDB` (string): Clan name from database
- `nameAPI` (string): Clan name from Clash of Clans API

**Status Codes:**
- `200 OK`: Success
- `500 Internal Server Error`: Server error

---

### 2. Get Clan Members

**Endpoint:** `GET /api/clans/{tag}/members`

**Description:** Returns the member list of a specific clan. Each player object includes complete player data so individual lookups are not required.

**Parameters:**
- `tag` (path parameter): The clan tag (e.g., `#2P0LRJP9V`, URL encoded as `%232P0LRJP9V`)

**Example Request:**
```
GET /api/clans/%232P0LRJP9V/members
```

**Response:**
```json
[
  {
    "tag": "#ABC123DEF",
    "userId": "123456789012345678",
    "roleInClan": "LEADER",
    "nameAPI": "PlayerName",
    "clanDB": "#2P0LRJP9V",
    "clanAPI": "#2P0LRJP9V"
  },
  {
    "tag": "#XYZ789GHI",
    "userId": null,
    "roleInClan": "MEMBER",
    "nameAPI": "AnotherPlayer",
    "clanDB": "#2P0LRJP9V",
    "clanAPI": "#2P0LRJP9V"
  }
]
```

**Response Fields (Player Object):**
- `tag` (string): Player's unique tag
- `userId` (string|null): Discord user ID if linked, null otherwise
- `roleInClan` (string): Role in database clan - one of: `LEADER`, `COLEADER`, `ELDER`, `MEMBER`, `NOTINCLAN`
  - **Note:** This is the player's clan role, NOT admin status. Admin status should be checked via the User endpoint.
- `nameAPI` (string): Player name from Clash of Clans API
- `clanDB` (string|null): Clan tag from database
- `clanAPI` (string|null): Clan tag from Clash of Clans API

**Status Codes:**
- `200 OK`: Success
- `400 Bad Request`: Invalid path format
- `404 Not Found`: Clan not found
- `500 Internal Server Error`: Server error

---

### 3. Get Player

**Endpoint:** `GET /api/players/{tag}`

**Description:** Returns detailed information about a specific player.

**Parameters:**
- `tag` (path parameter): The player tag (e.g., `#ABC123DEF`, URL encoded as `%23ABC123DEF`)

**Example Request:**
```
GET /api/players/%23ABC123DEF
```

**Response:**
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

**Response Fields:**
- `tag` (string): Player's unique tag
- `userId` (string|null): Discord user ID if linked
- `roleInClan` (string): Role in database clan (LEADER, COLEADER, ELDER, MEMBER, NOTINCLAN)
- `nameAPI` (string): Player name from API
- `clanDB` (string|null): Clan tag from database
- `clanAPI` (string|null): Clan tag from API

**Status Codes:**
- `200 OK`: Success
- `400 Bad Request`: Invalid path format
- `404 Not Found`: Player not found or not linked
- `500 Internal Server Error`: Server error

---

### 4. Get User

**Endpoint:** `GET /api/users/{userId}`

**Description:** Returns information about a Discord user, including admin status and linked player accounts.

**Parameters:**
- `userId` (path parameter): The Discord user ID (e.g., `123456789012345678`)

**Example Request:**
```
GET /api/users/123456789012345678
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

---

## Configuration

The REST API port can be configured using the `REST_API_PORT` environment variable:

```bash
export REST_API_PORT=8070
```

Default port is **8070**.

---

## Notes

1. **URL Encoding:** Clan and player tags containing `#` must be URL encoded (`#` becomes `%23`).

2. **Performance:** The `/api/clans/{tag}/members` endpoint includes full player data in the response to minimize subsequent API calls.

3. **Admin vs Role:** The `roleInClan` field in Player objects reflects the clan role (Leader, CoLeader, Elder, Member), not admin privileges. To check if a user is an admin, use the User endpoint.

4. **Linked Players:** Only players that have been linked in the database will be returned by the `/api/players/{tag}` endpoint. Unlinked players will return a 404 error.

---

## Examples

### Example 1: Get all clans and their members

```bash
# Get all clans
curl http://localhost:8070/api/clans

# Get members of a specific clan (tag #2P0LRJP9V)
curl http://localhost:8070/api/clans/%232P0LRJP9V/members
```

### Example 2: Get player and user information

```bash
# Get player details
curl http://localhost:8070/api/players/%23ABC123DEF

# Get user details
curl http://localhost:8070/api/users/123456789012345678
```

---

## CORS Support

The API includes CORS (Cross-Origin Resource Sharing) headers to allow access from web applications:
- `Access-Control-Allow-Origin: *`
- `Access-Control-Allow-Methods: GET, OPTIONS`
- `Access-Control-Allow-Headers: Content-Type`

This allows the API to be accessed from browser-based applications on different domains.

---

## Support

For issues or questions about the API, please contact the development team.
