package io.github.nytka_app.ui.today

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.DayEvents
import io.github.nytka_app.core.api.EventsClient
import io.github.nytka_app.core.api.Evidence
import io.github.nytka_app.core.api.Insights
import io.github.nytka_app.core.api.InsightsClient
import io.github.nytka_app.core.api.ItemMarksClient
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.core.api.Salience
import io.github.nytka_app.core.api.Timeline
import io.github.nytka_app.core.api.TodayClient
import io.github.nytka_app.core.api.TodayPage

/** Answers what the test set; records every call as `"today Europe/Kyiv"` or `"timeline 2026-10-23 UTC"`. */
class FakeToday : TodayClient {
    var today: ApiResult<TodayPage> = ApiResult.Ok(TodayPage("2026-10-23"))
    val timelines = mutableMapOf<String, ApiResult<Timeline>>()
    val calls = mutableListOf<String>()

    override suspend fun today(zone: String): ApiResult<TodayPage> = today.also { calls += "today $zone" }

    override suspend fun timeline(
        date: String,
        zone: String,
    ): ApiResult<Timeline> {
        calls += "timeline $date $zone"
        return timelines[date] ?: ApiResult.Ok(Timeline(date))
    }
}

/** Answers what the test set per date; records every call as `"events 2026-10-23 UTC"`. */
class FakeEvents : EventsClient {
    val days = mutableMapOf<String, ApiResult<DayEvents>>()
    val calls = mutableListOf<String>()

    override suspend fun events(
        date: String,
        zone: String,
    ): ApiResult<DayEvents> {
        calls += "events $date $zone"
        return days[date] ?: ApiResult.Ok(DayEvents(date))
    }
}

class FakeInsights(
    var answer: ApiResult<Insights> = ApiResult.Ok(Insights()),
) : InsightsClient {
    var calls = 0

    override suspend fun insights(): ApiResult<Insights> = answer.also { calls++ }
}

/** Records marks as `"salience memory m1 noise"`, `"line 7 false"`, `"person 7 p2"`; [answer] is every reply. */
class FakeItemMarks : ItemMarksClient {
    var answer: ApiResult<Unit> = ApiResult.Ok(Unit)
    val marks = mutableListOf<String>()

    override suspend fun setSalience(
        kind: String,
        id: String,
        salience: String?,
    ): ApiResult<Unit> = answer.also { marks += "salience $kind $id $salience" }

    override suspend fun markLine(
        segmentId: Long,
        isUser: Boolean?,
    ): ApiResult<Unit> = answer.also { marks += "line $segmentId $isUser" }

    override suspend fun setLinePerson(
        segmentId: Long,
        personId: String?,
    ): ApiResult<Unit> = answer.also { marks += "person $segmentId $personId" }
}

fun item(
    id: String,
    at: String? = "2026-10-23T07:00:00Z",
    salience: String? = Salience.VITAL,
    kind: String = LifeItem.KIND_TASK,
    part: String? = null,
    sensitivity: String? = null,
    evidence: List<Evidence> = emptyList(),
) = LifeItem(kind, id, "Item $id", salience, at = at, part = part, sensitivity = sensitivity, evidence = evidence)

fun line(
    segmentId: Long,
    isUser: Boolean? = true,
) = Evidence(segmentId, "c1", "2026-10-23T07:00:00Z", "Quote $segmentId", "Wearer", isUser, wearerConfidence = 0.7)
