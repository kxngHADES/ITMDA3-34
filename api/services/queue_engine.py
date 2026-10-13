from dataclasses import dataclass

from schemas import PriorityCategory, QueueConfig


@dataclass(frozen=True)
class Candidate:
    # One waiting ticket, reduced to what the ordering needs.
    id: str
    sequence_no: int
    waited_minutes: float
    category: PriorityCategory = PriorityCategory.none
    staff_confirmed: bool = False
    late: bool = False


@dataclass(frozen=True)
class EffectiveQueue:
    order: list[str]
    protected: frozenset[str]


def _move(order: list[str], ticket_id: str, target: int) -> None:
    order.remove(ticket_id)
    order.insert(target, ticket_id)


def recompute_effective_queue(candidates: list[Candidate], config: QueueConfig) -> EffectiveQueue:
    # One ordered list per service point: first come first served by sequence number, then confirmed
    # priority tickets jump forward by a bounded amount, then late returns are pushed back.
    ordered = sorted(candidates, key=lambda c: c.sequence_no)
    order = [c.id for c in ordered]
    # A ticket that waited long enough can no longer be passed by a priority ticket.
    protected = {c.id for c in ordered if c.waited_minutes >= config.starvation_threshold_w_minutes}

    for c in ordered:
        # An unconfirmed request is visible to staff but never moves the ticket.
        if c.category is PriorityCategory.none or not c.staff_confirmed:
            continue
        index = order.index(c.id)
        jump = min(config.priority_weights.for_category(c.category), config.priority_max_jump_k)
        target = max(0, index - jump)
        # Stop right behind the nearest protected ticket in the jump range.
        for i in range(index - 1, target - 1, -1):
            if order[i] in protected:
                target = i + 1
                break
        if target < index:
            _move(order, c.id, target)

    for c in ordered:
        if not c.late:
            continue
        index = order.index(c.id)
        target = min(len(order) - 1, index + config.late_requeue_penalty_p)
        if target > index:
            _move(order, c.id, target)

    return EffectiveQueue(order=order, protected=frozenset(protected))
