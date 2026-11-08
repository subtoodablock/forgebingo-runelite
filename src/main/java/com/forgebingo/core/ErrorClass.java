package com.forgebingo.core;

/** How the queue should react to an HTTP outcome (docs/API.md section 8, "Limits and errors"). */
public enum ErrorClass
{
    /** 2xx. */
    SUCCESS,
    /** Network error, 408, 425, 429 (honour Retry-After), 5xx: retry with backoff. */
    TRANSIENT,
    /** 401 invalid_api_key: keep the events, pause sending, ask the user for a new key. */
    AUTH,
    /** 409 event_id_conflict: regenerate the eventId and retry. */
    CONFLICT,
    /**
     * 400 (invalid_payload, invalid_items, invalid_rsn, invalid_account_hash, unsupported_source_type,
     * occurred_at_out_of_range, invalid_json), 403 not_a_member (refresh the board list), 404, 413, 415 and
     * any other 4xx: the server will never accept this event. Drop it and tell the user.
     */
    PERMANENT;

    /** @param status HTTP status, or 0 for a network failure. */
    public static ErrorClass of(int status)
    {
        return of(status, null);
    }

    public static ErrorClass of(int status, String code)
    {
        if (status >= 200 && status < 300)
        {
            return SUCCESS;
        }
        if (status == 0 || status == 408 || status == 425 || status == 429 || status >= 500)
        {
            return TRANSIENT;
        }
        if (status == 401)
        {
            return AUTH;
        }
        if (status == 409 && "event_id_conflict".equals(code))
        {
            return CONFLICT;
        }
        return PERMANENT;
    }
}
