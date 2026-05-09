---
name: api-documentation
description: |
  Generates API documentation in a standardized format to share with frontend team members.
  Use this skill whenever the user asks to document an API endpoint, create API docs, write API specification, or generate frontend-facing API documentation.
  Also trigger when the user says things like "API 문서 만들어줘", "API 문서 작성해줘", "프론트에 공유할 API 문서", or any variation requesting API documentation.
---

# API Documentation Skill

Generate a well-structured API document for frontend team members based on the controller code, use case, or endpoint description provided by the user.

## What to do

1. Read and understand the API endpoint — inspect the controller, request/response DTOs, service, and any relevant exception handling.
2. Fill in all sections of the document using the exact format defined below.
3. Cover every edge case in the Response Examples — success cases AND every known exception.
4. Create Api document in following directory: /Users/raewookang/Captone/MZTK-BE/docs/api_docs.
5. Find directory that this API endpoint belongs, then create API document inside of that directory. If the directory doesn't exist, make new directory and create document inside of that directory. 

---

## Document Format

Use this exact structure. Do not omit any section or table.

---

### **Description**

Write 3–5 sentences covering:
- The **purpose** of this API (what business action it performs)
- A **summary of the request** (what the caller must send)
- A **summary of the response** (what the caller receives on success and failure)

---

### **Request Header**

Always write a table, even if there are no required headers (write "없음" in the row or add a note below the table).

| 필드 명 | 타입 | 필수 여부 | 설명 |
| --- | --- | --- | --- |

Common headers to check:
- `Authorization: Bearer <token>` — required for authenticated endpoints
- `Content-Type: application/json` — required when there is a request body

---

### **Request Body**

If there is no request body, write "Request Body 없음" instead of the table.

If there is a request body, write a table with one row per field:

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |

Column guidance:
- **key**: JSON field name (use dot notation for nested fields, e.g. `address.city`)
- **설명**: what this field represents in Korean or English
- **value 타입**: `String`, `Integer`, `Long`, `Boolean`, `List<T>`, `Object`, etc.
- **옵션**: `필수` (required) or `선택` (optional)
- **Nullable**: `Y` or `N`
- **예시**: a realistic sample value

**Example**

Write one JSON example per meaningful combination of inputs. If the request has optional fields, write at least two cases — one with all fields, one with only required fields.

**CASE 1: ...**

```json

```

**CASE 2: ...** *(if applicable)*

```json

```

---

### **Response**

Write a table describing every field in the response body. Use dot notation for nested fields.

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
| --- | --- | --- | --- | --- | --- |

Column guidance matches the Request Body table above.

**Example**

Write one JSON block per distinct outcome — success AND every exception case you can identify from the code (e.g., 400, 401, 403, 404, 409, 500).

Success responses follow the `ApiResponse` wrapper:
```json
{
  "status": "SUCCESS",
  "message": "...",
  "data": { ... }
}
```

Failure responses follow this shape:
```json
{
  "status": "FAIL",
  "code": "ERROR_CODE",
  "message": "Human-readable error message"
}
```

**CASE 1: (200 OK)**

```json

```

**CASE 2: (4xx / error description)**

```json
{
  "status": "FAIL",
  "code": "...",
  "message": "..."
}
```

*(Add more CASE blocks for each additional exception)*

---

## How to gather information

Before writing the document, read the relevant source files:

1. **Controller** (`api/`) — HTTP method, path, request/response types, annotations (`@RequestHeader`, `@RequestBody`, `@PathVariable`, etc.), and security annotations.
2. **Request/Response DTOs** (`api/` or `application/dto/`) — field names, types, validation annotations (`@NotNull`, `@NotBlank`, `@Size`, etc.) which determine `옵션` and `Nullable`.
3. **Use case / Service** (`application/service/`) — business rules and what exceptions can be thrown.
4. **Exception types** (`global/error/` or module-specific) — `ErrorCode` values to fill in `code` fields in failure examples.
5. **Domain model** (`domain/model/`) — if needed to understand state transitions or validation.

If the user only provides an endpoint path or description without code, ask which files to read before proceeding.

---

## Quality checklist before outputting

- [ ] Description explains purpose, request summary, and response summary clearly
- [ ] Request Header table is filled in (not empty)
- [ ] Request Body table covers every field including nested ones
- [ ] Request Examples cover all meaningful combinations of optional fields
- [ ] Response table covers every returned field including nested ones
- [ ] Response Examples cover the success case AND every exception that can realistically occur
- [ ] All error `code` values match actual `ErrorCode` enum values in the codebase
- [ ] The format matches the template exactly (section headings, table columns, JSON code blocks)
