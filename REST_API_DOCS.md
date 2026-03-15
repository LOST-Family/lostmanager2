# lostmanager REST API Documentation

> **Bot**: Clash of Clans Discord manager (`lostmanager`)  
> **Base URL**: `http://localhost:8070`  
> **Auth env var**: `REST_API_TOKEN`

---

## Authentication

All endpoints (except `OPTIONS`) require an API token when `REST_API_TOKEN` is set in the environment.

**Supply the token via either header:**

| Header | Example |
|---|---|
| `Authorization` | `Bearer your_token_here` |
| `X-API-Token` | `your_token_here` |

If the environment variable is not set, all requests are allowed without a token.

---

## Common Response Shapes

### Error response
```json
{
  "error": "Description of what went wrong"
}
```

### Management endpoint error response
```json
{
  "success": false,
  "error": "Description of what went wrong"
}
```

### Management endpoint success response
```json
{
  "success": true,
  "message": "Human-readable confirmation"
}
```

---

## Data Types

### ClanDTO
```json
{
  "tag": "#2PP",
  "index": 1,
  "nameDB": "LOST",
  "badgeUrl": "https://api-assets.clashofclans.com/badges/...",
  "description": "Main LOST clan",
  "maxKickpoints": 10,
  "minSeasonWins": 8,
  "kickpointsExpireAfterDays": 30,
  "kickpointReasons": [
    {
      "name": "Missed War",
      "clanTag": "#2PP",
      "amount": 2
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `tag` | string | Clan tag (e.g. `#2PP`) |
| `index` | number \| null | Display sort order |
| `nameDB` | string \| null | Clan name stored in DB |
| `badgeUrl` | string \| null | Clan badge image URL |
| `description` | string \| null | Clan description from DB |
| `maxKickpoints` | number \| null | Kickpoints before kick (configured via `clanconfig`) |
| `minSeasonWins` | number \| null | Minimum required season wins |
| `kickpointsExpireAfterDays` | number \| null | Days until a kickpoint expires |
| `kickpointReasons` | KickpointReasonDTO[] \| null | Configured kickpoint reason presets |

### KickpointReasonDTO
```json
{
  "name": "Missed War",
  "clanTag": "#2PP",
  "amount": 2
}
```

### KickpointDTO
```json
{
  "id": 42,
  "description": "Missed War",
  "amount": 2,
  "date": "2024-01-15T00:00:00+01:00",
  "givenDate": "2024-01-15T00:00:00+01:00",
  "expirationDate": "2024-02-14T00:00:00+01:00",
  "givenByUserId": "987654321012345678"
}
```

| Field | Type | Description |
|---|---|---|
| `id` | number \| null | Unique kickpoint ID |
| `description` | string \| null | Reason text |
| `amount` | number \| null | Kickpoint value |
| `date` | string \| null | Reference date (ISO-8601) |
| `givenDate` | string \| null | Date the record was created (ISO-8601) |
| `expirationDate` | string \| null | Date the kickpoint expires (ISO-8601) |
| `givenByUserId` | string \| null | Discord user ID of the person who added it |

### PlayerDTO
```json
{
  "tag": "#ABC123",
  "nameDB": "PlayerName",
  "userId": "123456789012345678",
  "roleInClan": "COLEADER",
  "isHidden": false,
  "clanDB": { "...ClanDTO..." },
  "totalKickpoints": 3,
  "activeKickpoints": [
    { "...KickpointDTO..." }
  ],
  "monthlyWins": 5,
  "monthlyWinsHasWarning": false
}
```

