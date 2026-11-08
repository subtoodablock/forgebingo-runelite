package com.forgebingo.core;

/** Outcome of one HTTP call. {@code status == 0} means the request never completed. */
public final class ApiResult<T>
{
    public final int status;
    public final T body;
    /** Human-readable message. */
    public final String error;
    /** Machine code from the server's error body, e.g. invalid_rsn. */
    public final String errorCode;
    public final String etag;
    public final long retryAfterMillis;

    public ApiResult(int status, T body, String error, String errorCode, String etag, long retryAfterMillis)
    {
        this.status = status;
        this.body = body;
        this.error = error;
        this.errorCode = errorCode;
        this.etag = etag;
        this.retryAfterMillis = retryAfterMillis;
    }

    public ErrorClass errorClass()
    {
        return ErrorClass.of(status, errorCode);
    }

    public boolean ok()
    {
        return status >= 200 && status < 300;
    }

    public boolean notModified()
    {
        return status == 304;
    }

    /** True when this should leave a user-visible failure (not 304, not success). */
    public String describe()
    {
        if (status == 0)
        {
            return error == null ? "network error" : error;
        }
        String head = errorCode != null ? errorCode : error;
        return "HTTP " + status + (head == null ? "" : ": " + head) + (errorCode != null && error != null ? " (" + error + ")" : "");
    }
}
