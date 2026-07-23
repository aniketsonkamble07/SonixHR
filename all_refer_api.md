# SonixHR Subscription & Plan API Reference

This document catalogs the subscription, billing, and plan management APIs for the **SonixHR** multi-tenant SaaS platform. 

## Postman Collection Variables
```json
{
    "baseUrl": "http://localhost:8081",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "adminAccessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tenantId": 9,
    "planId": 3,
    "subscriptionId": 9,
    "planCode": "PREMIUM",
    "featureCode": "SHIFT_MANAGEMENT"
}
```

---

## Endpoint Summary

### Tenant Subscription APIs
| # | Method | Endpoint | Description |
|---|---|---|---|
| 1 | `GET` | `/api/tenant/subscriptions/current` | Get current subscription |
| 2 | `GET` | `/api/tenant/subscriptions/status` | Get subscription status |
| 3 | `GET` | `/api/tenant/subscriptions/validate` | Validate subscription |
| 4 | `GET` | `/api/tenant/subscriptions/check-renewal` | Check renewal eligibility |
| 5 | `GET` | `/api/platform/subscription-plans/public` | Get public plans (Public) |
| 6 | `GET` | `/api/platform/subscription-plans/{id}` | Get plan by ID |
| 7 | `GET` | `/api/platform/subscription-plans/code/{code}` | Get plan by code |
| 8 | `POST` | `/api/tenant/subscriptions/activate` | Activate new subscription |
| 9 | `POST` | `/api/tenant/subscriptions/reactivate` | Reactivate expired subscription |
| 10 | `POST` | `/api/tenant/subscriptions/renew` | Renew subscription |
| 11 | `POST` | `/api/tenant/subscriptions/upgrade` | Upgrade subscription |
| 12 | `POST` | `/api/tenant/subscriptions/cancel` | Cancel subscription |
| 13 | `GET` | `/api/tenant/subscriptions/history` | Get subscription history |
| 14 | `POST` | `/api/tenant/subscriptions/sync-status` | Sync tenant status |

### Admin Subscription Management APIs
| # | Method | Endpoint | Description |
|---|---|---|---|
| 15 | `GET` | `/api/admin/subscription-plans` | Get all plans |
| 16 | `POST` | `/api/admin/subscription-plans` | Create plan |
| 17 | `PUT` | `/api/admin/subscription-plans/{id}` | Update plan |
| 18 | `DELETE` | `/api/admin/subscription-plans/{id}` | Delete plan (Soft Delete) |
| 19 | `PATCH` | `/api/admin/subscription-plans/{id}/restore` | Restore plan |
| 20 | `PATCH` | `/api/admin/subscription-plans/{id}/toggle-active` | Toggle plan active |
| 21 | `GET` | `/api/admin/subscription-plans/{id}/history` | Get plan history |
| 22 | `GET` | `/api/admin/subscription-plans/history` | Get all plan history |
| 23 | `GET` | `/api/admin/subscription-plans/history/search` | Search plan operations |
| 24 | `GET` | `/api/admin/subscription-plans/active` | Get active plans |
| 25 | `GET` | `/api/admin/subscription-plans/deleted` | Get deleted plans |
| 26 | `GET` | `/api/admin/subscription-plans/statistics` | Get plan statistics |
| 27 | `POST` | `/api/admin/subscription-plans/{id}/features` | Add feature to plan |
| 28 | `DELETE` | `/api/admin/subscription-plans/{id}/features/{code}` | Remove feature |
| 29 | `POST` | `/api/admin/subscription-plans/bulk` | Bulk create plans |
| 30 | `DELETE` | `/api/admin/subscription-plans/bulk` | Bulk delete plans |
| 31 | `GET` | `/api/admin/subscription-plans/validate/{code}` | Validate plan code |
| 32 | `POST` | `/api/admin/tenants/{id}/restore` | Restore tenant |
| 33 | `GET` | `/api/admin/tenants/{id}/restore-history` | Get restore history |
| 34 | `GET` | `/api/admin/tenants/{id}/subscription-history` | Get tenant subscription history |

---

## Detailed API Documentation

### 1. Get Current Subscription
Retrieves details of the currently active subscription for the tenant.