| Field | Type | Description |
|---|---|---|
| `tag` | string | Player tag |
| `nameDB` | string \| null | Player name stored in DB |
| `userId` | string \| null | Linked Discord user ID (`null` if unlinked) |
| `roleInClan` | string | `LEADER`, `COLEADER`, `ELDER`, `MEMBER`, or `NOTINCLAN` |
| `isHidden` | boolean \| null | True if the player is a hidden co-leader |
| `clanDB` | ClanDTO \| null | The clan the player belongs to in DB |
| `totalKickpoints` | number \| null | Sum of all active kickpoint amounts |
| `activeKickpoints` | KickpointDTO[] \| null | Individual non-expired kickpoints |
| `monthlyWins` | number \| null | Current-month season wins |
| `monthlyWinsHasWarning` | boolean \| null | True if wins are below the clan minimum |

> **Note on `roleInClan`**: The DB stores CoC roles as `"leader"`, `"coLeader"`, `"hiddencoleader"`, `"admin"` (elder), `"member"`. These are mapped to the enum names returned here.

### UserDTO
```json
{
  "isAdmin": false,
  "linkedPlayers": ["#ABC123", "#DEF456"],
  "highestRole": "COLEADER",
  "nickname": "Username"
}
```

| Field | Type | Description |
|---|---|---|
| `isAdmin` | boolean | True if the user has Discord admin role |
| `linkedPlayers` | string[] | List of linked CoC player tags |
| `highestRole` | string \| null | Highest role across all clans: `ADMIN`, `LEADER`, `COLEADER`, `ELDER`, `MEMBER`, or `NOTINCLAN` |
| `nickname` | string \| null | Discord server nickname |

### SideclanDTO
```json
{
  "clan_tag": "#XYZABC",
  "name": "LOST Sideclan",
  "belongs_to": "#2PP",
  "index": 2
}
```

| Field | Type | Description |
|---|---|---|
| `clan_tag` | string | Clan tag of the side clan |
| `name` | string \| null | Side clan display name |
| `belongs_to` | string \| null | Tag of the main clan this side clan is associated with |
| `index` | number \| null | Sort order |

---

## Read Endpoints

### GET /api/clans

Returns all registered clans.

**Request**: No body required.

**Response** `200 OK`:
```json
[
  {
    "tag": "#2PP",
    "index": 1,
    "nameDB": "LOST",
    "badgeUrl": "https://...",
    "description": "Main clan",
    "maxKickpoints": 10,
    "minSeasonWins": 8,
    "kickpointsExpireAfterDays": 30,
    "kickpointReasons": [
      { "name": "Missed War", "clanTag": "#2PP", "amount": 2 }
    ]
  }
]
```

---

### GET /api/clans/{tag}

Returns a single clan by tag.

**Path param**: `tag` — e.g. `%232PP` (URL-encoded `#2PP`)

**Response** `200 OK` — single ClanDTO  
**Response** `404 Not Found`:
```json
{ "error": "Clan not found" }
```

**Example request**:
```
GET /api/clans/%232PP
```

**Example response**:
```json
{
  "tag": "#2PP",
  "index": 1,
  "nameDB": "LOST",
  "badgeUrl": "https://api-assets.clashofclans.com/badges/200/example.png",
  "description": "Main LOST clan",
  "maxKickpoints": 10,
  "minSeasonWins": 8,
  "kickpointsExpireAfterDays": 30,
  "kickpointReasons": [
    { "name": "Missed War", "clanTag": "#2PP", "amount": 2 },
    { "name": "No Attack", "clanTag": "#2PP", "amount": 1 }
  ]
}
```

---

### GET /api/clans/{tag}/members

Returns all members of a clan.

**Path param**: `tag` — clan tag

**Response** `200 OK` — array of PlayerDTO  
**Response** `404 Not Found`:
```json
{ "error": "Clan not found" }
```

---

### GET /api/clans/{tag}/kickpoint-reasons

Returns all kickpoint reason presets for a clan.

**Path param**: `tag` — clan tag

**Response** `200 OK`:
```json
[
  { "name": "Missed War",     "clanTag": "#2PP", "amount": 2 },
  { "name": "No Attack",      "clanTag": "#2PP", "amount": 1 },
  { "name": "Missed Raid",    "clanTag": "#2PP", "amount": 2 }
]
```

