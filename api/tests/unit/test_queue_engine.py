import pytest

from schemas import DEFAULT_QUEUE_CONFIG, PriorityCategory, PriorityWeights, QueueConfig
from services.queue_engine import Candidate, recompute_effective_queue

CONFIG = DEFAULT_QUEUE_CONFIG


def ticket(n, waited=0, category="none", confirmed=False, late=False):
    return Candidate(
        id=f"T{n}",
        sequence_no=n,
        waited_minutes=waited,
        category=PriorityCategory(category),
        staff_confirmed=confirmed,
        late=late,
    )


def order(tickets, config=CONFIG):
    return recompute_effective_queue(tickets, config).order


def config(**changes) -> QueueConfig:
    return CONFIG.model_copy(update=changes)


class TestFifo:
    def test_orders_by_sequence_number(self):
        assert order([ticket(3), ticket(1), ticket(2)]) == ["T1", "T2", "T3"]

    def test_empty_queue(self):
        assert order([]) == []


class TestPriorityJump:
    def test_urgent_ticket_jumps_to_the_front(self):
        tickets = [ticket(1), ticket(2), ticket(3, category="urgent", confirmed=True), ticket(4), ticket(5)]

        assert order(tickets) == ["T3", "T1", "T2", "T4", "T5"]

    def test_unconfirmed_request_does_not_move(self):
        tickets = [ticket(1), ticket(2), ticket(3, category="urgent", confirmed=False)]

        assert order(tickets) == ["T1", "T2", "T3"]

    def test_confirmed_without_a_category_does_not_move(self):
        assert order([ticket(1), ticket(2, confirmed=True)]) == ["T1", "T2"]

    def test_jump_is_limited_by_the_category_weight(self):
        tickets = [ticket(n) for n in range(1, 8)] + [ticket(8, category="elderly", confirmed=True)]

        # Elderly is worth 3 places, from index 7 to index 4.
        assert order(tickets) == ["T1", "T2", "T3", "T4", "T8", "T5", "T6", "T7"]

    def test_jump_is_capped_by_the_max_jump(self):
        tickets = [ticket(n) for n in range(1, 8)] + [ticket(8, category="urgent", confirmed=True)]

        result = order(tickets, config(priority_max_jump_k=2))

        assert result.index("T8") == 5

    def test_zero_weight_category_never_moves(self):
        weights = PriorityWeights(elderly=0, pregnant=3, disability=3, urgent=6)
        tickets = [ticket(1), ticket(2, category="elderly", confirmed=True)]

        assert order(tickets, config(priority_weights=weights)) == ["T1", "T2"]


class TestStarvationGuard:
    def test_a_protected_ticket_cannot_be_passed(self):
        tickets = [ticket(1, waited=100), ticket(2), ticket(3, category="urgent", confirmed=True)]

        assert order(tickets) == ["T1", "T3", "T2"]

    def test_just_under_the_threshold_can_be_passed(self):
        tickets = [ticket(1, waited=99.9), ticket(2, category="urgent", confirmed=True)]

        assert order(tickets) == ["T2", "T1"]

    def test_a_protected_ticket_in_the_middle_of_the_jump_is_not_passed_either(self):
        tickets = [ticket(1), ticket(2, waited=150), ticket(3), ticket(4, category="pregnant", confirmed=True)]

        # Pregnant is worth 3, which would reach the front, but it has to stop behind T2.
        assert order(tickets) == ["T1", "T2", "T4", "T3"]

    def test_reports_who_is_protected(self):
        result = recompute_effective_queue([ticket(1, waited=120), ticket(2)], CONFIG)

        assert result.protected == {"T1"}


class TestLateReturn:
    def test_late_ticket_goes_to_the_back_when_the_penalty_exceeds_the_queue(self):
        tickets = [ticket(7, late=True), ticket(8), ticket(9), ticket(10)]

        assert order(tickets) == ["T8", "T9", "T10", "T7"]

    def test_late_ticket_is_pushed_back_by_the_penalty(self):
        tickets = [ticket(n, late=n == 1) for n in range(1, 10)]

        # Penalty 5 from index 0 puts it at index 5.
        assert order(tickets).index("T1") == 5

    @pytest.mark.parametrize("penalty", [0, 1, 3])
    def test_penalty_is_configurable(self, penalty):
        tickets = [ticket(n, late=n == 1) for n in range(1, 6)]

        assert order(tickets, config(late_requeue_penalty_p=penalty)).index("T1") == penalty

    def test_priority_jump_happens_before_the_late_penalty(self):
        tickets = [ticket(1), ticket(2), ticket(3, category="urgent", confirmed=True, late=True)]

        # Jumps to the front, then is pushed back 5 places, which is capped at the end.
        assert order(tickets) == ["T1", "T2", "T3"]