- **Method**: `GET`
- **URL**: `/api/tenant/subscriptions/current`
- **Headers**:
  - `Authorization: Bearer <accessToken>`

#### Response Example (200 OK):
```json
{
    "id": 9,
    "planType": "Basic Monthly",
    "planName": "Basic Monthly",
    "planStatus": "ACTIVE",
    "maxEmployees": 10,
    "startedAt": "2026-07-21T20:29:00.53977",
    "endsAt": "2026-08-21T20:29:00.53977",
    "amount": 29.99,
    "currency": "USD",
    "isActive": true,
    "createdAt": "2026-07-21T20:29:00.548464",
    "billingPeriodStart": "2026-07-21T20:29:00.53977",
    "billingPeriodEnd": "2026-08-21T20:29:00.53977",
    "gracePeriodEnd": null,
    "cancellationReason": null,
    "cancelledAtEndOfPeriod": false,
    "activeSubscription": true,
    "expired": false
}
```

---

### 2. Get Subscription Status
Retrieves current subscription status details including days remaining.

- **Method**: `GET`
- **URL**: `/api/tenant/subscriptions/status`
- **Headers**:
  - `Authorization: Bearer <accessToken>`

#### Response Example (200 OK):
```json
{
    "subscriptionId": 9,
    "planName": "Basic Monthly",
    "planStatus": "ACTIVE",
    "isActive": true,
    "isExpired": false,
    "startDate": "2026-07-21T20:29:00.53977",
    "endDate": "2026-08-21T20:29:00.53977",
    "billingPeriodStart": "2026-07-21T20:29:00.53977",
    "billingPeriodEnd": "2026-08-21T20:29:00.53977",
    "daysUntilExpiry": 30
}
```

---

### 3. Validate Subscription
Validates whether the subscription is active.

- **Method**: `GET`
- **URL**: `/api/tenant/subscriptions/validate`
- **Headers**:
  - `Authorization: Bearer <accessToken>`

#### Response Example (200 OK):
```json
{
    "valid": true,
    "status": "ACTIVE",
    "daysUntilExpiry": 30,
    "message": "Subscription is active"
}
```

---

### 4. Check Renewal Eligibility
Checks if the tenant's subscription is eligible for renewal.

- **Method**: `GET`
- **URL**: `/api/tenant/subscriptions/check-renewal`
- **Headers**:
  - `Authorization: Bearer <accessToken>`

#### Response Example (200 OK):
```json
{
    "canRenew": true,
    "message": "Your subscription is active and can be renewed.",
    "currentStatus": "ACTIVE",
    "daysUntilExpiry": 30,
    "hasActiveSubscription": true
}
```

---

### 5. Get Available Plans (Public)
Retrieves subscription plans that are publicly available. No authentication required.

- **Method**: `GET`
- **URL**: `/api/platform/subscription-plans/public`

#### Response Example (200 OK):
```json
[
    {
        "id": 1,
        "code": "FREE",
        "name": "Free Plan",
        "description": "Basic free plan with limited features",
        "price": 0.00,
        "currency": "USD",
        "validityMonths": 1,
        "maxUsers": null,
        "maxEmployees": 5,
        "isActive": true,
        "isPublic": true,
        "displayOrder": 1,
        "isCustom": false,
        "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT"],
        "createdAt": "2026-01-01T00:00:00",
        "updatedAt": "2026-01-01T00:00:00",
        "deletedAt": null
    },
    {
        "id": 2,
        "code": "BASIC",
        "name": "Basic Plan",
        "description": "Essential features for small businesses",
        "price": 29.00,
        "currency": "USD",
        "validityMonths": 1,
        "maxEmployees": 25,
        "isActive": true,
        "isPublic": true,
        "displayOrder": 2,
        "isCustom": false,
        "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT", "BASIC_ATTENDANCE"]
    },
    {
        "id": 3,
        "code": "PREMIUM",
        "name": "Premium Plan",
        "description": "Full feature set for medium businesses",
        "price": 79.00,
        "currency": "USD",
        "validityMonths": 1,
        "maxEmployees": 100,
        "isActive": true,
        "isPublic": true,
        "displayOrder": 3,
        "isCustom": false,
        "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT", "ADVANCED_ATTENDANCE", "PAYROLL", "API_ACCESS"]
    },
    {
        "id": 4,
        "code": "ENTERPRISE",
        "name": "Enterprise Plan",
        "description": "Complete solution for large organizations",
        "price": 199.00,
        "currency": "USD",
        "validityMonths": 12,
        "maxEmployees": 500,
        "isActive": true,
        "isPublic": true,
        "displayOrder": 4,
        "isCustom": false,
        "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT", "ADVANCED_ATTENDANCE", "PAYROLL", "API_ACCESS", "WHITE_LABEL"]
    }
]
```

