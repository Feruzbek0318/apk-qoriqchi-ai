"""Provider abstraction: every external lookup returns a ProviderOutcome, never raises."""
import asyncio
import logging
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

import httpx

log = logging.getLogger(__name__)


class Status(str, Enum):
    OK = "ok"
    NOT_FOUND = "not_found"  # provider has no record; NOT a clean result
    NOT_CONFIGURED = "not_configured"
    QUOTA = "quota_exceeded"
    AUTH_FAILED = "auth_failed"
    FAILED = "failed"
    UNAVAILABLE = "circuit_open"


@dataclass
class ProviderOutcome:
    provider: str
    status: Status
    data: dict[str, Any] = field(default_factory=dict)
    error: str | None = None
    fixture: bool = False

    @property
    def completed(self) -> bool:
        return self.status in (Status.OK, Status.NOT_FOUND)

    def summary(self) -> dict[str, Any]:
        out: dict[str, Any] = {"status": self.status.value}
        if self.error:
            out["error"] = self.error
        if self.fixture:
            out["fixture"] = True
        return out


class CircuitBreaker:
    """Opens after N consecutive transport/5xx failures so a dead provider doesn't stall every scan."""

    def __init__(self, threshold: int = 5, cooldown_s: float = 60.0) -> None:
        self.threshold, self.cooldown_s = threshold, cooldown_s
        self.failures, self.opened_at = 0, 0.0

    def allow(self) -> bool:
        if self.failures < self.threshold:
            return True
        return time.monotonic() - self.opened_at > self.cooldown_s

    def record(self, ok: bool) -> None:
        if ok:
            self.failures = 0
        else:
            self.failures += 1
            if self.failures >= self.threshold:
                self.opened_at = time.monotonic()


class HttpProvider:
    name = "provider"

    def __init__(self, client: httpx.AsyncClient, max_retries: int = 2, backoff_s: float = 0.5) -> None:
        self.client = client
        self.max_retries = max_retries
        self.backoff_s = backoff_s
        self.breaker = CircuitBreaker()

    async def request(self, method: str, url: str, **kwargs: Any) -> httpx.Response | ProviderOutcome:
        """Bounded retries with exponential backoff on transport errors and 5xx. 4xx are returned as-is."""
        if not self.breaker.allow():
            return ProviderOutcome(self.name, Status.UNAVAILABLE, error="Provider temporarily disabled after repeated failures")
        last_error = "unknown error"
        for attempt in range(self.max_retries + 1):
            try:
                resp = await self.client.request(method, url, **kwargs)
            except httpx.TimeoutException:
                last_error = "timeout"
            except httpx.TransportError as e:
                last_error = f"network error: {type(e).__name__}"
            else:
                if resp.status_code < 500:
                    self.breaker.record(True)
                    return resp
                last_error = f"HTTP {resp.status_code}"
            if attempt < self.max_retries:
                await asyncio.sleep(self.backoff_s * (2**attempt))
        self.breaker.record(False)
        log.warning("provider request failed", extra={"fields": {"provider": self.name, "error": last_error}})
        return ProviderOutcome(self.name, Status.FAILED, error=last_error)
