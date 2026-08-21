import { useEffect, useRef } from 'react'
import type { LogItemResponse } from '../../types/log'
import { detectedFoodItems } from './detectedFoodItems'
import { formatEventTimeLabel } from './timelineDisplay'

interface LogListItemProps {
  log: LogItemResponse
  highlighted?: boolean
  /** Changing this value while highlighted scrolls the entry into view. */
  scrollKey?: number
}

export function LogListItem({ log, highlighted, scrollKey }: LogListItemProps) {
  const ref = useRef<HTMLLIElement>(null)
  const timeLabel = formatEventTimeLabel(log.timestamp, log.eventTimePrecision)
  const hasPhoto = Boolean(log.imageRef)
  const items = detectedFoodItems(log)
  const showJson =
    items.length === 0 && Object.keys(log.structuredJson ?? {}).length > 0 && !hasPhoto
  const structured = showJson ? JSON.stringify(log.structuredJson) : null

  useEffect(() => {
    if (!highlighted || scrollKey == null) {
      return
    }
    ref.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [highlighted, scrollKey])

  return (
    <li ref={ref} className={`log-item${highlighted ? ' log-item--highlighted' : ''}`}>
      <div className="log-item__meta">
        <span className="log-item__type">
          {log.eventType}
          {hasPhoto ? <span className="log-item__badge">Photo</span> : null}
          {log.loggedLater ? (
            <span className="log-item__badge log-item__badge--later">Logged later</span>
          ) : null}
        </span>
        <time className="log-item__time" dateTime={log.timestamp}>
          {timeLabel}
        </time>
      </div>
      <p className="log-item__text">{log.rawText}</p>
      {hasPhoto ? (
        <img
          className="log-item__thumb"
          src={`/api/logs/${log.id}/image`}
          alt=""
        />
      ) : null}
      {items.length > 0 ? (
        <div className="detected-items">
          <p className="detected-items__title">Detected Items</p>
          <ul className="detected-items__list">
            {items.map((item) => (
              <li key={`${item.label}-${item.quantity ?? 'x'}`}>
                {item.quantity != null ? `${item.label} × ${item.quantity}` : item.label}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {structured ? <p className="log-item__structured">{structured}</p> : null}
    </li>
  )
}
