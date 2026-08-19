export type ApiErrorBody = {
  status?: number;
  code?: string;
  message?: string;
  requestId?: string;
};

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
    public readonly requestId?: string,
  ) {
    super(message);
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("content-type")) {
    headers.set("content-type", "application/json");
  }
  const response = await fetch(`/api/proxy${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as ApiErrorBody;
    throw new ApiError(
      body.message ?? "The request could not be completed.",
      response.status,
      body.code ?? "REQUEST_FAILED",
      body.requestId,
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function messageFor(error: unknown) {
  return error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
}
