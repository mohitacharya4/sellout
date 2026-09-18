# Sellout — Problem Brief (Domain Deep-Dive)

Read this before designing a service or writing a spec. It defines *what good looks like* for a **flash-sale ticketing platform** — "100,000 people press *Buy* for 5,000 seats in the same second" — and why *fairness*, *never overselling*, and *surviving an unreliable payment provider* are the whole game.

> **Tagline:** *Sell out in seconds. Oversell never.*

---

## 1. The problem in one paragraph

When a popular event goes on sale, demand arrives as a wall, not a curve: tens or hundreds of thousands of buyers hit the same few thousand seats within the first second, and traffic stays at that level until the seats are gone. A naive system — one that reads a seat's status, sees "available", and writes "held" — falls over in three ways at once. It **oversells**, because two requests read "available" before either writes. It is **unfair**, because whoever has the fastest bot wins and real fans get a spinning wheel. And it **loses money or seats**, because the payment provider times out, retries, or sends a duplicate webhook and the system either charges twice or lets a paid seat lapse. The scale is not the hard part; anyone can add replicas. The hard part is that under this load, the *correctness* properties a ticketing business lives on — one seat, one buyer, one charge — are exactly the ones that a shared database, a retry, or a slow network breaks. The real job Sellout does: *admit buyers fairly at a rate the backend can sustain, reserve seats with a guarantee that two buyers can never hold the same seat, run the checkout as a saga that always reaches a terminal state, and prove all of it with metrics that stay green during a 100k-user spike.*

---

## 2. Who feels the pain (and who pays)

- **Fans.** They queued at 9:59, pressed *Buy* at 10:00:00, and got an error page while a scalper's script bought forty seats. Every failed sale is a public relations event.
- **Organizers and venues.** Oversold seats mean refunds, staff at the door turning away people with valid tickets, and a venue that will not book again. Unsold seats that were "held" by abandoned carts and never released are lost revenue.
- **Finance and support.** Every double charge is a chargeback plus a support ticket. Every "I paid but got no ticket" is an hour of manual reconciliation.
- **The engineers on call.** A sale is a scheduled incident. Autoscaling that lags the spike by ninety seconds is autoscaling that missed the sale.

**Who pays (the market this maps to):** primary ticketing is a concentrated, high-margin category (Ticketmaster, AXS, DICE, Eventbrite, SeatGeek) and every one of them has had a public sellout failure. Secondary use of the same architecture — sneaker drops, limited-edition launches, course enrolment, vaccine appointment slots — is anywhere a fixed inventory meets a demand spike. Pricing: per-ticket fee plus a platform fee to the organizer.

---

## 3. How a sale actually unfolds (the loop Sellout implements)

For one buyer, the system does:

1. **Authenticate.** Who is this, and are they allowed to buy for this event? A queue position must be tied to a real, logged-in account or bots simply open ten thousand tabs.
2. **Admit.** Put the buyer in a per-event waiting room and release buyers at the rate the inventory service can sustain. Fairness means *first come, first served within the arrival window*, and the position must be visible so the buyer trusts the wait.
3. **Hold.** The admitted buyer picks seats. Each seat is reserved for a short, fixed window (a few minutes) with a guarantee that no other buyer can hold it. Holds that expire are released, precisely, so seats go back on sale.
4. **Pay.** Hand the hold to a payment provider that may be slow, may fail, and may confirm the same payment twice. The order must survive every one of those without charging twice or losing the seat.
5. **Confirm and issue.** On successful payment, convert the hold into a sold seat and issue a ticket. On failure or timeout, release the hold and refund anything captured.
6. **Notify.** Send the ticket. Exactly once, even if the message that triggered it is delivered twice.

That loop — **authenticate → admit → hold → pay → confirm or compensate → notify** — is the workflow Sellout implements. It is *not* "put the seats in a table and add more pods."

---

## 4. The four things that separate a real ticketing system from a toy

These are the design center of Sellout, and the honest "what most projects miss":

- **Never oversell, proven under contention.** Two hundred concurrent requests for the same seat must produce exactly one hold. This has to be true at the database, not just in application code: a conditional update on the seat row plus a unique constraint on `(event, seat)` for sold tickets is the last line of defence, and a fast Redis pre-check keeps the database from being the bottleneck. It must be *tested* with real threads racing a real Postgres, and *measured* with an `oversell_total` metric that stays at zero during a load test.

- **Fairness and load shedding at the edge.** If every request reaches the inventory service, the fastest client wins and the service dies. A virtual waiting room turns a spike into a queue, releases buyers at a controlled rate, and hands each one a short-lived, signed **admission token** bound to their account and the event. The token is what lets them hold seats. Positions cannot be shared, replayed, or bought in bulk.

- **A checkout that always finishes.** Payment is a remote call to a provider you do not control. It times out, returns 5xx, and sends webhooks late and twice. The checkout is a **saga** with explicit states, idempotency keys on every side effect, timeouts that trigger compensation, and a transactional outbox so an event is published if and only if the state change committed. "Stuck order" is a metric with an alert, not a support ticket.

- **Observability that proves the claims.** A sellout is a five-minute event. If the dashboard cannot show admission rate, queue depth, active holds, hold p99, saga durations, and oversell count *during* the spike, the system's correctness is a belief, not a fact. Dashboards, alert rules, and SLOs are committed as code and the load test is part of the repository.

---

## 5. What "good" looks like (acceptance bar)

A sale is *good* when:

1. **Zero oversells.** A reconciliation job after every load test finds no seat with more than one confirmed ticket and no ticket without a paid order. The `oversell_total` counter reads zero.
2. **Fair and fast admission.** Buyers are admitted in arrival order at a rate the backend sustains; seat-hold p99 stays under the published target at the target request rate.
3. **No leaked seats.** Every expired hold is released within the TTL plus a small, bounded grace period. Held-but-abandoned seats return to sale.
4. **Every order terminates.** Every saga reaches `CONFIRMED`, `CANCELLED`, or `FAILED` within its deadline. There are no orders stuck in `PAYMENT_PENDING` an hour later.
5. **The payment provider cannot hurt us.** Timeouts, 5xx responses, and duplicate or late webhooks never cause a double charge, a lost seat, or a ticket without payment.
6. **It scales on its own.** Pods scale on queue depth and Kafka lag before the spike saturates them, and the dashboard shows it happening.

If Sellout hits that bar *while 100,000 virtual users hammer 5,000 seats and the payment simulator injects faults*, it clears the bar for a senior engineer building large-scale distributed systems and is something a real ticketing business could run.
