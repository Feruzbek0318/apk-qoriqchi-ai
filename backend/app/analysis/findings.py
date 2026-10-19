from dataclasses import asdict, dataclass
from typing import Literal

Severity = Literal["info", "low", "medium", "high"]


@dataclass(frozen=True)
class Finding:
    code: str
    severity: Severity
    evidence: str
    # "heuristic" findings are indicators, never confirmed detections.
    kind: Literal["heuristic", "reputation", "integrity"] = "heuristic"

    def to_dict(self) -> dict:
        return asdict(self)
