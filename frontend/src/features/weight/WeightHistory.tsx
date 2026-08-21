import { dateGroupLabel } from '../logs/timelineDisplay'
import type { WeightPoint } from '../../types/weight'
import { formatKg } from './weightDisplay'

interface WeightHistoryProps {
  points: WeightPoint[]
}

const HISTORY_LIMIT = 14

export function WeightHistory({ points }: WeightHistoryProps) {
  const recent = points.slice(-HISTORY_LIMIT).toReversed()

  return (
    <details className="weight-history">
      <summary className="weight-history__summary">Weight history</summary>
      {recent.length === 0 ? (
        <p className="weight-history__empty">No weigh-ins yet.</p>
      ) : (
        <ul className="weight-history__list">
          {recent.map((point) => (
            <li key={point.id} className="weight-history__item">
              <span>
                {dateGroupLabel(point.localDate)} — {formatKg(point.value)}
              </span>
              {point.loggedLater ? (
                <span className="log-item__badge log-item__badge--later">Logged later</span>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </details>
  )
}
