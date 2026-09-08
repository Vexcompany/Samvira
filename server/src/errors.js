/**
 * Consistent API error model.
 *
 * Every error returned to a client is a stable, machine-readable code plus a
 * human-readable message. HTTP status carries coarse semantics; the code is
 * the authoritative discriminator the Android client maps on.
 */
export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }

  toBody() {
    return { error: { code: this.code, message: this.message } };
  }

  static malformed(message = 'Malformed request') {
    return new ApiError(400, 'MALFORMED_REQUEST', message);
  }

  static notFound(code, message) {
    return new ApiError(404, code, message);
  }

  static conflict(code, message) {
    return new ApiError(409, code, message);
  }

  static gone(code, message) {
    return new ApiError(410, code, message);
  }

  static unauthorized(code, message) {
    return new ApiError(401, code, message);
  }

  static forbidden(code, message) {
    return new ApiError(403, code, message);
  }
}