---

### 6. Get Plan by ID
Retrieves details of a subscription plan by ID.

- **Method**: `GET`
- **URL**: `/api/platform/subscription-plans/{id}`
- **Headers**:
  - `Authorization: Bearer <accessToken>`

#### Response Example (200 OK):
```json
{
    "id": 3,
    "code": "PREMIUM",
    "name": "Premium Plan",
    "description": "Full feature set for medium businesses",
    "price": 79.00,
    "currency": "USD",
    "validityMonths": 1,
    "maxEmployees": 100,
    "isActive": true,
    "isPublic": true,
    "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT", "ADVANCED_ATTENDANCE", "PAYROLL", "API_ACCESS"]
}
```

---

### 7. Get Plan by Code
Retrieves details of a subscription plan by its code.

- **Method**: `GET`
- **URL**: `/api/platform/subscription-plans/code/{code}`
- **Headers**:
  - `Authorization: Bearer <accessToken>`

#### Response Example (200 OK):
(Same payload format as Get Plan by ID)

---

### 8. Activate New Subscription
Activates a new subscription plan for the tenant.

- **Method**: `POST`
- **URL**: `/api/tenant/subscriptions/activate?planId={planId}`
- **Headers**:
  - `Authorization: Bearer <accessToken>`
- **Request Body**: None

#### Response Example (200 OK):
```json
{
    "id": 10,
    "planType": "Premium Plan",
    "planName": "Premium Plan",
    "planStatus": "ACTIVE",
    "maxEmployees": 100,
    "startedAt": "2026-07-22T10:30:00.000000",
    "endsAt": "2026-08-22T10:30:00.000000",
    "amount": 79.00,
    "currency": "USD",
    "isActive": true,
    "createdAt": "2026-07-22T10:30:00.000000",
    "billingPeriodStart": "2026-07-22T10:30:00.000000",
    "billingPeriodEnd": "2026-08-22T10:30:00.000000",
    "gracePeriodEnd": null,
    "cancellationReason": null,
    "cancelledAtEndOfPeriod": false,
    "activeSubscription": true,
    "expired": false
}
```

---

### 9. Reactivate Subscription (For Expired)
Reactivates an expired subscription plan for the tenant.

- **Method**: `POST`
- **URL**: `/api/tenant/subscriptions/reactivate?planId={planId}`
- **Headers**:
  - `Authorization: Bearer <accessToken>`
- **Request Body**: None

#### Response Example (200 OK):
```json
{
    "id": 11,
    "planType": "Basic Plan",
    "planName": "Basic Plan",
    "planStatus": "ACTIVE",
    "maxEmployees": 25,
    "startedAt": "2026-07-22T10:35:00.000000",
    "endsAt": "2026-08-22T10:35:00.000000",
    "amount": 29.00,
    "currency": "USD",
    "isActive": true,
    "createdAt": "2026-07-22T10:35:00.000000",
    "billingPeriodStart": "2026-07-22T10:35:00.000000",
    "billingPeriodEnd": "2026-08-22T10:35:00.000000",
    "gracePeriodEnd": null,
    "cancellationReason": null,
    "cancelledAtEndOfPeriod": false,
    "activeSubscription": true,
    "expired": false
}
```

---

### 10. Renew Subscription
Renews the current active subscription.

- **Method**: `POST`
- **URL**: `/api/tenant/subscriptions/renew`
- **Headers**:
  - `Authorization: Bearer <accessToken>`
- **Request Body**: None