**Response** `404 Not Found`:
```json
{ "error": "Clan not found" }
```

---

### GET /api/clans/{tag}/war-members

Returns the CoC player tags of all members currently tracked in regular war for the given clan.

**Path param**: `tag` — clan tag

**Response** `200 OK`:
```json
["#ABC123", "#DEF456", "#GHI789"]
```

---

### GET /api/clans/{tag}/raid-members

Returns the CoC player tags of all members currently tracked in clan capital raids for the given clan.

**Response** `200 OK`:
```json
["#ABC123", "#DEF456"]
```

---

### GET /api/clans/{tag}/cwl-members

Returns the CoC player tags of all members currently tracked in CWL (Clan War League) for the given clan.

**Response** `200 OK`:
```json
["#ABC123", "#DEF456", "#GHI789", "#JKL012"]
```

---

### GET /api/sideclans

Returns all registered side clans.

**Response** `200 OK`:
```json
[
  {
    "clan_tag": "#XYZABC",
    "name": "LOST Sideclan",
    "belongs_to": "#2PP",
    "index": 2
  }
]
```

---

### GET /api/players/{tag}

Returns a single player by CoC tag.

**Path param**: `tag` — player tag (URL-encode `#` as `%23`)

**Response** `200 OK` — PlayerDTO  
**Response** `404 Not Found`:
```json
{ "error": "Player not found" }
```

**Example request**:
```
GET /api/players/%23ABC123
```

**Example response**:
```json
{
  "tag": "#ABC123",
  "nameDB": "SomeName",
  "userId": "123456789012345678",
  "roleInClan": "ELDER",
  "isHidden": false,
  "clanDB": {
    "tag": "#2PP",
    "index": 1,
    "nameDB": "LOST",
    "badgeUrl": "https://...",
    "description": "Main LOST clan",
    "maxKickpoints": 10,
    "minSeasonWins": 8,
    "kickpointsExpireAfterDays": 30,
    "kickpointReasons": []
  },
  "totalKickpoints": 2,
  "activeKickpoints": [
    {
      "id": 42,
      "description": "Missed War",
      "amount": 2,
      "date": "2024-01-15T00:00:00+01:00",
      "givenDate": "2024-01-15T10:30:00+01:00",
      "expirationDate": "2024-02-14T00:00:00+01:00",
      "givenByUserId": "987654321012345678"
    }
  ],
  "monthlyWins": 5,
  "monthlyWinsHasWarning": false
}
```

---

### GET /api/users/{userId}

Returns data about a Discord user.

**Path param**: `userId` — Discord user ID (18-digit snowflake)

**Response** `200 OK` — UserDTO  
**Response** `404 Not Found`:
```json
{ "error": "User not found" }
```

**Example request**:
```
GET /api/users/123456789012345678
```

**Example response**:
```json
{
  "isAdmin": false,
  "linkedPlayers": ["#ABC123", "#DEF456"],
  "highestRole": "COLEADER",
  "nickname": "SomeUser"
}
```

---

### GET /api/guild

Returns basic Discord guild information.

**Response** `200 OK`:
```json
{
  "membercount": 420
}
```

---

## Management Endpoints

All management endpoints:
- Use `POST` method
- Require a JSON body with `Content-Type: application/json`
- Require valid authentication
- Always include `"discordUserId"` — the Discord user ID of the caller, used to verify permissions

### Permission levels (in order of hierarchy)

| Level | Description |
|---|---|
| **admin** | User has the Discord server admin role |
| **leader** | User has a player with role `leader` in the target clan |
| **coleader or higher** | User has a player with role `coLeader`, `hiddencoleader`, `leader`, or is admin — in the specified clan or any clan depending on the endpoint |

---

### POST /api/manage/members/add

Adds a linked player to a clan in the database and assigns Discord roles.

