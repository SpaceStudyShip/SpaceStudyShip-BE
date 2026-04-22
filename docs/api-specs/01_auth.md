# 01. Auth (인증 + 프로필)

> Base Path: `/api/auth`
> 엔드포인트: 6개
> 공통 규칙: [00_common.md](./00_common.md) 참조

---

## 엔드포인트 요약

| # | Method | Path | 설명 | 인증 |
|---|--------|------|------|------|
| 1 | POST | `/api/auth/login` | 소셜 로그인 | X |
| 2 | POST | `/api/auth/logout` | 로그아웃 | O |
| 3 | POST | `/api/auth/reissue` | 토큰 재발급 | X |
| 4 | DELETE | `/api/auth/withdraw` | 회원 탈퇴 | O |
| 5 | GET | `/api/auth/check-nickname` | 닉네임 중복 확인 | O |
| 6 | PATCH | `/api/auth/nickname` | 닉네임 변경 | O |

---

## 1. 소셜 로그인

`POST /api/auth/login`

Firebase ID Token을 백엔드에 전송하여 JWT를 발급받습니다.
해당 유저가 DB에 없으면 자동으로 회원가입 처리됩니다.

### 인증: 불필요

### Request Body

| 필드 | 타입 | 필수 | 설명 | 예시 |
|------|------|------|------|------|
| `socialPlatform` | String | O | 소셜 로그인 플랫폼 | `"GOOGLE"`, `"APPLE"` |
| `idToken` | String | O | Firebase에서 발급받은 ID Token | `"eyJhbG..."` |
| `fcmToken` | String | O | Firebase Cloud Messaging 디바이스 토큰 | `"dK3mL..."` |
| `deviceType` | String | O | 디바이스 OS 타입 | `"IOS"`, `"ANDROID"` |
| `deviceId` | String | O | 디바이스 고유 식별자 (UUID) | `"550e8400-e29b..."` |

```json
{
  "socialPlatform": "GOOGLE",
  "idToken": "eyJhbGciOiJSUzI1NiIs...",
  "fcmToken": "dK3mL9xRTp2...",
  "deviceType": "IOS",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Response

**200 OK** - 기존 회원 로그인 성공
**201 Created** - 신규 회원 가입 및 로그인 성공

```json
{
  "userId": 1,
  "nickname": "민첩한괴도5308",
  "tokens": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
  },
  "isNewUser": false
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | Long | 서버에서 부여한 유저 ID |
| `nickname` | String | 닉네임 (신규 가입 시 서버에서 랜덤 생성: "형용사+명사+숫자4자리") |
| `tokens.accessToken` | String | JWT Access Token |
| `tokens.refreshToken` | String | JWT Refresh Token |
| `isNewUser` | Boolean | `true`: 신규 가입 (닉네임 설정 화면으로 이동), `false`: 기존 회원 |

### Error

| Status | code | 상황 |
|--------|------|------|
| 400 | `INVALID_ID_TOKEN` | Firebase ID Token 검증 실패 |
| 400 | `UNSUPPORTED_PLATFORM` | socialPlatform이 GOOGLE/APPLE이 아닌 경우 |

### 서버 처리 로직

```
1. Firebase ID Token 검증 (Firebase Admin SDK)
2. Token에서 이메일, 이름 추출
3. DB에서 해당 소셜 계정으로 유저 조회
   3-a. 기존 유저: JWT 발급, 디바이스 정보 업데이트, 200 응답
   3-b. 신규 유저: 유저 생성 (랜덤 닉네임), JWT 발급, 201 응답
4. FCM 토큰 + 디바이스 정보 저장/업데이트
5. Refresh Token을 DB에 저장 (디바이스별)
```

---

## 2. 로그아웃

`POST /api/auth/logout`

서버에서 Refresh Token 및 디바이스 정보를 삭제합니다.
클라이언트는 응답 후 로컬의 Access/Refresh Token을 삭제합니다.

### 인증: 필요

### Request Body

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `refreshToken` | String | O | 현재 디바이스의 Refresh Token |

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

### Response

**204 No Content** (응답 본문 없음)

### 서버 처리 로직

```
1. Access Token에서 userId 추출
2. 해당 userId + refreshToken 조합으로 DB에서 세션 삭제
3. 해당 디바이스의 FCM 토큰 삭제
```

---

## 3. 토큰 재발급

`POST /api/auth/reissue`

만료된 Access Token을 Refresh Token으로 재발급합니다.
Refresh Token도 함께 갱신됩니다 (Refresh Token Rotation).

### 인증: 불필요 (공개 API)

### Request Body

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `refreshToken` | String | O | 현재 보유한 Refresh Token |

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

### Response

**200 OK**

```json
{
  "tokens": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...(new)",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...(new)"
  }
}
```