#### Response Example (200 OK):
```json
{
    "id": 12,
    "planType": "Premium Plan",
    "planName": "Premium Plan",
    "planStatus": "ACTIVE",
    "maxEmployees": 100,
    "startedAt": "2026-07-21T20:29:00.53977",
    "endsAt": "2026-09-21T20:29:00.53977",
    "amount": 79.00,
    "currency": "USD",
    "isActive": true,
    "createdAt": "2026-07-22T10:40:00.000000",
    "billingPeriodStart": "2026-08-21T20:29:00.53977",
    "billingPeriodEnd": "2026-09-21T20:29:00.53977",
    "gracePeriodEnd": null,
    "cancellationReason": null,
    "cancelledAtEndOfPeriod": false,
    "activeSubscription": true,
    "expired": false
}
```

---

### 11. Upgrade Subscription
Upgrades the current active subscription to a new plan type.

- **Method**: `POST`
- **URL**: `/api/tenant/subscriptions/upgrade`
- **Headers**:
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`
- **Request Body**:
```json
{
    "planType": "PREMIUM"
}
```

#### Response Example (200 OK):
```json
{
    "id": 13,
    "planType": "Premium Plan",
    "planName": "Premium Plan",
    "planStatus": "ACTIVE",
    "maxEmployees": 100,
    "startedAt": "2026-07-22T10:45:00.000000",
    "endsAt": "2026-08-22T10:45:00.000000",
    "amount": 79.00,
    "currency": "USD",
    "isActive": true,
    "createdAt": "2026-07-22T10:45:00.000000",
    "billingPeriodStart": "2026-07-22T10:45:00.000000",
    "billingPeriodEnd": "2026-08-22T10:45:00.000000",
    "gracePeriodEnd": null,
    "cancellationReason": null,
    "cancelledAtEndOfPeriod": false,
    "activeSubscription": true,
    "expired": false
}
```

---

### 12. Cancel Subscription
Cancels the current active subscription.

- **Method**: `POST`
- **URL**: `/api/tenant/subscriptions/cancel?reason={reason}`
- **Headers**:
  - `Authorization: Bearer <accessToken>`
- **Request Body**: None

#### Response Example (200 OK):
```json
{
    "id": 13,
    "planType": "Premium Plan",
    "planName": "Premium Plan",
    "planStatus": "CANCELLED",
    "maxEmployees": 100,
    "startedAt": "2026-07-22T10:45:00.000000",
    "endsAt": "2026-08-22T10:45:00.000000",
    "amount": 79.00,
    "currency": "USD",
    "isActive": false,
    "createdAt": "2026-07-22T10:45:00.000000",
    "billingPeriodStart": "2026-07-22T10:45:00.000000",
    "billingPeriodEnd": "2026-08-22T10:45:00.000000",
    "gracePeriodEnd": null,
    "cancellationReason": "CUSTOMER_REQUEST",
    "cancelledAtEndOfPeriod": false,
    "activeSubscription": false,
    "expired": true
}
```

---

### 13. Get Subscription History
Retrieves paginated subscription event logs for the tenant.

- **Method**: `GET`
- **URL**: `/api/tenant/subscriptions/history?page=0&size=20&sort=createdAt,desc`
- **Headers**:
  - `Authorization: Bearer <accessToken>`

#### Response Example (200 OK):
```json
{
    "content": [
        {
            "id": 13,
            "planType": "Premium Plan",
            "planName": "Premium Plan",
            "planStatus": "CANCELLED",
            "maxEmployees": 100,
            "startedAt": "2026-07-22T10:45:00.000000",
            "endsAt": "2026-08-22T10:45:00.000000",
            "amount": 79.00,
            "currency": "USD",
            "isActive": false,
            "createdAt": "2026-07-22T10:45:00.000000",
            "billingPeriodStart": "2026-07-22T10:45:00.000000",
            "billingPeriodEnd": "2026-08-22T10:45:00.000000",
            "gracePeriodEnd": null,
            "cancellationReason": "CUSTOMER_REQUEST",
            "cancelledAtEndOfPeriod": false
        },
        {
            "id": 12,
            "planType": "Premium Plan",
            "planName": "Premium Plan",
            "planStatus": "ACTIVE",
            "maxEmployees": 100,
            "startedAt": "2026-07-21T20:29:00.53977",
            "endsAt": "2026-09-21T20:29:00.53977",
            "amount": 79.00,
            "currency": "USD",
            "isActive": true,
            "createdAt": "2026-07-22T10:40:00.000000",
            "billingPeriodStart": "2026-08-21T20:29:00.53977",
            "billingPeriodEnd": "2026-09-21T20:29:00.53977",
            "gracePeriodEnd": null,
            "cancellationReason": null,
            "cancelledAtEndOfPeriod": false
        }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "size": 20,
    "number": 0
}
```

---

### 14. Sync Tenant Subscription Status
Synchronizes tenant status manually.

- **Method**: `POST`
- **URL**: `/api/tenant/subscriptions/sync-status`
- **Headers**:
  - `Authorization: Bearer <accessToken>`
- **Request Body**: None

#### Response Example (200 OK):
```json
{
    "success": true,
    "message": "Tenant status synchronized successfully"
}
```

---

### 15. Admin - Get All Plans
Retrieves all subscription plans.

- **Method**: `GET`
- **URL**: `/api/admin/subscription-plans`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
List of all `SubscriptionPlanDTO` objects.

---

### 16. Admin - Create Plan
Creates a new subscription plan.

- **Method**: `POST`
- **URL**: `/api/admin/subscription-plans`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`
  - `Content-Type: application/json`
- **Request Body**:
```json
{
    "code": "PRO",
    "name": "Pro Plan",
    "description": "Professional plan with advanced features",
    "price": 49.99,
    "currency": "USD",
    "validityMonths": 1,
    "maxEmployees": 75,
    "isActive": true,
    "isPublic": true,
    "displayOrder": 5,
    "isCustom": false,
    "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT", "ADVANCED_ATTENDANCE", "REPORTING"]
}
```

#### Response Example (201 Created):
```json
{
    "id": 5,
    "code": "PRO",
    "name": "Pro Plan",
    "description": "Professional plan with advanced features",
    "price": 49.99,
    "currency": "USD",
    "validityMonths": 1,
    "maxEmployees": 75,
    "isActive": true,
    "isPublic": true,
    "displayOrder": 5,
    "isCustom": false,
    "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT", "ADVANCED_ATTENDANCE", "REPORTING"],
    "createdAt": "2026-07-22T11:00:00.000000",
    "updatedAt": "2026-07-22T11:00:00.000000",
    "deletedAt": null
}
```

---

### 17. Admin - Update Plan
Updates an existing subscription plan by ID.

- **Method**: `PUT`
- **URL**: `/api/admin/subscription-plans/{id}`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`
  - `Content-Type: application/json`
- **Request Body**:
```json
{
    "code": "PRO",
    "name": "Pro Plus Plan",
    "description": "Professional plan with advanced features",
    "price": 69.99,
    "currency": "USD",
    "validityMonths": 1,
    "maxEmployees": 150,
    "isActive": true,
    "isPublic": true,
    "displayOrder": 5,
    "isCustom": false,
    "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT", "ADVANCED_ATTENDANCE", "REPORTING", "API_ACCESS"]
}
```

#### Response Example (200 OK):
Updated `SubscriptionPlanDTO` object.

---

### 18. Admin - Delete Plan (Soft Delete)
Soft deletes a subscription plan.

- **Method**: `DELETE`
- **URL**: `/api/admin/subscription-plans/{id}`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (204 No Content):
No body returned.

---

### 19. Admin - Restore Plan
Restores a soft-deleted subscription plan.

- **Method**: `PATCH`
- **URL**: `/api/admin/subscription-plans/{id}/restore`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
Restored `SubscriptionPlanDTO` object.

---

### 20. Admin - Toggle Plan Active
Toggles the active state of a subscription plan.

- **Method**: `PATCH`
- **URL**: `/api/admin/subscription-plans/{id}/toggle-active`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
Updated `SubscriptionPlanDTO` with toggled `isActive` boolean value.

---

### 21. Admin - Get Plan History
Retrieves operation logs for a specific subscription plan.

- **Method**: `GET`
- **URL**: `/api/admin/subscription-plans/{id}/history`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
```json
[
    {
        "id": 1,
        "tenantId": 1,
        "planId": 5,
        "planCode": "PRO",
        "planName": "Pro Plus Plan",
        "eventType": "PLAN_UPDATED",
        "eventDate": "2026-07-22T11:05:00.000000",
        "amount": 69.99,
        "currency": "USD",
        "previousPlanId": null,
        "previousPlanCode": null,
        "previousPlanName": null,
        "previousPrice": 49.99,
        "newPrice": 69.99,
        "previousMaxEmployees": 75,
        "newMaxEmployees": 150,
        "previousValidityMonths": 1,
        "newValidityMonths": 1,
        "fieldChanged": "price,maxEmployees",
        "oldValue": "$49.99 - 75 employees",
        "newValue": "$69.99 - 150 employees",
        "triggerSource": "ADMIN",
        "triggeredById": null,
        "createdBy": "admin@techcorp.com",
        "createdAt": "2026-07-22T11:05:00.000000"
    },
    {
        "id": 2,
        "tenantId": 1,
        "planId": 5,
        "planCode": "PRO",
        "planName": "Pro Plan",
        "eventType": "PLAN_CREATED",
        "eventDate": "2026-07-22T11:00:00.000000",
        "amount": 49.99,
        "currency": "USD",
        "previousPlanId": null,
        "previousPlanCode": null,
        "previousPlanName": null,
        "previousPrice": null,
        "newPrice": 49.99,
        "previousMaxEmployees": null,
        "newMaxEmployees": 75,
        "fieldChanged": null,
        "oldValue": null,
        "newValue": null,
        "triggerSource": "ADMIN",
        "triggeredById": null,
        "createdBy": "admin@techcorp.com",
        "createdAt": "2026-07-22T11:00:00.000000"
    }
]
```

---

### 22. Admin - Get All Plan History
Retrieves all plan operations log history.

- **Method**: `GET`
- **URL**: `/api/admin/subscription-plans/history`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
List of all `PlanOperationLogDTO` objects.

---

### 23. Admin - Search Plan Operations
Searches plan operation logs with query parameter filters.

- **Method**: `GET`
- **URL**: `/api/admin/subscription-plans/history/search`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`
- **Query Parameters**:
  - `planCode` (string, optional)
  - `eventType` (string, optional: `PLAN_CREATED`, `PLAN_UPDATED`, `PLAN_DELETED`, `PLAN_RESTORED`, `PLAN_TOGGLED`)
  - `startDate` (string, optional, `yyyy-MM-dd`)
  - `endDate` (string, optional, `yyyy-MM-dd`)
  - `page` (number, default: `0`)
  - `size` (number, default: `20`)
  - `sort` (string, default: `eventDate,desc`)

