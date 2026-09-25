-- Reconcile derived daily counters from the immutable visitor event history.
WITH event_counts AS (
    SELECT
        event.post_detail_id,
        (event.occurred_at AT TIME ZONE 'UTC')::date AS summary_date,
        COUNT(*)::bigint AS view_count,
        COUNT(DISTINCT event.visitor_id)::bigint AS unique_visitor_count
    FROM blog.visitor_event event
    WHERE event.event_type = 'POST_VIEW'
      AND event.post_detail_id IS NOT NULL
    GROUP BY event.post_detail_id, (event.occurred_at AT TIME ZONE 'UTC')::date
)
INSERT INTO blog.visitor_daily_summary (
    id,
    summary_date,
    post_detail_id,
    landing_count,
    view_count,
    unique_visitor_count
)
SELECT
    md5(event_counts.post_detail_id::text || ':' || event_counts.summary_date::text)::uuid,
    event_counts.summary_date,
    event_counts.post_detail_id,
    event_counts.view_count,
    event_counts.view_count,
    event_counts.unique_visitor_count
FROM event_counts
ON CONFLICT (summary_date, post_detail_id) WHERE post_detail_id IS NOT NULL DO UPDATE
SET landing_count = EXCLUDED.landing_count,
    view_count = EXCLUDED.view_count,
    unique_visitor_count = EXCLUDED.unique_visitor_count;

UPDATE blog.visitor_daily_summary summary
SET landing_count = 0,
    view_count = 0,
    unique_visitor_count = 0
WHERE summary.post_detail_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM blog.visitor_event event
      WHERE event.event_type = 'POST_VIEW'
        AND event.post_detail_id = summary.post_detail_id
        AND (event.occurred_at AT TIME ZONE 'UTC')::date = summary.summary_date
  );