**Required permission**: Co-leader or higher in the target clan.  
**Extra permission**: Leader or admin required to assign `leader`, `coLeader`, or `hiddencoleader` roles.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#ABC123",
  "clanTag": "#2PP",
  "role": "elder"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `playerTag` | string | ✓ | CoC tag of the player to add |
| `clanTag` | string | ✓ | Tag of the destination clan |
| `role` | string | ✓ | One of: `leader`, `coLeader`, `hiddencoleader`, `admin`, `member` |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Player added to clan",
  "roleChanges": [
    { "type": "added", "roleId": "111222333444555666", "userId": "123456789012345678" }
  ]
}
```

`roleChanges` lists Discord roles that were assigned or removed. An empty array means no role changes were needed.

**Error responses**:

| Status | Condition |
|---|---|
| `400` | Missing fields \| invalid role \| player already in a clan |
| `403` | Insufficient permissions |
| `404` | Clan not found \| player not linked |

---

### POST /api/manage/members/edit

Changes a member's role within their current clan.

**Required permission**: Co-leader or higher in the member's clan.  
**Extra permission**: Leader or admin required to assign/change `leader`, `coLeader`, or `hiddencoleader` roles.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#ABC123",
  "role": "coLeader"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `playerTag` | string | ✓ | Player tag |
| `role` | string | ✓ | New role: `leader`, `coLeader`, `hiddencoleader`, `admin`, `member` |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Member role updated",
  "roleChanges": []
}
```

`roleChanges` may contain elder Discord role additions/removals.

---

### POST /api/manage/members/remove

Removes a player from their clan in the database and manages Discord roles (removes member/elder roles, adds ex-member role).

**Required permission**: Co-leader or higher in the member's clan.  
**Extra permission**: Leader or admin required to remove a `leader` or `coLeader`.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#ABC123"
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Member removed from clan",
  "roleChanges": [
    { "type": "removed", "roleId": "111222333444555666", "userId": "123456789012345678" },
    { "type": "added",   "roleId": "999888777666555444", "userId": "123456789012345678" }
  ]
}
```

---

### POST /api/manage/kickpoints/add

Adds a kickpoint entry for a player.

**Required permission**: Co-leader or higher in the player's clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#ABC123",
  "reason": "Missed War",
  "amount": 2,
  "date": "15.01.2024"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `playerTag` | string | ✓ | Player tag |
| `reason` | string | ✓ | Description/reason for the kickpoint |
| `amount` | number | ✓ | Kickpoint value |
| `date` | string | – | Date in `dd.MM.yyyy` format. Defaults to today if omitted. |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint added",
  "kickpointId": 42,
  "warnings": [
    "Player is currently signed off",
    "Player has reached maximum kickpoints"
  ]
}
```

`warnings` is only present when there are warnings. Possible warnings:
- `"Player is currently signed off"` — player has an active sign-off
- `"Kickpoint is already expired based on the given date"` — the provided date plus expiry is in the past
- `"Player has reached maximum kickpoints"` — player is at or above the clan's configured maximum

**Error responses**:

| Status | Condition |
|---|---|
| `400` | Missing fields \| invalid date format \| clan config not set |
| `403` | Insufficient permissions |
| `404` | Player not in a clan |

---

### POST /api/manage/kickpoints/edit

Edits an existing kickpoint entry.

**Required permission**: Co-leader or higher in the player's clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "id": 42,
  "reason": "Updated Reason",
  "amount": 3,
  "date": "16.01.2024"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `id` | number | ✓ | Kickpoint ID |
| `reason` | string | ✓ | New description |
| `amount` | number | ✓ | New kickpoint value |
| `date` | string | ✓ | New date in `dd.MM.yyyy` format |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint updated"
}
```

---

### POST /api/manage/kickpoints/remove

Deletes a kickpoint entry.

**Required permission**: Co-leader or higher in the player's clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "id": 42
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint removed"
}
```

**Error** `404 Not Found` if the kickpoint ID doesn't exist.