#### Response Example (200 OK):
Page of `PlanOperationLogDTO` objects.

---

### 24. Admin - Get Active Plans
Retrieves all active subscription plans.

- **Method**: `GET`
- **URL**: `/api/admin/subscription-plans/active`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
List of active `SubscriptionPlanDTO` objects.

---

### 25. Admin - Get Deleted Plans
Retrieves all soft-deleted subscription plans.

- **Method**: `GET`
- **URL**: `/api/admin/subscription-plans/deleted`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
List of deleted `SubscriptionPlanDTO` objects.

---

### 26. Admin - Get Plan Statistics
Retrieves statistical summary metrics for subscription plans.

- **Method**: `GET`
- **URL**: `/api/admin/subscription-plans/statistics`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
```json
{
    "totalPlans": 5,
    "activePlans": 4,
    "deletedPlans": 1,
    "publicPlans": 4,
    "customPlans": 0,
    "freePlans": 1,
    "averagePrice": 59.99,  
    "minPrice": 0.00,
    "maxPrice": 199.00
}
```

---

### 27. Admin - Add Feature to Plan
Adds a feature to a subscription plan.

- **Method**: `POST`
- **URL**: `/api/admin/subscription-plans/{id}/features?featureCode={featureCode}&description={description}`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
Updated `SubscriptionPlanDTO` object with added feature.

