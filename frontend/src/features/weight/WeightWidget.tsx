import { dateGroupLabel } from '../logs/timelineDisplay'
import type { WeightPoint } from '../../types/weight'
import { formatKg } from './weightDisplay'

interface WeightWidgetProps {
  latest: WeightPoint | null
  loading?: boolean
}

export function WeightWidget({ latest, loading }: WeightWidgetProps) {
  if (loading && !latest) {
    return (
      <div className="weight-widget" aria-busy="true">
        <p className="weight-widget__empty">Loading weight…</p>
      </div>
    )
  }

  if (!latest) {
    return (
      <div className="weight-widget">
        <p className="weight-widget__empty">No weight yet. Log kg below.</p>
      </div>
    )
  }

  return (
    <div className="weight-widget">
      <p className="weight-widget__value" aria-live="polite">
        {formatKg(latest.value)}
      </p>
      <p className="weight-widget__date">{dateGroupLabel(latest.localDate)}</p>
    </div>
  )
}
