import type { LogItemResponse } from '../../types/log'
import { LogListItem } from './LogListItem'
import { dateGroupLabel, localDateKey } from './timelineDisplay'

interface TimelineProps {
  logs: LogItemResponse[]
  loading?: boolean
  highlightId?: string | null
  scrollKey?: number
}

interface DateGroup {
  key: string
  label: string
  logs: LogItemResponse[]
}

export function Timeline({ logs, loading, highlightId, scrollKey }: TimelineProps) {
  if (loading && logs.length === 0) {
    return <p className="timeline__empty">Loading timeline…</p>
  }

  if (logs.length === 0) {
    return <p className="timeline__empty">No logs yet. Write something above.</p>
  }

  const groups = groupByLocalDate(logs)

  return (
    <div className="timeline">
      {groups.map((group) => (
        <section key={group.key} className="timeline-day" aria-label={group.label}>
          <h3 className="timeline-day__title">
            {group.label}
            <span className="timeline-day__count">{group.logs.length}</span>
          </h3>
          <ul className="timeline-day__list">
            {group.logs.map((log) => (
              <LogListItem
                key={log.id}
                log={log}
                highlighted={log.id === highlightId}
                scrollKey={scrollKey}
              />
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

function groupByLocalDate(logs: LogItemResponse[]): DateGroup[] {
  const groups: DateGroup[] = []
  for (const log of logs) {
    const key = localDateKey(log.timestamp)
    const last = groups[groups.length - 1]
    if (last && last.key === key) {
      last.logs.push(log)
    } else {
      groups.push({
        key,
        label: dateGroupLabel(key),
        logs: [log],
      })
    }
  }
  return groups
}