---

### 28. Admin - Remove Feature from Plan
Removes a feature from a subscription plan.

- **Method**: `DELETE`
- **URL**: `/api/admin/subscription-plans/{id}/features/{featureCode}`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
Updated `SubscriptionPlanDTO` object with feature removed.

---

### 29. Admin - Bulk Create Plans
Creates multiple subscription plans in bulk.

- **Method**: `POST`
- **URL**: `/api/admin/subscription-plans/bulk`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`
  - `Content-Type: application/json`
- **Request Body**:
```json
[
    {
        "code": "TEAM",
        "name": "Team Plan",
        "description": "Team plan for small teams",
        "price": 39.99,
        "currency": "USD",
        "validityMonths": 1,
        "maxEmployees": 50,
        "isActive": true,
        "isPublic": true,
        "displayOrder": 6,
        "isCustom": false,
        "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT", "ADVANCED_ATTENDANCE"]
    },
    {
        "code": "BUSINESS",
        "name": "Business Plan",
        "description": "Business plan for medium businesses",
        "price": 99.99,
        "currency": "USD",
        "validityMonths": 1,
        "maxEmployees": 200,
        "isActive": true,
        "isPublic": true,
        "displayOrder": 7,
        "isCustom": false,
        "features": ["BASIC_ACCESS", "EMPLOYEE_MANAGEMENT", "ADVANCED_ATTENDANCE", "PAYROLL", "API_ACCESS", "REPORTING"]
    }
]
```

#### Response Example (200 OK):
List of created `SubscriptionPlanDTO` objects.

---

### 30. Admin - Bulk Delete Plans
Bulk deletes multiple subscription plans.

- **Method**: `DELETE`
- **URL**: `/api/admin/subscription-plans/bulk?ids=5,6,7`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (204 No Content):
No content returned.

---

### 31. Admin - Validate Plan Code
Checks if a plan code is available.

- **Method**: `GET`
- **URL**: `/api/admin/subscription-plans/validate/{code}`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
```json
{
    "available": false,
    "message": "Plan code 'PRO' is already taken"
}
```

---

### 32. Admin - Restore Tenant
Restores a soft-deleted or archived tenant/organization and sets their subscription plan.

- **Method**: `POST`
- **URL**: `/api/admin/tenants/{tenantId}/restore`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`
  - `Content-Type: application/json`