### Error

| Status | code | 상황 | 클라이언트 처리 |
|--------|------|------|--------------|
| 401 | `INVALID_REFRESH_TOKEN` | Refresh Token이 만료되었거나 DB에 존재하지 않음 | 로그아웃 처리 → 로그인 화면 이동 |

### 서버 처리 로직

```
1. Refresh Token 유효성 검증 (서명 + 만료)
2. DB에서 해당 Refresh Token 조회
   2-a. 존재: 새 Access + Refresh Token 발급, DB의 Refresh Token 갱신
   2-b. 없음: 401 응답 (탈취 의심, 해당 유저의 모든 세션 무효화 권장)
3. 이전 Refresh Token 무효화
```

---

## 4. 회원 탈퇴

`DELETE /api/auth/withdraw`

계정 및 모든 관련 데이터를 삭제합니다. 이 작업은 되돌릴 수 없습니다.

### 인증: 필요

### Request Body: 없음

### Response

**204 No Content** (응답 본문 없음)

### 서버 처리 로직

```
1. Access Token에서 userId 추출
2. 해당 유저의 모든 데이터 삭제:
   - 유저 정보
   - Todo + 카테고리
   - 타이머 세션
   - 연료 + 거래 내역
   - 탐험 진행 상태
   - 배지 해금 상태
   - 친구 관계 + 친구 요청
   - Refresh Token + 디바이스 정보
3. Firebase 연동 해제 (선택적)
```

### 삭제 순서 주의사항

외래 키 제약 조건을 고려하여 자식 테이블부터 삭제해야 합니다.
또는 `ON DELETE CASCADE`로 설정하여 유저 삭제 시 자동 삭제되도록 합니다.

---

## 5. 닉네임 중복 확인

`GET /api/auth/check-nickname`

닉네임 변경 전 사용 가능 여부를 확인합니다.

### 인증: 필요

### Query Parameters

| 파라미터 | 타입 | 필수 | 제약조건 | 설명 |
|---------|------|------|---------|------|
| `nickname` | String | O | 2~10자, 한글/영문/숫자만 허용 | 확인할 닉네임 |

```
GET /api/auth/check-nickname?nickname=우주탐험가
```

### Response

**200 OK**

```json
{
  "available": true
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `available` | Boolean | `true`: 사용 가능, `false`: 이미 사용 중 |

### Error

| Status | code | 상황 |
|--------|------|------|
| 400 | `INVALID_NICKNAME_FORMAT` | 길이 미달/초과, 허용되지 않은 문자 포함 |

### 닉네임 규칙

- 길이: 2~10자
- 허용 문자: 한글, 영문 대소문자, 숫자
- 금지: 공백, 특수문자, 이모지
- 금지어 필터링: 욕설, 부적절한 단어 (서버에서 관리)

---

## 6. 닉네임 변경

`PATCH /api/auth/nickname`

사용자의 닉네임을 변경합니다.

### 인증: 필요

### Request Body

| 필드 | 타입 | 필수 | 제약조건 | 설명 |
|------|------|------|---------|------|
| `nickname` | String | O | 2~10자, 한글/영문/숫자만 허용 | 새 닉네임 |

```json
{
  "nickname": "우주탐험가"
}
```

### Response

**200 OK**

```json
{
  "nickname": "우주탐험가"
}
```

### Error

| Status | code | 상황 |
|--------|------|------|
| 400 | `INVALID_NICKNAME_FORMAT` | 닉네임 형식 오류 |
| 409 | `NICKNAME_DUPLICATED` | 이미 사용 중인 닉네임 |

### 서버 처리 로직

```
1. 닉네임 형식 검증 (길이, 허용 문자, 금지어)
2. 중복 확인
3. DB 업데이트
4. 친구 목록/랭킹 등에서 변경된 닉네임 자동 반영 (정규화된 DB 구조)
```

---

## DB 테이블 참고

### users

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | 유저 ID |
| `nickname` | VARCHAR(10) UNIQUE | 닉네임 |
| `social_platform` | VARCHAR(10) | GOOGLE / APPLE |
| `social_id` | VARCHAR(255) | Firebase UID |
| `email` | VARCHAR(255) | 이메일 (nullable) |
| `created_at` | TIMESTAMP | 가입일 |
| `updated_at` | TIMESTAMP | 수정일 |

### user_devices

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT (PK) | |
| `user_id` | BIGINT (FK → users) | 유저 ID |
| `device_id` | VARCHAR(255) | 디바이스 UUID |
| `device_type` | VARCHAR(10) | IOS / ANDROID |
| `fcm_token` | VARCHAR(255) | FCM 토큰 |
| `refresh_token` | VARCHAR(512) | Refresh Token |
| `last_login_at` | TIMESTAMP | 마지막 로그인 |