---

### POST /api/manage/clanconfig

Sets or updates the kickpoint configuration for a clan.

**Required permission**: Co-leader or higher in the specified clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "clanTag": "#2PP",
  "maxKickpoints": 10,
  "kickpointsExpireAfterDays": 30,
  "minSeasonWins": 8
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `clanTag` | string | ✓ | Clan tag to configure |
| `maxKickpoints` | number | ✓ | Number of kickpoints before a member should be kicked |
| `kickpointsExpireAfterDays` | number | ✓ | Days until kickpoints expire |
| `minSeasonWins` | number | ✓ | Minimum required season wins |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Clan config updated"
}
```

---

### POST /api/manage/kickpoint-reasons/add

Adds a new kickpoint reason preset for a clan.

**Required permission**: Co-leader or higher in the specified clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "clanTag": "#2PP",
  "reason": "Missed War",
  "amount": 2,
  "index": 1
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `clanTag` | string | ✓ | Clan tag |
| `reason` | string | ✓ | Reason name (must be unique per clan) |
| `amount` | number | ✓ | Default kickpoint value |
| `index` | number | – | Display sort order. Auto-assigned (max+1) if omitted. |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint reason added",
  "index": 1
}
```

**Error** `400` if the reason name already exists for that clan.

---

### POST /api/manage/kickpoint-reasons/edit

Updates an existing kickpoint reason preset.

**Required permission**: Co-leader or higher in the specified clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "clanTag": "#2PP",
  "reason": "Missed War",
  "amount": 3,
  "index": 2
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `clanTag` | string | ✓ | Clan tag |
| `reason` | string | ✓ | Existing reason name to edit |
| `amount` | number | ✓ | New kickpoint value |
| `index` | number | – | New sort order (optional) |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint reason updated"
}
```

---

### POST /api/manage/kickpoint-reasons/remove

Removes a kickpoint reason preset.

**Required permission**: Co-leader or higher in the specified clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "clanTag": "#2PP",
  "reason": "Missed War"
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint reason removed"
}
```

**Error** `404` if the reason doesn't exist.

---

### POST /api/manage/links/link

Links a CoC player to a Discord user (first-time link).

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#ABC123",
  "targetUserId": "987654321098765432"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `playerTag` | string | ✓ | CoC player tag to link. `#` is prepended if missing; `O` is replaced with `0`. |
| `targetUserId` | string | ✓ | Discord user ID to link the player to |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Player linked to user"
}
```

**Error** `400` if the player is already linked — use `relink` instead.  
**Error** `404` if the player doesn't exist in the CoC API.

---

### POST /api/manage/links/relink

Changes the Discord user a CoC player is linked to.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#ABC123",
  "targetUserId": "111222333444555666"
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Player relinked to user"
}
```

**Error** `400` if the player is not yet linked — use `link` instead.

---

### POST /api/manage/links/unlink

Removes a player's link (deletes from DB). The player must not be in any clan.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#ABC123"
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Player unlinked"
}
```

**Error** `400` if the player is still in a clan (remove from clan first).  
**Error** `404` if the player is not linked.

---

### POST /api/manage/restart

Initiates a graceful bot restart. Waits for any active verification tasks to finish (up to 6 minutes) before calling `System.exit(0)`.

**Required permission**: Admin only.

**Request body**:
```json
{
  "discordUserId": "123456789012345678"
}
```

**Response** `200 OK` (returned before the process exits):
```json
{
  "success": true,
  "message": "Bot restart initiated"
}
```

---

## HTTP Status Code Summary

| Code | Meaning |
|---|---|
| `200` | Success |
| `204` | CORS preflight (OPTIONS) |
| `400` | Bad request (missing/invalid fields, business rule violation) |
| `401` | Missing or invalid API token |
| `403` | Insufficient permissions |
| `404` | Resource not found |
| `405` | Wrong HTTP method |
| `500` | Internal server error |