- **Request Body**:
```json
{
    "planId": 3,
    "notes": "Restoring tenant after subscription expiry"
}
```

#### Response Example (200 OK):
```json
{
    "id": 14,
    "planType": "Premium Plan",
    "planName": "Premium Plan",
    "planStatus": "ACTIVE",
    "maxEmployees": 100,
    "startedAt": "2026-07-22T11:30:00.000000",
    "endsAt": "2026-08-22T11:30:00.000000",
    "amount": 79.00,
    "currency": "USD",
    "isActive": true,
    "createdAt": "2026-07-22T11:30:00.000000",
    "billingPeriodStart": "2026-07-22T11:30:00.000000",
    "billingPeriodEnd": "2026-08-22T11:30:00.000000",
    "gracePeriodEnd": null,
    "cancellationReason": null,
    "cancelledAtEndOfPeriod": false,
    "activeSubscription": true,
    "expired": false
}
```

---

### 33. Admin - Get Restore History
Retrieves the restoration event logs for a specific tenant.

- **Method**: `GET`
- **URL**: `/api/admin/tenants/{tenantId}/restore-history`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
```json
[
    {
        "id": 1,
        "oldValue": "ARCHIVED",
        "newValue": "RETAINED",
        "createdAt": "2026-07-22T11:30:00.000000",
        "performedByEmail": "admin@techcorp.com",
        "notes": "Restoring tenant after subscription expiry",
        "planName": "Premium Plan"
    }
]
```

---

### 34. Admin - Get Tenant Subscription History
Retrieves all historical subscription plans for a specific tenant.

- **Method**: `GET`
- **URL**: `/api/admin/tenants/{tenantId}/subscription-history`
- **Headers**:
  - `Authorization: Bearer <adminAccessToken>`

#### Response Example (200 OK):
List of `TenantSubscriptionResponseDTO` objects.
